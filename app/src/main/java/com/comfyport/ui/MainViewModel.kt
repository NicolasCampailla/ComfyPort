package com.comfyport.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.comfyport.data.AppSettings
import com.comfyport.data.ConversionResult
import com.comfyport.data.FormatType
import com.comfyport.data.GalleryItem
import com.comfyport.data.GenerationState
import com.comfyport.data.ProgressInfo
import com.comfyport.data.SavedWorkflow
import com.comfyport.data.SeedMode
import com.comfyport.data.SettingsManager
import com.comfyport.data.SshLaunchState
import com.comfyport.network.AppLogger
import com.comfyport.network.ComfyClient
import com.comfyport.network.SshClient
import com.comfyport.network.UrlValidator
import com.comfyport.network.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsManager = SettingsManager(application)
    
    private val _settings = MutableStateFlow(settingsManager.getSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _queueList = MutableStateFlow<List<com.comfyport.data.QueueJob>>(emptyList())
    val queueList: StateFlow<List<com.comfyport.data.QueueJob>> = _queueList.asStateFlow()

    private val _activeJobId = MutableStateFlow<String?>(null)
    val activeJobId: StateFlow<String?> = _activeJobId.asStateFlow()

    init {
        viewModelScope.launch {
            _settings.collect { s ->
                if (s.serverUrl.isNotBlank()) {
                    com.comfyport.network.api.ComfySessionInterpreter.initSession(application, s.serverUrl)
                }
            }
        }
    }

    fun importAndSaveWorkflow(context: Context, uri: Uri, customLabel: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                } ?: throw Exception("Could not read file contents.")
                
                val format = FormatType.detect(jsonString)
                val finalApiJson = if (format == FormatType.API_READY) {
                    jsonString
                } else if (format == FormatType.UI_STANDARD) {
                    val result = ComfyClient.convertWorkflowToApi(jsonString, _settings.value.serverUrl, context)
                    when (result) {
                        is ConversionResult.Success -> result.apiJson
                        else -> jsonString
                    }
                } else {
                    jsonString
                }

                val workflowId = java.util.UUID.randomUUID().toString()
                val filename = "wf_${workflowId}.json"
                context.openFileOutput(filename, Context.MODE_PRIVATE).use { output ->
                    output.write(finalApiJson.toByteArray())
                }

                // Extract workflow metadata (prompt, resolution, batch size)
                val meta = ComfyClient.extractWorkflowMetadata(finalApiJson).let { apiMeta ->
                    if (apiMeta.prompt != null && apiMeta.width != null && apiMeta.height != null && apiMeta.batchSize != null) {
                        apiMeta
                    } else {
                        val uiMeta = ComfyClient.extractWorkflowMetadata(jsonString)
                        ComfyClient.WorkflowMetadata(
                            prompt = apiMeta.prompt ?: uiMeta.prompt,
                            negativePrompt = apiMeta.negativePrompt ?: uiMeta.negativePrompt,
                            width = apiMeta.width ?: uiMeta.width,
                            height = apiMeta.height ?: uiMeta.height,
                            batchSize = apiMeta.batchSize ?: uiMeta.batchSize,
                            hasImageInput = apiMeta.hasImageInput || uiMeta.hasImageInput,
                            hasMaskInput = apiMeta.hasMaskInput || uiMeta.hasMaskInput,
                            isResolutionManagedByWorkflow = apiMeta.isResolutionManagedByWorkflow || uiMeta.isResolutionManagedByWorkflow
                        )
                    }
                }

                val resolvedLabel = customLabel.ifBlank { "Custom Workflow" }
                val detectedMapping = ComfyClient.autoDetectNodeMapping(finalApiJson).let { apiMap ->
                    if (apiMap.isCustomized) apiMap else ComfyClient.autoDetectNodeMapping(jsonString)
                }
                val newWf = com.comfyport.data.SavedWorkflow(
                    id = workflowId,
                    label = resolvedLabel,
                    filename = filename,
                    nodeMapping = detectedMapping.takeIf { it.isCustomized }
                )
                val targetResMode = if (meta.isResolutionManagedByWorkflow) {
                    "WORKFLOW"
                } else if (meta.width != null && meta.height != null) {
                    "CUSTOM"
                } else {
                    _settings.value.resolutionMode
                }
                val updatedList = _settings.value.savedWorkflows + newWf
                val updatedSettings = _settings.value.copy(
                    savedWorkflows = updatedList,
                    selectedWorkflowId = workflowId,
                    workflowToUse = filename,
                    customWidth = meta.width ?: _settings.value.customWidth,
                    customHeight = meta.height ?: _settings.value.customHeight,
                    resolutionMode = targetResMode,
                    batchSize = meta.batchSize ?: _settings.value.batchSize
                )
                updateSettings(updatedSettings)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved workflow '$resolvedLabel'!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error saving workflow: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun importWorkflow(context: Context, uri: Uri) {
        importAndSaveWorkflow(context, uri, "Imported Workflow")
    }

    fun loadLastServerWorkflow(context: Context, onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        val serverUrl = _settings.value.serverUrl
        if (serverUrl.isBlank()) {
            Toast.makeText(context, "Please configure ComfyUI server IP in Settings first.", Toast.LENGTH_LONG).show()
            onComplete(false, "Server URL not configured")
            return
        }

        Toast.makeText(context, "Fetching last workflow from server...", Toast.LENGTH_SHORT).show()
        ComfyClient.fetchLastWorkflow(serverUrl) { jsonString, errorMsg ->
            viewModelScope.launch(Dispatchers.IO) {
                if (jsonString != null) {
                    try {
                        val format = FormatType.detect(jsonString)
                        val finalApiJson = if (format == FormatType.API_READY) {
                            jsonString
                        } else if (format == FormatType.UI_STANDARD) {
                            val result = ComfyClient.convertWorkflowToApi(jsonString, _settings.value.serverUrl, context)
                            when (result) {
                                is ConversionResult.Success -> result.apiJson
                                is ConversionResult.Error.MissingExtension -> {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Missing ComfyUI API converter extension on server.", Toast.LENGTH_LONG).show()
                                        onComplete(false, "Missing extension")
                                    }
                                    return@launch
                                }
                                is ConversionResult.Error.Generic -> {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Conversion failed: ${result.message}", Toast.LENGTH_LONG).show()
                                        onComplete(false, result.message)
                                    }
                                    return@launch
                                }
                            }
                        } else {
                            jsonString
                        }

                        val customLabel = "Last PC Workflow"
                        val existing = _settings.value.savedWorkflows.firstOrNull { it.label == customLabel }
                        val workflowId = existing?.id ?: java.util.UUID.randomUUID().toString()
                        val filename = "wf_${workflowId}.json"
                        context.openFileOutput(filename, Context.MODE_PRIVATE).use { output ->
                            output.write(finalApiJson.toByteArray())
                        }

                        // Extract workflow metadata (prompt, resolution, batch size)
                        val meta = ComfyClient.extractWorkflowMetadata(finalApiJson).let { apiMeta ->
                            if (apiMeta.prompt != null && apiMeta.width != null && apiMeta.height != null && apiMeta.batchSize != null) {
                                apiMeta
                            } else {
                                val uiMeta = ComfyClient.extractWorkflowMetadata(jsonString)
                                ComfyClient.WorkflowMetadata(
                                    prompt = apiMeta.prompt ?: uiMeta.prompt,
                                    negativePrompt = apiMeta.negativePrompt ?: uiMeta.negativePrompt,
                                    width = apiMeta.width ?: uiMeta.width,
                                    height = apiMeta.height ?: uiMeta.height,
                                    batchSize = apiMeta.batchSize ?: uiMeta.batchSize,
                                    hasImageInput = apiMeta.hasImageInput || uiMeta.hasImageInput,
                                    hasMaskInput = apiMeta.hasMaskInput || uiMeta.hasMaskInput,
                                    isResolutionManagedByWorkflow = apiMeta.isResolutionManagedByWorkflow || uiMeta.isResolutionManagedByWorkflow
                                )
                            }
                        }

                        val detectedMapping = ComfyClient.autoDetectNodeMapping(finalApiJson).let { apiMap ->
                            if (apiMap.isCustomized) apiMap else ComfyClient.autoDetectNodeMapping(jsonString)
                        }
                        val newWf = com.comfyport.data.SavedWorkflow(
                            id = workflowId,
                            label = customLabel,
                            filename = filename,
                            nodeMapping = detectedMapping.takeIf { it.isCustomized }
                        )
                        val updatedList = if (existing != null) {
                            _settings.value.savedWorkflows.map { if (it.id == workflowId) newWf else it }
                        } else {
                            _settings.value.savedWorkflows + newWf
                        }
                        val targetResMode = if (meta.isResolutionManagedByWorkflow) {
                            "WORKFLOW"
                        } else if (meta.width != null && meta.height != null) {
                            "CUSTOM"
                        } else {
                            _settings.value.resolutionMode
                        }
                        val updatedSettings = _settings.value.copy(
                            savedWorkflows = updatedList,
                            selectedWorkflowId = workflowId,
                            workflowToUse = filename,
                            customWidth = meta.width ?: _settings.value.customWidth,
                            customHeight = meta.height ?: _settings.value.customHeight,
                            resolutionMode = targetResMode,
                            batchSize = meta.batchSize ?: _settings.value.batchSize
                        )
                        updateSettings(updatedSettings)

                        withContext(Dispatchers.Main) {
                            val details = buildString {
                                append("Selected last workflow from computer!")
                                val parts = mutableListOf<String>()
                                if (!meta.prompt.isNullOrBlank()) parts.add("prompt")
                                if (meta.width != null && meta.height != null) parts.add("${meta.width}x${meta.height}")
                                if (meta.batchSize != null) parts.add("${meta.batchSize} img")
                                if (parts.isNotEmpty()) {
                                    append(" (${parts.joinToString(", ")})")
                                }
                            }
                            Toast.makeText(context, details, Toast.LENGTH_SHORT).show()
                            onComplete(true, "Success")
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to save workflow: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            onComplete(false, e.localizedMessage ?: "Unknown error")
                        }
                    }
                } else {
                    val msg = errorMsg ?: "Could not fetch workflow from server"
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        onComplete(false, msg)
                    }
                }
            }
        }
    }

    fun deleteSavedWorkflow(context: Context, id: String) {
        try {
            context.deleteFile("wf_${id}.json")
        } catch (_: Exception) {}
        val updatedList = _settings.value.savedWorkflows.filter { it.id != id }
        val newSelected = if (_settings.value.selectedWorkflowId == id) {
            updatedList.firstOrNull()?.id ?: ""
        } else {
            _settings.value.selectedWorkflowId
        }
        val newWfToUse = updatedList.firstOrNull { it.id == newSelected }?.filename ?: ""
        val updated = _settings.value.copy(
            savedWorkflows = updatedList,
            selectedWorkflowId = newSelected,
            workflowToUse = newWfToUse
        )
        updateSettings(updated)
    }

    fun updateWorkflowLabel(id: String, newLabel: String) {
        val updatedList = _settings.value.savedWorkflows.map {
            if (it.id == id) it.copy(label = newLabel) else it
        }
        val updated = _settings.value.copy(savedWorkflows = updatedList)
        updateSettings(updated)
    }

    fun selectWorkflowById(id: String) {
        val wf = _settings.value.savedWorkflows.firstOrNull { it.id == id }
        val wfToUse = wf?.filename ?: ""

        var newWidth = _settings.value.customWidth
        var newHeight = _settings.value.customHeight
        var newResMode = _settings.value.resolutionMode
        var newBatchSize = _settings.value.batchSize
        var newPrompt: String? = null

        var updatedSavedWorkflows = _settings.value.savedWorkflows
        if (wfToUse.isNotBlank()) {
            try {
                val app = getApplication<Application>()
                val file = app.getFileStreamPath(wfToUse)
                if (file != null && file.exists()) {
                    val jsonString = file.readText()
                    val meta = ComfyClient.extractWorkflowMetadata(jsonString)
                    if (meta.width != null) newWidth = meta.width
                    if (meta.height != null) newHeight = meta.height
                    if (meta.isResolutionManagedByWorkflow) {
                        newResMode = "WORKFLOW"
                    } else if (meta.width != null && meta.height != null) {
                        newResMode = "CUSTOM"
                    }
                    if (meta.batchSize != null) newBatchSize = meta.batchSize
                    if (!meta.prompt.isNullOrBlank()) newPrompt = meta.prompt

                    if (wf != null && (wf.nodeMapping == null || !wf.nodeMapping.isCustomized)) {
                        val detected = ComfyClient.autoDetectNodeMapping(jsonString)
                        if (detected.isCustomized) {
                            updatedSavedWorkflows = updatedSavedWorkflows.map {
                                if (it.id == wf.id) it.copy(nodeMapping = detected) else it
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("MainViewModel", "Error loading specs from selected workflow: ${e.localizedMessage}")
            }
        }

        val updated = _settings.value.copy(
            savedWorkflows = updatedSavedWorkflows,
            selectedWorkflowId = id,
            workflowToUse = wfToUse,
            customWidth = newWidth,
            customHeight = newHeight,
            resolutionMode = newResMode,
            batchSize = newBatchSize
        )
        updateSettings(updated)
    }

    private val _machineHistory = MutableStateFlow<List<ComfyClient.MachineHistoryItem>>(emptyList())
    val machineHistory: StateFlow<List<ComfyClient.MachineHistoryItem>> = _machineHistory.asStateFlow()

    private val _isFetchingHistory = MutableStateFlow(false)
    val isFetchingHistory: StateFlow<Boolean> = _isFetchingHistory.asStateFlow()

    fun fetchMachineHistory(onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        val serverUrl = _settings.value.serverUrl
        if (serverUrl.isBlank()) {
            onComplete(false, "Server URL not configured")
            return
        }
        _isFetchingHistory.value = true
        ComfyClient.fetchMachineHistory(serverUrl) { items, error ->
            _isFetchingHistory.value = false
            _machineHistory.value = items
            onComplete(items.isNotEmpty(), error)
        }
    }

    fun getWorkflowPreview(context: Context, workflow: com.comfyport.data.SavedWorkflow): ComfyClient.WorkflowPreviewData? {
        return try {
            val jsonString = context.openFileInput(workflow.filename).bufferedReader().use { it.readText() }
            val resolvedMapping = if (workflow.nodeMapping != null && workflow.nodeMapping.isCustomized) {
                workflow.nodeMapping
            } else {
                ComfyClient.autoDetectNodeMapping(jsonString).takeIf { it.isCustomized } ?: workflow.nodeMapping
            }
            if (workflow.nodeMapping == null && resolvedMapping != null && resolvedMapping.isCustomized) {
                val updatedList = _settings.value.savedWorkflows.map {
                    if (it.id == workflow.id) it.copy(nodeMapping = resolvedMapping) else it
                }
                updateSettings(_settings.value.copy(savedWorkflows = updatedList))
            }
            ComfyClient.parseWorkflowPreview(workflow.label, workflow.filename, jsonString, resolvedMapping).copy(
                id = workflow.id,
                activeMapping = resolvedMapping
            )
        } catch (e: Exception) {
            AppLogger.e("MainViewModel", "Failed to load workflow preview: ${e.localizedMessage}")
            null
        }
    }

    fun updateWorkflowNodeMapping(workflowId: String, mapping: com.comfyport.data.WorkflowNodeMapping) {
        val updatedList = _settings.value.savedWorkflows.map { wf ->
            if (wf.id == workflowId) wf.copy(nodeMapping = mapping) else wf
        }
        val updatedSettings = _settings.value.copy(savedWorkflows = updatedList)
        updateSettings(updatedSettings)
        AppLogger.d("MainViewModel", "Updated node mapping for workflow $workflowId (customCount=${mapping.customCount})")
    }

    fun resetWorkflowNodeMapping(workflowId: String) {
        updateWorkflowNodeMapping(workflowId, com.comfyport.data.WorkflowNodeMapping())
    }

    fun applyWorkflowSpecs(context: Context, preview: ComfyClient.WorkflowPreviewData) {
        if (!preview.prompt.isNullOrBlank()) {
            updatePrompt(preview.prompt)
        }
        val targetResMode = if (preview.isResolutionManagedByWorkflow) {
            "WORKFLOW"
        } else if (preview.width != null && preview.height != null) {
            "CUSTOM"
        } else {
            _settings.value.resolutionMode
        }
        val updated = _settings.value.copy(
            selectedWorkflowId = if (preview.id.isNotBlank()) preview.id else _settings.value.selectedWorkflowId,
            workflowToUse = if (preview.filename.isNotBlank()) preview.filename else _settings.value.workflowToUse,
            customWidth = preview.width ?: _settings.value.customWidth,
            customHeight = preview.height ?: _settings.value.customHeight,
            resolutionMode = targetResMode,
            batchSize = preview.batchSize ?: _settings.value.batchSize
        )
        updateSettings(updated)
        Toast.makeText(context, "Loaded prompt & specs from '${preview.label}'!", Toast.LENGTH_SHORT).show()
    }

    fun importMachineWorkflow(
        context: Context,
        item: ComfyClient.MachineHistoryItem,
        customLabel: String? = null,
        activate: Boolean = false,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = item.rawJson
                val finalApiJson = if (item.isUiFormat) {
                    val result = ComfyClient.convertWorkflowToApi(jsonString, _settings.value.serverUrl, getApplication<Application>())
                    when (result) {
                        is ConversionResult.Success -> result.apiJson
                        else -> jsonString
                    }
                } else {
                    jsonString
                }

                val defaultName = buildString {
                    if (!item.modelName.isNullOrBlank()) {
                        append(item.modelName.substringBeforeLast("."))
                    } else {
                        append("Machine Run #${item.promptIndex}")
                    }
                    if (item.width != null && item.height != null) {
                        append(" (${item.width}x${item.height})")
                    }
                }
                val label = customLabel?.ifBlank { defaultName } ?: defaultName

                val workflowId = java.util.UUID.randomUUID().toString()
                val filename = "wf_${workflowId}.json"
                context.openFileOutput(filename, Context.MODE_PRIVATE).use { output ->
                    output.write(finalApiJson.toByteArray())
                }

                val meta = ComfyClient.extractWorkflowMetadata(finalApiJson)
                val detectedMapping = ComfyClient.autoDetectNodeMapping(finalApiJson).let { apiMap ->
                    if (apiMap.isCustomized) apiMap else ComfyClient.autoDetectNodeMapping(jsonString)
                }
                val newWf = com.comfyport.data.SavedWorkflow(
                    id = workflowId,
                    label = label,
                    filename = filename,
                    nodeMapping = detectedMapping.takeIf { it.isCustomized }
                )
                val updatedList = _settings.value.savedWorkflows + newWf
                val targetResMode = if (meta.isResolutionManagedByWorkflow) {
                    "WORKFLOW"
                } else if (meta.width != null && meta.height != null) {
                    "CUSTOM"
                } else {
                    _settings.value.resolutionMode
                }
                val updatedSettings = if (activate) {
                    _settings.value.copy(
                        savedWorkflows = updatedList,
                        selectedWorkflowId = workflowId,
                        workflowToUse = filename,
                        customWidth = meta.width ?: _settings.value.customWidth,
                        customHeight = meta.height ?: _settings.value.customHeight,
                        resolutionMode = targetResMode,
                        batchSize = meta.batchSize ?: _settings.value.batchSize
                    )
                } else {
                    _settings.value.copy(savedWorkflows = updatedList)
                }
                updateSettings(updatedSettings)

                withContext(Dispatchers.Main) {
                    val msg = if (activate) "Imported & activated '$label'!" else "Saved '$label' to Workflows!"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    onComplete(true)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to import machine workflow: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    onComplete(false)
                }
            }
        }
    }

    fun addSavedServer(label: String, url: String) {
        val normalizedUrl = UrlValidator.normalizeUrl(url)
        val id = java.util.UUID.randomUUID().toString()
        val newServer = com.comfyport.data.SavedServer(id = id, label = label.ifBlank { normalizedUrl }, url = normalizedUrl)
        val updatedList = _settings.value.savedServers + newServer
        val updated = _settings.value.copy(
            savedServers = updatedList,
            activeServerId = id,
            serverUrl = normalizedUrl
        )
        updateSettings(updated)
        refreshSavedWorkflows()
    }

    fun updateSavedServer(id: String, newLabel: String, newUrl: String) {
        val normalizedUrl = UrlValidator.normalizeUrl(newUrl)
        val updatedList = _settings.value.savedServers.map { server ->
            if (server.id == id) {
                server.copy(label = newLabel.ifBlank { normalizedUrl }, url = normalizedUrl)
            } else {
                server
            }
        }
        val newServerUrl = if (_settings.value.activeServerId == id) normalizedUrl else _settings.value.serverUrl
        val updated = _settings.value.copy(
            savedServers = updatedList,
            serverUrl = newServerUrl
        )
        updateSettings(updated)
        refreshSavedWorkflows()
    }

    fun deleteSavedServer(id: String) {
        val updatedList = _settings.value.savedServers.filter { it.id != id }
        val newActive = if (_settings.value.activeServerId == id) {
            updatedList.firstOrNull()?.id ?: ""
        } else {
            _settings.value.activeServerId
        }
        val newUrl = updatedList.firstOrNull { it.id == newActive }?.url ?: _settings.value.serverUrl
        val updated = _settings.value.copy(
            savedServers = updatedList,
            activeServerId = newActive,
            serverUrl = newUrl
        )
        updateSettings(updated)
        refreshSavedWorkflows()
    }

    fun selectServerById(id: String) {
        val server = _settings.value.savedServers.firstOrNull { it.id == id } ?: return
        val updated = _settings.value.copy(
            activeServerId = id,
            serverUrl = server.url
        )
        updateSettings(updated)
        refreshSavedWorkflows()
    }

    fun testServerConnection(url: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = ComfyClient.testConnection(url)
            withContext(Dispatchers.Main) {
                onResult(result.success, result.message)
            }
        }
    }

    private val _sshLaunchState = MutableStateFlow<SshLaunchState>(SshLaunchState.Idle)
    val sshLaunchState: StateFlow<SshLaunchState> = _sshLaunchState.asStateFlow()

    fun resetSshLaunchState() {
        _sshLaunchState.value = SshLaunchState.Idle
    }

    // --- SSH Terminal State ---
    private val _sshTerminalLines = MutableStateFlow<List<com.comfyport.data.SshTerminalLine>>(emptyList())
    val sshTerminalLines: StateFlow<List<com.comfyport.data.SshTerminalLine>> = _sshTerminalLines.asStateFlow()

    private val _isSshTerminalRunning = MutableStateFlow(false)
    val isSshTerminalRunning: StateFlow<Boolean> = _isSshTerminalRunning.asStateFlow()

    private val _isSshStreaming = MutableStateFlow(false)
    val isSshStreaming: StateFlow<Boolean> = _isSshStreaming.asStateFlow()

    private var sshStreamJob: kotlinx.coroutines.Job? = null

    private var _terminalLineIdCounter = java.util.concurrent.atomic.AtomicLong(0L)

    private fun addTerminalLine(type: com.comfyport.data.SshLineType, text: String) {
        val line = com.comfyport.data.SshTerminalLine(
            id = _terminalLineIdCounter.incrementAndGet(),
            type = type,
            text = text
        )
        _sshTerminalLines.value = _sshTerminalLines.value + line
    }

    fun clearSshTerminal() {
        _sshTerminalLines.value = emptyList()
    }

    fun executeSshTerminalCommand(
        command: String,
        customHost: String? = null,
        customPort: Int? = null,
        customUser: String? = null,
        customPass: String? = null
    ) {
        val s = _settings.value
        val host = (customHost ?: s.sshHost).ifBlank { UrlValidator.extractHost(s.serverUrl) }.trim()
        val port = customPort ?: s.sshPort
        val user = (customUser ?: s.sshUsername).trim()
        val pass = customPass ?: s.sshPassword
        val cmd = command.trim()

        if (host.isBlank()) {
            addTerminalLine(com.comfyport.data.SshLineType.ERROR, "No SSH host configured. Please set a host in SSH settings.")
            return
        }
        if (user.isBlank()) {
            addTerminalLine(com.comfyport.data.SshLineType.ERROR, "No SSH username configured.")
            return
        }
        if (cmd.isBlank()) return

        val effectiveCmd = if (cmd.trim().endsWith(".bat", ignoreCase = true) ||
            cmd.trim().endsWith(".cmd", ignoreCase = true) ||
            cmd.trim() == s.sshCommand.trim() ||
            cmd.trim().startsWith("start ", ignoreCase = true)
        ) {
            SshClient.prepareLaunchCommand(cmd, user)
        } else {
            cmd
        }

        addTerminalLine(com.comfyport.data.SshLineType.COMMAND, "$user@$host:~\$ $cmd")

        viewModelScope.launch {
            _isSshTerminalRunning.value = true
            addTerminalLine(com.comfyport.data.SshLineType.SYSTEM, "Connecting to $host:$port...")

            val result = SshClient.executeCommand(
                host = host,
                port = port,
                user = user,
                password = pass,
                command = effectiveCmd,
                waitOutputMs = 7000
            )

            if (result.output.isNotBlank()) {
                result.output.lines().forEach { line ->
                    if (line.isNotBlank()) {
                        addTerminalLine(com.comfyport.data.SshLineType.STDOUT, line)
                    }
                }
            }

            if (!result.success && !result.errorMessage.isNullOrBlank()) {
                result.errorMessage.lines().forEach { line ->
                    if (line.isNotBlank()) {
                        addTerminalLine(com.comfyport.data.SshLineType.STDERR, line)
                    }
                }
                addTerminalLine(com.comfyport.data.SshLineType.ERROR, "Command failed (exit code non-zero)")
            } else if (result.success) {
                if (result.output.isBlank()) {
                    addTerminalLine(com.comfyport.data.SshLineType.SUCCESS, "Command dispatched successfully.")
                }
            }

            _isSshTerminalRunning.value = false
        }
    }



    fun turnOnComfyUiViaSsh(
        customHost: String? = null,
        customPort: Int? = null,
        customUser: String? = null,
        customPass: String? = null,
        customCommand: String? = null,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        val s = _settings.value
        val host = (customHost ?: s.sshHost).ifBlank { UrlValidator.extractHost(s.serverUrl) }.trim()
        val port = customPort ?: s.sshPort
        val user = (customUser ?: s.sshUsername).trim()
        val pass = customPass ?: s.sshPassword
        val rawCmd = (customCommand ?: s.sshCommand).ifBlank { "C:\\Users\\%USERNAME%\\Desktop\\ComfyUI_windows_portable\\run_nvidia_gpu.bat" }.trim()
        val cmd = SshClient.prepareLaunchCommand(rawCmd, user)

        if (host.isBlank()) {
            val msg = "Please enter an SSH Host/IP address or set up a ComfyUI server."
            _sshLaunchState.value = SshLaunchState.Error(msg)
            onComplete?.invoke(false, msg)
            return
        }

        if (user.isBlank()) {
            val msg = "Please enter your SSH Username."
            _sshLaunchState.value = SshLaunchState.Error(msg)
            onComplete?.invoke(false, msg)
            return
        }

        addTerminalLine(com.comfyport.data.SshLineType.COMMAND, "$user@$host:~\$ [Turn on ComfyUI]")
        addTerminalLine(com.comfyport.data.SshLineType.SYSTEM, "Dispatched start command via OpenSSH...")

        viewModelScope.launch {
            _sshLaunchState.value = SshLaunchState.Connecting("Connecting to $host via OpenSSH...")
            val result = SshClient.executeCommand(
                host = host,
                port = port,
                user = user,
                password = pass,
                command = cmd,
                waitOutputMs = 7000
            )

            if (result.output.isNotBlank()) {
                result.output.lines().forEach { line ->
                    if (line.isNotBlank()) {
                        addTerminalLine(com.comfyport.data.SshLineType.STDOUT, line)
                    }
                }
            }

            if (!result.success) {
                val errorMsg = result.errorMessage ?: "SSH failed to launch ComfyUI"
                addTerminalLine(com.comfyport.data.SshLineType.ERROR, errorMsg)
                _sshLaunchState.value = SshLaunchState.Error(errorMsg)
                onComplete?.invoke(false, errorMsg)
                return@launch
            }

            addTerminalLine(com.comfyport.data.SshLineType.SUCCESS, "Process created! Waiting for ComfyUI server to come online...")

            // Immediately start streaming live ComfyUI logs to the terminal viewer
            startComfyUiLogStream(host, port, user, pass, rawCmd)

            // Command dispatched successfully! Check ComfyUI server response
            val testUrl = s.serverUrl.ifBlank { "http://$host:8188" }
            var serverOnline = false
            for (attempt in 1..12) {
                _sshLaunchState.value = SshLaunchState.WaitingForServer("ComfyUI launched via SSH! Checking server ($attempt/12)...")
                delay(2500)
                val test = ComfyClient.testConnection(testUrl)
                if (test.success) {
                    serverOnline = true
                    break
                }
            }

            if (serverOnline) {
                val successMsg = "ComfyUI is now online and ready!"
                addTerminalLine(com.comfyport.data.SshLineType.SUCCESS, "ComfyUI server at $testUrl is online and responsive!")
                _sshLaunchState.value = SshLaunchState.Success(successMsg)
                refreshSavedWorkflows()
                onComplete?.invoke(true, successMsg)
            } else {
                val infoMsg = "Start command dispatched via SSH! ComfyUI may still be loading models on $testUrl."
                addTerminalLine(com.comfyport.data.SshLineType.SYSTEM, "Start command sent. If ComfyUI hasn't finished loading yet, check server logs.")
                _sshLaunchState.value = SshLaunchState.Success(infoMsg)
                onComplete?.invoke(true, infoMsg)
            }
        }
    }

    fun startComfyUiLogStream(
        customHost: String? = null,
        customPort: Int? = null,
        customUser: String? = null,
        customPass: String? = null,
        customCommand: String? = null
    ) {
        val s = _settings.value
        val host = (customHost ?: s.sshHost).ifBlank { UrlValidator.extractHost(s.serverUrl) }.trim()
        val port = customPort ?: s.sshPort
        val user = (customUser ?: s.sshUsername).trim()
        val pass = customPass ?: s.sshPassword
        val cmd = (customCommand ?: s.sshCommand).ifBlank { "C:\\Users\\%USERNAME%\\Desktop\\ComfyUI_windows_portable\\run_nvidia_gpu.bat" }.trim()

        if (host.isBlank() || user.isBlank()) return

        sshStreamJob?.cancel()
        sshStreamJob = viewModelScope.launch {
            _isSshStreaming.value = true
            addTerminalLine(com.comfyport.data.SshLineType.SYSTEM, "Attaching live ComfyUI log stream from $host...")

            val logCommand = SshClient.buildLogTailCommand(cmd, user)
            SshClient.streamCommandOutput(
                host = host,
                port = port,
                user = user,
                password = pass,
                command = logCommand
            ) { line, isError ->
                if (line.isNotBlank()) {
                    addTerminalLine(
                        if (isError) com.comfyport.data.SshLineType.STDERR
                        else com.comfyport.data.SshLineType.STDOUT,
                        line
                    )
                }
            }
            _isSshStreaming.value = false
        }
    }

    fun stopComfyUiViaSsh(
        customHost: String? = null,
        customPort: Int? = null,
        customUser: String? = null,
        customPass: String? = null
    ) {
        val s = _settings.value
        val host = (customHost ?: s.sshHost).ifBlank { UrlValidator.extractHost(s.serverUrl) }.trim()
        val port = customPort ?: s.sshPort
        val user = (customUser ?: s.sshUsername).trim()
        val pass = customPass ?: s.sshPassword

        sshStreamJob?.cancel()
        sshStreamJob = null
        _isSshStreaming.value = false

        if (host.isBlank() || user.isBlank()) return

        viewModelScope.launch {
            addTerminalLine(com.comfyport.data.SshLineType.COMMAND, "$user@$host:~\$ [Stop ComfyUI]")
            addTerminalLine(com.comfyport.data.SshLineType.SYSTEM, "Stopping ComfyUI processes...")

            val killCmd = "powershell -NoProfile -ExecutionPolicy Bypass -Command \"taskkill /F /IM python.exe 2>&1 | Out-Null; if (\$LASTEXITCODE -eq 0 -or \$LASTEXITCODE -eq 128) { Write-Output 'ComfyUI stopped successfully' } else { Write-Output 'Process terminated' }\""
            val result = SshClient.executeCommand(
                host = host,
                port = port,
                user = user,
                password = pass,
                command = killCmd,
                waitOutputMs = 5000
            )

            if (result.output.isNotBlank()) {
                addTerminalLine(com.comfyport.data.SshLineType.STDOUT, result.output)
            }
            addTerminalLine(com.comfyport.data.SshLineType.SUCCESS, "ComfyUI process stopped.")
            _sshLaunchState.value = SshLaunchState.Idle
        }
    }

    fun stopLogStream() {
        sshStreamJob?.cancel()
        sshStreamJob = null
        _isSshStreaming.value = false
        addTerminalLine(com.comfyport.data.SshLineType.SYSTEM, "Live log stream disconnected.")
    }

    private val _currentPrompt = MutableStateFlow(settingsManager.getLastPrompt())
    val currentPrompt: StateFlow<String> = _currentPrompt.asStateFlow()

    private val _currentNegativePrompt = MutableStateFlow(settingsManager.getLastNegativePrompt())
    val currentNegativePrompt: StateFlow<String> = _currentNegativePrompt.asStateFlow()

    fun updateNegativePrompt(negativePrompt: String) {
        _currentNegativePrompt.value = negativePrompt
        settingsManager.saveLastNegativePrompt(negativePrompt)
    }

    private val _inputImageUri = MutableStateFlow<Uri?>(null)
    val inputImageUri: StateFlow<Uri?> = _inputImageUri.asStateFlow()

    private val _inputMaskBitmap = MutableStateFlow<android.graphics.Bitmap?>(null)
    val inputMaskBitmap: StateFlow<android.graphics.Bitmap?> = _inputMaskBitmap.asStateFlow()

    fun setInputImage(uri: Uri?) {
        if (_inputImageUri.value != uri) {
            _inputMaskBitmap.value = null
        }
        _inputImageUri.value = uri
    }

    fun setInputMask(bitmap: android.graphics.Bitmap?) {
        _inputMaskBitmap.value = bitmap
    }

    fun clearInputImageAndMask() {
        _inputImageUri.value = null
        _inputMaskBitmap.value = null
    }

    private val _generateCooldownSeconds = MutableStateFlow(0)
    val generateCooldownSeconds: StateFlow<Int> = _generateCooldownSeconds.asStateFlow()

    private val _galleryItems = MutableStateFlow(settingsManager.getGalleryItems())
    val galleryItems: StateFlow<List<GalleryItem>> = _galleryItems.asStateFlow()

    private val _savedWorkflows = MutableStateFlow<List<String>>(emptyList())
    val savedWorkflows: StateFlow<List<String>> = _savedWorkflows.asStateFlow()

    private val _progressInfo = MutableStateFlow(ProgressInfo())
    val progressInfo: StateFlow<ProgressInfo> = _progressInfo.asStateFlow()

    fun refreshSavedWorkflows() {
        ComfyClient.fetchSavedWorkflows(_settings.value.serverUrl) { list ->
            _savedWorkflows.value = list
        }
    }

    init {
        refreshSavedWorkflows()
        viewModelScope.launch {
            ComfyClient.progressFlow.collect { progress ->
                val activeId = _activeJobId.value
                val activeJob = _queueList.value.firstOrNull { it.id == activeId }

                if (activeId != null) {
                    updateJobProgress(activeId, progress)
                    _progressInfo.value = progress
                } else {
                    _progressInfo.value = progress
                }

                if (progress.state == GenerationState.Completed || 
                    progress.state == GenerationState.Failed || 
                    progress.state == GenerationState.Cancelled) {
                    
                    var shouldRemoveJob = true
                    if (progress.state == GenerationState.Failed) {
                        // Job failed
                    }
                    if (progress.state == GenerationState.Completed && (progress.finalImages.isNotEmpty() || progress.finalImage != null)) {
                        val currentSettings = activeJob?.settings ?: _settings.value
                        val seed = when (currentSettings.seedMode) {
                            SeedMode.Fixed -> currentSettings.fixedSeedValue
                            SeedMode.Custom -> currentSettings.customSeedValue
                            else -> currentSettings.lastUsedSeedValue
                        }
                        
                        val activeWf = currentSettings.savedWorkflows.firstOrNull { it.id == currentSettings.selectedWorkflowId }
                        val items = settingsManager.getGalleryItems().toMutableList()
                        val imagesToSave = if (progress.finalImages.isNotEmpty()) progress.finalImages else listOfNotNull(progress.finalImage)
                        var savedAny = false
                        for (imgUrl in imagesToSave) {
                            if (items.none { it.imageUrl == imgUrl }) {
                                items.add(
                                    0,
                                    com.comfyport.data.GalleryItem(
                                        imageUrl = imgUrl,
                                        prompt = activeJob?.prompt ?: _currentPrompt.value,
                                        seed = seed,
                                        negativePrompt = _currentNegativePrompt.value.takeIf { it.isNotBlank() },
                                        workflowId = currentSettings.selectedWorkflowId.takeIf { it.isNotBlank() },
                                        workflowName = activeWf?.label ?: activeWf?.filename
                                    )
                                )
                                savedAny = true
                            }
                        }
                        if (savedAny) {
                            settingsManager.saveGalleryItems(items)
                            _galleryItems.value = items
                        }
                    }

                    if (shouldRemoveJob) {
                        _queueList.value = _queueList.value.filter { it.id != activeId }
                    }
                    _activeJobId.value = null
                    processQueueNext()
                }
            }
        }
    }

    fun reuseGalleryItemParams(item: GalleryItem) {
        _currentPrompt.value = item.prompt
        settingsManager.saveLastPrompt(item.prompt)
        if (!item.negativePrompt.isNullOrBlank()) {
            _currentNegativePrompt.value = item.negativePrompt
            settingsManager.saveLastNegativePrompt(item.negativePrompt)
        }
        val updatedSettings = _settings.value.copy(
            seedMode = SeedMode.Custom,
            customSeedValue = item.seed
        )
        updateSettings(updatedSettings)
        if (!item.workflowId.isNullOrBlank()) {
            selectWorkflowById(item.workflowId)
        }
    }

    fun setInputImageUri(uriString: String) {
        setInputImage(Uri.parse(uriString))
    }

    fun exportBackupData(context: Context): String {
        return com.comfyport.data.BackupRestoreManager.exportBackupData(context, _settings.value)
    }

    fun restoreBackupData(context: Context, backupJson: String): Result<String> {
        val result = com.comfyport.data.BackupRestoreManager.restoreBackupData(context, _settings.value, backupJson)
        return result.map { (updated, restoredWorkflows) ->
            updateSettings(updated)
            refreshSavedWorkflows()
            "Restored ${restoredWorkflows.size} workflow(s) and settings successfully!"
        }
    }



    fun deleteGalleryItem(id: String) {
        val items = settingsManager.getGalleryItems().toMutableList()
        if (items.removeAll { it.id == id }) {
            settingsManager.saveGalleryItems(items)
            _galleryItems.value = items
        }
    }

    fun updatePrompt(prompt: String) {
        _currentPrompt.value = prompt
        settingsManager.saveLastPrompt(prompt)
    }

    fun updateSettings(newSettings: AppSettings) {
        val normalizedUrl = if (newSettings.serverUrl.isNotBlank()) {
            UrlValidator.normalizeUrl(newSettings.serverUrl)
        } else {
            ""
        }
        val settingsToSave = if (normalizedUrl != newSettings.serverUrl) {
            newSettings.copy(serverUrl = normalizedUrl)
        } else {
            newSettings
        }
        val serverUrlChanged = _settings.value.serverUrl != settingsToSave.serverUrl
        _settings.value = settingsToSave
        settingsManager.saveSettings(settingsToSave)
        if (serverUrlChanged) {
            refreshSavedWorkflows()
        }
    }

    fun generateImage(
        prompt: String,
        negativePrompt: String = _currentNegativePrompt.value
    ) {
        if (_settings.value.serverUrl.isBlank()) {
            Toast.makeText(getApplication(), "Please configure a ComfyUI server IP in Settings.", Toast.LENGTH_LONG).show()
            return
        }
        if (_settings.value.workflowToUse.isBlank() && _settings.value.savedWorkflows.isEmpty()) {
            Toast.makeText(getApplication(), "Please import a ComfyUI workflow in Workflow Manager.", Toast.LENGTH_LONG).show()
            return
        }
        _generateCooldownSeconds.value = 0

        _currentPrompt.value = prompt
        _currentNegativePrompt.value = negativePrompt
        settingsManager.saveLastPrompt(prompt)
        settingsManager.saveLastNegativePrompt(negativePrompt)

        queueGeneration(prompt, negativePrompt)
    }

    private fun queueGeneration(prompt: String, negativePrompt: String) {
        val maskUriStr = _inputMaskBitmap.value?.let { bmp ->
            try {
                val file = java.io.File(getApplication<Application>().cacheDir, "job_mask_${System.currentTimeMillis()}.png")
                file.outputStream().use { out ->
                    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                Uri.fromFile(file).toString()
            } catch (e: Exception) {
                AppLogger.e("MainViewModel", "Failed to cache mask for queued job: ${e.message}")
                null
            }
        }

        val job = com.comfyport.data.QueueJob(
            prompt = prompt,
            negativePrompt = negativePrompt,
            inputImageUri = _inputImageUri.value?.toString(),
            inputMaskUri = maskUriStr,
            settings = _settings.value
        )
        _queueList.value = _queueList.value + job
        AppLogger.i("MainViewModel", "Queued job: ${job.id}. Queue size: ${_queueList.value.size}")
        processQueueNext()
    }

    private fun processQueueNext() {
        val activeId = _activeJobId.value
        if (activeId != null) {
            AppLogger.d("MainViewModel", "Queue processing: job $activeId is already running")
            return
        }
        val nextJob = _queueList.value.firstOrNull { it.progress.state == GenerationState.Idle }
        if (nextJob == null) {
            AppLogger.d("MainViewModel", "Queue is empty or all jobs are processed")
            return
        }
        _activeJobId.value = nextJob.id
        AppLogger.i("MainViewModel", "Starting queued job: ${nextJob.id}")
        
        viewModelScope.launch {
            runJob(nextJob)
        }
    }

    private suspend fun runJob(job: com.comfyport.data.QueueJob) {
        val context = getApplication<Application>()
        val currentSettings = job.settings
        
        val progress1 = ProgressInfo(
            state = GenerationState.ConnectingComfy,
            statusText = "Connecting to ComfyUI..."
        )
        updateJobProgress(job.id, progress1)
        _progressInfo.value = progress1

        val imageBytes = job.inputImageUri?.let { uriStr ->
            try {
                context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { it.readBytes() }
            } catch (e: Exception) {
                AppLogger.e("MainViewModel", "Failed to read input image bytes: ${e.localizedMessage}")
                null
            }
        }

        val maskBytes = job.inputMaskUri?.let { uriStr ->
            try {
                context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { it.readBytes() }
            } catch (e: Exception) {
                AppLogger.e("MainViewModel", "Failed to read queued mask bytes: ${e.localizedMessage}")
                null
            }
        } ?: _inputMaskBitmap.value?.let { bmp ->
            try {
                val stream = java.io.ByteArrayOutputStream()
                bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                stream.toByteArray()
            } catch (e: Exception) {
                AppLogger.e("MainViewModel", "Failed to compress mask bitmap: ${e.localizedMessage}")
                null
            }
        }

        ComfyClient.startGeneration(
            context = getApplication(),
            prompt = job.prompt,
            settings = currentSettings,
            negativePrompt = job.negativePrompt.ifBlank { null },
            inputImageBytes = imageBytes,
            inputMaskBytes = maskBytes
        ) { generatedSeed ->
            val updated = _settings.value.copy(lastUsedSeedValue = generatedSeed)
            updateSettings(updated)
        }
    }

    private fun updateJobProgress(jobId: String, progress: ProgressInfo) {
        _queueList.value = _queueList.value.map {
            if (it.id == jobId) {
                it.copy(progress = progress)
            } else {
                it
            }
        }
    }

    fun cancelJob(jobId: String) {
        val activeId = _activeJobId.value
        if (jobId == activeId) {
            AppLogger.i("MainViewModel", "Cancelling active job: $jobId")
            stopGeneration()
        } else {
            AppLogger.i("MainViewModel", "Cancelling pending job: $jobId")
            _queueList.value = _queueList.value.filter { it.id != jobId }
        }
    }

    fun stopAllJobs() {
        AppLogger.i("MainViewModel", "Stopping active generation and clearing all queued jobs")
        val activeId = _activeJobId.value
        if (activeId != null) {
            stopGeneration()
        }
        _queueList.value = emptyList()
        _activeJobId.value = null
    }

    fun clearPendingQueue() {
        AppLogger.i("MainViewModel", "Clearing pending queued jobs from the queue")
        val activeId = _activeJobId.value
        if (activeId != null) {
            _queueList.value = _queueList.value.filter { it.id == activeId }
        } else {
            _queueList.value = emptyList()
        }
    }

    fun stopGeneration() {
        ComfyClient.stopGeneration(_settings.value)
    }

    fun resetState() {
        ComfyClient.progressFlow.value = ProgressInfo(state = GenerationState.Idle)
    }


    fun saveImageToDownloads(imageUrl: String, format: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            com.comfyport.data.MediaExportManager.saveImageToDownloads(context, imageUrl, format)
        }
    }

    fun shareImage(imageUrl: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            com.comfyport.data.MediaExportManager.shareImage(context, imageUrl)
        }
    }

    fun onAppBackgrounded() {
    }

    fun onAppForegrounded() {
        com.comfyport.network.ComfyClient.evictConnectionPool()
    }
}
