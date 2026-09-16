package com.comfyport.data

enum class SeedMode {
    Random, Fixed, LastUsed, Custom
}


enum class GenerationState {
    Idle, 
    ConnectingComfy, 
    GeneratingBase, 
    Completed, 
    Failed, 
    Cancelled
}

data class SavedServer(
    val id: String = java.util.UUID.randomUUID().toString(),
    val label: String,
    val url: String
)

data class NodeWidgetInfo(
    val name: String,
    val type: String = "string",
    val value: String = "",
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val options: List<String> = emptyList()
)

data class CustomWorkflowInput(
    val id: String = java.util.UUID.randomUUID().toString(),
    val nodeId: String,
    val nodeTitle: String = "",
    val nodeType: String = "",
    val widgetName: String,
    val widgetType: String = "STRING", // "INT", "FLOAT", "BOOLEAN", "COMBO", "STRING"
    val label: String = widgetName,
    val value: String = "",
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val options: List<String> = emptyList()
)

data class WorkflowNodeMapping(
    val positivePromptNodeId: String? = null,
    val negativePromptNodeId: String? = null,
    val emptyLatentNodeId: String? = null,
    val seedNodeId: String? = null,
    val loadImageNodeId: String? = null,
    val loadImageMaskNodeId: String? = null,
    val outputNodeId: String? = null,
    val customInputs: List<CustomWorkflowInput> = emptyList()
) {
    val isCustomized: Boolean
        get() = positivePromptNodeId != null ||
                negativePromptNodeId != null ||
                emptyLatentNodeId != null ||
                seedNodeId != null ||
                loadImageNodeId != null ||
                loadImageMaskNodeId != null ||
                outputNodeId != null ||
                customInputs.isNotEmpty()

    val customCount: Int
        get() {
            var count = 0
            if (positivePromptNodeId != null) count++
            if (negativePromptNodeId != null) count++
            if (emptyLatentNodeId != null) count++
            if (seedNodeId != null) count++
            if (loadImageNodeId != null) count++
            if (loadImageMaskNodeId != null) count++
            if (outputNodeId != null) count++
            count += customInputs.size
            return count
        }
}

data class NodeSlot(
    val name: String,
    val type: String = "*",
    val slotIndex: Int = 0,
    val linkId: Int? = null
)

data class GraphWire(
    val id: String,
    val fromNodeId: String,
    val fromSlotIndex: Int,
    val toNodeId: String,
    val toSlotIndex: Int,
    val wireType: String = "*"
)

data class WorkflowNodeInfo(
    val id: String,
    val title: String,
    val type: String,
    val details: String = "",
    val inputs: Map<String, String> = emptyMap(),
    val incomingLinks: List<String> = emptyList(),
    val outgoingLinks: List<String> = emptyList(),
    val posX: Float = 0f,
    val posY: Float = 0f,
    val width: Float = 220f,
    val height: Float = 120f,
    val inputSlots: List<NodeSlot> = emptyList(),
    val outputSlots: List<NodeSlot> = emptyList()
)

data class SavedWorkflow(
    val id: String = java.util.UUID.randomUUID().toString(),
    val label: String,
    val filename: String,
    val dateAdded: Long = System.currentTimeMillis(),
    val nodeMapping: WorkflowNodeMapping? = null
)

data class AppSettings(
    val serverUrl: String = "",
    val savedServers: List<SavedServer> = emptyList(),
    val activeServerId: String = "",
    val savedWorkflows: List<SavedWorkflow> = emptyList(),
    val selectedWorkflowId: String = "",
    val resolutionMode: String = "SDXL", // "SDXL", "SD_15", "CUSTOM", "WORKFLOW"
    val customWidth: Int = 1024,
    val customHeight: Int = 1024,
    val batchSize: Int = 1,
    val megapixel: String = "1.0",
    val aspectRatio: String = "1:1 (Square)",
    val workflowToUse: String = "",
    val outputFormat: String = "PNG",
    // Modular main screen feature visibility toggles
    val showWorkflowSelector: Boolean = true,
    val showResolutionControls: Boolean = true,
    val showBatchSizeControls: Boolean = true,
    val showNegativePrompt: Boolean = true,
    val showSeedControls: Boolean = true,
    val showImageMaskInput: Boolean = true,
    // Seed settings
    val seedMode: SeedMode = SeedMode.Random,
    val fixedSeedValue: Long = 42L,
    val customSeedValue: Long = 42L,
    val lastUsedSeedValue: Long = 42L,
    // SSH Remote Control
    val sshHost: String = "",
    val sshPort: Int = 22,
    val sshUsername: String = "",
    val sshPassword: String = "",
    val sshCommand: String = "C:\\Users\\%USERNAME%\\Desktop\\ComfyUI_windows_portable\\run_nvidia_gpu.bat",
    // UI Theme Customization
    val highlightColor: String = "cyan"
)

sealed class SshLaunchState {
    object Idle : SshLaunchState()
    data class Connecting(val message: String = "Connecting via OpenSSH...") : SshLaunchState()
    data class WaitingForServer(val message: String = "Command sent via SSH! Waiting for ComfyUI to respond...") : SshLaunchState()
    data class Success(val message: String) : SshLaunchState()
    data class Error(val message: String) : SshLaunchState()
}

enum class SshLineType {
    COMMAND,   // user-typed command prompt line
    STDOUT,    // stdout output
    STDERR,    // stderr output (errors)
    SYSTEM,    // system notices (connected, disconnected)
    SUCCESS,   // success confirmation
    ERROR      // error/failure notice
}

data class SshTerminalLine(
    val id: Long,
    val type: SshLineType,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)


data class ResolutionPreset(
    val width: Int,
    val height: Int,
    val label: String,
    val aspectRatio: String
) {
    val displayText: String get() = "${width}x${height} ($aspectRatio - $label)"
    val shortDisplay: String get() = "${width}x${height} ($aspectRatio)"
}

object ResolutionPresets {
    val SDXL = listOf(
        ResolutionPreset(1024, 1024, "Square", "1:1"),
        ResolutionPreset(1152, 896, "Landscape", "4:3"),
        ResolutionPreset(896, 1152, "Portrait", "3:4"),
        ResolutionPreset(1216, 832, "Landscape", "3:2"),
        ResolutionPreset(832, 1216, "Portrait", "2:3"),
        ResolutionPreset(1344, 768, "Widescreen", "16:9"),
        ResolutionPreset(768, 1344, "Vertical", "9:16"),
        ResolutionPreset(1536, 640, "Ultrawide", "21:9"),
        ResolutionPreset(640, 1536, "Tall Vertical", "9:21")
    )

    val SD_15 = listOf(
        ResolutionPreset(512, 512, "Square", "1:1"),
        ResolutionPreset(576, 448, "Landscape", "4:3"),
        ResolutionPreset(448, 576, "Portrait", "3:4"),
        ResolutionPreset(608, 400, "Landscape", "3:2"),
        ResolutionPreset(400, 608, "Portrait", "2:3"),
        ResolutionPreset(688, 384, "Widescreen", "16:9"),
        ResolutionPreset(384, 688, "Vertical", "9:16"),
        ResolutionPreset(768, 320, "Ultrawide", "21:9"),
        ResolutionPreset(320, 768, "Tall Vertical", "9:21")
    )
}

data class ProgressInfo(
    val state: GenerationState = GenerationState.Idle,
    val percent: Float = 0f,
    val currentNode: String = "",
    val statusText: String = "",
    val baseImage: String? = null,      // Image view path/URL
    val finalImage: String? = null,     // Image view path/URL
    val finalImages: List<String> = emptyList() // All generated images from batch
)

data class GalleryItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val imageUrl: String,
    val prompt: String,
    val seed: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val negativePrompt: String? = null,
    val workflowId: String? = null,
    val workflowName: String? = null
)

data class AppBackupData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val settings: AppSettingsBackup,
    val workflows: List<WorkflowBackupItem> = emptyList()
)

data class AppSettingsBackup(
    val serverUrl: String = "",
    val savedServers: List<SavedServer> = emptyList(),
    val activeServerId: String = "",
    val selectedWorkflowId: String = "",
    val resolutionMode: String = "SDXL",
    val customWidth: Int = 1024,
    val customHeight: Int = 1024,
    val batchSize: Int = 1,
    val megapixel: String = "1.0",
    val aspectRatio: String = "1:1 (Square)",
    val highlightColor: String = "cyan",
    val showWorkflowSelector: Boolean = true,
    val showResolutionControls: Boolean = true,
    val showBatchSizeControls: Boolean = true,
    val showNegativePrompt: Boolean = true,
    val showSeedControls: Boolean = true,
    val showImageMaskInput: Boolean = true,
    // SSH Configuration (NOTE: sshPassword is explicitly omitted for security!)
    val sshHost: String = "",
    val sshPort: Int = 22,
    val sshUsername: String = "",
    val sshCommand: String = ""
)

data class WorkflowBackupItem(
    val id: String,
    val label: String,
    val filename: String,
    val rawJson: String,
    val dateAdded: Long = System.currentTimeMillis(),
    val nodeMapping: WorkflowNodeMapping? = null
)

enum class FormatType {
    UI_STANDARD,
    API_READY;
    
    companion object {
        fun detect(jsonString: String): FormatType {
            val element = try {
                com.google.gson.JsonParser.parseString(jsonString)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid JSON for ComfyUI workflow", e)
            }

            if (element.isJsonArray) return UI_STANDARD
            if (element.isJsonObject) {
                var obj = element.asJsonObject

                // If wrapped in {"workflow": {...}}, check inner workflow
                if (obj.has("workflow") && obj.get("workflow").isJsonObject) {
                    val wf = obj.getAsJsonObject("workflow")
                    if (wf.has("nodes") && wf.get("nodes").isJsonArray) return UI_STANDARD
                    
                    // Could be API format wrapped in {"workflow": {...}}
                    var wfHasClassType = false
                    wf.keySet().forEach { k ->
                        val n = wf.get(k)
                        if (n != null && n.isJsonObject && n.asJsonObject.has("class_type")) {
                            wfHasClassType = true
                        }
                    }
                    if (wfHasClassType) return API_READY
                }

                // If wrapped in {"prompt": {...}}, unwrap for check
                if (obj.has("prompt") && obj.get("prompt").isJsonObject) {
                    val pObj = obj.getAsJsonObject("prompt")
                    var pHasClassType = false
                    pObj.keySet().forEach { k ->
                        val n = pObj.get(k)
                        if (n != null && n.isJsonObject && n.asJsonObject.has("class_type")) {
                            pHasClassType = true
                        }
                    }
                    if (pHasClassType) return API_READY
                    obj = pObj
                }

                // If wrapped in {"output": {...}}
                if (obj.has("output") && obj.get("output").isJsonObject) {
                    val outObj = obj.getAsJsonObject("output")
                    var outHasClassType = false
                    outObj.keySet().forEach { k ->
                        val n = outObj.get(k)
                        if (n != null && n.isJsonObject && n.asJsonObject.has("class_type")) {
                            outHasClassType = true
                        }
                    }
                    if (outHasClassType) return API_READY
                }

                // UI standard has "nodes" array
                if (obj.has("nodes") && obj.get("nodes").isJsonArray) {
                    return UI_STANDARD
                }

                // API ready format: objects mapping node ID to { "class_type": ..., "inputs": ... }
                var hasNodesWithClassType = false
                obj.keySet().forEach { key ->
                    val node = obj.get(key)
                    if (node != null && node.isJsonObject) {
                        val nodeObj = node.asJsonObject
                        if (nodeObj.has("class_type")) {
                            hasNodesWithClassType = true
                        }
                    }
                }

                if (hasNodesWithClassType) {
                    return API_READY
                }

                throw IllegalArgumentException("Unrecognized ComfyUI workflow format. Neither standard UI graph nor API prompt mapping found.")
            }
            throw IllegalArgumentException("Unrecognized ComfyUI workflow format.")
        }
    }
}

sealed class ConversionResult {
    data class Success(val apiJson: String) : ConversionResult()
    sealed class Error : ConversionResult() {
        object MissingExtension : Error()
        data class Generic(val message: String) : Error()
    }
}

data class QueueJob(
    val id: String = java.util.UUID.randomUUID().toString(),
    val prompt: String,
    val negativePrompt: String = "",
    val inputImageUri: String? = null,
    val inputMaskUri: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val progress: ProgressInfo = ProgressInfo(),
    val settings: AppSettings
)

data class ConnectionTestResult(
    val success: Boolean,
    val message: String,
    val systemStats: com.google.gson.JsonObject? = null
)

data class WorkflowMetadata(
    val prompt: String? = null,
    val negativePrompt: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val batchSize: Int? = null,
    val hasImageInput: Boolean = false,
    val hasMaskInput: Boolean = false,
    val isResolutionManagedByWorkflow: Boolean = false
)

data class WorkflowPreviewData(
    val id: String = "",
    val label: String = "",
    val filename: String = "",
    val prompt: String? = null,
    val negativePrompt: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val batchSize: Int? = null,
    val modelName: String? = null,
    val samplerName: String? = null,
    val scheduler: String? = null,
    val steps: Int? = null,
    val cfg: Float? = null,
    val loras: List<String> = emptyList(),
    val nodeCount: Int = 0,
    val nodeTypes: Map<String, Int> = emptyMap(),
    val rawJson: String = "",
    val hasEmptyConditioning: Boolean = false,
    val hasImageInput: Boolean = false,
    val hasMaskInput: Boolean = false,
    val nodes: List<WorkflowNodeInfo> = emptyList(),
    val activeMapping: WorkflowNodeMapping? = null,
    val wires: List<GraphWire> = emptyList(),
    val isResolutionManagedByWorkflow: Boolean = false
)

data class MachineHistoryItem(
    val promptId: String,
    val promptIndex: Long,
    val timestamp: Long? = null,
    val thumbnailUrl: String? = null,
    val promptText: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val batchSize: Int? = null,
    val modelName: String? = null,
    val steps: Int? = null,
    val samplerName: String? = null,
    val rawJson: String,
    val isUiFormat: Boolean
)

