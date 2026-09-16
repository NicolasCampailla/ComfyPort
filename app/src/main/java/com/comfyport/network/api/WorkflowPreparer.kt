package com.comfyport.network.api

import android.content.Context
import com.comfyport.data.AppSettings
import com.comfyport.data.ConversionResult
import com.comfyport.data.SeedMode
import com.comfyport.data.WorkflowNodeMapping
import com.comfyport.network.AppLogger
import com.comfyport.network.ComfyWorkflowConverter
import com.comfyport.network.UrlValidator
import com.comfyport.network.http.ComfyHttpClient
import com.comfyport.network.mask.MaskProcessor
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.random.Random

data class PreparedWorkflow(
    val promptJson: String,
    val workflowObj: JsonObject,
    val saveImageNodeId: String,
    val activeSeed: Long,
    val uploadedImageName: String? = null,
    val uploadedMaskName: String? = null
)

/**
 * Handles workflow loading, format conversion, parameter injection, and asset uploading.
 * Decouples workflow preparation logic from ComfyClient.
 */
object WorkflowPreparer {

    suspend fun convertWorkflowToApi(
        uiJsonString: String,
        serverUrl: String,
        context: Context? = null
    ): ConversionResult = withContext(Dispatchers.IO) {
        val cleanUrl = UrlValidator.normalizeUrl(serverUrl)

        // 1. Option 2: Official ComfyUI standard session interpreter (window.app.graphToPrompt)
        if (cleanUrl.isNotBlank()) {
            try {
                val sessionApiJson = ComfySessionInterpreter.convert(context, uiJsonString, cleanUrl)
                if (!sessionApiJson.isNullOrBlank()) {
                    val parsedPrompt = JsonParser.parseString(sessionApiJson)
                    if (parsedPrompt.isJsonObject && parsedPrompt.asJsonObject.size() > 0) {
                        AppLogger.i("WorkflowPreparer", "Converted workflow to API prompt using ComfyUI session interpreter (${parsedPrompt.asJsonObject.size()} nodes)")
                        return@withContext ConversionResult.Success(sessionApiJson)
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("WorkflowPreparer", "ComfyUI session interpreter error: ${e.localizedMessage}")
            }
        }

        // 2. Fallback to server endpoint if serverUrl is configured
        if (cleanUrl.isNotBlank()) {
            try {
                val dedicatedClient = ComfyHttpClient.okHttp.newBuilder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .writeTimeout(5, TimeUnit.SECONDS)
                    .build()

                val url = "$cleanUrl/workflow/convert"
                val requestBody = uiJsonString.toRequestBody(ComfyHttpClient.jsonMediaType)
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                dedicatedClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    if (response.code == 404) {
                        // Extension missing, fall through to native
                        AppLogger.d("WorkflowPreparer", "Server missing /workflow/convert extension")
                    } else if (response.isSuccessful && responseBody.isNotBlank()) {
                        AppLogger.i("WorkflowPreparer", "Converted workflow via server /workflow/convert endpoint")
                        return@withContext ConversionResult.Success(responseBody)
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("WorkflowPreparer", "Server /workflow/convert error: ${e.localizedMessage}")
            }
        }

        // 3. Fallback to on-device native conversion
        try {
            val nativeApiJson = ComfyWorkflowConverter.convertUiToApi(uiJsonString)
            val parsedPrompt = JsonParser.parseString(nativeApiJson)
            if (parsedPrompt.isJsonObject && parsedPrompt.asJsonObject.size() > 0) {
                AppLogger.i("WorkflowPreparer", "Converted workflow to API format natively on-device (${parsedPrompt.asJsonObject.size()} nodes)")
                return@withContext ConversionResult.Success(nativeApiJson)
            }
        } catch (e: Exception) {
            AppLogger.w("WorkflowPreparer", "Native workflow conversion failed: ${e.localizedMessage}")
        }

        ConversionResult.Error.Generic("Could not convert workflow to API format. Please verify ComfyUI server is reachable at $cleanUrl.")
    }

    suspend fun loadWorkflowJson(context: Context, settings: AppSettings): String = withContext(Dispatchers.IO) {
        val isLocalCustom = (context.getFileStreamPath(settings.workflowToUse)?.exists() == true) ||
                            settings.workflowToUse.startsWith("wf_") ||
                            settings.workflowToUse == "imported_workflow.json"

        if (isLocalCustom) {
            AppLogger.d("WorkflowPreparer", "Loading custom workflow locally: ${settings.workflowToUse}")
            context.openFileInput(settings.workflowToUse).bufferedReader().use { it.readText() }
        } else if (settings.workflowToUse.isNotBlank()) {
            val cleanUrl = UrlValidator.normalizeUrl(settings.serverUrl)
            if (cleanUrl.isBlank()) {
                throw Exception("ComfyUI Server URL is empty. Please configure it in Settings.")
            }
            val encodedPath = URLEncoder.encode("workflows/${settings.workflowToUse}", "UTF-8").replace("+", "%20")
            val fetchUrl = "$cleanUrl/userdata/$encodedPath"
            AppLogger.d("WorkflowPreparer", "Fetching workflow: $fetchUrl")
            val request = Request.Builder().url(fetchUrl).build()
            ComfyHttpClient.okHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Failed to fetch workflow '${settings.workflowToUse}' (HTTP ${response.code})")
                }
                response.body?.string() ?: throw Exception("Fetched workflow is empty")
            }
        } else {
            throw Exception("No workflow selected. Please import a workflow first.")
        }
    }

    suspend fun prepareWorkflow(
        context: Context,
        prompt: String,
        settings: AppSettings,
        negativePrompt: String? = null,
        inputImageBytes: ByteArray? = null,
        inputMaskBytes: ByteArray? = null,
        onStatusUpdate: (String) -> Unit = {}
    ): PreparedWorkflow = withContext(Dispatchers.IO) {
        // 1. Determine active seed
        val activeSeed = when (settings.seedMode) {
            SeedMode.Fixed -> settings.fixedSeedValue
            SeedMode.Custom -> settings.customSeedValue
            SeedMode.LastUsed -> settings.lastUsedSeedValue
            SeedMode.Random -> Random.nextLong(0, 1125899906842624L)
        }

        // 2. Load workflow JSON string
        val workflowJsonString = loadWorkflowJson(context, settings)

        var workflowObj = ComfyHttpClient.gson.fromJson(workflowJsonString, JsonObject::class.java)
        if (workflowObj.has("prompt") && workflowObj.get("prompt").isJsonObject) {
            workflowObj = workflowObj.getAsJsonObject("prompt")
        } else if (workflowObj.has("workflow") && workflowObj.get("workflow").isJsonObject) {
            val innerWf = workflowObj.getAsJsonObject("workflow")
            if (innerWf.has("nodes") && innerWf.get("nodes").isJsonArray) {
                workflowObj = innerWf
            } else {
                var hasClassType = false
                innerWf.keySet().forEach { k ->
                    val n = innerWf.get(k)
                    if (n != null && n.isJsonObject && n.asJsonObject.has("class_type")) {
                        hasClassType = true
                    }
                }
                if (hasClassType) workflowObj = innerWf
            }
        } else if (workflowObj.has("output") && workflowObj.get("output").isJsonObject) {
            val outObj = workflowObj.getAsJsonObject("output")
            var hasClassType = false
            outObj.keySet().forEach { k ->
                val n = outObj.get(k)
                if (n != null && n.isJsonObject && n.asJsonObject.has("class_type")) {
                    hasClassType = true
                }
            }
            if (hasClassType) workflowObj = outObj
        }

        // 3. Convert UI-format workflow to API-format if necessary, or re-interpret if unconverted Reroute nodes exist
        val isUiFormat = workflowObj.has("nodes") && workflowObj.get("nodes").isJsonArray
        val hasReroutes = workflowObj.entrySet().any { (_, v) ->
            v.isJsonObject && v.asJsonObject.get("class_type")?.asString == "Reroute"
        }

        if (isUiFormat || hasReroutes) {
            AppLogger.d("WorkflowPreparer", "Interpreting workflow with ComfyUI session (isUiFormat=$isUiFormat, hasReroutes=$hasReroutes)")
            onStatusUpdate("Converting workflow with ComfyUI session interpreter...")
            val conversionResult = convertWorkflowToApi(workflowJsonString, settings.serverUrl, context)
            when (conversionResult) {
                is ConversionResult.Success -> {
                    val parsed = ComfyHttpClient.gson.fromJson(conversionResult.apiJson, JsonObject::class.java)
                    val resolvedPrompt = if (parsed.has("prompt") && parsed.get("prompt").isJsonObject && !parsed.has("nodes")) {
                        parsed.getAsJsonObject("prompt")
                    } else parsed
                    val stillHasReroute = resolvedPrompt.entrySet().any { (_, v) ->
                        v.isJsonObject && v.asJsonObject.get("class_type")?.asString == "Reroute"
                    }
                    if (!stillHasReroute || isUiFormat) {
                        workflowObj = resolvedPrompt
                    }
                }
                is ConversionResult.Error.MissingExtension -> {
                    if (isUiFormat) {
                        throw Exception("Failed to convert UI workflow. ComfyUI session is not ready and server is missing the conversion extension.")
                    }
                }
                is ConversionResult.Error.Generic -> {
                    if (isUiFormat) {
                        throw Exception("Failed to convert UI workflow: ${conversionResult.message}")
                    } else {
                        AppLogger.w("WorkflowPreparer", "Could not re-interpret workflow with reroutes: ${conversionResult.message}")
                    }
                }
            }
        }

        // 4. Clean non-node metadata fields and non-executable nodes that ComfyUI rejects
        val cleanObj = JsonObject()
        workflowObj.entrySet().forEach { (k, v) ->
            if (v.isJsonObject && (v.asJsonObject.has("class_type") || v.asJsonObject.has("inputs"))) {
                val classType = v.asJsonObject.get("class_type")?.asString ?: ""
                if (!classType.equals("Reroute", ignoreCase = true)) {
                    cleanObj.add(k, v)
                }
            }
        }
        if (cleanObj.size() > 0) {
            workflowObj = cleanObj
        }

        // 5. Upload input image and mask if provided
        var uploadedImageName: String? = null
        if (inputImageBytes != null && inputImageBytes.isNotEmpty()) {
            onStatusUpdate("Uploading input image...")
            val imgBytesToUpload = if (inputMaskBytes != null && inputMaskBytes.isNotEmpty()) {
                MaskProcessor.createCompositeImageWithMask(inputImageBytes, inputMaskBytes)
            } else {
                inputImageBytes
            }
            val imgFilename = "input_${System.currentTimeMillis()}.png"
            uploadedImageName = ComfyApiService.uploadImage(settings.serverUrl, imgBytesToUpload, imgFilename)
            if (uploadedImageName == null) {
                throw Exception("Failed to upload input image to ComfyUI server at ${settings.serverUrl}.")
            }
        }

        var uploadedMaskName: String? = null
        if (inputMaskBytes != null && inputMaskBytes.isNotEmpty()) {
            onStatusUpdate("Uploading inpainting mask...")
            val maskBytesToUpload = MaskProcessor.createStandaloneMaskImage(inputMaskBytes)
            val maskFilename = "mask_${System.currentTimeMillis()}.png"
            uploadedMaskName = ComfyApiService.uploadImage(settings.serverUrl, maskBytesToUpload, maskFilename)
            if (uploadedMaskName == null) {
                throw Exception("Failed to upload inpainting mask to ComfyUI server at ${settings.serverUrl}.")
            }
        }

        onStatusUpdate("Preparing workflow parameters...")

        // 6. Calculate dimensions & get custom mappings
        val (width, height) = WorkflowAnalyzer.resolveDimensions(settings)
        val activeSaved = settings.savedWorkflows.firstOrNull { it.id == settings.selectedWorkflowId }
        val customMapping = activeSaved?.nodeMapping

        // 7. Detect SaveImage / Preview output node
        val mappedOutputId = customMapping?.outputNodeId
        val saveImageId: String = if (!mappedOutputId.isNullOrBlank() && workflowObj.has(mappedOutputId)) {
            mappedOutputId
        } else {
            var bestId: String? = null
            var bestPriority = -1

            for ((id, nodeEl) in workflowObj.entrySet()) {
                val node = nodeEl.asJsonObject
                val classType = node.get("class_type")?.asString ?: ""
                if (classType.contains("Mask", ignoreCase = true)) continue

                var priority = -1
                when {
                    classType == "SaveImage" || classType == "SaveImageWebsocket" -> priority = 100
                    classType.contains("SaveImage", ignoreCase = true) -> priority = 90
                    classType == "PreviewImage" -> {
                        val inputs = node.getAsJsonObject("inputs")
                        val inpsStr = inputs?.toString() ?: ""
                        val isUncrop = inpsStr.contains("180") || workflowObj.entrySet().any { (upstreamId, upstreamEl) ->
                            val uClass = upstreamEl.asJsonObject.get("class_type")?.asString ?: ""
                            (uClass.contains("Uncrop", ignoreCase = true) || uClass.contains("Composite", ignoreCase = true)) &&
                            inputs?.entrySet()?.any { it.value.isJsonArray && it.value.asJsonArray.size() > 0 && it.value.asJsonArray[0].asString == upstreamId } == true
                        }
                        priority = if (isUncrop) 80 else 50
                    }
                    classType.contains("Preview", ignoreCase = true) -> priority = 40
                }

                if (priority > bestPriority) {
                    bestPriority = priority
                    bestId = id
                }
            }

            bestId ?: (if (workflowObj.has("9")) "9" else workflowObj.keySet().firstOrNull() ?: "9")
        }

        // 8. Inject parameters into workflow
        injectWorkflowParameters(
            workflowObj = workflowObj,
            prompt = prompt,
            negativePrompt = negativePrompt,
            activeSeed = activeSeed,
            width = width,
            height = height,
            batchSize = settings.batchSize,
            uploadedImageName = uploadedImageName,
            uploadedMaskName = uploadedMaskName,
            customMapping = customMapping,
            resolutionMode = settings.resolutionMode
        )

        val promptJson = ComfyHttpClient.gson.toJson(workflowObj)

        PreparedWorkflow(
            promptJson = promptJson,
            workflowObj = workflowObj,
            saveImageNodeId = saveImageId,
            activeSeed = activeSeed,
            uploadedImageName = uploadedImageName,
            uploadedMaskName = uploadedMaskName
        )
    }

    fun injectWorkflowParameters(
        workflowObj: JsonObject,
        prompt: String,
        negativePrompt: String? = null,
        activeSeed: Long = 0L,
        width: Int = 1024,
        height: Int = 1024,
        batchSize: Int = 1,
        uploadedImageName: String? = null,
        uploadedMaskName: String? = null,
        customMapping: WorkflowNodeMapping? = null,
        resolutionMode: String = "SDXL"
    ) {
        val isWorkflowManaged = resolutionMode.equals("WORKFLOW", ignoreCase = true)
        val latentDerivedFromImage = WorkflowAnalyzer.isLatentDerivedFromImage(workflowObj)

        // Positive & Negative prompt nodes
        val mappedPosId = customMapping?.positivePromptNodeId
        var positiveNodeId: String? = if (!mappedPosId.isNullOrBlank() && workflowObj.has(mappedPosId)) {
            mappedPosId
        } else null

        val mappedNegId = customMapping?.negativePromptNodeId
        var negativeNodeId: String? = if (!mappedNegId.isNullOrBlank() && workflowObj.has(mappedNegId)) {
            mappedNegId
        } else null

        if (positiveNodeId == null || negativeNodeId == null) {
            for ((_, nodeEl) in workflowObj.entrySet()) {
                val node = nodeEl.asJsonObject
                val classType = node.get("class_type")?.asString ?: ""
                if (classType.contains("KSampler") || classType.contains("Sampler")) {
                    val inputs = node.getAsJsonObject("inputs") ?: continue
                    if (positiveNodeId == null && inputs.has("positive") && inputs.get("positive").isJsonArray) {
                        val arr = inputs.getAsJsonArray("positive")
                        if (arr.size() > 0) positiveNodeId = arr[0].asString
                    }
                    if (negativeNodeId == null && inputs.has("negative") && inputs.get("negative").isJsonArray) {
                        val arr = inputs.getAsJsonArray("negative")
                        if (arr.size() > 0) negativeNodeId = arr[0].asString
                    }
                }
            }
        }

        if (positiveNodeId == null) {
            positiveNodeId = workflowObj.keySet().firstOrNull { id ->
                workflowObj.getAsJsonObject(id)?.get("class_type")?.asString == "CLIPTextEncode"
            } ?: (if (workflowObj.has("6")) "6" else null)
        }
        if (negativeNodeId == null) {
            val secondClip = workflowObj.keySet().filter { id ->
                id != positiveNodeId && workflowObj.getAsJsonObject(id)?.get("class_type")?.asString == "CLIPTextEncode"
            }
            if (secondClip.isNotEmpty()) {
                negativeNodeId = secondClip.first()
            } else if (workflowObj.has("7") && "7" != positiveNodeId) {
                negativeNodeId = "7"
            } else {
                negativeNodeId = workflowObj.keySet().firstOrNull { id ->
                    id != positiveNodeId && (workflowObj.getAsJsonObject(id)?.get("class_type")?.asString?.contains("Text") == true)
                }
            }
        }

        if (positiveNodeId != null) {
            val posNode = workflowObj.getAsJsonObject(positiveNodeId)
            val inputs = posNode?.getAsJsonObject("inputs")
            if (inputs != null) {
                when {
                    inputs.has("text") -> inputs.addProperty("text", prompt)
                    inputs.has("value") -> inputs.addProperty("value", prompt)
                    inputs.has("string") -> inputs.addProperty("string", prompt)
                    else -> inputs.addProperty("text", prompt)
                }
                AppLogger.d("WorkflowPreparer", "Injected prompt into node $positiveNodeId")
            }
        }

        if (!negativePrompt.isNullOrBlank() && negativeNodeId != null) {
            val negNode = workflowObj.getAsJsonObject(negativeNodeId)
            val inputs = negNode?.getAsJsonObject("inputs")
            if (inputs != null) {
                when {
                    inputs.has("text") -> inputs.addProperty("text", negativePrompt)
                    inputs.has("value") -> inputs.addProperty("value", negativePrompt)
                    inputs.has("string") -> inputs.addProperty("string", negativePrompt)
                    else -> inputs.addProperty("text", negativePrompt)
                }
                AppLogger.d("WorkflowPreparer", "Injected negative prompt into node $negativeNodeId")
            }
        }

        // Custom Mappings for Latent, Seed, Image, Mask
        val customLatentId = customMapping?.emptyLatentNodeId?.takeIf { workflowObj.has(it) }
        val customSeedId = customMapping?.seedNodeId?.takeIf { workflowObj.has(it) }
        val customImageId = customMapping?.loadImageNodeId?.takeIf { workflowObj.has(it) }
        val customMaskId = customMapping?.loadImageMaskNodeId?.takeIf { workflowObj.has(it) }

        if (customSeedId != null) {
            val inputs = workflowObj.getAsJsonObject(customSeedId)?.getAsJsonObject("inputs")
            if (inputs != null) inputs.addProperty("seed", activeSeed)
        }
        if (customLatentId != null) {
            val inputs = workflowObj.getAsJsonObject(customLatentId)?.getAsJsonObject("inputs")
            if (inputs != null) {
                if (!isWorkflowManaged) {
                    if (inputs.has("width") && !inputs.get("width").isJsonArray) {
                        inputs.addProperty("width", width)
                    }
                    if (inputs.has("height") && !inputs.get("height").isJsonArray) {
                        inputs.addProperty("height", height)
                    }
                }
                if (inputs.has("batch_size") && !inputs.get("batch_size").isJsonArray) {
                    inputs.addProperty("batch_size", batchSize.coerceAtLeast(1))
                }
            }
        }

        if (!uploadedImageName.isNullOrBlank()) {
            if (customImageId != null) {
                workflowObj.getAsJsonObject(customImageId)?.getAsJsonObject("inputs")?.addProperty("image", uploadedImageName)
                AppLogger.d("WorkflowPreparer", "Injected uploaded image into customImageId node $customImageId")
            } else if (customMaskId != null) {
                val maskClass = workflowObj.getAsJsonObject(customMaskId)?.get("class_type")?.asString ?: ""
                if (maskClass == "LoadImage" || maskClass == "ImageLoad") {
                    workflowObj.getAsJsonObject(customMaskId)?.getAsJsonObject("inputs")?.addProperty("image", uploadedImageName)
                    AppLogger.d("WorkflowPreparer", "Injected uploaded composite image into LoadImage mapped as customMaskId $customMaskId")
                }
            }
        }

        if (!uploadedMaskName.isNullOrBlank() && customMaskId != null) {
            val maskNode = workflowObj.getAsJsonObject(customMaskId)
            val maskClass = maskNode?.get("class_type")?.asString ?: ""
            if (maskClass != "LoadImage" && maskClass != "ImageLoad") {
                maskNode?.getAsJsonObject("inputs")?.addProperty("image", uploadedMaskName)
                AppLogger.d("WorkflowPreparer", "Injected standalone mask into customMaskId node $customMaskId")
            }
        }

        // Fallback pass for non-custom mapped elements
        for ((id, nodeEl) in workflowObj.entrySet()) {
            val node = nodeEl.asJsonObject
            val classType = node.get("class_type")?.asString ?: ""
            val inputs = node.getAsJsonObject("inputs") ?: continue

            if (customSeedId == null && inputs.has("seed") && inputs.get("seed").isJsonPrimitive && inputs.get("seed").asJsonPrimitive.isNumber) {
                inputs.addProperty("seed", activeSeed)
            }

            if (customLatentId == null && !isWorkflowManaged && !latentDerivedFromImage &&
                (classType == "EmptyLatentImage" || classType.contains("LatentImage") || classType.contains("EmptyLatent"))
            ) {
                if (inputs.has("width") && !inputs.get("width").isJsonArray) {
                    inputs.addProperty("width", width)
                }
                if (inputs.has("height") && !inputs.get("height").isJsonArray) {
                    inputs.addProperty("height", height)
                }
                if (inputs.has("batch_size") && !inputs.get("batch_size").isJsonArray) {
                    inputs.addProperty("batch_size", batchSize.coerceAtLeast(1))
                }
            }

            if (customImageId == null && !uploadedImageName.isNullOrBlank() && (classType == "LoadImage" || classType == "ImageLoad")) {
                if (id != customMaskId) {
                    inputs.addProperty("image", uploadedImageName)
                    AppLogger.d("WorkflowPreparer", "Injected uploaded image into node $id")
                }
            }

            if (customMaskId == null && !uploadedMaskName.isNullOrBlank() && (classType == "LoadImageMask" || classType.contains("LoadImageMask"))) {
                inputs.addProperty("image", uploadedMaskName)
                AppLogger.d("WorkflowPreparer", "Injected inpainting mask into node $id")
            }
        }

        // Custom Exposed Workflow Inputs
        customMapping?.customInputs?.forEach { customInput ->
            val targetNodeId = customInput.nodeId
            if (workflowObj.has(targetNodeId)) {
                val nodeObj = workflowObj.getAsJsonObject(targetNodeId)
                val nodeInputs = nodeObj?.getAsJsonObject("inputs")
                if (nodeInputs != null) {
                    when (customInput.widgetType.uppercase()) {
                        "INT", "INTEGER" -> {
                            val intVal = customInput.value.toIntOrNull()
                            if (intVal != null) {
                                nodeInputs.addProperty(customInput.widgetName, intVal)
                            } else {
                                customInput.value.toDoubleOrNull()?.let { nodeInputs.addProperty(customInput.widgetName, it.toInt()) }
                            }
                        }
                        "FLOAT", "NUMBER" -> {
                            customInput.value.toDoubleOrNull()?.let { nodeInputs.addProperty(customInput.widgetName, it) }
                        }
                        "BOOLEAN", "TOGGLE" -> {
                            customInput.value.toBooleanStrictOrNull()?.let { nodeInputs.addProperty(customInput.widgetName, it) }
                                ?: nodeInputs.addProperty(customInput.widgetName, customInput.value.equals("true", ignoreCase = true) || customInput.value == "1")
                        }
                        else -> {
                            nodeInputs.addProperty(customInput.widgetName, customInput.value)
                        }
                    }
                    AppLogger.d("WorkflowPreparer", "Injected custom input '${customInput.widgetName}'=${customInput.value} into node $targetNodeId")
                }
            }
        }
    }
}
