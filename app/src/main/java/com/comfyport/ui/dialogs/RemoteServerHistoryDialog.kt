package com.comfyport.ui.dialogs

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.TextStyle
import com.comfyport.network.ComfyClient
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.comfyport.theme.AccentGray
import com.comfyport.theme.CardGray
import com.comfyport.theme.DarkGray
import com.comfyport.theme.LightGray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteServerHistoryDialog(
    historyItems: List<ComfyClient.MachineHistoryItem>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    onPreviewItem: (ComfyClient.MachineHistoryItem) -> Unit,
    onImportItem: (item: ComfyClient.MachineHistoryItem, activate: Boolean) -> Unit
) = MachineHistoryDialog(historyItems, isLoading, onRefresh, onDismiss, onPreviewItem, onImportItem)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MachineHistoryDialog(
    historyItems: List<ComfyClient.MachineHistoryItem>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    onPreviewItem: (ComfyClient.MachineHistoryItem) -> Unit,
    onImportItem: (item: ComfyClient.MachineHistoryItem, activate: Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardGray,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Machine Workflow History",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                    Text(
                        if (isLoading) "Querying recent server runs..." else "${historyItems.size} runs retrieved from computer",
                        fontSize = 11.sp,
                        color = AccentGray
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !isLoading,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = if (isLoading) AccentGray else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = LightGray, modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                        Text("Retrieving recent server workflows...", fontSize = 13.sp, color = LightGray)
                    }
                } else if (historyItems.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            "No recent workflows found in server history.\n\nMake sure your ComfyUI server is running and you've executed at least one prompt on your computer.",
                            color = LightGray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onRefresh,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text("Retry", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(historyItems) { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = DarkGray),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        // Output thumbnail preview (if available)
                                        if (!item.thumbnailUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = item.thumbnailUrl,
                                                contentDescription = "Output Image Preview",
                                                modifier = Modifier
                                                    .size(68.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.Black),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(68.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(CardGray),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("⚙️", fontSize = 24.sp)
                                            }
                                        }

                                        // Item info
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Run #${item.promptIndex}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = Color.White,
                                                    modifier = Modifier.weight(1f, fill = false),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Surface(
                                                    color = if (item.isUiFormat) AccentGray.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = if (item.isUiFormat) "UI FORMAT" else "API FORMAT",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }

                                            // Model & Resolution
                                            val modelTitle = item.modelName?.substringBeforeLast(".") ?: "Custom Pipeline"
                                            Text(
                                                text = modelTitle,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = LightGray,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            // Resolution & steps
                                            val specsSummary = buildString {
                                                if (item.width != null && item.height != null) {
                                                    append("${item.width}×${item.height}")
                                                }
                                                if (item.steps != null) {
                                                    if (isNotEmpty()) append(" • ")
                                                    append("${item.steps} st")
                                                }
                                                if (!item.samplerName.isNullOrBlank()) {
                                                    if (isNotEmpty()) append(" • ")
                                                    append(item.samplerName)
                                                }
                                            }
                                            if (specsSummary.isNotBlank()) {
                                                Text(
                                                    text = specsSummary,
                                                    fontSize = 11.sp,
                                                    color = AccentGray
                                                )
                                            }

                                            // Prompt snippet
                                            if (!item.promptText.isNullOrBlank()) {
                                                Text(
                                                    text = item.promptText,
                                                    fontSize = 11.sp,
                                                    color = LightGray,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = androidx.compose.ui.text.TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                                )
                                            }
                                        }
                                    }

                                    // Action buttons for this item
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { onPreviewItem(item) },
                                            modifier = Modifier.weight(1f).height(34.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                            border = BorderStroke(1.dp, AccentGray.copy(alpha = 0.6f)),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                        ) {
                                            Text("PREVIEW", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                        }

                                        Button(
                                            onClick = { onImportItem(item, false) },
                                            modifier = Modifier.weight(1f).height(34.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = CardGray, contentColor = Color.White),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                        ) {
                                            Text("IMPORT", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                        }

                                        Button(
                                            onClick = { onImportItem(item, true) },
                                            modifier = Modifier.weight(1.3f).height(34.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                        ) {
                                            Text("IMPORT & USE", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = AccentGray)
            }
        }
    )
}

