package com.comfyport.ui.screens

import com.comfyport.ui.dialogs.InteractiveMaskEditorDialog
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.launch

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.comfyport.data.SavedWorkflow
import com.comfyport.data.WorkflowNodeMapping
import com.comfyport.data.CustomWorkflowInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.comfyport.data.AppSettings
import com.comfyport.data.ResolutionPresets
import com.comfyport.data.SeedMode
import com.comfyport.data.SshLaunchState
import com.comfyport.theme.AccentGray
import com.comfyport.theme.AccentRed
import com.comfyport.theme.CardGray
import com.comfyport.theme.CardBorder
import com.comfyport.theme.CardBackgroundElevated
import com.comfyport.theme.DarkGray
import com.comfyport.theme.LightGray
import com.comfyport.theme.SuccessGreen
import com.comfyport.theme.LocalHighlightColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptScreen(
    prompt: String,
    onPromptChange: (String) -> Unit,
    negativePrompt: String = "",
    onNegativePromptChange: (String) -> Unit = {},
    inputImageUri: Uri? = null,
    inputMaskBitmap: Bitmap? = null,
    onSetInputImage: (Uri?) -> Unit = {},
    onSetInputMask: (Bitmap?) -> Unit = {},
    onClearInputImageAndMask: () -> Unit = {},
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit = {},
    onGenerateClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onManageWorkflowsClick: () -> Unit = {},
    onSelectWorkflowClick: (String) -> Unit = {},
    onSeedModeChange: (SeedMode, Long) -> Unit = { _, _ -> },
    onResolutionChange: (mode: String, width: Int, height: Int, aspectRatio: String) -> Unit = { _, _, _, _ -> },
    onBatchSizeChange: (Int) -> Unit = {},
    onWorkflowChange: (String) -> Unit = {},
    savedWorkflows: List<String> = emptyList(),
    onImportWorkflowClick: (android.content.Context, android.net.Uri) -> Unit = { _, _ -> },
    onMegapixelChange: (String) -> Unit = {},
    onAspectRatioChange: (String) -> Unit = {},
    cooldownSeconds: Int = 0,
    onPreviewWorkflowClick: (SavedWorkflow) -> Unit = {},
    onBrowseMachineHistoryClick: () -> Unit = {},
    onTurnOnSshClick: () -> Unit = {},
    sshLaunchState: SshLaunchState = SshLaunchState.Idle,
    onUpdateWorkflowNodeMapping: (String, WorkflowNodeMapping) -> Unit = { _, _ -> }
) {
    val activeSavedWf = settings.savedWorkflows.firstOrNull { it.id == settings.selectedWorkflowId }
        ?: settings.savedWorkflows.firstOrNull { it.filename == settings.workflowToUse }
    val activeMapping = activeSavedWf?.nodeMapping ?: WorkflowNodeMapping()

    val showRes = if (activeSavedWf != null) {
        activeMapping.emptyLatentNodeId != null
    } else {
        !settings.resolutionMode.equals("WORKFLOW", ignoreCase = true)
    }
    val showBatch = showRes
    val showImageInput = if (activeSavedWf != null) {
        activeMapping.loadImageNodeId != null || activeMapping.loadImageMaskNodeId != null || inputImageUri != null
    } else {
        inputImageUri != null || settings.workflowToUse.contains("inpaint", ignoreCase = true) || settings.workflowToUse.contains("img2img", ignoreCase = true)
    }
    val showNeg = if (activeSavedWf != null) {
        activeMapping.negativePromptNodeId != null
    } else {
        true
    }
    val showSeed = if (activeSavedWf != null) {
        activeMapping.seedNodeId != null
    } else {
        true
    }
    val hasCustomInputs = activeMapping.customInputs.isNotEmpty()

    var customWidthInput by remember { mutableStateOf(settings.customWidth.toString()) }
    var customHeightInput by remember { mutableStateOf(settings.customHeight.toString()) }
    var resDropdownExpanded by remember { mutableStateOf(false) }
    var workflowDropdownExpanded by remember { mutableStateOf(false) }
    var showBatchSizeDialog by remember { mutableStateOf(false) }
    var batchSizeDialogInput by remember { mutableStateOf(settings.batchSize.toString()) }

    var customSeedInput by remember(settings.customSeedValue) { mutableStateOf(settings.customSeedValue.toString()) }
    var fixedSeedInput by remember(settings.fixedSeedValue) { mutableStateOf(settings.fixedSeedValue.toString()) }
    var showMaskEditor by remember { mutableStateOf(false) }
    var showManageParametersDialog by remember { mutableStateOf(false) }
    var collapsedCustomNodeIds by remember { mutableStateOf(setOf<String>()) }
    var editingCustomInput by remember { mutableStateOf<CustomWorkflowInput?>(null) }
    var editingCustomInputValue by remember { mutableStateOf("") }

    val context = LocalContext.current
    val workflowLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onImportWorkflowClick(context, uri)
        }
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onSetInputImage(uri)
        }
    }
    val configuration = LocalConfiguration.current
    val isExpandedScreen = configuration.screenWidthDp >= 600

    val promptInputCard = @Composable {
        val highlight = LocalHighlightColor.current
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardGray),
            border = BorderStroke(1.dp, CardBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Create,
                            contentDescription = null,
                            tint = highlight.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "ENTER YOUR PROMPT",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (prompt.isNotEmpty()) {
                        Text(
                            text = "CLEAR",
                            modifier = Modifier
                                .clickable { onPromptChange("") }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentRed,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    placeholder = { Text("Enter prompt...", color = AccentGray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = highlight.primary,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }

    val negativePromptCard = @Composable {
        val highlight = LocalHighlightColor.current
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardGray),
            border = BorderStroke(1.dp, CardBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            tint = AccentRed.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "NEGATIVE PROMPT",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (negativePrompt.isNotEmpty()) {
                        Text(
                            text = "CLEAR",
                            modifier = Modifier
                                .clickable { onNegativePromptChange("") }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentRed,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = negativePrompt,
                    onValueChange = onNegativePromptChange,
                    placeholder = { Text("What to avoid (e.g. blurry, low quality, distorted, extra limbs)...", color = AccentGray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = highlight.primary,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }

    val seedCard = @Composable {
        val highlight = LocalHighlightColor.current
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardGray),
            border = BorderStroke(1.dp, CardBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Casino,
                            contentDescription = null,
                            tint = highlight.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "SEED OPTIONS",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val activeDisplay = when (settings.seedMode) {
                        SeedMode.Random -> "Random"
                        SeedMode.Fixed -> "${settings.fixedSeedValue}"
                        SeedMode.Custom -> "${settings.customSeedValue}"
                        SeedMode.LastUsed -> if (settings.lastUsedSeedValue > 0) "${settings.lastUsedSeedValue}" else "None"
                    }
                    Surface(
                        color = DarkGray,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Text(
                            text = activeDisplay,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Mode Selector Tabs (Random, Fixed, Custom, Last Used)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        SeedMode.Random to "Random",
                        SeedMode.Fixed to "Fixed",
                        SeedMode.Custom to "Custom",
                        SeedMode.LastUsed to "Last"
                    ).forEach { (mode, label) ->
                        val isSelected = settings.seedMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) highlight.subtleBackground else DarkGray)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) highlight.primary else CardBorder,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    val seedVal = when (mode) {
                                        SeedMode.Fixed -> settings.fixedSeedValue
                                        SeedMode.Custom -> settings.customSeedValue
                                        SeedMode.LastUsed -> settings.lastUsedSeedValue
                                        SeedMode.Random -> 0L
                                    }
                                    onSeedModeChange(mode, seedVal)
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) highlight.primary else LightGray,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                when (settings.seedMode) {
                    SeedMode.Random -> {
                        Text(
                            text = "A new random seed is generated for each run.",
                            fontSize = 12.sp,
                            color = LightGray
                        )
                    }
                    SeedMode.Fixed -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = fixedSeedInput,
                                onValueChange = { input ->
                                    if (input.all { it.isDigit() }) {
                                        fixedSeedInput = input
                                        val s = input.toLongOrNull() ?: 0L
                                        onSeedModeChange(SeedMode.Fixed, s)
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                label = { Text("Fixed Seed Number", color = AccentGray, fontSize = 12.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = AccentGray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    SeedMode.Custom -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customSeedInput,
                                onValueChange = { input ->
                                    if (input.all { it.isDigit() }) {
                                        customSeedInput = input
                                        val s = input.toLongOrNull() ?: 0L
                                        onSeedModeChange(SeedMode.Custom, s)
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                label = { Text("Custom Seed Number", color = AccentGray, fontSize = 12.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = AccentGray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkGray)
                                    .clickable {
                                        val nextRandom = kotlin.random.Random.nextLong(1L, 999999999999999L)
                                        customSeedInput = nextRandom.toString()
                                        onSeedModeChange(SeedMode.Custom, nextRandom)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Casino, contentDescription = "Randomize", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    SeedMode.LastUsed -> {
                        Text(
                            text = if (settings.lastUsedSeedValue > 0) {
                                "Re-running using previous generation seed: ${settings.lastUsedSeedValue}"
                            } else {
                                "No seed recorded yet. Run a generation first to capture its seed."
                            },
                            fontSize = 12.sp,
                            color = LightGray
                        )
                    }
                }
            }
        }
    }

    val imageMaskCard = @Composable {
        val highlight = LocalHighlightColor.current
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardGray),
            border = BorderStroke(1.dp, CardBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = highlight.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "INPUT IMAGE & INPAINT MASK",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (inputImageUri != null) {
                        Text(
                            text = "REMOVE",
                            modifier = Modifier
                                .clickable { onClearInputImageAndMask() }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentRed,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                if (inputImageUri == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkGray)
                            .border(1.dp, AccentGray.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .clickable { imagePickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                            Text("Tap to select image for Img2Img / Inpaint", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Loads into ComfyUI LoadImage / LoadImageMask nodes", color = AccentGray, fontSize = 10.sp)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Image preview thumbnail
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkGray)
                                .border(1.dp, AccentGray, RoundedCornerShape(10.dp))
                                .clickable { imagePickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = inputImageUri,
                                contentDescription = "Input Image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        // Mask preview thumbnail
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkGray)
                                .border(1.dp, AccentGray, RoundedCornerShape(10.dp))
                                .clickable { showMaskEditor = true },
                            contentAlignment = Alignment.Center
                        ) {
                            if (inputMaskBitmap != null) {
                                Image(
                                    bitmap = inputMaskBitmap.asImageBitmap(),
                                    contentDescription = "Mask Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.padding(4.dp)
                                ) {
                                    Icon(Icons.Default.Brush, contentDescription = null, tint = AccentGray, modifier = Modifier.size(18.dp))
                                    Text("No Mask", color = LightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text("Img2Img", color = AccentGray, fontSize = 9.sp)
                                }
                            }
                        }

                        // Action buttons
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showMaskEditor = true },
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Text(
                                        if (inputMaskBitmap != null) "EDIT MASK" else "PAINT MASK",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (inputMaskBitmap != null) {
                                OutlinedButton(
                                    onClick = { onSetInputMask(null) },
                                    modifier = Modifier.fillMaxWidth().height(36.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = BorderStroke(1.dp, AccentGray),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text("CLEAR MASK", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentRed, maxLines = 1, softWrap = false)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { imagePickerLauncher.launch("image/*") },
                                    modifier = Modifier.fillMaxWidth().height(36.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = BorderStroke(1.dp, AccentGray),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text("CHANGE IMAGE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val batchSizeCard = @Composable {
        val highlight = LocalHighlightColor.current
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardGray),
            border = BorderStroke(1.dp, CardBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Collections,
                            contentDescription = null,
                            tint = highlight.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "NUMBER OF IMAGES (BATCH)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${settings.batchSize} ${if (settings.batchSize == 1) "image" else "images"}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // Row 1: Quick Presets evenly spaced across full width
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1, 4, 8, 16, 32).forEach { count ->
                        val isSelected = settings.batchSize == count
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color.White else DarkGray)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color.White else AccentGray.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onBatchSizeChange(count) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$count",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else Color.White
                            )
                        }
                    }
                }

                // Row 2: Direct Stepper with plenty of space
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Custom (tap number to edit):",
                        fontSize = 12.sp,
                        color = LightGray,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // - Button
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkGray)
                                .clickable {
                                    if (settings.batchSize > 1) {
                                        onBatchSizeChange(settings.batchSize - 1)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("-", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }

                        // Editable Number Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkGray)
                                .clickable {
                                    batchSizeDialogInput = settings.batchSize.toString()
                                    showBatchSizeDialog = true
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${settings.batchSize}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.widthIn(min = 24.dp),
                                textAlign = TextAlign.Center
                            )
                        }

                        // + Button (No limitation!)
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkGray)
                                .clickable {
                                    onBatchSizeChange(settings.batchSize + 1)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                    }
                }
            }
        }
    }

    val workflowCard = @Composable {
        val highlight = LocalHighlightColor.current
        val currentWf = settings.savedWorkflows.firstOrNull { it.id == settings.selectedWorkflowId }
            ?: settings.savedWorkflows.firstOrNull()
        val currentLabel = currentWf?.label ?: "No Workflow Selected"

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardGray),
            border = BorderStroke(1.dp, CardBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountTree,
                            contentDescription = null,
                            tint = highlight.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "COMFYUI WORKFLOW",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (currentWf != null) {
                        Text(
                            text = "PREVIEW",
                            modifier = Modifier
                                .clickable { onPreviewWorkflowClick(currentWf) }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkGray)
                        .border(1.dp, AccentGray, RoundedCornerShape(8.dp))
                        .clickable { workflowDropdownExpanded = true }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentLabel,
                            color = if (currentWf == null) AccentGray else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text("▼", color = AccentGray, fontSize = 10.sp)
                    }

                    DropdownMenu(
                        expanded = workflowDropdownExpanded,
                        onDismissRequest = { workflowDropdownExpanded = false },
                        modifier = Modifier.background(CardGray)
                    ) {
                        if (settings.savedWorkflows.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No workflows saved yet", color = AccentGray) },
                                onClick = {
                                    workflowDropdownExpanded = false
                                    onManageWorkflowsClick()
                                }
                            )
                        } else {
                            settings.savedWorkflows.forEach { wf ->
                                val isSelected = wf.id == settings.selectedWorkflowId
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                wf.label,
                                                color = if (isSelected) Color.White else LightGray,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                modifier = Modifier.weight(1f, fill = false),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isSelected) {
                                                Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    },
                                    onClick = {
                                        onSelectWorkflowClick(wf.id)
                                        workflowDropdownExpanded = false
                                    }
                                )
                            }
                        }
                        HorizontalDivider(color = DarkGray)
                        DropdownMenuItem(
                            text = { Text("+ Manage / Import Workflows...", color = Color.White, fontWeight = FontWeight.Bold) },
                            onClick = {
                                workflowDropdownExpanded = false
                                onManageWorkflowsClick()
                            }
                        )
                    }
                }
            }
        }
    }

    val resolutionCard = @Composable {
        val highlight = LocalHighlightColor.current
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardGray),
            border = BorderStroke(1.dp, CardBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = null,
                            tint = highlight.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "RESOLUTION & ASPECT RATIO",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        color = DarkGray,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        val badgeText = if (settings.resolutionMode == "WORKFLOW") "Native / Image" else "${settings.customWidth} × ${settings.customHeight}"
                        Text(
                            text = badgeText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Mode Selector Tabs (Auto/Workflow, SDXL, SD 1.5, Custom)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "WORKFLOW" to "Auto",
                        "SDXL" to "SDXL",
                        "SD_15" to "SD 1.5",
                        "CUSTOM" to "Exact"
                    ).forEach { (modeKey, modeTitle) ->
                        val isSelected = settings.resolutionMode == modeKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) highlight.subtleBackground else DarkGray)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) highlight.primary else CardBorder,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    when (modeKey) {
                                        "WORKFLOW" -> {
                                            onResolutionChange("WORKFLOW", settings.customWidth, settings.customHeight, "Auto")
                                        }
                                        "SDXL" -> {
                                            val preset = ResolutionPresets.SDXL.first()
                                            onResolutionChange("SDXL", preset.width, preset.height, preset.shortDisplay)
                                        }
                                        "SD_15" -> {
                                            val preset = ResolutionPresets.SD_15.first()
                                            onResolutionChange("SD_15", preset.width, preset.height, preset.shortDisplay)
                                        }
                                        else -> {
                                            onResolutionChange("CUSTOM", settings.customWidth, settings.customHeight, "Custom")
                                        }
                                    }
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = modeTitle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) highlight.primary else LightGray,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                when (settings.resolutionMode) {
                    "WORKFLOW" -> {
                        Surface(
                            color = DarkGray,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AccentGray.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(SuccessGreen.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("✓", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        "Workflow / Input Image Governed",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Text(
                                        "Resolution is derived dynamically from your input image or workflow nodes (e.g. VAE Encode or wired Empty Latent). Overrides are disabled.",
                                        fontSize = 11.sp,
                                        color = LightGray,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                    "SDXL", "SD_15" -> {
                        val presets = if (settings.resolutionMode == "SDXL") ResolutionPresets.SDXL else ResolutionPresets.SD_15
                        val currentPreset = presets.firstOrNull { it.width == settings.customWidth && it.height == settings.customHeight }
                            ?: presets.first()

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (settings.resolutionMode == "SDXL") "SDXL Optimal Resolutions" else "SD 1.5 Native Resolutions",
                                fontSize = 12.sp,
                                color = LightGray
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Stepper previous
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkGray)
                                        .clickable {
                                            val curIdx = presets.indexOfFirst { it.width == settings.customWidth && it.height == settings.customHeight }
                                            if (curIdx > 0) {
                                                val p = presets[curIdx - 1]
                                                onResolutionChange(settings.resolutionMode, p.width, p.height, p.shortDisplay)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("<", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }

                                // Preset Dropdown Selector
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkGray)
                                        .border(1.dp, AccentGray, RoundedCornerShape(8.dp))
                                        .clickable { resDropdownExpanded = true }
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = currentPreset.displayText,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    DropdownMenu(
                                        expanded = resDropdownExpanded,
                                        onDismissRequest = { resDropdownExpanded = false },
                                        modifier = Modifier.background(CardGray)
                                    ) {
                                        presets.forEach { p ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(p.displayText, color = Color.White, fontSize = 13.sp)
                                                },
                                                onClick = {
                                                    onResolutionChange(settings.resolutionMode, p.width, p.height, p.shortDisplay)
                                                    resDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Stepper next
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkGray)
                                        .clickable {
                                            val curIdx = presets.indexOfFirst { it.width == settings.customWidth && it.height == settings.customHeight }
                                            if (curIdx >= 0 && curIdx < presets.lastIndex) {
                                                val p = presets[curIdx + 1]
                                                onResolutionChange(settings.resolutionMode, p.width, p.height, p.shortDisplay)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(">", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                    else -> {
                    // Exact / Custom Mode
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Width input
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Width (px)", fontSize = 12.sp, color = LightGray)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = customWidthInput,
                                    onValueChange = { input ->
                                        if (input.all { it.isDigit() }) {
                                            customWidthInput = input
                                            val w = input.toIntOrNull()
                                            if (w != null && w > 0) {
                                                onResolutionChange("CUSTOM", w, settings.customHeight, "Custom")
                                            }
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.White,
                                        unfocusedBorderColor = AccentGray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true
                                )
                            }

                            // Orientation Swap Button
                            Box(
                                modifier = Modifier
                                    .padding(top = 16.dp)
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkGray)
                                    .clickable {
                                        val newW = settings.customHeight
                                        val newH = settings.customWidth
                                        customWidthInput = newW.toString()
                                        customHeightInput = newH.toString()
                                        onResolutionChange("CUSTOM", newW, newH, "Custom")
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("⇄", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }

                            // Height input
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Height (px)", fontSize = 12.sp, color = LightGray)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = customHeightInput,
                                    onValueChange = { input ->
                                        if (input.all { it.isDigit() }) {
                                            customHeightInput = input
                                            val h = input.toIntOrNull()
                                            if (h != null && h > 0) {
                                                onResolutionChange("CUSTOM", settings.customWidth, h, "Custom")
                                            }
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.White,
                                        unfocusedBorderColor = AccentGray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

    val workflowParametersCard = @Composable {
        if (hasCustomInputs) {
            val highlight = LocalHighlightColor.current
            var isExpanded by remember { mutableStateOf(true) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardGray),
                border = BorderStroke(1.dp, CardBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { isExpanded = !isExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = highlight.primary, modifier = Modifier.size(16.dp))
                            Text(
                                "WORKFLOW PARAMETERS",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = AccentGray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                color = highlight.subtleBackground,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, highlight.borderHighlight)
                            ) {
                                Text(
                                    "${activeMapping.customInputs.size}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = highlight.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = DarkGray,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.clickable { showManageParametersDialog = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Manage",
                                        tint = highlight.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        "Manage",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }
                            IconButton(
                                onClick = { isExpanded = !isExpanded },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = LightGray,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    if (isExpanded) {
                        val groupedInputs = remember(activeMapping.customInputs) {
                            activeMapping.customInputs.groupBy { it.nodeId }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            groupedInputs.forEach { (nodeId, inputsForNode) ->
                                val firstInput = inputsForNode.first()
                                val nodeTitle = firstInput.nodeTitle.ifBlank { "Node #$nodeId" }
                                val isNodeCollapsed = collapsedCustomNodeIds.contains(nodeId)

                                Surface(
                                    color = CardBackgroundElevated,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, CardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        // Header Row (ComfyUI node style)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        collapsedCustomNodeIds = if (isNodeCollapsed) collapsedCustomNodeIds - nodeId else collapsedCustomNodeIds + nodeId
                                                    }
                                                    .padding(vertical = 4.dp)
                                            ) {
                                                // Collapsible dot (exact ComfyUI style)
                                                Box(
                                                    modifier = Modifier
                                                        .size(11.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isNodeCollapsed) Color(0xFF555560) else Color(0xFF8E8E9A)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isNodeCollapsed) {
                                                        Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(Color(0xFF222225)))
                                                    }
                                                }
                                                Text(
                                                    nodeTitle,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFD4D4DC),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            // Top right X in similar style to the dot to remove the custom parameter
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clickable {
                                                        val updatedInputs = activeMapping.customInputs.filterNot { it.nodeId == nodeId }
                                                        val updatedMap = activeMapping.copy(customInputs = updatedInputs)
                                                        activeSavedWf?.let { wf -> onUpdateWorkflowNodeMapping(wf.id, updatedMap) }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(13.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF383842)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = "Remove custom parameter",
                                                        tint = Color(0xFFD4D4DC),
                                                        modifier = Modifier.size(8.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Node Body (when not collapsed)
                                        if (!isNodeCollapsed) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                                                verticalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                inputsForNode.forEach { input ->
                                                    val wType = input.widgetType.uppercase()
                                                    val step = input.step ?: if (input.widgetName.contains("size", ignoreCase = true) || input.widgetName == "left" || input.widgetName == "right" || input.widgetName == "top" || input.widgetName == "bottom") 64.0 else 1.0

                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        // The Inset Capsule (matches the screenshot)
                                                        Surface(
                                                            color = DarkGray,
                                                            shape = RoundedCornerShape(16.dp),
                                                            border = BorderStroke(1.dp, CardBorder),
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .height(34.dp)
                                                        ) {
                                                            when (wType) {
                                                                "INT", "INTEGER", "FLOAT", "NUMBER" -> {
                                                                    val isFloat = wType == "FLOAT" || wType == "NUMBER" || input.value.contains(".")
                                                                    var dragAccumulator by remember(input.id, input.value) { mutableFloatStateOf(0f) }

                                                                    fun updateValueDelta(delta: Double) {
                                                                        if (isFloat) {
                                                                            val cur = input.value.toDoubleOrNull() ?: 0.0
                                                                            var next = cur + delta
                                                                            if (input.min != null) next = Math.max(next, input.min)
                                                                            if (input.max != null) next = Math.min(next, input.max)
                                                                            val formatted = String.format(java.util.Locale.US, "%.2f", next).trimEnd('0').trimEnd('.')
                                                                            val updatedInputs = activeMapping.customInputs.map {
                                                                                if (it.id == input.id) it.copy(value = formatted) else it
                                                                            }
                                                                            val updatedMap = activeMapping.copy(customInputs = updatedInputs)
                                                                            activeSavedWf?.let { wf -> onUpdateWorkflowNodeMapping(wf.id, updatedMap) }
                                                                        } else {
                                                                            val cur = input.value.toIntOrNull() ?: 0
                                                                            var next = cur + delta.toInt()
                                                                            if (input.min != null) next = Math.max(next, input.min.toInt())
                                                                            if (input.max != null) next = Math.min(next, input.max.toInt())
                                                                            val updatedInputs = activeMapping.customInputs.map {
                                                                                if (it.id == input.id) it.copy(value = next.toString()) else it
                                                                            }
                                                                            val updatedMap = activeMapping.copy(customInputs = updatedInputs)
                                                                            activeSavedWf?.let { wf -> onUpdateWorkflowNodeMapping(wf.id, updatedMap) }
                                                                        }
                                                                    }

                                                                    Row(
                                                                        modifier = Modifier
                                                                            .fillMaxSize()
                                                                            .padding(horizontal = 6.dp)
                                                                            .pointerInput(input.id, input.value) {
                                                                                detectHorizontalDragGestures { change, dragAmount ->
                                                                                    change.consume()
                                                                                    dragAccumulator += dragAmount
                                                                                    val threshold = 12f
                                                                                    if (Math.abs(dragAccumulator) >= threshold) {
                                                                                        val steps = (dragAccumulator / threshold).toInt()
                                                                                        dragAccumulator -= steps * threshold
                                                                                        val stepSize = if (isFloat) (input.step ?: 0.05) else step
                                                                                        updateValueDelta(steps * stepSize)
                                                                                    }
                                                                                }
                                                                            },
                                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        // Left triangle ◀ (clickable)
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .size(24.dp)
                                                                                .clickable {
                                                                                    val stepSize = if (isFloat) (input.step ?: 0.05) else step
                                                                                    updateValueDelta(-stepSize)
                                                                                },
                                                                            contentAlignment = Alignment.Center
                                                                        ) {
                                                                            Text("◀", fontSize = 10.sp, color = Color(0xFFA0A0AA))
                                                                        }

                                                                        // Label
                                                                        Text(
                                                                            input.label.ifBlank { input.widgetName },
                                                                            fontSize = 12.sp,
                                                                            color = Color(0xFFA0A0AA),
                                                                            fontWeight = FontWeight.Normal,
                                                                            maxLines = 1,
                                                                            overflow = TextOverflow.Ellipsis,
                                                                            modifier = Modifier.padding(horizontal = 4.dp)
                                                                        )

                                                                        Spacer(modifier = Modifier.weight(1f))

                                                                        // Value (tap to type exact value)
                                                                        Text(
                                                                            input.value.ifBlank { "0" },
                                                                            fontSize = 13.sp,
                                                                            color = Color.White,
                                                                            fontWeight = FontWeight.Bold,
                                                                            modifier = Modifier
                                                                                .clickable {
                                                                                    editingCustomInput = input
                                                                                    editingCustomInputValue = input.value
                                                                                }
                                                                                .padding(horizontal = 6.dp)
                                                                        )

                                                                        // Right triangle ▶ (clickable)
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .size(24.dp)
                                                                                .clickable {
                                                                                    val stepSize = if (isFloat) (input.step ?: 0.05) else step
                                                                                    updateValueDelta(stepSize)
                                                                                },
                                                                            contentAlignment = Alignment.Center
                                                                        ) {
                                                                            Text("▶", fontSize = 10.sp, color = Color(0xFFA0A0AA))
                                                                        }
                                                                    }
                                                                }
                                                                "BOOLEAN", "TOGGLE" -> {
                                                                    val isChecked = input.value.toBooleanStrictOrNull()
                                                                        ?: (input.value.equals("true", ignoreCase = true) || input.value == "1")
                                                                    Row(
                                                                        modifier = Modifier
                                                                            .fillMaxSize()
                                                                            .clickable {
                                                                                val next = (!isChecked).toString()
                                                                                val updatedInputs = activeMapping.customInputs.map {
                                                                                    if (it.id == input.id) it.copy(value = next) else it
                                                                                }
                                                                                val updatedMap = activeMapping.copy(customInputs = updatedInputs)
                                                                                activeSavedWf?.let { wf -> onUpdateWorkflowNodeMapping(wf.id, updatedMap) }
                                                                            }
                                                                            .padding(horizontal = 12.dp),
                                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Text(input.label.ifBlank { input.widgetName }, fontSize = 12.sp, color = Color(0xFFA0A0AA))
                                                                        Row(
                                                                            verticalAlignment = Alignment.CenterVertically,
                                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                                        ) {
                                                                            Box(
                                                                                modifier = Modifier
                                                                                    .size(8.dp)
                                                                                    .clip(CircleShape)
                                                                                    .background(if (isChecked) highlight.primary else Color(0xFF555560))
                                                                            )
                                                                            Text(
                                                                                if (isChecked) "true" else "false",
                                                                                fontSize = 12.sp,
                                                                                fontWeight = FontWeight.Bold,
                                                                                color = if (isChecked) highlight.primary else Color(0xFF888890)
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                                "COMBO" -> {
                                                                    var expandedCombo by remember { mutableStateOf(false) }
                                                                    Box(modifier = Modifier.fillMaxSize()) {
                                                                        Row(
                                                                            modifier = Modifier
                                                                                .fillMaxSize()
                                                                                .clickable { expandedCombo = true }
                                                                                .padding(horizontal = 12.dp),
                                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                                            verticalAlignment = Alignment.CenterVertically
                                                                        ) {
                                                                            Text(input.label.ifBlank { input.widgetName }, fontSize = 12.sp, color = Color(0xFFA0A0AA))
                                                                            Row(
                                                                                verticalAlignment = Alignment.CenterVertically,
                                                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                                            ) {
                                                                                Text(input.value.ifBlank { "Select..." }, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                                                                Text("▼", fontSize = 8.sp, color = Color(0xFFA0A0AA))
                                                                            }
                                                                        }
                                                                        DropdownMenu(
                                                                            expanded = expandedCombo,
                                                                            onDismissRequest = { expandedCombo = false },
                                                                            containerColor = Color(0xFF1E1E24)
                                                                        ) {
                                                                            input.options.forEach { opt ->
                                                                                DropdownMenuItem(
                                                                                    text = { Text(opt, fontSize = 12.sp, color = if (opt == input.value) highlight.primary else Color.White) },
                                                                                    onClick = {
                                                                                        expandedCombo = false
                                                                                        val updatedInputs = activeMapping.customInputs.map {
                                                                                            if (it.id == input.id) it.copy(value = opt) else it
                                                                                        }
                                                                                        val updatedMap = activeMapping.copy(customInputs = updatedInputs)
                                                                                        activeSavedWf?.let { wf -> onUpdateWorkflowNodeMapping(wf.id, updatedMap) }
                                                                                    }
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                                else -> {
                                                                    Row(
                                                                        modifier = Modifier
                                                                            .fillMaxSize()
                                                                            .clickable {
                                                                                editingCustomInput = input
                                                                                editingCustomInputValue = input.value
                                                                            }
                                                                            .padding(horizontal = 12.dp),
                                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Text(input.label.ifBlank { input.widgetName }, fontSize = 12.sp, color = Color(0xFFA0A0AA))
                                                                        Text(
                                                                            input.value.ifBlank { "(empty)" },
                                                                            fontSize = 12.sp,
                                                                            color = Color.White,
                                                                            maxLines = 1,
                                                                            overflow = TextOverflow.Ellipsis
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        // Small matching x in similar style to dot on the right of the parameter row if multiple parameters exist
                                                        if (inputsForNode.size > 1) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(24.dp)
                                                                    .clickable {
                                                                        val updatedInputs = activeMapping.customInputs.filterNot { it.id == input.id }
                                                                        val updatedMap = activeMapping.copy(customInputs = updatedInputs)
                                                                        activeSavedWf?.let { wf -> onUpdateWorkflowNodeMapping(wf.id, updatedMap) }
                                                                    },
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(12.dp)
                                                                        .clip(CircleShape)
                                                                        .background(Color(0xFF2E2E36)),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Icon(
                                                                        Icons.Default.Close,
                                                                        contentDescription = "Remove parameter",
                                                                        tint = Color(0xFFA0A0AA),
                                                                        modifier = Modifier.size(7.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val generateButton = @Composable {
        val highlight = LocalHighlightColor.current
        Button(
            onClick = {
                if (settings.serverUrl.isBlank()) {
                    Toast.makeText(context, "Please configure a ComfyUI server IP in Settings.", Toast.LENGTH_LONG).show()
                } else if (settings.savedWorkflows.isEmpty() || settings.workflowToUse.isBlank()) {
                    Toast.makeText(context, "Please import a ComfyUI workflow first in Workflow Manager.", Toast.LENGTH_LONG).show()
                } else if (prompt.isNotBlank()) {
                    onGenerateClick(prompt)
                } else {
                    Toast.makeText(context, "Please enter a prompt.", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(highlight.primary, highlight.gradientEnd)
                    ),
                    shape = RoundedCornerShape(28.dp)
                ),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = highlight.onPrimary,
                disabledContainerColor = DarkGray,
                disabledContentColor = AccentGray
            ),
            contentPadding = PaddingValues(0.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = highlight.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                val buttonText = if (settings.batchSize > 1) {
                    "GENERATE (${settings.batchSize} IMAGES)"
                } else {
                    "GENERATE"
                }
                Text(
                    buttonText,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontSize = 16.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    color = highlight.onPrimary
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.comfyport.R.drawable.app_logo),
                            contentDescription = "comfyport logo",
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                        )
                        Text(
                            "ComfyPort",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = Color.White
                        )
                    }
                },
                actions = {
                    val isSshLaunching = sshLaunchState is SshLaunchState.Connecting || sshLaunchState is SshLaunchState.WaitingForServer
                    IconButton(onClick = onTurnOnSshClick, enabled = !isSshLaunching) {
                        if (isSshLaunching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                Icons.Default.PowerSettingsNew,
                                contentDescription = "Turn on ComfyUI via SSH",
                                tint = Color.White
                            )
                        }
                    }
                    IconButton(onClick = onGalleryClick) {
                        Icon(Icons.Default.Image, contentDescription = "Creations Gallery", tint = Color.White)
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        bottomBar = {
            Surface(
                color = Color.Black,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    generateButton()
                }
            }
        },
        containerColor = Color.Black
    ) { paddingValues ->
        if (isExpandedScreen) {
            // Unfolded split screen Row layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(2.dp))
                    promptInputCard()
                    if (showNeg) negativePromptCard()
                    if (settings.showWorkflowSelector) workflowCard()
                    if (hasCustomInputs) workflowParametersCard()
                    if (showBatch) batchSizeCard()
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(2.dp))
                    if (showImageInput) imageMaskCard()
                    if (showSeed) seedCard()
                    if (showRes) resolutionCard()
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        } else {
            // Folded standard Column layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(2.dp))
                promptInputCard()
                if (showNeg) negativePromptCard()
                if (settings.showWorkflowSelector) workflowCard()
                if (hasCustomInputs) workflowParametersCard()
                if (showImageInput) imageMaskCard()
                if (showSeed) seedCard()
                if (showBatch) batchSizeCard()
                if (showRes) resolutionCard()
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
    if (showBatchSizeDialog) {
        AlertDialog(
            onDismissRequest = { showBatchSizeDialog = false },
            containerColor = CardGray,
            title = { Text("Set Number of Images", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter exact number of images to generate (latent batch):", fontSize = 13.sp, color = LightGray)
                    OutlinedTextField(
                        value = batchSizeDialogInput,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() }) {
                                batchSizeDialogInput = input
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                        val num = batchSizeDialogInput.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        onBatchSizeChange(num)
                        showBatchSizeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Text("Set", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchSizeDialog = false }) {
                    Text("Cancel", color = AccentGray)
                }
            }
        )
    }
    if (showManageParametersDialog) {
        val highlight = LocalHighlightColor.current
        AlertDialog(
            onDismissRequest = { showManageParametersDialog = false },
            containerColor = CardGray,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = highlight.primary, modifier = Modifier.size(20.dp))
                        Text(
                            "Workflow Parameters",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 17.sp
                        )
                    }
                    if (activeMapping.customInputs.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                val updatedMap = activeMapping.copy(customInputs = emptyList())
                                activeSavedWf?.let { wf -> onUpdateWorkflowNodeMapping(wf.id, updatedMap) }
                                showManageParametersDialog = false
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Remove All", color = Color(0xFFFF5252), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            text = {
                if (activeMapping.customInputs.isEmpty()) {
                    Text(
                        "No custom parameters are currently exposed on the main page.",
                        fontSize = 13.sp,
                        color = LightGray,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Select or remove parameters exposed on the main page:",
                            fontSize = 12.sp,
                            color = LightGray
                        )
                        activeMapping.customInputs.forEach { inputItem ->
                            Surface(
                                color = CardBackgroundElevated,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            inputItem.label.ifBlank { inputItem.widgetName },
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                color = Color.Black,
                                                shape = RoundedCornerShape(3.dp)
                                            ) {
                                                Text(
                                                    "Node #${inputItem.nodeId}",
                                                    fontSize = 9.sp,
                                                    color = Color(0xFFFFD54F),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                            Text(
                                                inputItem.widgetType.uppercase(),
                                                fontSize = 10.sp,
                                                color = highlight.primary,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (inputItem.value.isNotBlank()) {
                                                Text(
                                                    "• val: ${inputItem.value}",
                                                    fontSize = 10.sp,
                                                    color = LightGray,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    Surface(
                                        color = Color(0xFF2A1C20),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.3f)),
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clickable {
                                                val updatedInputs = activeMapping.customInputs.filterNot { it.id == inputItem.id }
                                                val updatedMap = activeMapping.copy(customInputs = updatedInputs)
                                                activeSavedWf?.let { wf -> onUpdateWorkflowNodeMapping(wf.id, updatedMap) }
                                                if (updatedInputs.isEmpty()) {
                                                    showManageParametersDialog = false
                                                }
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Remove parameter",
                                                tint = Color(0xFFFF6E6E),
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showManageParametersDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = highlight.primary,
                        contentColor = highlight.onPrimary
                    )
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
    if (editingCustomInput != null) {
        val inputToEdit = editingCustomInput!!
        val highlight = LocalHighlightColor.current
        AlertDialog(
            onDismissRequest = { editingCustomInput = null },
            containerColor = CardGray,
            title = {
                Text(
                    "Set ${inputToEdit.label.ifBlank { inputToEdit.widgetName }}",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter value for Node #${inputToEdit.nodeId}:",
                        fontSize = 12.sp,
                        color = LightGray
                    )
                    OutlinedTextField(
                        value = editingCustomInputValue,
                        onValueChange = { editingCustomInputValue = it },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (inputToEdit.widgetType.uppercase().contains("INT")) KeyboardType.Number
                            else if (inputToEdit.widgetType.uppercase().contains("FLOAT")) KeyboardType.Decimal
                            else KeyboardType.Text
                        ),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = highlight.primary,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updatedInputs = activeMapping.customInputs.map {
                            if (it.id == inputToEdit.id) it.copy(value = editingCustomInputValue.trim()) else it
                        }
                        val updatedMap = activeMapping.copy(customInputs = updatedInputs)
                        activeSavedWf?.let { wf -> onUpdateWorkflowNodeMapping(wf.id, updatedMap) }
                        editingCustomInput = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = highlight.primary,
                        contentColor = highlight.onPrimary
                    )
                ) {
                    Text("Set", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCustomInput = null }) {
                    Text("Cancel", color = AccentGray)
                }
            }
        )
    }
    if (showMaskEditor && inputImageUri != null) {
        InteractiveMaskEditorDialog(
            imageUri = inputImageUri,
            initialMask = inputMaskBitmap,
            onDismiss = { showMaskEditor = false },
            onSaveMask = { mask ->
                onSetInputMask(mask)
                showMaskEditor = false
            }
        )
    }
}

