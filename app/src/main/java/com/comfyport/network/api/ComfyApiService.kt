package com.comfyport.network.api

import com.comfyport.network.AppLogger
import com.comfyport.network.UrlValidator
import com.comfyport.network.http.ComfyHttpClient
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

import com.comfyport.data.ConnectionTestResult
import com.comfyport.data.MachineHistoryItem

/**
 * Handles all HTTP REST calls to a ComfyUI server.
 * Pure suspend functions with typed return values — no global state.
 */
object ComfyApiService {

    private val http get() = ComfyHttpClient.okHttp
    private val gson get() = ComfyHttpClient.gson

    // ── Connection test ────────────────────────────────────────────────

    suspend fun testConnection(serverUrl: String): ConnectionTestResult = withContext(Dispatchers.IO) {
        val normalized = UrlValidator.normalizeUrl(serverUrl)
        if (normalized.isBlank()) {
            return@withContext ConnectionTestResult(false, "Server URL cannot be empty")
        }

        try {
            val statsUrl = "$normalized/system_stats"
            val req = Request.Builder()
                .url(statsUrl)
                .header("Connection", "close")
                .get()
                .build()

            val (code, body, isSuccess) = try {
                http.newCall(req).execute().use { resp ->
                    Triple(resp.code, resp.body?.string(), resp.isSuccessful)
                }
            } catch (e: Exception) {
                http.connectionPool.evictAll()
                try {
                    http.newCall(req).execute().use { resp ->
                        Triple(resp.code, resp.body?.string(), resp.isSuccessful)
                    }
                } catch (retryEx: Exception) {
                    throw retryEx
                }
            }

            if (isSuccess && !body.isNullOrBlank()) {
                val statsObj = try { gson.fromJson(body, JsonObject::class.java) } catch (_: Exception) { null }
                val devices = statsObj?.getAsJsonArray("devices")
                var gpuInfo = ""
                if (devices != null && devices.size() > 0) {
                    val firstDevice = devices[0].asJsonObject
                    val name = firstDevice?.get("name")?.asString
                    val vramFree = firstDevice?.get("vram_free")?.asLong
                    if (name != null) {
                        val freeGb = if (vramFree != null) String.format(java.util.Locale.US, "%.1f GB free", vramFree.toDouble() / (1024 * 1024 * 1024)) else ""
                        gpuInfo = " (GPU: $name${if (freeGb.isNotBlank()) ", $freeGb" else ""})"
                    }
                }

                val msg = "Connected to ComfyUI successfully!$gpuInfo"
                return@withContext ConnectionTestResult(true, msg, statsObj)
            } else if (code == 404) {
                val promptReq = Request.Builder().url("$normalized/prompt").header("Connection", "close").get().build()
                val promptSuccess = http.newCall(promptReq).execute().use { it.isSuccessful }
                if (promptSuccess) {
                    return@withContext ConnectionTestResult(true, "Connected to ComfyUI server!")
                }
            }

            return@withContext ConnectionTestResult(false, "Server returned HTTP $code")
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Unknown error"
            val userMsg = when {
                msg.contains("Connection refused", ignoreCase = true) || e is java.net.ConnectException ->
                    "Connection refused at $normalized.\n\nMake sure ComfyUI is running with the '--listen' argument (e.g. run_nvidia_gpu.bat --listen)."
                msg.contains("timed out", ignoreCase = true) || e is java.net.SocketTimeoutException ->
                    "Connection timed out at $normalized.\n\nCheck if the IP and port are correct and phone is on the same Wi-Fi network."
                msg.contains("No route to host", ignoreCase = true) || e is java.net.NoRouteToHostException ->
                    "Host unreachable at $normalized.\n\nEnsure phone and ComfyUI PC are connected to the same network."
                e is java.net.UnknownHostException ->
                    "Could not resolve host. Please verify the IP address or hostname."
                else -> "Connection error: $msg"
            }
            return@withContext ConnectionTestResult(false, userMsg)
        }
    }

    // ── Image Upload ───────────────────────────────────────────────────

    fun uploadImage(serverUrl: String, imageBytes: ByteArray, filename: String, overwrite: Boolean = true): String? {
        val cleanUrl = UrlValidator.normalizeUrl(serverUrl)
        if (cleanUrl.isBlank()) return null
        return try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "image",
                    filename,
                    imageBytes.toRequestBody(ComfyHttpClient.imageMediaType)
                )
                .addFormDataPart("overwrite", overwrite.toString())
                .build()

            val request = Request.Builder()
                .url("$cleanUrl/upload/image")
                .post(requestBody)
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.e("ComfyApiService", "Upload image failed: HTTP ${response.code}")
                    return null
                }
                val bodyStr = response.body?.string() ?: return null
                val json = gson.fromJson(bodyStr, JsonObject::class.java)
                json.get("name")?.asString ?: filename
            }
        } catch (e: Exception) {
            AppLogger.e("ComfyApiService", "Failed to upload image to ComfyUI: ${e.localizedMessage}")
            null
        }
    }

    // ── Workflow Listing ───────────────────────────────────────────────

    suspend fun fetchSavedWorkflows(serverUrl: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = UrlValidator.normalizeUrl(serverUrl)
            if (cleanUrl.isBlank()) return@withContext emptyList()
            val url = "$cleanUrl/userdata?dir=workflows"
            val request = Request.Builder().url(url).build()
            http.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: "[]"
                    val listType = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                    val list: List<String> = gson.fromJson(body, listType)
                    list.filter { it.endsWith(".json", ignoreCase = true) }
                } else {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            AppLogger.e("ComfyApiService", "Failed to fetch saved workflows: ${e.localizedMessage}")
            emptyList()
        }
    }

    // ── Fetch Last Workflow ────────────────────────────────────────────

    data class LastWorkflowResult(
        val workflowJson: String? = null,
        val errorMsg: String? = null
    )

    suspend fun fetchLastWorkflow(serverUrl: String): LastWorkflowResult = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = UrlValidator.normalizeUrl(serverUrl)
            if (cleanUrl.isBlank()) return@withContext LastWorkflowResult(errorMsg = "Server URL cannot be empty")

            var workflowJson: String? = null

            // 1. Fetch recent history
            try {
                val historyUrl = "$cleanUrl/history?max_items=5"
                val request = Request.Builder().url(historyUrl).header("Connection", "close").build()
                http.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: "{}"
                        val jsonObj = gson.fromJson(body, JsonObject::class.java)
                        if (jsonObj != null && jsonObj.size() > 0) {
                            var latestEntry: JsonObject? = null
                            var highestPromptIndex = -1L

                            jsonObj.entrySet().forEach { entry ->
                                val itemObj = entry.value.asJsonObject
                                val promptArray = itemObj.getAsJsonArray("prompt")
                                val promptIndex = if (promptArray != null && promptArray.size() > 0 && promptArray[0].isJsonPrimitive) {
                                    try { promptArray[0].asLong } catch (_: Exception) { -1L }
                                } else -1L

                                if (promptIndex >= highestPromptIndex) {
                                    highestPromptIndex = promptIndex
                                    latestEntry = itemObj
                                }
                            }

                            if (latestEntry == null) {
                                latestEntry = jsonObj.entrySet().lastOrNull()?.value?.asJsonObject
                            }

                            if (latestEntry != null) {
                                val extraData = latestEntry!!.getAsJsonObject("extra_data")
                                val extraPngInfo = extraData?.getAsJsonObject("extra_pnginfo")
                                val uiWorkflow = extraPngInfo?.getAsJsonObject("workflow")
                                if (uiWorkflow != null && uiWorkflow.size() > 0) {
                                    workflowJson = gson.toJson(uiWorkflow)
                                } else {
                                    val promptArr = latestEntry!!.getAsJsonArray("prompt")
                                    if (promptArr != null && promptArr.size() >= 3 && promptArr[2].isJsonObject) {
                                        workflowJson = gson.toJson(promptArr[2].asJsonObject)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (histEx: Exception) {
                AppLogger.w("ComfyApiService", "Failed to query /history: ${histEx.localizedMessage}")
            }

            // 2. If history was empty, check /queue
            if (workflowJson == null) {
                try {
                    val queueUrl = "$cleanUrl/queue"
                    val queueReq = Request.Builder().url(queueUrl).header("Connection", "close").build()
                    http.newCall(queueReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val qBody = resp.body?.string() ?: "{}"
                            val qObj = gson.fromJson(qBody, JsonObject::class.java)
                            val running = qObj.getAsJsonArray("queue_running")
                            val pending = qObj.getAsJsonArray("queue_pending")
                            val activeQueueItem = when {
                                running != null && running.size() > 0 -> running[0].asJsonArray
                                pending != null && pending.size() > 0 -> pending[0].asJsonArray
                                else -> null
                            }
                            if (activeQueueItem != null && activeQueueItem.size() >= 3) {
                                if (activeQueueItem.size() >= 4 && activeQueueItem[3].isJsonObject) {
                                    val extra = activeQueueItem[3].asJsonObject
                                    val uiWf = extra.getAsJsonObject("extra_pnginfo")?.getAsJsonObject("workflow")
                                    if (uiWf != null && uiWf.size() > 0) {
                                        workflowJson = gson.toJson(uiWf)
                                    }
                                }
                                if (workflowJson == null && activeQueueItem[2].isJsonObject) {
                                    workflowJson = gson.toJson(activeQueueItem[2].asJsonObject)
                                }
                            }
                        }
                    }
                } catch (queueEx: Exception) {
                    AppLogger.w("ComfyApiService", "Failed to query /queue: ${queueEx.localizedMessage}")
                }
            }

            if (workflowJson != null) {
                AppLogger.i("ComfyApiService", "Successfully fetched last workflow from server")
                LastWorkflowResult(workflowJson = workflowJson)
            } else {
                LastWorkflowResult(errorMsg = "No workflows found in ComfyUI history or queue. Please run a workflow on your computer first.")
            }
        } catch (e: Exception) {
            AppLogger.e("ComfyApiService", "Error fetching last workflow: ${e.localizedMessage}")
            LastWorkflowResult(errorMsg = "Could not connect to ComfyUI server: ${e.localizedMessage}")
        }
    }

    // ── Prompt Submission ──────────────────────────────────────────────

    data class PromptSubmissionResult(
        val success: Boolean,
        val promptId: String? = null,
        val errorMessage: String? = null
    )

    suspend fun submitPrompt(
        serverUrl: String,
        workflowObj: JsonObject,
        clientId: String
    ): PromptSubmissionResult = withContext(Dispatchers.IO) {
        val serverBaseUrl = UrlValidator.normalizeUrl(serverUrl)
        if (serverBaseUrl.isBlank()) {
            return@withContext PromptSubmissionResult(false, errorMessage = "ComfyUI Server URL is empty.")
        }

        val promptRequestBody = JsonObject().apply {
            add("prompt", workflowObj)
            addProperty("client_id", clientId)
        }
        val jsonBody = gson.toJson(promptRequestBody)
        val promptUrl = "$serverBaseUrl/prompt"
        val request = Request.Builder()
            .url(promptUrl)
            .post(jsonBody.toRequestBody(ComfyHttpClient.jsonMediaType))
            .build()

        val promptClient = http.newBuilder()
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        var responseBody = ""
        var responseCode = 0
        var requestSuccess = false

        try {
            try {
                promptClient.newCall(request).execute().use { response ->
                    responseCode = response.code
                    requestSuccess = response.isSuccessful
                    responseBody = response.body?.string() ?: ""
                }
            } catch (ioEx: java.io.IOException) {
                AppLogger.w("ComfyApiService", "Prompt POST network error: ${ioEx.localizedMessage}. Retrying...")
                promptClient.connectionPool.evictAll()
                promptClient.newCall(request).execute().use { response ->
                    responseCode = response.code
                    requestSuccess = response.isSuccessful
                    responseBody = response.body?.string() ?: ""
                }
            }

            if (!requestSuccess) {
                val cleanErrorDesc = try {
                    val errObj = gson.fromJson(responseBody, JsonObject::class.java)
                    val err = errObj.getAsJsonObject("error")
                    val msg = err?.get("message")?.asString
                    val nodeErrors = errObj.getAsJsonObject("node_errors")
                    val firstNodeErr = nodeErrors?.entrySet()?.firstOrNull()?.let { (k, v) ->
                        val nodeMsg = v.asJsonObject.getAsJsonArray("errors")?.firstOrNull()?.asJsonObject?.get("message")?.asString
                        "Node $k: $nodeMsg"
                    }
                    listOfNotNull(msg, firstNodeErr).joinToString(" - ")
                } catch (_: Exception) { null }

                val displayErr = if (!cleanErrorDesc.isNullOrBlank()) cleanErrorDesc else "Server rejected request (HTTP $responseCode)"
                return@withContext PromptSubmissionResult(false, errorMessage = displayErr)
            }

            val responseObj = gson.fromJson(responseBody, JsonObject::class.java)
            val promptId = responseObj.get("prompt_id")?.asString

            if (promptId == null) {
                val err = responseObj.get("error")?.asJsonObject
                val errType = err?.get("type")?.asString ?: "Unknown"
                val errMsg = err?.get("message")?.asString ?: "Server failed to return prompt ID"
                val errDetails = err?.get("details")?.asString ?: ""
                return@withContext PromptSubmissionResult(false, errorMessage = "Error: $errType - $errMsg\n$errDetails")
            }

            AppLogger.d("ComfyApiService", "Prompt submitted: prompt_id=$promptId")
            PromptSubmissionResult(true, promptId = promptId)

        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Unknown connection error"
            val userMsg = when {
                msg.contains("Connection refused", ignoreCase = true) || e is java.net.ConnectException ->
                    "Connection refused at $serverBaseUrl. Check if ComfyUI is running with '--listen'."
                msg.contains("timed out", ignoreCase = true) || e is java.net.SocketTimeoutException ->
                    "Connection timed out connecting to $serverBaseUrl."
                msg.contains("No route to host", ignoreCase = true) || e is java.net.NoRouteToHostException ->
                    "Host unreachable at $serverBaseUrl. Check Wi-Fi connection."
                else -> "Connection error to $serverBaseUrl: $msg"
            }
            PromptSubmissionResult(false, errorMessage = userMsg)
        }
    }

    // ── Fetch Prompt Outputs (from /history) ───────────────────────────

    suspend fun fetchPromptOutputs(
        serverUrl: String,
        promptId: String,
        targetOutputNodeId: String = "",
        activeWorkflow: JsonObject? = null
    ): List<String> = withContext(Dispatchers.IO) {
        val cleanUrl = UrlValidator.normalizeUrl(serverUrl)
        if (cleanUrl.isBlank() || promptId.isBlank()) return@withContext emptyList()
        try {
            val url = "$cleanUrl/history/$promptId"
            val request = Request.Builder().url(url).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val rootObj = gson.fromJson(body, JsonObject::class.java)
                val promptEntry = rootObj.getAsJsonObject(promptId) ?: return@withContext emptyList()
                val outputs = promptEntry.getAsJsonObject("outputs") ?: return@withContext emptyList()

                fun extractUrls(outObj: JsonObject): List<String> {
                    val images = outObj.getAsJsonArray("images") ?: outObj.getAsJsonArray("gifs") ?: return emptyList()
                    val result = mutableListOf<String>()
                    images.forEach { imgEl ->
                        if (imgEl.isJsonObject) {
                            val imgObj = imgEl.asJsonObject
                            val filename = imgObj.get("filename")?.asString ?: return@forEach
                            val subfolder = imgObj.get("subfolder")?.asString ?: ""
                            val type = imgObj.get("type")?.asString ?: "output"
                            val subParam = if (subfolder.isNotBlank()) "&subfolder=$subfolder" else ""
                            result.add("$cleanUrl/view?filename=$filename$subParam&type=$type")
                        }
                    }
                    return result
                }

                // 1. If target output node is in outputs, return its images first
                if (targetOutputNodeId.isNotBlank() && outputs.has(targetOutputNodeId) && outputs.get(targetOutputNodeId).isJsonObject) {
                    val targetUrls = extractUrls(outputs.getAsJsonObject(targetOutputNodeId))
                    if (targetUrls.isNotEmpty()) return@withContext targetUrls
                }

                // 2. Otherwise collect from non-mask outputs
                val candidateUrls = mutableListOf<String>()
                outputs.entrySet().forEach { (nodeId, nodeOutput) ->
                    if (nodeOutput.isJsonObject) {
                        val classType = activeWorkflow?.getAsJsonObject(nodeId)?.get("class_type")?.asString ?: ""
                        if (!classType.contains("MaskPreview", ignoreCase = true) && !classType.contains("PreviewMask", ignoreCase = true)) {
                            candidateUrls.addAll(extractUrls(nodeOutput.asJsonObject))
                        }
                    }
                }
                if (candidateUrls.isNotEmpty()) candidateUrls else {
                    val fallbackUrls = mutableListOf<String>()
                    outputs.entrySet().forEach { (_, nodeOutput) ->
                        if (nodeOutput.isJsonObject) fallbackUrls.addAll(extractUrls(nodeOutput.asJsonObject))
                    }
                    fallbackUrls
                }
            }
        } catch (e: Exception) {
            AppLogger.w("ComfyApiService", "Failed to fetch prompt outputs from /history/$promptId: ${e.localizedMessage}")
            emptyList()
        }
    }

    // ── Interrupt/Cancel ───────────────────────────────────────────────

    suspend fun interruptGeneration(serverUrl: String) = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = UrlValidator.normalizeUrl(serverUrl)
            if (cleanUrl.isBlank()) return@withContext
            val request = Request.Builder()
                .url("$cleanUrl/interrupt")
                .post("{}".toRequestBody(ComfyHttpClient.jsonMediaType))
                .build()
            http.newCall(request).execute().use { }
        } catch (e: Exception) {
            AppLogger.e("ComfyApiService", "Exception sending interrupt: ${e.localizedMessage}")
        }
    }

    // ── Machine History ────────────────────────────────────────────────

    suspend fun fetchMachineHistory(serverUrl: String): Pair<List<MachineHistoryItem>, String?> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = UrlValidator.normalizeUrl(serverUrl)
            if (cleanUrl.isBlank()) return@withContext Pair(emptyList(), "Server URL is empty")

            val historyUrl = "$cleanUrl/history?max_items=30"
            val request = Request.Builder().url(historyUrl).header("Connection", "close").build()
            val items = mutableListOf<MachineHistoryItem>()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Pair(emptyList(), "Failed to query server history (HTTP ${response.code})")
                }
                val body = response.body?.string() ?: "{}"
                val root = gson.fromJson(body, JsonObject::class.java) ?: JsonObject()

                for (historyId in root.keySet().toList()) {
                    val itemObj = root.getAsJsonObject(historyId) ?: continue
                    val promptArray = itemObj.getAsJsonArray("prompt") ?: continue
                    if (promptArray.size() < 3) continue

                    val promptIndex = try { promptArray[0].asLong } catch (_: Exception) { 0L }
                    val promptId = try { promptArray[1].asString } catch (_: Exception) { historyId }

                    var rawJson = ""
                    var isUi = false
                    if (promptArray.size() >= 4 && promptArray[3].isJsonObject) {
                        val extra = promptArray[3].asJsonObject
                        val uiWf = extra.getAsJsonObject("extra_pnginfo")?.getAsJsonObject("workflow")
                        if (uiWf != null && uiWf.size() > 0) {
                            rawJson = gson.toJson(uiWf)
                            isUi = true
                        }
                    }
                    if (rawJson.isBlank() && promptArray[2].isJsonObject) {
                        rawJson = gson.toJson(promptArray[2].asJsonObject)
                        isUi = false
                    }
                    if (rawJson.isBlank()) continue

                    var thumbnailUrl: String? = null
                    val outputs = itemObj.getAsJsonObject("outputs")
                    if (outputs != null) {
                        for (outKey in outputs.keySet().toList()) {
                            val outObj = outputs.getAsJsonObject(outKey) ?: continue
                            val imgArr = outObj.getAsJsonArray("images")
                            if (imgArr != null && imgArr.size() > 0) {
                                val firstImg = imgArr[0].asJsonObject
                                val fn = firstImg.get("filename")?.asString
                                val sf = firstImg.get("subfolder")?.asString ?: ""
                                val tp = firstImg.get("type")?.asString ?: "output"
                                if (!fn.isNullOrBlank()) {
                                    thumbnailUrl = "$cleanUrl/view?filename=${java.net.URLEncoder.encode(fn, "UTF-8")}&subfolder=${java.net.URLEncoder.encode(sf, "UTF-8")}&type=${java.net.URLEncoder.encode(tp, "UTF-8")}"
                                    break
                                }
                            }
                        }
                    }

                    // Preview data is parsed in the caller (WorkflowAnalyzer)
                    items.add(
                        MachineHistoryItem(
                            promptId = promptId,
                            promptIndex = promptIndex,
                            timestamp = null,
                            thumbnailUrl = thumbnailUrl,
                            rawJson = rawJson,
                            isUiFormat = isUi
                        )
                    )
                }
            }

            items.sortByDescending { it.promptIndex }

            if (items.isEmpty()) {
                Pair(emptyList(), "No recent workflows found in server history.")
            } else {
                Pair(items, null)
            }
        } catch (e: Exception) {
            AppLogger.e("ComfyApiService", "Error fetching machine history: ${e.localizedMessage}")
            Pair(emptyList(), e.localizedMessage ?: "Failed to connect to ComfyUI server.")
        }
    }

    // ── Fetch Server Workflow Content ──────────────────────────────────

    suspend fun fetchWorkflowContent(serverUrl: String, workflowFilename: String): String = withContext(Dispatchers.IO) {
        val cleanUrl = UrlValidator.normalizeUrl(serverUrl)
        if (cleanUrl.isBlank()) throw Exception("ComfyUI Server URL is empty. Please configure it in Settings.")

        val encodedPath = java.net.URLEncoder.encode("workflows/$workflowFilename", "UTF-8").replace("+", "%20")
        val fetchUrl = "$cleanUrl/userdata/$encodedPath"
        AppLogger.d("ComfyApiService", "Fetching workflow: $fetchUrl")

        val request = Request.Builder().url(fetchUrl).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to fetch workflow '$workflowFilename' (HTTP ${response.code})")
            }
            response.body?.string() ?: throw Exception("Fetched workflow is empty")
        }
    }
}
