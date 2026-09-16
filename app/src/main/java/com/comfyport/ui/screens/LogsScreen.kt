package com.comfyport.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comfyport.data.LogLevel
import com.comfyport.network.AppLogger
import com.comfyport.theme.CardGray
import com.comfyport.theme.DarkGray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ParsedLogEntry(
    val id: Int,
    val raw: String,
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val details: String? = null
)

private fun parseLogs(rawText: String): List<ParsedLogEntry> {
    if (rawText.isBlank()) return emptyList()
    val lines = rawText.lines()
    val entries = mutableListOf<ParsedLogEntry>()
    val logHeaderRegex = Regex("""^\[(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})\] \[([A-Z]+)\] \[([^\]]+)\] (.*)$""")

    var currentEntry: ParsedLogEntry? = null
    val currentDetails = StringBuilder()

    fun flushCurrent() {
        if (currentEntry != null) {
            val detailsStr = currentDetails.toString().trim()
            entries.add(
                if (detailsStr.isNotBlank()) {
                    currentEntry!!.copy(details = detailsStr)
                } else {
                    currentEntry!!
                }
            )
            currentDetails.clear()
            currentEntry = null
        }
    }

    for ((index, line) in lines.withIndex()) {
        val trimmed = line.trimEnd()
        if (trimmed.isBlank()) continue
        val match = logHeaderRegex.matchEntire(trimmed)
        if (match != null) {
            flushCurrent()
            val (time, levelStr, tag, msg) = match.destructured
            val level = try {
                LogLevel.valueOf(levelStr)
            } catch (_: Exception) {
                LogLevel.INFO
            }
            currentEntry = ParsedLogEntry(
                id = index,
                raw = trimmed,
                timestamp = time,
                level = level,
                tag = tag,
                message = msg
            )
        } else {
            if (currentEntry != null) {
                if (currentDetails.isNotEmpty()) currentDetails.append("\n")
                currentDetails.append(trimmed)
            } else {
                entries.add(
                    ParsedLogEntry(
                        id = index,
                        raw = trimmed,
                        timestamp = "",
                        level = LogLevel.VERBOSE,
                        tag = "System",
                        message = trimmed
                    )
                )
            }
        }
    }
    flushCurrent()
    return entries
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    var logText by remember { mutableStateOf("") }
    var logSize by remember { mutableStateOf("0 KB") }
    var parsedEntries by remember { mutableStateOf<List<ParsedLogEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var isNewestFirst by remember { mutableStateOf(true) }
    var viewMode by remember { mutableStateOf("STRUCTURED") } // "STRUCTURED" or "RAW"

    var showClearDialog by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTrigger) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val text = AppLogger.readActiveLogFile()
            val size = AppLogger.getActiveLogSizeFormatted()
            val parsed = parseLogs(text)
            withContext(Dispatchers.Main) {
                logText = text
                logSize = size
                parsedEntries = parsed
                isLoading = false
            }
        }
    }

    // Available tags for quick filtering
    val availableTags = remember(parsedEntries) {
        parsedEntries.map { it.tag }.filter { it.isNotBlank() }.distinct()
    }

    val errorCount = remember(parsedEntries) { parsedEntries.count { it.level == LogLevel.ERROR } }
    val warnCount = remember(parsedEntries) { parsedEntries.count { it.level == LogLevel.WARNING } }

    val filteredEntries = remember(parsedEntries, searchQuery, selectedLevel, selectedTag, isNewestFirst) {
        val list = parsedEntries.filter { entry ->
            val matchesLevel = selectedLevel == null || entry.level == selectedLevel
            val matchesTag = selectedTag == null || entry.tag.equals(selectedTag, ignoreCase = true)
            val matchesSearch = if (searchQuery.isBlank()) true else {
                entry.message.contains(searchQuery, ignoreCase = true) ||
                entry.tag.contains(searchQuery, ignoreCase = true) ||
                entry.timestamp.contains(searchQuery, ignoreCase = true) ||
                (entry.details?.contains(searchQuery, ignoreCase = true) == true)
            }
            matchesLevel && matchesTag && matchesSearch
        }
        if (isNewestFirst) list.reversed() else list
    }

    // Copy to clipboard helper
    fun copyToClipboard(label: String, content: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, content)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    // Export log function
    fun exportLogFile() {
        val activeFile = AppLogger.getActiveLogFile()
        if (activeFile == null || !activeFile.exists() || activeFile.length() == 0L) {
            Toast.makeText(context, "Log file is empty or does not exist.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val filename = "comfyport_logs_${System.currentTimeMillis()}.log"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri).use { outputStream ->
                        activeFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream!!)
                        }
                    }
                    Toast.makeText(context, "Exported to Downloads folder", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Failed to create MediaStore entry", Toast.LENGTH_SHORT).show()
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val destFile = File(downloadsDir, filename)
                FileOutputStream(destFile).use { outputStream ->
                    activeFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Toast.makeText(context, "Exported to Downloads folder", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    // Share log function
    fun shareLogText() {
        val text = AppLogger.readActiveLogFile()
        if (text.isBlank()) {
            Toast.makeText(context, "Nothing to share.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "ComfyPort Diagnostics Log")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share Logs via"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("App Diagnostic Logs", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${parsedEntries.size} total entries · $logSize",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    // View mode toggle (List vs Raw)
                    IconButton(onClick = {
                        viewMode = if (viewMode == "STRUCTURED") "RAW" else "STRUCTURED"
                    }) {
                        Icon(
                            if (viewMode == "STRUCTURED") Icons.Default.Terminal else Icons.Default.List,
                            contentDescription = "Toggle View Mode",
                            tint = Color.White
                        )
                    }
                    // Sort direction
                    IconButton(onClick = { isNewestFirst = !isNewestFirst }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            contentDescription = if (isNewestFirst) "Newest First" else "Oldest First",
                            tint = if (isNewestFirst) Color.White else Color(0xFF81D4FA)
                        )
                    }
                    // Refresh
                    IconButton(onClick = { refreshTrigger++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
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
        ) {
            // Live Search Input Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search logs (e.g. error, comfy, http)...", fontSize = 12.sp, color = Color.Gray) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = CardGray,
                        unfocusedContainerColor = CardGray
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                )
            }

            // Severity Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ALL chip
                FilterChip(
                    selected = selectedLevel == null,
                    onClick = { selectedLevel = null },
                    label = { Text("ALL (${parsedEntries.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.White,
                        selectedLabelColor = Color.Black,
                        containerColor = DarkGray,
                        labelColor = Color.LightGray
                    )
                )

                // ERROR chip
                FilterChip(
                    selected = selectedLevel == LogLevel.ERROR,
                    onClick = { selectedLevel = if (selectedLevel == LogLevel.ERROR) null else LogLevel.ERROR },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("ERROR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            if (errorCount > 0) {
                                Surface(
                                    color = Color(0xFFD32F2F),
                                    shape = CircleShape
                                ) {
                                    Text(
                                        "$errorCount",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFD32F2F),
                        selectedLabelColor = Color.White,
                        containerColor = DarkGray,
                        labelColor = if (errorCount > 0) Color(0xFFFF5252) else Color.LightGray
                    )
                )

                // WARN chip
                FilterChip(
                    selected = selectedLevel == LogLevel.WARNING,
                    onClick = { selectedLevel = if (selectedLevel == LogLevel.WARNING) null else LogLevel.WARNING },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("WARN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            if (warnCount > 0) {
                                Surface(
                                    color = Color(0xFFF57C00),
                                    shape = CircleShape
                                ) {
                                    Text(
                                        "$warnCount",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFF57C00),
                        selectedLabelColor = Color.White,
                        containerColor = DarkGray,
                        labelColor = if (warnCount > 0) Color(0xFFFFB74D) else Color.LightGray
                    )
                )

                // INFO chip
                FilterChip(
                    selected = selectedLevel == LogLevel.INFO,
                    onClick = { selectedLevel = if (selectedLevel == LogLevel.INFO) null else LogLevel.INFO },
                    label = { Text("INFO", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0288D1),
                        selectedLabelColor = Color.White,
                        containerColor = DarkGray,
                        labelColor = Color.LightGray
                    )
                )

                // DEBUG chip
                FilterChip(
                    selected = selectedLevel == LogLevel.DEBUG,
                    onClick = { selectedLevel = if (selectedLevel == LogLevel.DEBUG) null else LogLevel.DEBUG },
                    label = { Text("DEBUG", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF5C6BC0),
                        selectedLabelColor = Color.White,
                        containerColor = DarkGray,
                        labelColor = Color.LightGray
                    )
                )

                // Tag Filter dropdown if available
                if (availableTags.isNotEmpty()) {
                    availableTags.forEach { tag ->
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = { selectedTag = if (selectedTag == tag) null else tag },
                            label = { Text(tag, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF455A64),
                                selectedLabelColor = Color.White,
                                containerColor = CardGray,
                                labelColor = Color.LightGray
                            )
                        )
                    }
                }
            }

            // Results summary bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Showing ${filteredEntries.size} of ${parsedEntries.size} entries" +
                            if (searchQuery.isNotBlank() || selectedLevel != null || selectedTag != null) " (filtered)" else "",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                if (filteredEntries.isNotEmpty()) {
                    Text(
                        text = if (isNewestFirst) "▼ Newest first" else "▲ Chronological",
                        fontSize = 10.sp,
                        color = Color(0xFF81D4FA),
                        modifier = Modifier.clickable { isNewestFirst = !isNewestFirst }
                    )
                }
            }

            // Main log content area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                    }
                } else if (parsedEntries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                            Text("Log file is empty.", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                } else if (filteredEntries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.SearchOff, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                            Text("No log entries match your filter.", color = Color.Gray, fontSize = 13.sp)
                            Button(
                                onClick = {
                                    searchQuery = ""
                                    selectedLevel = null
                                    selectedTag = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkGray, contentColor = Color.White),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("Reset Filters", fontSize = 12.sp)
                            }
                        }
                    }
                } else if (viewMode == "RAW") {
                    // Raw view mode
                    val scrollVert = rememberScrollState()
                    val scrollHoriz = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F141C))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = logText,
                            color = Color(0xFFD4D4D4),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier
                                .verticalScroll(scrollVert)
                                .horizontalScroll(scrollHoriz)
                        )
                    }
                } else {
                    // Structured view mode with interactive cards
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredEntries, key = { it.id }) { entry ->
                            LogItemCard(entry = entry, onCopy = { copyToClipboard("Log Entry", it) })
                        }
                    }
                }
            }

            // Bottom action buttons panel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Copy All
                OutlinedButton(
                    onClick = { copyToClipboard("All Logs", logText) },
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, DarkGray),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy All", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }

                // Download/Export
                OutlinedButton(
                    onClick = { exportLogFile() },
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, DarkGray),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }

                // Share
                OutlinedButton(
                    onClick = { shareLogText() },
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, DarkGray),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }

                // Clear
                Button(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F0000), contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            }
        }
    }

    // Confirmation dialog for clearing logs
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Logs?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently delete all diagnostic log entries from your device.", color = Color.LightGray) },
            confirmButton = {
                Button(
                    onClick = {
                        AppLogger.clearLogs()
                        showClearDialog = false
                        refreshTrigger++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F), contentColor = Color.White)
                ) {
                    Text("DELETE LOGS", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("CANCEL", color = Color.White)
                }
            },
            containerColor = CardGray,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun LogItemCard(
    entry: ParsedLogEntry,
    onCopy: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    val levelColor = when (entry.level) {
        LogLevel.ERROR -> Color(0xFFE53935)
        LogLevel.WARNING -> Color(0xFFFFA000)
        LogLevel.INFO -> Color(0xFF039BE5)
        LogLevel.DEBUG -> Color(0xFF7E57C2)
        LogLevel.VERBOSE -> Color(0xFF78909C)
    }

    val levelLabel = when (entry.level) {
        LogLevel.ERROR -> "ERR"
        LogLevel.WARNING -> "WRN"
        LogLevel.INFO -> "INF"
        LogLevel.DEBUG -> "DBG"
        LogLevel.VERBOSE -> "VRB"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161922)),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            0.8.dp,
            if (entry.level == LogLevel.ERROR) Color(0xFFE53935).copy(alpha = 0.5f)
            else if (entry.level == LogLevel.WARNING) Color(0xFFFFA000).copy(alpha = 0.4f)
            else Color(0xFF262C3A)
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Header: Level Badge, Tag, Timestamp, Copy Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = levelColor,
                        shape = RoundedCornerShape(3.dp)
                    ) {
                        Text(
                            text = levelLabel,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    if (entry.tag.isNotBlank()) {
                        Surface(
                            color = Color(0xFF252D3D),
                            shape = RoundedCornerShape(3.dp)
                        ) {
                            Text(
                                text = entry.tag,
                                color = Color(0xFF90CAF9),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (entry.timestamp.isNotBlank()) {
                        Text(
                            text = entry.timestamp.substringAfter(" "),
                            fontSize = 10.sp,
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Copy single entry button
                IconButton(
                    onClick = {
                        val toCopy = buildString {
                            append("[${entry.timestamp}] [${entry.level.name}] [${entry.tag}] ${entry.message}")
                            if (!entry.details.isNullOrBlank()) {
                                append("\n${entry.details}")
                            }
                        }
                        onCopy(toCopy)
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy Entry",
                        tint = Color.Gray,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // Message text
            Text(
                text = entry.message,
                color = if (entry.level == LogLevel.ERROR) Color(0xFFFF8A80)
                        else if (entry.level == LogLevel.WARNING) Color(0xFFFFD180)
                        else Color(0xFFEEEEEE),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 15.sp
            )

            // Stack trace / details section
            if (!entry.details.isNullOrBlank()) {
                Surface(
                    color = Color(0xFF0D1017),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(0.5.dp, Color(0xFF30363D)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isExpanded) "▼ Hide Stack Trace / Output" else "▶ Show Stack Trace / Output (${entry.details.lines().size} lines)",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF81D4FA)
                            )
                        }

                        AnimatedVisibility(visible = isExpanded) {
                            Column(modifier = Modifier.padding(top = 4.dp)) {
                                Text(
                                    text = entry.details,
                                    fontSize = 10.sp,
                                    color = Color(0xFFCFD8DC),
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
