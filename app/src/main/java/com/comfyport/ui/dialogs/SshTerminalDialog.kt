package com.comfyport.ui.dialogs

import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.launch

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comfyport.data.SshTerminalLine
import com.comfyport.data.SshLineType
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context as AndroidContext

@Composable
fun LiveComfyUiTerminalViewer(
    terminalLines: List<SshTerminalLine>,
    isStreaming: Boolean,
    isStarting: Boolean,
    isOnline: Boolean,
    onClear: () -> Unit,
    onReconnectStream: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lazyListState = rememberLazyListState()

    // Auto-scroll to bottom as new log lines stream in
    LaunchedEffect(terminalLines.size) {
        if (terminalLines.isNotEmpty()) {
            lazyListState.animateScrollToItem(terminalLines.size - 1)
        }
    }

    Surface(
        color = Color(0xFF0D1117),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF30363D)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Terminal Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161B22))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Status dot
                    Surface(
                        shape = CircleShape,
                        color = when {
                            isStreaming -> Color(0xFF4CAF50)
                            isOnline -> Color(0xFF66BB6A)
                            isStarting -> Color(0xFFFFB74D)
                            else -> Color(0xFF78909C)
                        },
                        modifier = Modifier.size(8.dp)
                    ) {}

                    Text(
                        text = "LIVE COMFYUI OUTPUT",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )

                    Surface(
                        color = when {
                            isStreaming -> Color(0x334CAF50)
                            isOnline -> Color(0x3381C784)
                            isStarting -> Color(0x33FFA000)
                            else -> Color(0x22FFFFFF)
                        },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = when {
                                isStreaming -> "STREAMING"
                                isOnline -> "ONLINE"
                                isStarting -> "STARTING"
                                else -> "IDLE"
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isStreaming -> Color(0xFF4CAF50)
                                isOnline -> Color(0xFF81C784)
                                isStarting -> Color(0xFFFFB74D)
                                else -> Color(0xFF8B949E)
                            },
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (terminalLines.isNotEmpty()) {
                        Text(
                            text = "${terminalLines.size} lines",
                            fontSize = 10.sp,
                            color = Color(0xFF8B949E),
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Reconnect Stream Icon
                    IconButton(
                        onClick = onReconnectStream,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Attach/Reconnect Stream",
                            tint = Color(0xFF8B949E),
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Copy Console Output
                    IconButton(
                        onClick = {
                            val text = terminalLines.joinToString("\n") { it.text }
                            val clipboard = context.getSystemService(AndroidContext.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("ComfyUI Output", text))
                            Toast.makeText(context, "Copied console output", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy Output",
                            tint = Color(0xFF8B949E),
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Clear Console Output
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Clear Console",
                            tint = Color(0xFF8B949E),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Terminal Console Body
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .padding(8.dp)
            ) {
                if (terminalLines.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Terminal,
                            contentDescription = null,
                            tint = Color(0xFF30363D),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap 'START COMFYUI & LIVE VIEWER' above to launch and view real-time logs here.",
                            fontSize = 11.5.sp,
                            color = Color(0xFF6E7681),
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(terminalLines, key = { line -> line.id }) { line ->
                            ComfyUiLogLineItem(line = line)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComfyUiLogLineItem(line: SshTerminalLine) {
    val text = line.text
    val textColor = when (line.type) {
        SshLineType.COMMAND -> Color(0xFF58A6FF)
        SshLineType.SYSTEM -> Color(0xFF79C0FF)
        SshLineType.SUCCESS -> Color(0xFF7EE787)
        SshLineType.ERROR, SshLineType.STDERR -> Color(0xFFFF7B72)
        SshLineType.STDOUT -> {
            when {
                text.contains("100%|") || text.contains("%|") -> Color(0xFF58A6FF)
                text.contains("got prompt", ignoreCase = true) || text.contains("Prompt executed in", ignoreCase = true) -> Color(0xFF79C0FF)
                text.contains("loaded completely", ignoreCase = true) || text.contains("Starting server", ignoreCase = true) -> Color(0xFF7EE787)
                text.contains("warning", ignoreCase = true) -> Color(0xFFFFA657)
                text.contains("error", ignoreCase = true) || text.contains("failed", ignoreCase = true) -> Color(0xFFFF7B72)
                text.startsWith("[Attached") -> Color(0xFFA5D6A7)
                else -> Color(0xFFC9D1D9)
            }
        }
    }

    Text(
        text = text,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        color = textColor,
        lineHeight = 15.sp,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun SshTerminalDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    terminalLines: List<SshTerminalLine>,
    isRunning: Boolean,
    sshHost: String,
    sshUser: String,
    sshCommand: String = "",
    onExecuteCommand: (String) -> Unit,
    onClearTerminal: () -> Unit
) {
    if (!isVisible) return

    val context = LocalContext.current
    var commandInput by remember { mutableStateOf("") }
    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to bottom when new lines appear
    LaunchedEffect(terminalLines.size) {
        if (terminalLines.isNotEmpty()) {
            lazyListState.animateScrollToItem(terminalLines.size - 1)
        }
    }

    val defaultComfyCmd = if (sshCommand.isNotBlank()) sshCommand else "run_nvidia_gpu.bat"
    val presetCommands = listOf(
        "Start ComfyUI" to defaultComfyCmd,
        "nvidia-smi" to "nvidia-smi",
        "Process Check" to "tasklist /FI \"IMAGENAME eq python.exe\"",
        "Kill Python" to "taskkill /F /IM python.exe",
        "Dir Listing" to "dir"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D1117),
        shape = RoundedCornerShape(16.dp),
        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Terminal indicator dot
                        Surface(
                            shape = CircleShape,
                            color = if (isRunning) Color(0xFF4CAF50) else Color(0xFF78909C),
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Text(
                            "SSH Terminal",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = if (sshUser.isNotBlank() && sshHost.isNotBlank()) {
                            "$sshUser@$sshHost"
                        } else {
                            "Not configured — set SSH credentials in Settings"
                        },
                        fontSize = 11.sp,
                        color = Color(0xFF6E7681),
                        fontFamily = FontFamily.Monospace
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onClearTerminal,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Terminal", tint = Color(0xFF6E7681), modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            // Copy all output
                            val text = terminalLines.joinToString("\n") { it.text }
                            val clipboard = context.getSystemService(AndroidContext.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("SSH Terminal Output", text))
                            Toast.makeText(context, "Output copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Output", tint = Color(0xFF6E7681), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF6E7681), modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Preset command chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetCommands.forEach { (label, cmd) ->
                        Surface(
                            color = if (commandInput == cmd) Color(0xFF21262D) else Color(0xFF161B22),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (commandInput == cmd) Color(0xFF58A6FF) else Color(0xFF30363D)),
                            modifier = Modifier.clickable { commandInput = cmd }
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (commandInput == cmd) Color(0xFF58A6FF) else Color(0xFF8B949E),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Terminal console output
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF010409))
                        .padding(8.dp)
                ) {
                    if (terminalLines.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "ComfyPort SSH Terminal",
                                color = Color(0xFF3D444D),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Enter a command below or use a preset chip",
                                color = Color(0xFF3D444D),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(terminalLines, key = { line -> line.id }) { line ->
                                SshTerminalLineItem(line = line)
                            }
                            if (isRunning) {
                                item {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 1.5.dp,
                                            color = Color(0xFF4CAF50)
                                        )
                                        Text(
                                            "Running...",
                                            fontSize = 11.sp,
                                            color = Color(0xFF4CAF50),
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Command input row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$ ",
                        color = Color(0xFF4CAF50),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    OutlinedTextField(
                        value = commandInput,
                        onValueChange = { commandInput = it },
                        placeholder = { Text("Enter SSH command...", fontSize = 11.sp, color = Color(0xFF3D444D), fontFamily = FontFamily.Monospace) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF388E3C),
                            unfocusedBorderColor = Color(0xFF21262D),
                            focusedTextColor = Color(0xFFE6EDF3),
                            unfocusedTextColor = Color(0xFFE6EDF3),
                            focusedContainerColor = Color(0xFF161B22),
                            unfocusedContainerColor = Color(0xFF0D1117),
                            cursorColor = Color(0xFF4CAF50)
                        ),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        ),
                        shape = RoundedCornerShape(6.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1f).height(48.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Send
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSend = {
                                if (commandInput.isNotBlank() && !isRunning) {
                                    onExecuteCommand(commandInput)
                                    commandInput = ""
                                }
                            }
                        )
                    )
                    IconButton(
                        onClick = {
                            if (commandInput.isNotBlank() && !isRunning) {
                                onExecuteCommand(commandInput)
                                commandInput = ""
                            }
                        },
                        enabled = !isRunning && commandInput.isNotBlank(),
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (!isRunning && commandInput.isNotBlank()) Color(0xFF1A7F37) else Color(0xFF21262D),
                                RoundedCornerShape(6.dp)
                            )
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF4CAF50)
                            )
                        } else {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "Run",
                                tint = if (commandInput.isNotBlank()) Color.White else Color(0xFF3D444D),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun SshTerminalLineItem(line: SshTerminalLine) {
    val (textColor, prefix) = when (line.type) {
        SshLineType.COMMAND -> Color(0xFFE6EDF3) to ""
        SshLineType.STDOUT -> Color(0xFFE6EDF3) to ""
        SshLineType.STDERR -> Color(0xFFFF7B72) to ""
        SshLineType.SYSTEM -> Color(0xFF6E7681) to "# "
        SshLineType.SUCCESS -> Color(0xFF3FB950) to "✓ "
        SshLineType.ERROR -> Color(0xFFFF7B72) to "✗ "
    }
    Text(
        text = "$prefix${line.text}",
        color = textColor,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 16.sp,
        modifier = Modifier.fillMaxWidth()
    )
}

