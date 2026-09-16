package com.comfyport.network.websocket

import com.comfyport.data.GenerationState
import com.comfyport.data.ProgressInfo
import com.comfyport.network.AppLogger
import com.comfyport.network.UrlValidator
import com.comfyport.network.api.ComfyApiService
import com.comfyport.network.http.ComfyHttpClient
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Manages the WebSocket connection to a ComfyUI server for real-time progress updates.
 * Handles connection, reconnection, message parsing, and progress state updates.
 */
class ComfyWebSocket(
    private val clientId: String,
    private val progressFlow: MutableStateFlow<ProgressInfo>,
    private val scope: CoroutineScope
) {
    private var activeWebSocket: WebSocket? = null

    @Volatile
    var isConnected: Boolean = false
        private set

    @Volatile
    var executionFinished: Boolean = false
        internal set

    var currentPromptId: String? = null
        internal set

    var activeOutputNodeId: String = ""
    var activeWorkflow: JsonObject? = null
    var activeServerUrl: String? = null

    private var retryCount = 0
    private val maxRetries = 5

    // ── Connect ────────────────────────────────────────────────────────

    fun connect(serverUrl: String, isReconnect: Boolean = false) {
        val cleanUrl = UrlValidator.normalizeUrl(serverUrl)
        if (cleanUrl.isBlank()) return

        // Reuse existing connected WebSocket if already open to same URL
        if (!isReconnect && activeWebSocket != null && isConnected && activeServerUrl == cleanUrl) {
            AppLogger.d("ComfyWebSocket", "Reusing existing WebSocket connection to: $cleanUrl")
            return
        }

        disconnect(clearPromptId = !isReconnect)
        activeServerUrl = cleanUrl

        val wsScheme = if (cleanUrl.startsWith("https://", ignoreCase = true)) "wss://" else "ws://"
        val wsHostPath = cleanUrl.removePrefix("https://").removePrefix("http://")
        val wsUrl = "$wsScheme$wsHostPath/ws?clientId=$clientId"
        AppLogger.d("ComfyWebSocket", "Connecting WebSocket to: $wsUrl")

        val request = Request.Builder().url(wsUrl).build()
        activeWebSocket = ComfyHttpClient.okHttp.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                AppLogger.d("ComfyWebSocket", "WebSocket connected!")
                isConnected = true
                retryCount = 0
                val current = progressFlow.value
                if (current.state == GenerationState.ConnectingComfy) {
                    progressFlow.value = current.copy(
                        state = GenerationState.GeneratingBase,
                        statusText = "Connected. Submitting prompt..."
                    )
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                AppLogger.d("ComfyWebSocket", "WebSocket closed: $code / $reason")
                isConnected = false
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    AppLogger.d("ComfyWebSocket", "WS msg: $text")
                    val msgObj = ComfyHttpClient.gson.fromJson(text, JsonObject::class.java)
                    val type = msgObj.get("type")?.asString ?: return
                    val data = msgObj.getAsJsonObject("data") ?: return

                    val promptId = data.get("prompt_id")?.asString
                    if (promptId != null && currentPromptId != null && promptId != currentPromptId) {
                        AppLogger.d("ComfyWebSocket", "Ignoring message for old promptId: $promptId")
                        return
                    }

                    handleMessage(type, data, cleanUrl)
                } catch (e: Exception) {
                    AppLogger.e("ComfyWebSocket", "Exception handling message: ${e.localizedMessage}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                AppLogger.e("ComfyWebSocket", "WebSocket failure", t)
                isConnected = false
                handleFailure(t, cleanUrl)
            }
        })
    }

    // ── Disconnect ─────────────────────────────────────────────────────

    fun disconnect(clearPromptId: Boolean = true) {
        try {
            isConnected = false
            activeWebSocket?.close(1000, "Clean closure")
            activeWebSocket?.cancel()
        } catch (e: Exception) {
            AppLogger.e("ComfyWebSocket", "Exception disconnecting: ${e.localizedMessage}")
        }
        activeWebSocket = null
        if (clearPromptId) {
            currentPromptId = null
            executionFinished = false
        }
    }

    // ── Message Handler ────────────────────────────────────────────────

    private fun handleMessage(type: String, data: JsonObject, serverUrl: String) {
        when (type) {
            "execution_start" -> {
                progressFlow.value = progressFlow.value.copy(
                    state = GenerationState.GeneratingBase,
                    statusText = "Generation started..."
                )
            }
            "status" -> {
                val statusObj = data.getAsJsonObject("status")
                if (statusObj != null && statusObj.has("exec_info")) {
                    val execInfo = statusObj.getAsJsonObject("exec_info")
                    if (execInfo.has("queue_remaining")) {
                        val remaining = execInfo.get("queue_remaining").asInt
                        val current = progressFlow.value
                        if (current.state == GenerationState.ConnectingComfy || current.state == GenerationState.GeneratingBase) {
                            if (remaining > 0) {
                                progressFlow.value = current.copy(
                                    state = GenerationState.GeneratingBase,
                                    statusText = "Queued ($remaining remaining)..."
                                )
                            }
                        }
                    }
                }
            }
            "executing" -> {
                val node = data.get("node")?.let { if (it.isJsonNull) null else it.asString }
                if (node == null) {
                    executionFinished = true
                    handleExecutionComplete(serverUrl)
                } else {
                    handleNodeExecution(node)
                }
            }
            "progress" -> {
                val value = data.get("value").asInt
                val max = data.get("max").asInt
                val percent = value.toFloat() / max.toFloat()
                val current = progressFlow.value
                progressFlow.value = current.copy(
                    percent = percent,
                    statusText = "${current.statusText.substringBefore(" (")} (${(percent * 100).toInt()}%)"
                )
            }
            "executed" -> handleExecutedOutput(data, serverUrl)
            "execution_error" -> {
                val exceptionMsg = data.get("exception_message")?.asString ?: "Unknown server error"
                val nodeType = data.get("node_type")?.asString ?: "Node"
                AppLogger.e("ComfyWebSocket", "Execution error in $nodeType: $exceptionMsg")
                progressFlow.value = progressFlow.value.copy(
                    state = GenerationState.Failed,
                    statusText = "Error in $nodeType: $exceptionMsg"
                )
                disconnect()
            }
            "execution_interrupted" -> {
                AppLogger.w("ComfyWebSocket", "Execution was interrupted on server")
                progressFlow.value = progressFlow.value.copy(
                    state = GenerationState.Cancelled,
                    statusText = "Generation was interrupted on server."
                )
                disconnect()
            }
        }
    }

    // ── Node Execution Display ─────────────────────────────────────────

    private fun handleNodeExecution(node: String) {
        val current = progressFlow.value
        val classType = activeWorkflow?.getAsJsonObject(node)?.get("class_type")?.asString
        val nodeName = when {
            classType != null && (classType.contains("Sampler", ignoreCase = true) || classType.contains("KSampler", ignoreCase = true)) -> "Sampling (KSampler)"
            classType != null && classType.contains("VAE", ignoreCase = true) -> "Decoding VAE"
            classType != null && (classType.contains("Save", ignoreCase = true) || classType.contains("Preview", ignoreCase = true)) -> "Saving Output"
            classType != null && (classType.contains("CLIP", ignoreCase = true) || classType.contains("Prompt", ignoreCase = true)) -> "Encoding Prompt"
            classType != null && (classType.contains("Loader", ignoreCase = true) || classType.contains("Checkpoint", ignoreCase = true)) -> "Loading Checkpoint"
            classType != null -> "Executing $classType"
            else -> "Executing Node $node"
        }
        progressFlow.value = current.copy(
            state = GenerationState.GeneratingBase,
            currentNode = node,
            statusText = nodeName
        )
    }

    // ── Execution Complete Handling ─────────────────────────────────────

    private fun handleExecutionComplete(serverUrl: String) {
        val current = progressFlow.value
        if (current.state == GenerationState.Completed ||
            current.state == GenerationState.Cancelled ||
            current.state == GenerationState.Failed) return

        if (current.finalImages.isNotEmpty() || current.finalImage != null) {
            progressFlow.value = current.copy(
                state = GenerationState.Completed,
                percent = 1f,
                statusText = "Completed successfully."
            )
            disconnect()
        } else {
            // Image not received yet; fetch from /history
            val pid = currentPromptId
            val sUrl = activeServerUrl ?: serverUrl
            if (pid != null && sUrl.isNotBlank()) {
                scope.launch {
                    var recovered = false
                    for (attempt in 1..4) {
                        delay(if (attempt == 1) 300L else 700L)
                        val urls = ComfyApiService.fetchPromptOutputs(sUrl, pid, activeOutputNodeId, activeWorkflow)
                        if (urls.isNotEmpty()) {
                            progressFlow.value = progressFlow.value.copy(
                                finalImage = urls.first(),
                                finalImages = urls,
                                state = GenerationState.Completed,
                                percent = 1f,
                                statusText = "Completed successfully."
                            )
                            disconnect()
                            recovered = true
                            break
                        }
                    }
                    if (!recovered && progressFlow.value.state != GenerationState.Completed) {
                        progressFlow.value = progressFlow.value.copy(
                            state = GenerationState.Completed,
                            percent = 1f,
                            statusText = "Completed successfully."
                        )
                        disconnect()
                    }
                }
            }
        }
    }

    // ── Executed Output Handling ────────────────────────────────────────

    private fun handleExecutedOutput(data: JsonObject, serverUrl: String) {
        val executedNode = data.get("node")?.let { if (it.isJsonNull) null else it.asString }
        val output = data.get("output")
        if (output == null || !output.isJsonObject) return

        val outputObj = output.asJsonObject
        val images = outputObj.getAsJsonArray("images") ?: outputObj.getAsJsonArray("gifs") ?: return
        if (images.size() == 0) return

        val executedClassType = executedNode?.let { activeWorkflow?.getAsJsonObject(it)?.get("class_type")?.asString } ?: ""
        if (executedClassType.contains("MaskPreview", ignoreCase = true) ||
            executedClassType.contains("PreviewMask", ignoreCase = true)) {
            AppLogger.d("ComfyWebSocket", "Ignoring intermediate MaskPreview from node $executedNode")
            return
        }

        val isTargetNode = (executedNode != null && executedNode == activeOutputNodeId)
        val urls = mutableListOf<String>()
        for (i in 0 until images.size()) {
            val img = images[i].asJsonObject
            val filename = img.get("filename")?.asString ?: continue
            val subfolder = img.get("subfolder")?.asString ?: ""
            val type = img.get("type")?.asString ?: "output"
            val subfolderParam = if (subfolder.isNotBlank()) "&subfolder=$subfolder" else ""
            val imageUrl = "$serverUrl/view?filename=$filename$subfolderParam&type=$type"
            urls.add(imageUrl)
        }
        if (urls.isEmpty()) return

        val current = progressFlow.value
        val shouldUpdateImage = isTargetNode || current.finalImage == null || current.finalImage!!.contains("Mask")
        val isNowCompleted = executionFinished && (isTargetNode || current.state != GenerationState.Completed)
        progressFlow.value = current.copy(
            finalImage = if (shouldUpdateImage) urls.first() else current.finalImage,
            finalImages = if (shouldUpdateImage) urls else current.finalImages,
            state = if (isNowCompleted) GenerationState.Completed else current.state,
            percent = if (isNowCompleted) 1f else current.percent,
            statusText = if (isNowCompleted) "Completed successfully." else current.statusText
        )
        if (isNowCompleted && isTargetNode) {
            disconnect()
        }
    }

    // ── Failure Handling ───────────────────────────────────────────────

    private fun handleFailure(t: Throwable, serverUrl: String) {
        val current = progressFlow.value
        val pid = currentPromptId
        val sUrl = activeServerUrl ?: serverUrl

        if (current.state != GenerationState.Completed &&
            current.state != GenerationState.Cancelled &&
            current.state != GenerationState.Failed &&
            pid != null) {

            if (retryCount < maxRetries) {
                retryCount++
                AppLogger.w("ComfyWebSocket", "Disconnected. Reconnecting in 2s (attempt $retryCount/$maxRetries)...")
                scope.launch {
                    delay(2000)
                    activeServerUrl?.let { connect(it, isReconnect = true) }
                }
                return
            }

            // Retries exhausted: check if prompt finished in history
            if (sUrl.isNotBlank()) {
                scope.launch {
                    val urls = ComfyApiService.fetchPromptOutputs(sUrl, pid, activeOutputNodeId, activeWorkflow)
                    if (urls.isNotEmpty()) {
                        progressFlow.value = progressFlow.value.copy(
                            finalImage = urls.first(),
                            finalImages = urls,
                            state = GenerationState.Completed,
                            percent = 1f,
                            statusText = "Completed successfully."
                        )
                        disconnect()
                        return@launch
                    }
                    if (progressFlow.value.state != GenerationState.Completed &&
                        progressFlow.value.state != GenerationState.Cancelled) {
                        progressFlow.value = progressFlow.value.copy(
                            state = GenerationState.Failed,
                            statusText = "Connection lost: ${t.localizedMessage ?: "WebSocket disconnected"}"
                        )
                    }
                }
                return
            }
        }

        if (progressFlow.value.state != GenerationState.Completed &&
            progressFlow.value.state != GenerationState.Cancelled) {
            progressFlow.value = progressFlow.value.copy(
                state = GenerationState.Failed,
                statusText = "Connection failed: ${t.localizedMessage ?: "WebSocket failure"}"
            )
        }
    }
}
