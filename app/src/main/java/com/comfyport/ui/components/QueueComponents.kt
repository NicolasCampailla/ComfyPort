package com.comfyport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comfyport.data.QueueJob
import com.comfyport.theme.*

@Composable
fun QueueFAB(
    queueSize: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(72.dp) // extra padding room for the badge
    ) {
        FloatingActionButton(
            onClick = onClick,
            shape = CircleShape,
            containerColor = CardGray,
            contentColor = White,
            modifier = Modifier
                .size(56.dp)
                .align(Alignment.BottomStart)
        ) {
            Icon(
                imageVector = Icons.Default.Queue,
                contentDescription = "View Queue",
                tint = White,
                modifier = Modifier.size(24.dp)
            )
        }

        if (queueSize > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(AccentRed),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = queueSize.toString(),
                    color = White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun QueueBottomSheetContent(
    queueJobs: List<QueueJob>,
    activeJobId: String?,
    onCancelJob: (String) -> Unit,
    onClearPending: () -> Unit,
    onStopAll: () -> Unit,
    onJobClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val activeJob = queueJobs.firstOrNull { it.id == activeJobId }
    val pendingJobs = queueJobs.filter { it.id != activeJobId }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
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
                Text(
                    text = "Generation Queue",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (queueJobs.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardGray)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${queueJobs.size} Active",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            if (queueJobs.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onClearPending,
                        colors = ButtonDefaults.textButtonColors(contentColor = AccentGray),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ClearAll,
                            contentDescription = "Clear Queue",
                            modifier = Modifier.size(16.dp),
                            tint = AccentGray
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Clear",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = AccentGray,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    TextButton(
                        onClick = onStopAll,
                        colors = ButtonDefaults.textButtonColors(contentColor = AccentRed),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Stop All",
                            modifier = Modifier.size(16.dp),
                            tint = AccentRed
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Stop All",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = AccentRed,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = CardGray, thickness = 1.dp)

        // 1. Active Job
        if (activeJob != null) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "ACTIVE JOB",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SuccessGreen,
                    letterSpacing = 1.sp
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onJobClick(activeJob.id) },
                    colors = CardDefaults.cardColors(containerColor = CardGray),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeJob.prompt,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = activeJob.progress.statusText.ifBlank { "Initializing..." },
                                    fontSize = 13.sp,
                                    color = LightGray
                                )
                            }

                            IconButton(
                                onClick = { onCancelJob(activeJob.id) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(DarkGray, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel Job",
                                    tint = AccentRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Progress Bar
                        val progressPercent = activeJob.progress.percent
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LinearProgressIndicator(
                                progress = { progressPercent },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = SuccessGreen,
                                trackColor = DarkGray
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = activeJob.progress.state.name,
                                    fontSize = 11.sp,
                                    color = AccentGray
                                )
                                Text(
                                    text = "${(progressPercent * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Pending Jobs
        if (pendingJobs.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                Text(
                    text = "PENDING IN QUEUE (${pendingJobs.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentGray,
                    letterSpacing = 1.sp
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(pendingJobs, key = { it.id }) { job ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = DarkGray),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = job.prompt,
                                        fontSize = 14.sp,
                                        color = White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Queued",
                                        fontSize = 12.sp,
                                        color = AccentGray
                                    )
                                }

                                IconButton(
                                    onClick = { onCancelJob(job.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove from Queue",
                                        tint = AccentGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (activeJob == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No active generations.",
                    color = AccentGray,
                    fontSize = 14.sp
                )
            }
        }
    }
}
