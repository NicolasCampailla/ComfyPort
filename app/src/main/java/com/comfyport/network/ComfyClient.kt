package com.comfyport.network

import android.content.Context
import com.comfyport.data.*
import com.comfyport.network.api.ComfyApiService
import com.comfyport.network.api.WorkflowAnalyzer
import com.comfyport.network.api.WorkflowPreparer
import com.comfyport.network.http.ComfyHttpClient
import com.comfyport.network.mask.MaskProcessor
import com.comfyport.network.websocket.ComfyWebSocket
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

/**
 * Main coordinator for ComfyPort image generation and server communication.
 * Acts as a clean facade orchestrating specialized modules:
 * - [ComfyHttpClient]: OkHttp instance, connection pool, media types
 * - [ComfyApiService]: HTTP REST endpoints
 * - [WorkflowPreparer]: Loading, format conversion, and parameter injection
 * - [WorkflowAnalyzer]: Metadata extraction and visual graph preview analysis
 * - [ComfyWebSocket]: Real-time execution events and progress tracking
 * - [MaskProcessor]: Inpainting composite and standalone mask generation
 */
object ComfyClient {

    val clientId: String = UUID.randomUUID().toString()
    val progressFlow = MutableStateFlow(ProgressInfo())
    val clientScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    internal val webSocket = ComfyWebSocket(clientId, progressFlow, clientScope)
    internal var activeGenerationJob: Job? = null

    // Backward-compatible typealiases for callers expecting nested types
    typealias ConnectionTestResult = com.comfyport.data.ConnectionTestResult
    typealias WorkflowMetadata = com.comfyport.data.WorkflowMetadata
    typealias WorkflowPreviewData = com.comfyport.data.WorkflowPreviewData
    typealias MachineHistoryItem = com.comfyport.data.MachineHistoryItem

    // ── Generation Lifecycle ─────────────────────────────────────────────

    fun startGeneration(
        context: Context,
        prompt: String,
        settings: AppSettings,
        negativePrompt: String? = null,
        inputImageBytes: ByteArray? = null,
        inputMaskBytes: ByteArray? = null,
        onSeedGenerated: (Long) -> Unit
    ) {
        activeGenerationJob?.cancel()
        activeGenerationJob = clientScope.launch {
            try {
                progressFlow.value = ProgressInfo(
                    state = GenerationState.ConnectingComfy,
                    statusText = "Initializing workflow..."
                )

                val prepared = WorkflowPreparer.prepareWorkflow(
                    context = context,
                    prompt = prompt,
                    settings = settings,
                    negativePrompt = negativePrompt,
                    inputImageBytes = inputImageBytes,
                    inputMaskBytes = inputMaskBytes,
                    onStatusUpdate = { status ->
                        progressFlow.value = progressFlow.value.copy(statusText = status)
                    }
                )

                onSeedGenerated(prepared.activeSeed)
                webSocket.activeOutputNodeId = prepared.saveImageNodeId
                webSocket.activeWorkflow = prepared.workflowObj

                executeLocalGeneration(prepared.promptJson, settings)
            } catch (e: Exception) {
                AppLogger.e("ComfyClient", "Error during generation: ${e.localizedMessage}")
                progressFlow.value = ProgressInfo(
                    state = GenerationState.Failed,
                    statusText = "Error: ${e.localizedMessage}"
                )
            }
        }
    }

    internal suspend fun executeLocalGeneration(promptJson: String, settings: AppSettings) = withContext(Dispatchers.IO) {
        val serverBaseUrl = UrlValidator.normalizeUrl(settings.serverUrl)
        if (serverBaseUrl.isBlank()) {
            progressFlow.value = ProgressInfo(
                state = GenerationState.Failed,
                statusText = "ComfyUI Server URL is empty. Please configure it in Settings."
            )
            return@withContext
        }

        try {
            progressFlow.value = progressFlow.value.copy(
                state = GenerationState.ConnectingComfy,
                statusText = "Connecting to ComfyUI..."
            )

            webSocket.connect(serverBaseUrl)

            // Wait briefly for WebSocket handshake
            var waitedMs = 0
            while (!webSocket.isConnected && waitedMs < 2500) {
                delay(100)
                waitedMs += 100
            }

            progressFlow.value = progressFlow.value.copy(
                state = GenerationState.ConnectingComfy,
                statusText = "Submitting prompt to ComfyUI..."
            )

            val rawWorkflowObj = ComfyHttpClient.gson.fromJson(promptJson, JsonObject::class.java)
            val workflowObj = if (rawWorkflowObj.has("prompt") && rawWorkflowObj.get("prompt").isJsonObject) {
                rawWorkflowObj.getAsJsonObject("prompt")
            } else {
                rawWorkflowObj
            }
            webSocket.activeWorkflow = workflowObj

            val submitResult = ComfyApiService.submitPrompt(serverBaseUrl, workflowObj, clientId)

            if (!submitResult.success) {
                val displayErr = submitResult.errorMessage ?: "Server rejected prompt request"
                progressFlow.value = ProgressInfo(
                    state = GenerationState.Failed,
                    statusText = displayErr
                )
                return@withContext
            }

            val promptId = submitResult.promptId
            webSocket.currentPromptId = promptId
            webSocket.executionFinished = false

            progressFlow.value = progressFlow.value.copy(
                state = GenerationState.GeneratingBase,
                statusText = "Prompt queued in ComfyUI..."
            )

            if (promptId == null) {
                progressFlow.value = ProgressInfo(
                    state = GenerationState.Failed,
                    statusText = "Server failed to return prompt ID"
                )
            }
        } catch (e: Exception) {
            AppLogger.e("ComfyClient", "Exception during local generation: ${e.localizedMessage}")
            val msg = e.localizedMessage ?: "Unknown connection error"
            progressFlow.value = ProgressInfo(
                state = GenerationState.Failed,
                statusText = "Connection error to $serverBaseUrl: $msg"
            )
        }
    }

    fun stopGeneration(settings: AppSettings) {
        activeGenerationJob?.cancel()
        activeGenerationJob = null

        clientScope.launch {
            try {
                ComfyApiService.interruptGeneration(settings.serverUrl)
                progressFlow.value = progressFlow.value.copy(
                    state = GenerationState.Cancelled,
                    statusText = "Generation cancelled."
                )
            } catch (e: Exception) {
                AppLogger.e("ComfyClient", "Exception stopping generation: ${e.localizedMessage}")
            } finally {
                webSocket.disconnect()
            }
        }
    }

    // ── Delegating API helpers ──────────────────────────────────────────

    suspend fun testConnection(serverUrl: String): ConnectionTestResult =
        ComfyApiService.testConnection(serverUrl)

    fun uploadImage(serverUrl: String, imageBytes: ByteArray, filename: String, overwrite: Boolean = true): String? =
        ComfyApiService.uploadImage(serverUrl, imageBytes, filename, overwrite)

    fun fetchSavedWorkflows(serverUrl: String, onResult: (List<String>) -> Unit) {
        clientScope.launch {
            val list = ComfyApiService.fetchSavedWorkflows(serverUrl)
            onResult(list)
        }
    }

    fun fetchLastWorkflow(serverUrl: String, onResult: (workflowJson: String?, errorMsg: String?) -> Unit) {
        clientScope.launch {
            val result = ComfyApiService.fetchLastWorkflow(serverUrl)
            onResult(result.workflowJson, result.errorMsg)
        }
    }

    fun fetchMachineHistory(serverUrl: String, onResult: (items: List<MachineHistoryItem>, errorMsg: String?) -> Unit) {
        clientScope.launch {
            val (items, error) = ComfyApiService.fetchMachineHistory(serverUrl)
            onResult(items, error)
        }
    }

    suspend fun fetchPromptOutputs(serverUrl: String, promptId: String): List<String> =
        ComfyApiService.fetchPromptOutputs(serverUrl, promptId, webSocket.activeOutputNodeId, webSocket.activeWorkflow)

    fun extractWorkflowMetadata(workflowJsonString: String): WorkflowMetadata =
        WorkflowAnalyzer.extractWorkflowMetadata(workflowJsonString)

    fun parseWorkflowPreview(
        label: String,
        filename: String,
        jsonString: String,
        activeMapping: WorkflowNodeMapping? = null
    ): WorkflowPreviewData =
        WorkflowAnalyzer.parseWorkflowPreview(label, filename, jsonString, activeMapping)

    fun autoDetectNodeMapping(workflowJsonString: String): WorkflowNodeMapping =
        WorkflowAnalyzer.autoDetectNodeMapping(workflowJsonString)

    fun resolveDimensions(settings: AppSettings): Pair<Int, Int> =
        WorkflowAnalyzer.resolveDimensions(settings)

    fun calculateDimensions(megapixel: String, aspectRatio: String, divisibleBy: Int = 16): Pair<Int, Int> =
        WorkflowAnalyzer.calculateDimensions(megapixel, aspectRatio, divisibleBy)

    suspend fun convertWorkflowToApi(
        uiJsonString: String,
        serverUrl: String,
        context: Context? = null
    ): ConversionResult =
        WorkflowPreparer.convertWorkflowToApi(uiJsonString, serverUrl, context)

    fun createCompositeImageWithMask(imageBytes: ByteArray, maskBytes: ByteArray): ByteArray =
        MaskProcessor.createCompositeImageWithMask(imageBytes, maskBytes)

    fun createStandaloneMaskImage(maskBytes: ByteArray): ByteArray =
        MaskProcessor.createStandaloneMaskImage(maskBytes)

    fun hasLinkedLatentResolution(workflowObj: JsonObject): Boolean =
        WorkflowAnalyzer.hasLinkedLatentResolution(workflowObj)

    fun isLatentDerivedFromImage(workflowObj: JsonObject): Boolean =
        WorkflowAnalyzer.isLatentDerivedFromImage(workflowObj)

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
    ) = WorkflowPreparer.injectWorkflowParameters(
        workflowObj, prompt, negativePrompt, activeSeed, width, height,
        batchSize, uploadedImageName, uploadedMaskName, customMapping, resolutionMode
    )

    fun evictConnectionPool() = ComfyHttpClient.evictConnectionPool()
}
