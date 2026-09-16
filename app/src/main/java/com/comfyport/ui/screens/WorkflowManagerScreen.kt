package com.comfyport.ui.screens

import com.comfyport.ui.dialogs.MachineHistoryDialog
import com.comfyport.ui.dialogs.RemoteServerHistoryDialog
import com.comfyport.ui.dialogs.WorkflowCanvasDialog
import com.comfyport.ui.dialogs.WorkflowPreviewDialog
import kotlinx.coroutines.launch

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.comfyport.data.SavedWorkflow
import com.comfyport.data.WorkflowNodeMapping
import com.comfyport.network.ComfyClient
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comfyport.data.AppSettings
import com.comfyport.theme.AccentGray
import com.comfyport.theme.AccentRed
import com.comfyport.theme.CardGray
import com.comfyport.theme.CardBorder
import com.comfyport.theme.DarkGray
import com.comfyport.theme.LightGray
import com.comfyport.theme.LocalHighlightColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowManagerScreen(

    settings: AppSettings,
    onBackClick: () -> Unit,
    onImportWorkflowClick: (Context, Uri, String) -> Unit,
    onDeleteWorkflowClick: (Context, String) -> Unit,
    onUpdateWorkflowLabelClick: (String, String) -> Unit,
    onSelectWorkflowClick: (String) -> Unit,
    machineHistory: List<ComfyClient.MachineHistoryItem> = emptyList(),
    isFetchingHistory: Boolean = false,
    onFetchMachineHistory: ((Boolean, String?) -> Unit) -> Unit = {},
    onImportMachineWorkflow: (Context, ComfyClient.MachineHistoryItem, String?, Boolean) -> Unit = { _, _, _, _ -> },
    onGetWorkflowPreview: (Context, SavedWorkflow) -> ComfyClient.WorkflowPreviewData? = { _, _ -> null },
    onApplyWorkflowSpecs: (Context, ComfyClient.WorkflowPreviewData) -> Unit = { _, _ -> },
    onUpdateWorkflowNodeMapping: (String, WorkflowNodeMapping) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var showImportLabelDialog by remember { mutableStateOf(false) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var workflowLabelInput by remember { mutableStateOf("") }

    var showEditLabelDialog by remember { mutableStateOf(false) }
    var editingWorkflowId by remember { mutableStateOf("") }
    var editLabelInput by remember { mutableStateOf("") }

    // Preview & Machine History dialog states
    var previewDataToShow by remember { mutableStateOf<ComfyClient.WorkflowPreviewData?>(null) }
    var isPreviewActive by remember { mutableStateOf(false) }
    var previewMachineItem by remember { mutableStateOf<ComfyClient.MachineHistoryItem?>(null) }
    var showMachineHistoryDialog by remember { mutableStateOf(false) }

    var showMachineImportLabelDialog by remember { mutableStateOf(false) }
    var pendingMachineItemToImport by remember { mutableStateOf<ComfyClient.MachineHistoryItem?>(null) }
    var pendingMachineItemActivate by remember { mutableStateOf(false) }
    var machineImportLabelInput by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            workflowLabelInput = "Custom Workflow ${settings.savedWorkflows.size}"
            showImportLabelDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workflow Manager", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Import Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardGray),
                border = BorderStroke(1.dp, CardBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "WORKFLOW SOURCES",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentGray
                    )
                    Text(
                        "Browse your machine history, or import a ComfyUI workflow JSON from your phone.",
                        fontSize = 13.sp,
                        color = LightGray
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            onFetchMachineHistory { _, _ -> }
                            showMachineHistoryDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkGray,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, AccentGray.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("BROWSE MACHINE WORKFLOW HISTORY", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = { filePickerLauncher.launch("application/json") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkGray,
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, AccentGray.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("IMPORT WORKFLOW FILE (.JSON)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // Saved Workflows List Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "SAVED WORKFLOWS (${settings.savedWorkflows.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AccentGray
                )
            }

            // Workflows List
            if (settings.savedWorkflows.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardGray),
                    border = BorderStroke(1.dp, CardBorder),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No workflows saved yet.\nTap 'BROWSE MACHINE WORKFLOW HISTORY' or 'IMPORT WORKFLOW FILE' above to add your first ComfyUI workflow.",
                            color = LightGray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val highlight = LocalHighlightColor.current
                settings.savedWorkflows.forEach { wf ->
                    val isActive = wf.id == settings.selectedWorkflowId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectWorkflowClick(wf.id) },
                        colors = CardDefaults.cardColors(containerColor = CardGray),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            width = if (isActive) 1.5.dp else 1.dp,
                            color = if (isActive) highlight.primary else CardBorder
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Top Row: Workflow Title + Edit/Delete Actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = wf.label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            editingWorkflowId = wf.id
                                            editLabelInput = wf.label
                                            showEditLabelDialog = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Label", tint = Color.White, modifier = Modifier.size(16.dp))
                                    }

                                    IconButton(
                                        onClick = { onDeleteWorkflowClick(context, wf.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AccentRed, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            // Dedicated Badges & Subtitle Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isActive) {
                                    Surface(
                                        color = highlight.primary,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            "ACTIVE",
                                            color = highlight.onPrimary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                                if (wf.nodeMapping?.isCustomized == true) {
                                    Surface(
                                        color = Color(0xFF673AB7),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            "MAPPED (${wf.nodeMapping.customCount})",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                                Text(
                                    text = "Custom Imported Workflow",
                                    fontSize = 12.sp,
                                    color = LightGray
                                )
                            }

                            // Actions Row (Preview & Map, Select)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val preview = onGetWorkflowPreview(context, wf)
                                        if (preview != null) {
                                            previewDataToShow = preview
                                            isPreviewActive = isActive
                                            previewMachineItem = null
                                        } else {
                                            Toast.makeText(context, "Unable to read workflow file", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = BorderStroke(1.dp, AccentGray.copy(alpha = 0.6f)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("PREVIEW & MAP", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                }

                                if (!isActive) {
                                    Button(
                                        onClick = { onSelectWorkflowClick(wf.id) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.White,
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("ACTIVATE", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Import Label Dialog
    if (showImportLabelDialog) {
        AlertDialog(
            onDismissRequest = { showImportLabelDialog = false },
            containerColor = CardGray,
            title = { Text("Name Your Workflow", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Give this workflow a label so you can easily recognize it:", fontSize = 13.sp, color = LightGray)
                    OutlinedTextField(
                        value = workflowLabelInput,
                        onValueChange = { workflowLabelInput = it },
                        label = { Text("Workflow Name", color = AccentGray) },
                        placeholder = { Text("e.g. SDXL Turbo, Anime, Realism", color = AccentGray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = AccentGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = selectedUri
                        if (uri != null) {
                            onImportWorkflowClick(context, uri, workflowLabelInput)
                        }
                        showImportLabelDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Text("Save & Activate", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportLabelDialog = false }) {
                    Text("Cancel", color = AccentGray)
                }
            }
        )
    }

    // Edit Label Dialog
    if (showEditLabelDialog) {
        AlertDialog(
            onDismissRequest = { showEditLabelDialog = false },
            containerColor = CardGray,
            title = { Text("Edit Workflow Label", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                OutlinedTextField(
                    value = editLabelInput,
                    onValueChange = { editLabelInput = it },
                    label = { Text("Workflow Label", color = AccentGray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = AccentGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editLabelInput.isNotBlank()) {
                            onUpdateWorkflowLabelClick(editingWorkflowId, editLabelInput.trim())
                        }
                        showEditLabelDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditLabelDialog = false }) {
                    Text("Cancel", color = AccentGray)
                }
            }
        )
    }

    // Machine History Dialog
    if (showMachineHistoryDialog) {
        MachineHistoryDialog(
            historyItems = machineHistory,
            isLoading = isFetchingHistory,
            onRefresh = { onFetchMachineHistory { _, _ -> } },
            onDismiss = { showMachineHistoryDialog = false },
            onPreviewItem = { item ->
                val preview = ComfyClient.parseWorkflowPreview("Run #${item.promptIndex}", "", item.rawJson)
                previewDataToShow = preview
                isPreviewActive = false
                previewMachineItem = item
            },
            onImportItem = { item, activate ->
                pendingMachineItemToImport = item
                pendingMachineItemActivate = activate
                val defaultLabel = buildString {
                    if (!item.modelName.isNullOrBlank()) {
                        append(item.modelName.substringBeforeLast("."))
                    } else {
                        append("Machine Run #${item.promptIndex}")
                    }
                    if (item.width != null && item.height != null) {
                        append(" (${item.width}x${item.height})")
                    }
                }
                machineImportLabelInput = defaultLabel
                showMachineImportLabelDialog = true
            }
        )
    }

    // Machine Import Label Dialog
    if (showMachineImportLabelDialog && pendingMachineItemToImport != null) {
        AlertDialog(
            onDismissRequest = { showMachineImportLabelDialog = false },
            containerColor = CardGray,
            title = { Text("Import Machine Workflow", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Give this machine workflow a name:", fontSize = 13.sp, color = LightGray)
                    OutlinedTextField(
                        value = machineImportLabelInput,
                        onValueChange = { machineImportLabelInput = it },
                        label = { Text("Workflow Name", color = AccentGray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = AccentGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val item = pendingMachineItemToImport
                        if (item != null) {
                            onImportMachineWorkflow(context, item, machineImportLabelInput.trim(), pendingMachineItemActivate)
                        }
                        showMachineImportLabelDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Text(if (pendingMachineItemActivate) "Save & Activate" else "Save Workflow", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMachineImportLabelDialog = false }) {
                    Text("Cancel", color = AccentGray)
                }
            }
        )
    }

    // Workflow Preview Dialog
    val currentPreview = previewDataToShow
    if (currentPreview != null) {
        WorkflowPreviewDialog(
            preview = currentPreview,
            serverUrl = settings.serverUrl,
            isActive = isPreviewActive,
            onDismiss = {
                previewDataToShow = null
                previewMachineItem = null
            },
            onActivate = if (currentPreview.id.isNotBlank() && !isPreviewActive) {
                { onSelectWorkflowClick(currentPreview.id) }
            } else null,
            onApplySpecs = {
                onApplyWorkflowSpecs(context, currentPreview)
            },
            onSaveMapping = { id, mapping ->
                onUpdateWorkflowNodeMapping(id, mapping)
            },
            onImport = if (previewMachineItem != null) {
                {
                    val item = previewMachineItem!!
                    pendingMachineItemToImport = item
                    pendingMachineItemActivate = true
                    val defaultLabel = buildString {
                        if (!item.modelName.isNullOrBlank()) {
                            append(item.modelName.substringBeforeLast("."))
                        } else {
                            append("Machine Run #${item.promptIndex}")
                        }
                        if (item.width != null && item.height != null) {
                            append(" (${item.width}x${item.height})")
                        }
                    }
                    machineImportLabelInput = defaultLabel
                    showMachineImportLabelDialog = true
                }
            } else null
        )
    }
}

