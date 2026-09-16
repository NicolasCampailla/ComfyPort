package com.comfyport

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.comfyport.data.GenerationState
import com.comfyport.ui.MainViewModel
import com.comfyport.ui.screens.ProgressScreen
import com.comfyport.ui.screens.PromptScreen
import com.comfyport.ui.screens.ResultScreen
import com.comfyport.ui.screens.SettingsScreen
import com.comfyport.ui.screens.GalleryScreen
import com.comfyport.ui.screens.LogsScreen
import com.comfyport.ui.components.QueueFAB
import com.comfyport.ui.components.QueueBottomSheetContent
import com.comfyport.ui.dialogs.WorkflowCanvasDialog
import com.comfyport.ui.dialogs.RemoteServerHistoryDialog

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(viewModel: MainViewModel = viewModel()) {
    val backStack = rememberNavBackStack(Prompt as NavKey)
    val context = LocalContext.current

    val settings by viewModel.settings.collectAsState()
    val progressInfo by viewModel.progressInfo.collectAsState()
    val prompt by viewModel.currentPrompt.collectAsState()
    val negativePrompt by viewModel.currentNegativePrompt.collectAsState()
    val inputImageUri by viewModel.inputImageUri.collectAsState()
    val inputMaskBitmap by viewModel.inputMaskBitmap.collectAsState()
    val cooldownSeconds by viewModel.generateCooldownSeconds.collectAsState()

    val queueJobs by viewModel.queueList.collectAsState()
    val activeJobId by viewModel.activeJobId.collectAsState()
    var showBottomSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState()
    val savedWorkflows by viewModel.savedWorkflows.collectAsState()
    val machineHistory by viewModel.machineHistory.collectAsState()
    val isFetchingHistory by viewModel.isFetchingHistory.collectAsState()
    val sshLaunchState by viewModel.sshLaunchState.collectAsState()
    val sshTerminalLines by viewModel.sshTerminalLines.collectAsState()
    val isSshTerminalRunning by viewModel.isSshTerminalRunning.collectAsState()
    val isSshStreaming by viewModel.isSshStreaming.collectAsState()
    var promptPreviewData by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.comfyport.network.ComfyClient.WorkflowPreviewData?>(null) }
    var showPromptMachineHistory by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }


    // Monitor active generation flow to auto-navigate
    LaunchedEffect(progressInfo.state) {
        val currentScreen = backStack.lastOrNull()
        when (progressInfo.state) {
            GenerationState.Completed -> {
                val finalImage = progressInfo.finalImage
                if (finalImage != null) {
                    val seed = when (settings.seedMode) {
                        com.comfyport.data.SeedMode.Fixed -> settings.fixedSeedValue
                        com.comfyport.data.SeedMode.Custom -> settings.customSeedValue
                        else -> settings.lastUsedSeedValue
                    }
                    if (currentScreen is Progress && backStack.lastOrNull() !is Result) {
                        backStack.removeLastOrNull() // Remove Progress screen
                        val batchList = progressInfo.finalImages.ifEmpty { listOf(finalImage) }
                        backStack.add(
                            Result(
                                imageUrl = finalImage,
                                seed = seed,
                                batchImages = batchList,
                                prompt = queueJobs.firstOrNull { it.id == activeJobId }?.prompt ?: prompt
                            )
                        )
                    }
                }
            }
            GenerationState.Failed -> {
                if (currentScreen is Progress) {
                    Toast.makeText(context, progressInfo.statusText, Toast.LENGTH_LONG).show()
                    backStack.removeLastOrNull() // Go back from Progress
                    viewModel.resetState()
                }
            }
            GenerationState.Cancelled -> {
                if (currentScreen is Progress) {
                    backStack.removeLastOrNull() // Go back from Progress
                    viewModel.resetState()
                }
            }
            else -> {}
        }
    }

    androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = backStack,
            onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
            entryProvider = entryProvider {
                entry<Prompt> {
                    PromptScreen(
                        prompt = prompt,
                        onPromptChange = { viewModel.updatePrompt(it) },
                        negativePrompt = negativePrompt,
                        onNegativePromptChange = { viewModel.updateNegativePrompt(it) },
                        inputImageUri = inputImageUri,
                        inputMaskBitmap = inputMaskBitmap,
                        onSetInputImage = { uri -> viewModel.setInputImage(uri) },
                        onSetInputMask = { bmp -> viewModel.setInputMask(bmp) },
                        onClearInputImageAndMask = { viewModel.clearInputImageAndMask() },
                        settings = settings,
                        onUpdateSettings = { updated -> viewModel.updateSettings(updated) },
                        onGenerateClick = { promptText ->
                            viewModel.generateImage(promptText)
                            val countMsg = if (settings.batchSize > 1) "${settings.batchSize} images added to queue" else "Prompt added to queue"
                            Toast.makeText(context, countMsg, Toast.LENGTH_SHORT).show()
                            backStack.add(Progress)
                        },
                        onSettingsClick = { backStack.add(Settings) },
                        onGalleryClick = { backStack.add(Gallery) },
                        onManageWorkflowsClick = { backStack.add(WorkflowManager) },
                        onSelectWorkflowClick = { id -> viewModel.selectWorkflowById(id) },
                        onSeedModeChange = { mode, customVal ->
                            val updated = settings.copy(
                                seedMode = mode,
                                customSeedValue = customVal
                            )
                            viewModel.updateSettings(updated)
                        },
                        onResolutionChange = { mode, width, height, ar ->
                            val updated = settings.copy(
                                resolutionMode = mode,
                                customWidth = width,
                                customHeight = height,
                                aspectRatio = ar
                            )
                            viewModel.updateSettings(updated)
                        },
                        onBatchSizeChange = { count ->
                            val updated = settings.copy(batchSize = count)
                            viewModel.updateSettings(updated)
                        },
                        onWorkflowChange = { wf ->
                            val updated = settings.copy(workflowToUse = wf)
                            viewModel.updateSettings(updated)
                        },
                        savedWorkflows = savedWorkflows,
                        onImportWorkflowClick = { ctx, uri -> viewModel.importWorkflow(ctx, uri) },
                        onMegapixelChange = { mp ->
                            val updated = settings.copy(megapixel = mp)
                            viewModel.updateSettings(updated)
                        },
                        onAspectRatioChange = { ar ->
                            val updated = settings.copy(aspectRatio = ar)
                            viewModel.updateSettings(updated)
                        },
                        cooldownSeconds = cooldownSeconds,
                        onPreviewWorkflowClick = { wf ->
                            val preview = viewModel.getWorkflowPreview(context, wf)
                            if (preview != null) {
                                promptPreviewData = preview
                            } else {
                                Toast.makeText(context, "Unable to read workflow", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onBrowseMachineHistoryClick = {
                            viewModel.fetchMachineHistory()
                            showPromptMachineHistory = true
                        },
                        onTurnOnSshClick = {
                            val host = settings.sshHost.ifBlank { com.comfyport.network.UrlValidator.extractHost(settings.serverUrl) }
                            if (host.isBlank() || settings.sshUsername.isBlank()) {
                                Toast.makeText(context, "Please configure SSH settings first in Settings", Toast.LENGTH_LONG).show()
                                backStack.add(Settings)
                            } else {
                                Toast.makeText(context, "Sending start command to $host via OpenSSH...", Toast.LENGTH_SHORT).show()
                                viewModel.turnOnComfyUiViaSsh { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        sshLaunchState = sshLaunchState,
                        onUpdateWorkflowNodeMapping = { id, mapping ->
                            viewModel.updateWorkflowNodeMapping(id, mapping)
                        }
                    )
                }
                entry<Progress> {
                    val activeJobPrompt = queueJobs.firstOrNull { it.id == activeJobId }?.prompt
                    ProgressScreen(
                        progressInfo = progressInfo,
                        prompt = activeJobPrompt ?: prompt,
                        onStopClick = { viewModel.stopGeneration() },
                        onSaveClick = { imageUrl -> viewModel.saveImageToDownloads(imageUrl, settings.outputFormat) },
                        onShareClick = { imageUrl -> viewModel.shareImage(imageUrl) }
                    )
                }
                entry<Result> { key ->
                    ResultScreen(
                        finalImageUrl = key.imageUrl,
                        seed = key.seed,
                        prompt = key.prompt,
                        settings = settings,
                        batchImages = key.batchImages.ifEmpty { listOf(key.imageUrl) },
                        onSaveClick = { viewModel.saveImageToDownloads(key.imageUrl, settings.outputFormat) },
                        onShareClick = { viewModel.shareImage(key.imageUrl) },
                        onSaveImageClick = { url -> viewModel.saveImageToDownloads(url, settings.outputFormat) },
                        onShareImageClick = { url -> viewModel.shareImage(url) },
                        onBackClick = {
                            viewModel.resetState()
                            backStack.removeLastOrNull()
                        },
                        onReRunClick = {
                            viewModel.resetState()
                            // Clear up to Prompt
                            while (backStack.size > 1) {
                                backStack.removeLastOrNull()
                            }
                        },
                        onUsePromptClick = { promptToUse ->
                            viewModel.updatePrompt(promptToUse)
                            viewModel.resetState()
                            while (backStack.size > 1) {
                                backStack.removeLastOrNull()
                            }
                        },
                        viewModel = viewModel
                    )
                }
                entry<Settings> {
                    SettingsScreen(
                        settings = settings,
                        onSaveClick = { updatedSettings ->
                            viewModel.updateSettings(updatedSettings)
                        },
                        onBackClick = { backStack.removeLastOrNull() },
                        onAddSavedServer = { label, url ->
                            viewModel.addSavedServer(label, url)
                            Toast.makeText(context, "Server added!", Toast.LENGTH_SHORT).show()
                        },
                        onDeleteSavedServer = { id ->
                            viewModel.deleteSavedServer(id)
                            Toast.makeText(context, "Server removed", Toast.LENGTH_SHORT).show()
                        },
                        onUpdateSavedServer = { id, label, url ->
                            viewModel.updateSavedServer(id, label, url)
                            Toast.makeText(context, "Server updated!", Toast.LENGTH_SHORT).show()
                        },
                        onExportBackup = { ctx -> viewModel.exportBackupData(ctx) },
                        onRestoreBackup = { ctx, json -> viewModel.restoreBackupData(ctx, json) },
                        onSelectServer = { id ->
                            viewModel.selectServerById(id)
                        },
                        onTestServerConnection = { url, callback ->
                            viewModel.testServerConnection(url, callback)
                        },
                        onViewLogsClick = { backStack.add(Logs) },
                        onManageWorkflowsClick = { backStack.add(WorkflowManager) },
                        onTurnOnComfyUiViaSsh = { host, port, user, pass, cmd, onDone ->
                            viewModel.turnOnComfyUiViaSsh(host, port, user, pass, cmd, onDone)
                        },
                        sshLaunchState = sshLaunchState,
                        onResetSshLaunchState = { viewModel.resetSshLaunchState() },
                        terminalLines = sshTerminalLines,
                        isSshTerminalRunning = isSshTerminalRunning,
                        isSshStreaming = isSshStreaming,
                        onStopComfyUiViaSsh = { host, port, user, pass ->
                            viewModel.stopComfyUiViaSsh(host, port, user, pass)
                        },
                        onStartLogStream = { host, port, user, pass, cmd ->
                            viewModel.startComfyUiLogStream(host, port, user, pass, cmd)
                        },
                        onStopLogStream = { viewModel.stopLogStream() },
                        onExecuteSshTerminalCommand = { cmd -> viewModel.executeSshTerminalCommand(cmd) },
                        onClearSshTerminal = { viewModel.clearSshTerminal() }
                    )
                }
                entry<WorkflowManager> {
                    com.comfyport.ui.screens.WorkflowManagerScreen(
                        settings = settings,
                        onBackClick = { backStack.removeLastOrNull() },
                        onImportWorkflowClick = { ctx, uri, label ->
                            viewModel.importAndSaveWorkflow(ctx, uri, label)
                        },
                        onDeleteWorkflowClick = { ctx, id ->
                            viewModel.deleteSavedWorkflow(ctx, id)
                            Toast.makeText(context, "Workflow deleted", Toast.LENGTH_SHORT).show()
                        },
                        onUpdateWorkflowLabelClick = { id, label ->
                            viewModel.updateWorkflowLabel(id, label)
                        },
                        onSelectWorkflowClick = { id ->
                            viewModel.selectWorkflowById(id)
                            Toast.makeText(context, "Active workflow updated!", Toast.LENGTH_SHORT).show()
                        },
                        machineHistory = machineHistory,
                        isFetchingHistory = isFetchingHistory,
                        onFetchMachineHistory = { cb -> viewModel.fetchMachineHistory(cb) },
                        onImportMachineWorkflow = { ctx, item, label, act ->
                            viewModel.importMachineWorkflow(ctx, item, label, act)
                        },
                        onGetWorkflowPreview = { ctx, wf ->
                            viewModel.getWorkflowPreview(ctx, wf)
                        },
                        onApplyWorkflowSpecs = { ctx, preview ->
                            viewModel.applyWorkflowSpecs(ctx, preview)
                        },
                        onUpdateWorkflowNodeMapping = { id, mapping ->
                            viewModel.updateWorkflowNodeMapping(id, mapping)
                        }
                    )
                }
                entry<Logs> {
                    LogsScreen(
                        onBackClick = { backStack.removeLastOrNull() }
                    )
                }
                entry<Gallery> {
                    val galleryItems by viewModel.galleryItems.collectAsState()
                    GalleryScreen(
                        items = galleryItems,
                        onBackClick = { backStack.removeLastOrNull() },
                        onReRunClick = { promptText ->
                            viewModel.updatePrompt(promptText)
                            // Clear up to Prompt (Home)
                            while (backStack.size > 1) {
                                backStack.removeLastOrNull()
                            }
                        },
                        onShareClick = { url -> viewModel.shareImage(url) },
                        onDeleteClick = { id -> viewModel.deleteGalleryItem(id) },
                        onDownloadClick = { url -> viewModel.saveImageToDownloads(url, settings.outputFormat) },
                        onRefinePromptClick = { imageUrl, seed, itemPrompt ->
                            backStack.add(
                                Result(
                                    imageUrl = imageUrl,
                                    seed = seed,
                                    batchImages = galleryItems.map { it.imageUrl },
                                    prompt = itemPrompt
                                )
                            )
                        }
                    )
                }
            }
        )

        val currentScreen = backStack.lastOrNull()
        val showFab = queueJobs.isNotEmpty() && currentScreen != null && currentScreen !is Progress

        if (showFab) {
            QueueFAB(
                queueSize = queueJobs.size,
                onClick = { showBottomSheet = true },
                modifier = androidx.compose.ui.Modifier
                    .align(androidx.compose.ui.Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }

        if (showBottomSheet) {
            androidx.compose.material3.ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                containerColor = com.comfyport.theme.DarkGray,
                scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f)
            ) {
                QueueBottomSheetContent(
                    queueJobs = queueJobs,
                    activeJobId = activeJobId,
                    onCancelJob = { jobId -> viewModel.cancelJob(jobId) },
                    onClearPending = { viewModel.clearPendingQueue() },
                    onStopAll = { viewModel.stopAllJobs() },
                    onJobClick = { jobId ->
                        showBottomSheet = false
                        if (backStack.lastOrNull() !is Progress) {
                            backStack.add(Progress)
                        }
                    },
                    onDismiss = { showBottomSheet = false }
                )
            }
        }

        val pPreview = promptPreviewData
        if (pPreview != null) {
            WorkflowCanvasDialog(
                preview = pPreview,
                serverUrl = settings.serverUrl,
                isActive = pPreview.id == settings.selectedWorkflowId,
                onDismiss = { promptPreviewData = null },
                onActivate = if (pPreview.id.isNotBlank() && pPreview.id != settings.selectedWorkflowId) {
                    { viewModel.selectWorkflowById(pPreview.id) }
                } else null,
                onApplySpecs = {
                    viewModel.applyWorkflowSpecs(context, pPreview)
                },
                onSaveMapping = { id, mapping ->
                    viewModel.updateWorkflowNodeMapping(id, mapping)
                }
            )
        }

        if (showPromptMachineHistory) {
            RemoteServerHistoryDialog(
                historyItems = machineHistory,
                isLoading = isFetchingHistory,
                onRefresh = { viewModel.fetchMachineHistory() },
                onDismiss = { showPromptMachineHistory = false },
                onPreviewItem = { item ->
                    promptPreviewData = com.comfyport.network.ComfyClient.parseWorkflowPreview("Run #${item.promptIndex}", "", item.rawJson)
                },
                onImportItem = { item, activate ->
                    viewModel.importMachineWorkflow(context, item, null, activate)
                    showPromptMachineHistory = false
                }
            )
        }
    }
}

