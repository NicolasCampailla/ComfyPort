package com.comfyport.ui.screens

import com.comfyport.ui.dialogs.LiveComfyUiTerminalViewer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.launch

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comfyport.data.AppSettings
import com.comfyport.data.SshLaunchState
import com.comfyport.data.SshTerminalLine
import com.comfyport.network.UrlValidator
import com.comfyport.network.ValidationResult
import com.comfyport.theme.AccentGray
import com.comfyport.theme.AccentRed
import com.comfyport.theme.CardGray
import com.comfyport.theme.CardBorder
import com.comfyport.theme.DarkGray
import com.comfyport.theme.LightGray
import com.comfyport.theme.AppHighlightColor
import com.comfyport.theme.LocalHighlightColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSaveClick: (AppSettings) -> Unit,
    onBackClick: () -> Unit,
    onAddSavedServer: (label: String, url: String) -> Unit,
    onDeleteSavedServer: (id: String) -> Unit,
    onUpdateSavedServer: ((id: String, label: String, url: String) -> Unit)? = null,
    onExportBackup: ((Context) -> String)? = null,
    onRestoreBackup: ((Context, String) -> Result<String>)? = null,
    onSelectServer: (id: String) -> Unit,
    onTestServerConnection: ((String, (Boolean, String) -> Unit) -> Unit)? = null,
    onViewLogsClick: () -> Unit = {},
    onManageWorkflowsClick: () -> Unit = {},
    onTurnOnComfyUiViaSsh: ((host: String, port: Int, user: String, pass: String, command: String, onComplete: (Boolean, String) -> Unit) -> Unit)? = null,
    sshLaunchState: SshLaunchState = SshLaunchState.Idle,
    onResetSshLaunchState: () -> Unit = {},
    terminalLines: List<SshTerminalLine> = emptyList(),
    isSshTerminalRunning: Boolean = false,
    isSshStreaming: Boolean = false,
    onStopComfyUiViaSsh: ((host: String, port: Int, user: String, pass: String) -> Unit)? = null,
    onStartLogStream: ((host: String, port: Int, user: String, pass: String, cmd: String) -> Unit)? = null,
    onStopLogStream: (() -> Unit)? = null,
    onExecuteSshTerminalCommand: ((String) -> Unit)? = null,
    onClearSshTerminal: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var showAddServerDialog by remember { mutableStateOf(false) }
    var newServerLabel by remember { mutableStateOf("") }
    var newServerUrl by remember { mutableStateOf("") }
    var urlError by remember { mutableStateOf<String?>(null) }
    var testingServerId by remember { mutableStateOf<String?>(null) }
    var serverTestResults by remember { mutableStateOf<Map<String, Pair<Boolean, String>>>(emptyMap()) }
    var isTestingDialogServer by remember { mutableStateOf(false) }
    var dialogTestResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    var showEditServerDialog by remember { mutableStateOf(false) }
    var editingServerId by remember { mutableStateOf("") }
    var editServerLabel by remember { mutableStateOf("") }
    var editServerUrl by remember { mutableStateOf("") }
    var editUrlError by remember { mutableStateOf<String?>(null) }
    var isTestingEditDialogServer by remember { mutableStateOf(false) }
    var editDialogTestResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    var showExportBackupDialog by remember { mutableStateOf(false) }
    var exportBackupJson by remember { mutableStateOf("") }
    var showRestoreBackupDialog by remember { mutableStateOf(false) }
    var restoreBackupJsonInput by remember { mutableStateOf("") }
    var restoreBackupError by remember { mutableStateOf<String?>(null) }

    val downloadBackupLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(exportBackupJson.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Backup JSON file downloaded successfully!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val uploadBackupLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    val json = reader.readText()
                    restoreBackupJsonInput = json
                    restoreBackupError = null
                    Toast.makeText(context, "Backup file uploaded! Ready to restore.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                restoreBackupError = "Failed to read file: ${e.message}"
            }
        }
    }

    var sshHostInput by remember(settings.sshHost) { mutableStateOf(settings.sshHost) }
    var sshPortInput by remember(settings.sshPort) { mutableStateOf(settings.sshPort.toString()) }
    var sshUserInput by remember(settings.sshUsername) { mutableStateOf(settings.sshUsername) }
    var sshPasswordInput by remember(settings.sshPassword) { mutableStateOf(settings.sshPassword) }
    var sshCommandInput by remember(settings.sshCommand) { mutableStateOf(settings.sshCommand) }
    var sshPasswordVisible by remember { mutableStateOf(false) }

    var showCustomColorDialog by remember { mutableStateOf(false) }
    var customHexInput by remember { mutableStateOf("") }
    var customHexError by remember { mutableStateOf<String?>(null) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    val currentHighlight = LocalHighlightColor.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, color = Color.White) },
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
            val currentHighlight = LocalHighlightColor.current

            // Saved Servers Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardGray),
                border = BorderStroke(1.dp, CardBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "SAVED SERVERS (IP ADDRESSES)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentGray
                    )
                    Text(
                        "Tap a server to set it as active, or add and label new ComfyUI host addresses.",
                        fontSize = 12.sp,
                        color = LightGray
                    )

                    HorizontalDivider(color = DarkGray, thickness = 1.dp)

                    if (settings.savedServers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No server IP addresses saved yet.\nTap below to add your ComfyUI server IP.",
                                color = LightGray,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // List of saved servers
                        settings.savedServers.forEach { server ->
                            val isActive = server.id == settings.activeServerId || server.url == settings.serverUrl
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelectServer(server.id) },
                                color = if (isActive) currentHighlight.subtleBackground else CardGray,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    width = if (isActive) 1.5.dp else 1.dp,
                                    color = if (isActive) currentHighlight.primary else CardBorder
                                )
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            RadioButton(
                                                selected = isActive,
                                                onClick = { onSelectServer(server.id) },
                                                colors = RadioButtonDefaults.colors(
                                                    selectedColor = Color.White,
                                                    unselectedColor = AccentGray
                                                )
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = server.label,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    fontSize = 15.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = server.url,
                                                    fontSize = 12.sp,
                                                    color = if (isActive) Color.White.copy(alpha = 0.8f) else LightGray,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (onTestServerConnection != null) {
                                                val isTestingThis = testingServerId == server.id
                                                IconButton(
                                                    onClick = {
                                                        testingServerId = server.id
                                                        onTestServerConnection(server.url) { success, msg ->
                                                            testingServerId = null
                                                            serverTestResults = serverTestResults + (server.id to (success to msg))
                                                        }
                                                    },
                                                    enabled = !isTestingThis,
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    if (isTestingThis) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(16.dp),
                                                            strokeWidth = 2.dp,
                                                            color = Color.White
                                                        )
                                                    } else {
                                                        Icon(
                                                            Icons.Default.NetworkCheck,
                                                            contentDescription = "Test Server Connection",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            if (onUpdateSavedServer != null) {
                                                IconButton(
                                                    onClick = {
                                                        editingServerId = server.id
                                                        editServerLabel = server.label
                                                        editServerUrl = server.url
                                                        editUrlError = null
                                                        editDialogTestResult = null
                                                        showEditServerDialog = true
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Edit,
                                                        contentDescription = "Edit Server",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }

                                            IconButton(
                                                onClick = { onDeleteSavedServer(server.id) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Delete Server",
                                                    tint = AccentRed,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    serverTestResults[server.id]?.let { (success, msg) ->
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = (if (success) "✓ " else "✗ ") + msg,
                                            fontSize = 11.sp,
                                            color = if (success) Color(0xFF4CAF50) else AccentRed
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            newServerLabel = ""
                            newServerUrl = ""
                            urlError = null
                            isTestingDialogServer = false
                            dialogTestResult = null
                            showAddServerDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("+ ADD NEW IP ADDRESS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // ComfyUI Workflow Manager Entry Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onManageWorkflowsClick() },
                colors = CardDefaults.cardColors(containerColor = CardGray),
                border = BorderStroke(1.dp, CardBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AccountTree,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Workflow Manager",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    modifier = Modifier.weight(1f, fill = false),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (settings.savedWorkflows.isNotEmpty()) {
                                    Surface(
                                        color = DarkGray,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "${settings.savedWorkflows.size} saved",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = LightGray,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                            Text(
                                "Import, rename, inspect nodes, and manage workflow JSON files",
                                fontSize = 12.sp,
                                color = LightGray
                            )
                        }
                    }

                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Open Workflow Manager",
                        tint = AccentGray
                    )
                }
            }

            // Remote Server (OpenSSH) Card
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkGray),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Terminal,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    "REMOTE COMFYUI (OPENSSH)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentGray
                                )
                                Text(
                                    "Start your ComfyUI server remotely via SSH",
                                    fontSize = 12.sp,
                                    color = LightGray
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = DarkGray, thickness = 1.dp)

                    // Auto-fill button if serverUrl has a host
                    val extractedActiveHost = UrlValidator.extractHost(settings.serverUrl)
                    if (extractedActiveHost.isNotBlank() && extractedActiveHost != "localhost" && extractedActiveHost != "127.0.0.1") {
                        Surface(
                            color = DarkGray,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AccentGray.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    sshHostInput = extractedActiveHost
                                    val port = sshPortInput.toIntOrNull() ?: 22
                                    onSaveClick(settings.copy(sshHost = extractedActiveHost, sshPort = port, sshUsername = sshUserInput, sshPassword = sshPasswordInput, sshCommand = sshCommandInput))
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Sync,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Auto-fill host from active server ($extractedActiveHost)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Host and Port Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = sshHostInput,
                            onValueChange = {
                                sshHostInput = it
                                val port = sshPortInput.toIntOrNull() ?: 22
                                onSaveClick(settings.copy(sshHost = it, sshPort = port, sshUsername = sshUserInput, sshPassword = sshPasswordInput, sshCommand = sshCommandInput))
                            },
                            label = { Text("SSH Host / IP", color = AccentGray) },
                            placeholder = { Text("e.g. 192.168.1.100", color = LightGray.copy(alpha = 0.5f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = AccentGray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            modifier = Modifier.weight(0.7f)
                        )

                        OutlinedTextField(
                            value = sshPortInput,
                            onValueChange = {
                                sshPortInput = it
                                val port = it.toIntOrNull() ?: 22
                                onSaveClick(settings.copy(sshHost = sshHostInput, sshPort = port, sshUsername = sshUserInput, sshPassword = sshPasswordInput, sshCommand = sshCommandInput))
                            },
                            label = { Text("Port", color = AccentGray) },
                            placeholder = { Text("22", color = LightGray.copy(alpha = 0.5f)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = AccentGray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            modifier = Modifier.weight(0.3f)
                        )
                    }

                    // Username
                    OutlinedTextField(
                        value = sshUserInput,
                        onValueChange = {
                            sshUserInput = it
                            val port = sshPortInput.toIntOrNull() ?: 22
                            onSaveClick(settings.copy(sshHost = sshHostInput, sshPort = port, sshUsername = it, sshPassword = sshPasswordInput, sshCommand = sshCommandInput))
                        },
                        label = { Text("SSH Username", color = AccentGray) },
                        placeholder = { Text("e.g. windows_user or ubuntu", color = LightGray.copy(alpha = 0.5f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = AccentGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Password with visibility toggle
                    OutlinedTextField(
                        value = sshPasswordInput,
                        onValueChange = {
                            sshPasswordInput = it
                            val port = sshPortInput.toIntOrNull() ?: 22
                            onSaveClick(settings.copy(sshHost = sshHostInput, sshPort = port, sshUsername = sshUserInput, sshPassword = it, sshCommand = sshCommandInput))
                        },
                        label = { Text("SSH Password", color = AccentGray) },
                        placeholder = { Text("Enter SSH password", color = LightGray.copy(alpha = 0.5f)) },
                        visualTransformation = if (sshPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { sshPasswordVisible = !sshPasswordVisible }) {
                                Icon(
                                    if (sshPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (sshPasswordVisible) "Hide password" else "Show password",
                                    tint = AccentGray
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = AccentGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Start Command
                    OutlinedTextField(
                        value = sshCommandInput,
                        onValueChange = {
                            sshCommandInput = it
                            val port = sshPortInput.toIntOrNull() ?: 22
                            onSaveClick(settings.copy(sshHost = sshHostInput, sshPort = port, sshUsername = sshUserInput, sshPassword = sshPasswordInput, sshCommand = it))
                        },
                        label = { Text("Start Command / Batch File Path", color = AccentGray) },
                        placeholder = { Text("C:\\Users\\...\\run_nvidia_gpu.bat", color = AccentGray.copy(alpha = 0.5f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = AccentGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Helper / warning note about full path requirement
                    val trimmedCmd = sshCommandInput.trim()
                    val needsPathWarning = trimmedCmd.isNotBlank() &&
                            !trimmedCmd.contains("\\") &&
                            !trimmedCmd.contains("/") &&
                            !trimmedCmd.startsWith("nohup", ignoreCase = true) &&
                            !trimmedCmd.startsWith("powershell", ignoreCase = true)

                    if (needsPathWarning) {
                        Surface(
                            color = Color(0x22FFA000),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0x66FFA000)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⚠️", fontSize = 12.sp)
                                Text(
                                    "Provide the full path (e.g. C:\\Users\\$sshUserInput\\Desktop\\ComfyUI_windows_portable\\run_nvidia_gpu.bat) so ComfyUI finds Python.",
                                    fontSize = 11.sp,
                                    color = Color(0xFFFFCC80)
                                )
                            }
                        }
                    } else {
                        Text(
                            "Provide the full absolute path to run_nvidia_gpu.bat. ComfyPort automatically decouples it via WMI so it stays running after SSH disconnects.",
                            fontSize = 10.5.sp,
                            color = LightGray
                        )
                    }

                    // Command Preset Chips
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Presets:", fontSize = 11.sp, color = LightGray, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val userDir = if (sshUserInput.isNotBlank()) sshUserInput else "YOUR_USER"
                            val presets = listOf(
                                "Desktop Portable" to "C:\\Users\\$userDir\\Desktop\\ComfyUI_windows_portable\\run_nvidia_gpu.bat",
                                "C:\\ Drive" to "C:\\ComfyUI_windows_portable\\run_nvidia_gpu.bat",
                                "Linux/Mac" to "nohup python3 main.py --listen 0.0.0.0 > /dev/null 2>&1 &"
                            )
                            presets.forEach { (label, cmd) ->
                                Surface(
                                    color = if (sshCommandInput == cmd) Color.White else DarkGray,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (sshCommandInput == cmd) Color.White else AccentGray.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            sshCommandInput = cmd
                                            val port = sshPortInput.toIntOrNull() ?: 22
                                            onSaveClick(settings.copy(sshHost = sshHostInput, sshPort = port, sshUsername = sshUserInput, sshPassword = sshPasswordInput, sshCommand = cmd))
                                        }
                                ) {
                                    Text(
                                        text = label,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (sshCommandInput == cmd) Color.Black else Color.White,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    // Status feedback
                    when (sshLaunchState) {
                        is SshLaunchState.Connecting -> {
                            Surface(
                                color = DarkGray,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = sshLaunchState.message,
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                        is SshLaunchState.WaitingForServer -> {
                            Surface(
                                color = DarkGray,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = sshLaunchState.message,
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                        is SshLaunchState.Success -> {
                            Surface(
                                color = DarkGray,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF4CAF50)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "✓ ${sshLaunchState.message}",
                                    modifier = Modifier.padding(12.dp),
                                    fontSize = 12.sp,
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        is SshLaunchState.Error -> {
                            Surface(
                                color = DarkGray,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, AccentRed),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "✗ ${sshLaunchState.message}",
                                    modifier = Modifier.padding(12.dp),
                                    fontSize = 12.sp,
                                    color = AccentRed,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        SshLaunchState.Idle -> {}
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    val isStarting = isSshTerminalRunning && !isSshStreaming
                    val isOnline = sshLaunchState is SshLaunchState.Success
                    val isBusy = isStarting

                    // Single Main Action Button: Start or Stop ComfyUI
                    Button(
                        onClick = {
                            val port = sshPortInput.toIntOrNull() ?: 22
                            val updated = settings.copy(
                                sshHost = sshHostInput,
                                sshPort = port,
                                sshUsername = sshUserInput,
                                sshPassword = sshPasswordInput,
                                sshCommand = sshCommandInput
                            )
                            onSaveClick(updated)

                            if (isSshStreaming || isOnline) {
                                onStopComfyUiViaSsh?.invoke(sshHostInput, port, sshUserInput, sshPasswordInput)
                            } else {
                                onTurnOnComfyUiViaSsh?.invoke(
                                    sshHostInput,
                                    port,
                                    sshUserInput,
                                    sshPasswordInput,
                                    sshCommandInput
                                ) { _, _ -> }
                            }
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSshStreaming || isOnline) Color(0xFF3E1212) else Color.White,
                            contentColor = if (isSshStreaming || isOnline) Color(0xFFFF8A80) else Color.Black,
                            disabledContainerColor = DarkGray,
                            disabledContentColor = AccentGray
                        ),
                        border = if (isSshStreaming || isOnline) BorderStroke(1.dp, Color(0xFFE53935)) else null,
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = AccentGray
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("STARTING COMFYUI...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else if (isSshStreaming || isOnline) {
                            Icon(
                                Icons.Default.PowerSettingsNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color(0xFFFF8A80)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("STOP COMFYUI", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Icon(
                                Icons.Default.PowerSettingsNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("START COMFYUI & LIVE VIEWER", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    // Live ComfyUI Output Viewer
                    LiveComfyUiTerminalViewer(
                        terminalLines = terminalLines,
                        isStreaming = isSshStreaming,
                        isStarting = isBusy,
                        isOnline = isOnline,
                        onClear = onClearSshTerminal,
                        onReconnectStream = {
                            val port = sshPortInput.toIntOrNull() ?: 22
                            onStartLogStream?.invoke(sshHostInput, port, sshUserInput, sshPasswordInput, sshCommandInput)
                        }
                    )
                }
            }


            // Accent & Highlight Color Selector Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardGray),
                border = BorderStroke(1.dp, CardBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = currentHighlight.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "ACCENT & HIGHLIGHT COLOR",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentGray
                        )
                    }
                    Text(
                        "Personalize the UI highlight color used for buttons, active indicators, and selections.",
                        fontSize = 12.sp,
                        color = LightGray
                    )

                    HorizontalDivider(color = CardBorder, thickness = 1.dp)

                    // Highlight Color Options: 5 Curated Presets + 1 Custom Hex Option (in matching style)
                    val presets = AppHighlightColor.PRESETS
                    val isCustomSelected = presets.none { it.id.equals(settings.highlightColor, ignoreCase = true) }
                    val customColor = if (isCustomSelected) currentHighlight else AppHighlightColor.CUSTOM_DEFAULT

                    data class ColorOptionItem(
                        val id: String,
                        val label: String,
                        val primary: Color,
                        val gradientEnd: Color,
                        val subtleBackground: Color,
                        val isSelected: Boolean,
                        val onClick: () -> Unit
                    )

                    val colorOptions = presets.map { preset ->
                        val isSelected = settings.highlightColor.equals(preset.id, ignoreCase = true)
                        ColorOptionItem(
                            id = preset.id,
                            label = preset.label,
                            primary = preset.primary,
                            gradientEnd = preset.gradientEnd,
                            subtleBackground = preset.subtleBackground,
                            isSelected = isSelected,
                            onClick = { onSaveClick(settings.copy(highlightColor = preset.id)) }
                        )
                    } + ColorOptionItem(
                        id = "custom",
                        label = if (isCustomSelected && !settings.highlightColor.equals(AppHighlightColor.DEFAULT_CUSTOM_HEX, ignoreCase = true)) {
                            "Hex: ${settings.highlightColor.uppercase()}"
                        } else {
                            "Custom Hex"
                        },
                        primary = customColor.primary,
                        gradientEnd = customColor.gradientEnd,
                        subtleBackground = customColor.subtleBackground,
                        isSelected = isCustomSelected,
                        onClick = {
                            customHexInput = if (isCustomSelected) settings.highlightColor else AppHighlightColor.DEFAULT_CUSTOM_HEX
                            customHexError = null
                            showCustomColorDialog = true
                        }
                    )

                    // Display all 6 color options in balanced 2-column rows
                    colorOptions.chunked(2).forEach { rowColors ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowColors.forEach { item ->
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { item.onClick() },
                                    color = if (item.isSelected) item.subtleBackground else DarkGray,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        width = if (item.isSelected) 1.5.dp else 1.dp,
                                        color = if (item.isSelected) item.primary else CardBorder
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    brush = Brush.linearGradient(
                                                        listOf(item.primary, item.gradientEnd)
                                                    )
                                                )
                                        )
                                        Text(
                                            text = item.label,
                                            fontSize = 12.sp,
                                            fontWeight = if (item.isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (item.isSelected) Color.White else LightGray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (item.isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = item.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Diagnostics & Logs Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardGray),
                border = BorderStroke(1.dp, CardBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "DIAGNOSTICS & LOGS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentGray
                    )
                    Text(
                        "View application logs for troubleshooting connection or generation errors.",
                        fontSize = 13.sp,
                        color = LightGray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onViewLogsClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkGray,
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, AccentGray),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("VIEW SYSTEM LOGS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // Workflow & Settings Backup Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardGray),
                border = BorderStroke(1.dp, CardBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "WORKFLOW & SETTINGS BACKUP",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentGray,
                            textAlign = TextAlign.Center
                        )
                    }
                    Text(
                        "Export and restore your custom workflows, saved servers, and preferences as JSON. For your security, SSH passwords are never included in backups.",
                        fontSize = 13.sp,
                        color = LightGray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (onExportBackup != null) {
                                    exportBackupJson = onExportBackup(context)
                                    showExportBackupDialog = true
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(23.dp),
                            border = BorderStroke(1.dp, AccentGray),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("BACKUP", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White, textAlign = TextAlign.Center)
                                }
                            }
                        }

                        Button(
                            onClick = {
                                restoreBackupJsonInput = ""
                                restoreBackupError = null
                                showRestoreBackupDialog = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DarkGray,
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.dp, AccentGray),
                            shape = RoundedCornerShape(23.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("RESTORE", fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }

            // About & Support Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardGray),
                border = BorderStroke(1.dp, CardBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = currentHighlight.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "ABOUT & SUPPORT",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentGray,
                            letterSpacing = 1.sp
                        )
                    }

                    // Ko-fi Donation Row
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/comfyport"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    clipboardManager.setText(AnnotatedString("https://ko-fi.com/comfyport"))
                                    Toast.makeText(context, "Link copied to clipboard: https://ko-fi.com/comfyport", Toast.LENGTH_LONG).show()
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = DarkGray),
                        border = BorderStroke(1.dp, CardBorder.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF5E5B).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = Color(0xFFFF5E5B),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Support on Ko-fi",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "Help fund future updates, new features & testing",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LightGray,
                                    fontSize = 12.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                tint = AccentGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Open Source License Row
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLicenseDialog = true },
                        colors = CardDefaults.cardColors(containerColor = DarkGray),
                        border = BorderStroke(1.dp, CardBorder.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(currentHighlight.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = currentHighlight.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Open Source License",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "MIT License • © 2026 Nicolas Campailla",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LightGray,
                                    fontSize = 12.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = AccentGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // App Version Footer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = com.comfyport.R.drawable.app_logo),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                            )
                            Text(
                                "ComfyPort",
                                fontWeight = FontWeight.SemiBold,
                                color = LightGray,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            "Version 1.1.1 (Build 10)",
                            color = AccentGray,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }



    // Add Server Dialog
    if (showAddServerDialog) {

        AlertDialog(
            onDismissRequest = { showAddServerDialog = false },
            containerColor = CardGray,
            title = { Text("Add ComfyUI Server", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter a friendly label and IP address or URL for your ComfyUI server.", fontSize = 13.sp, color = LightGray)
                    OutlinedTextField(
                        value = newServerLabel,
                        onValueChange = { newServerLabel = it },
                        label = { Text("Server Label (e.g. Main PC)", color = AccentGray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = AccentGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newServerUrl,
                        onValueChange = {
                            newServerUrl = it
                            urlError = null
                            dialogTestResult = null
                        },
                        label = { Text("IP Address / URL", color = AccentGray) },
                        placeholder = { Text("http://192.168.1.100:8188", color = AccentGray) },
                        isError = urlError != null,
                        supportingText = urlError?.let { { Text(it, color = AccentRed) } },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = AccentGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (onTestServerConnection != null) {
                        OutlinedButton(
                            onClick = {
                                val trimmed = newServerUrl.trim()
                                if (trimmed.isBlank() || trimmed == "http://" || trimmed == "https://") {
                                    urlError = "Enter an IP address or URL first."
                                    return@OutlinedButton
                                }
                                val normalized = UrlValidator.normalizeUrl(trimmed)
                                isTestingDialogServer = true
                                dialogTestResult = null
                                onTestServerConnection(normalized) { success, msg ->
                                    isTestingDialogServer = false
                                    dialogTestResult = success to msg
                                }
                            },
                            enabled = !isTestingDialogServer,
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, AccentGray)
                        ) {
                            if (isTestingDialogServer) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Testing Connection...", fontSize = 12.sp, color = Color.White)
                            } else {
                                Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("TEST CONNECTION", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    dialogTestResult?.let { (success, msg) ->
                        Text(
                            text = (if (success) "✓ " else "✗ ") + msg,
                            fontSize = 12.sp,
                            color = if (success) Color(0xFF4CAF50) else AccentRed
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedUrl = newServerUrl.trim()
                        if (trimmedUrl.isBlank() || trimmedUrl == "http://" || trimmedUrl == "https://") {
                            urlError = "Please enter a valid IP address or URL."
                            return@Button
                        }
                        val normalizedUrl = UrlValidator.normalizeUrl(trimmedUrl)
                        val validation = UrlValidator.validateUrl(normalizedUrl)
                        if (validation is ValidationResult.Error) {
                            urlError = validation.message
                            return@Button
                        }
                        val label = if (newServerLabel.isBlank()) normalizedUrl else newServerLabel.trim()
                        onAddSavedServer(label, normalizedUrl)
                        showAddServerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Text("Save Server", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddServerDialog = false }) {
                    Text("Cancel", color = AccentGray)
                }
            }
        )
    }

    // Edit Server Dialog
    if (showEditServerDialog) {
        AlertDialog(
            onDismissRequest = { showEditServerDialog = false },
            containerColor = CardGray,
            title = { Text("Edit Saved Server", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Update the friendly label or URL of your server.", fontSize = 13.sp, color = LightGray)
                    OutlinedTextField(
                        value = editServerLabel,
                        onValueChange = { editServerLabel = it },
                        label = { Text("Server Label", color = AccentGray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = AccentGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editServerUrl,
                        onValueChange = {
                            editServerUrl = it
                            editUrlError = null
                            editDialogTestResult = null
                        },
                        label = { Text("IP Address / URL", color = AccentGray) },
                        placeholder = { Text("http://192.168.1.100:8188", color = AccentGray) },
                        isError = editUrlError != null,
                        supportingText = editUrlError?.let { { Text(it, color = AccentRed) } },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = AccentGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (onTestServerConnection != null) {
                        OutlinedButton(
                            onClick = {
                                val trimmed = editServerUrl.trim()
                                if (trimmed.isBlank() || trimmed == "http://" || trimmed == "https://") {
                                    editUrlError = "Enter an IP address or URL first."
                                    return@OutlinedButton
                                }
                                val normalized = UrlValidator.normalizeUrl(trimmed)
                                isTestingEditDialogServer = true
                                editDialogTestResult = null
                                onTestServerConnection(normalized) { success, msg ->
                                    isTestingEditDialogServer = false
                                    editDialogTestResult = success to msg
                                }
                            },
                            enabled = !isTestingEditDialogServer,
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, AccentGray)
                        ) {
                            if (isTestingEditDialogServer) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Testing Connection...", fontSize = 12.sp, color = Color.White)
                            } else {
                                Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("TEST CONNECTION", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    editDialogTestResult?.let { (success, msg) ->
                        Text(
                            text = (if (success) "✓ " else "✗ ") + msg,
                            fontSize = 12.sp,
                            color = if (success) Color(0xFF4CAF50) else AccentRed
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedUrl = editServerUrl.trim()
                        if (trimmedUrl.isBlank() || trimmedUrl == "http://" || trimmedUrl == "https://") {
                            editUrlError = "Please enter a valid IP address or URL."
                            return@Button
                        }
                        val normalizedUrl = UrlValidator.normalizeUrl(trimmedUrl)
                        val validation = UrlValidator.validateUrl(normalizedUrl)
                        if (validation is ValidationResult.Error) {
                            editUrlError = validation.message
                            return@Button
                        }
                        val label = if (editServerLabel.isBlank()) normalizedUrl else editServerLabel.trim()
                        onUpdateSavedServer?.invoke(editingServerId, label, normalizedUrl)
                        showEditServerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditServerDialog = false }) {
                    Text("Cancel", color = AccentGray)
                }
            }
        )
    }

    // Export Backup Dialog
    if (showExportBackupDialog) {
        AlertDialog(
            onDismissRequest = { showExportBackupDialog = false },
            containerColor = CardGray,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Backup Workflows & Settings", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Your workflows, saved servers, and UI settings have been exported to JSON format. You can download the JSON file or share it.",
                        fontSize = 13.sp,
                        color = LightGray
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .clickable {
                                clipboardManager.setText(AnnotatedString(exportBackupJson))
                                Toast.makeText(context, "Backup JSON copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                        color = DarkGray,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Box(modifier = Modifier.padding(10.dp).verticalScroll(rememberScrollState())) {
                            Text(
                                text = exportBackupJson,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = LightGray
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                downloadBackupLauncher.launch("comfyport_backup_${System.currentTimeMillis()}.json")
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            shape = RoundedCornerShape(22.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "DOWNLOAD",
                                        fontSize = 12.sp,
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        lineHeight = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                try {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, exportBackupJson)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share ComfyPort Backup"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open share sheet", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(22.dp),
                            border = BorderStroke(1.dp, AccentGray),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "SHARE",
                                        fontSize = 12.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        lineHeight = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportBackupDialog = false }) {
                    Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Restore Backup Dialog
    if (showRestoreBackupDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreBackupDialog = false },
            containerColor = CardGray,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restore Backup", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Upload a backup JSON file or paste backup JSON below. Existing SSH passwords will not be overwritten.",
                        fontSize = 13.sp,
                        color = LightGray
                    )

                    OutlinedTextField(
                        value = restoreBackupJsonInput,
                        onValueChange = {
                            restoreBackupJsonInput = it
                            restoreBackupError = null
                        },
                        label = { Text("Backup JSON", color = AccentGray) },
                        placeholder = { Text("{\"version\":1,...}", color = AccentGray.copy(alpha = 0.5f)) },
                        isError = restoreBackupError != null,
                        supportingText = restoreBackupError?.let { { Text(it, color = AccentRed) } },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = AccentGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 180.dp),
                        maxLines = 10,
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clip = clipboardManager.getText()
                                if (clip != null) {
                                    restoreBackupJsonInput = clip.text
                                    restoreBackupError = null
                                } else {
                                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(22.dp),
                            border = BorderStroke(1.dp, AccentGray),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "PASTE",
                                        fontSize = 12.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false,
                                        lineHeight = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                uploadBackupLauncher.launch("*/*")
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            shape = RoundedCornerShape(22.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.FileUpload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "UPLOAD",
                                        fontSize = 12.sp,
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        lineHeight = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val input = restoreBackupJsonInput.trim()
                        if (input.isBlank()) {
                            restoreBackupError = "Please upload or paste backup JSON first."
                            return@Button
                        }
                        val result = onRestoreBackup?.invoke(context, input)
                        if (result != null) {
                            if (result.isSuccess) {
                                val msg = result.getOrNull() ?: "Backup restored successfully!"
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                showRestoreBackupDialog = false
                            } else {
                                restoreBackupError = result.exceptionOrNull()?.message ?: "Failed to restore backup"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Text("Restore", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreBackupDialog = false }) {
                    Text("Cancel", color = AccentGray)
                }
            }
        )
    }

    // Custom Color Input Dialog
    if (showCustomColorDialog) {
        val previewColor = remember(customHexInput) {
            AppHighlightColor.parseHex(customHexInput)
        }
        val currentHighlight = LocalHighlightColor.current

        AlertDialog(
            onDismissRequest = { showCustomColorDialog = false },
            containerColor = CardGray,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Palette,
                        contentDescription = null,
                        tint = previewColor?.primary ?: currentHighlight.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text("Custom Highlight Color", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Enter a 6-character hex color code (e.g. ${AppHighlightColor.DEFAULT_CUSTOM_HEX}, #FF5722, #7C4DFF, #00B0FF) to customize your app accent color.",
                        fontSize = 13.sp,
                        color = LightGray
                    )
                    OutlinedTextField(
                        value = customHexInput,
                        onValueChange = {
                            customHexInput = it
                            customHexError = null
                        },
                        label = { Text("Hex Code (#RRGGBB)", color = AccentGray) },
                        placeholder = { Text(AppHighlightColor.DEFAULT_CUSTOM_HEX, color = AccentGray.copy(alpha = 0.5f)) },
                        isError = customHexError != null,
                        supportingText = customHexError?.let { { Text(it, color = AccentRed) } },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = previewColor?.primary ?: Color.White,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Live Color Swatch & Contrast Preview Box
                    if (previewColor != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            color = previewColor.primary,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "PREVIEW: ${previewColor.id}",
                                    color = previewColor.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = AppHighlightColor.parseHex(customHexInput)
                        if (parsed == null) {
                            customHexError = "Please enter a valid 6-character hex code (e.g. #FF5722)."
                        } else {
                            onSaveClick(settings.copy(highlightColor = parsed.id))
                            showCustomColorDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = previewColor?.primary ?: Color.White,
                        contentColor = previewColor?.onPrimary ?: Color.Black
                    )
                ) {
                    Text("Apply Color", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomColorDialog = false }) {
                    Text("Cancel", color = AccentGray)
                }
            }
        )
    }

    if (showLicenseDialog) {
        val mitLicenseText = """
MIT License

Copyright (c) 2026 Nicolas Campailla

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
        """.trimIndent()

        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            containerColor = CardGray,
            shape = RoundedCornerShape(16.dp),
            icon = {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = currentHighlight.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "MIT License",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "ComfyPort is open source software released under the MIT License.",
                        fontSize = 12.sp,
                        color = LightGray
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                            .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = mitLicenseText,
                            fontSize = 12.sp,
                            color = LightGray,
                            lineHeight = 17.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showLicenseDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = currentHighlight.primary,
                        contentColor = currentHighlight.onPrimary
                    )
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(mitLicenseText))
                        Toast.makeText(context, "License copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = AccentGray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Copy", color = AccentGray)
                }
            }
        )
    }
}


