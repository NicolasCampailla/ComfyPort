package com.comfyport.ui.screens

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.comfyport.data.ProgressInfo
import com.comfyport.theme.AccentGray
import com.comfyport.theme.AccentRed
import com.comfyport.theme.CardGray
import com.comfyport.theme.DarkGray
import com.comfyport.theme.LightGray

@Composable
fun ProgressScreen(
    progressInfo: ProgressInfo,
    prompt: String,
    onStopClick: () -> Unit,
    onSaveClick: (String) -> Unit,
    onShareClick: (String) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isExpandedScreen = configuration.screenWidthDp >= 600

    // 1. Live Elapsed Stopwatch Timer
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (true) {
            elapsedMillis = System.currentTimeMillis() - start
            kotlinx.coroutines.delay(100)
        }
    }
    val totalSeconds = elapsedMillis / 1000
    val tenths = (elapsedMillis % 1000) / 100
    val timerText = String.format("%02d:%02d.%d", totalSeconds / 60, totalSeconds % 60, tenths)

    // 2. Animated Breathing Aura & Rotation
    val infiniteTransition = rememberInfiniteTransition(label = "ReactorAura")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AuraScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AuraAlpha"
    )
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "OrbRotation"
    )

    // 3. 4-Stage ComfyUI Pipeline Status
    val stages = listOf("Queued", "Sampling", "VAE Decode", "Output")
    val currentStageIndex = when {
        progressInfo.statusText.contains("Saving", ignoreCase = true) || 
        progressInfo.statusText.contains("Output", ignoreCase = true) || 
        progressInfo.finalImage != null -> 3
        progressInfo.statusText.contains("VAE", ignoreCase = true) || 
        progressInfo.statusText.contains("Decode", ignoreCase = true) -> 2
        progressInfo.percent > 0.01f || 
        progressInfo.statusText.contains("Sampling", ignoreCase = true) || 
        progressInfo.statusText.contains("KSampler", ignoreCase = true) -> 1
        else -> 0
    }

    Scaffold(
        containerColor = Color.Black
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Bar: Live Timer Badge & Percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = DarkGray,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AccentGray.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("⏱️", fontSize = 12.sp)
                        Text(
                            text = timerText,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    color = DarkGray,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = if (progressInfo.percent > 0.01f) "${(progressInfo.percent * 100).toInt()}%" else "LIVE",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            // Prompt Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardGray),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "GENERATING PROMPT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AccentGray
                    )
                    Text(
                        text = prompt,
                        fontSize = 13.sp,
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Interactive Ambient Reactor & Progress Orb Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isExpandedScreen) 400.dp else 300.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background Glowing Breathing Aura
                Box(
                    modifier = Modifier
                        .size(if (isExpandedScreen) 340.dp else 260.dp)
                        .graphicsLayer(
                            scaleX = pulseScale,
                            scaleY = pulseScale
                        )
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF00E5FF).copy(alpha = pulseAlpha * 0.35f),
                                    Color(0xFF7C4DFF).copy(alpha = pulseAlpha * 0.25f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                val activeImage = progressInfo.finalImage ?: progressInfo.baseImage

                if (activeImage != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkGray),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = activeImage,
                                contentDescription = "Intermediate Output",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                            Surface(
                                color = Color.Black.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = "Preview Generated",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Neural Orb: active spinning ring when 0%, percentage when > 0%
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Outer rotating ambient ring
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(120.dp)
                                    .graphicsLayer(rotationZ = rotationAngle),
                                color = Color(0xFF00E5FF).copy(alpha = 0.8f),
                                strokeWidth = 3.dp,
                                trackColor = Color.Transparent
                            )

                            // Main progress or indeterminate spinner
                            if (progressInfo.percent > 0.01f) {
                                CircularProgressIndicator(
                                    progress = { progressInfo.percent },
                                    modifier = Modifier.size(96.dp),
                                    color = Color.White,
                                    strokeWidth = 6.dp,
                                    trackColor = DarkGray
                                )
                                Text(
                                    text = "${(progressInfo.percent * 100).toInt()}%",
                                    color = Color.White,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(96.dp),
                                    color = Color.White,
                                    strokeWidth = 5.dp,
                                    trackColor = DarkGray
                                )
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Text(
                            text = progressInfo.statusText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // 4-Stage ComfyUI Pipeline Visualizer
            Surface(
                color = CardGray,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    stages.forEachIndexed { idx, stageName ->
                        val isDone = idx < currentStageIndex
                        val isCurrent = idx == currentStageIndex
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .background(
                                        color = when {
                                            isDone -> Color.White
                                            isCurrent -> Color(0xFF00E5FF)
                                            else -> DarkGray
                                        },
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isDone) {
                                    Text("✓", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                } else if (isCurrent) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color.Black, CircleShape)
                                    )
                                }
                            }
                            Text(
                                text = stageName,
                                fontSize = 11.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isCurrent -> Color.White
                                    isDone -> LightGray
                                    else -> AccentGray
                                },
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Stop Generation Button
            OutlinedButton(
                onClick = onStopClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                border = BorderStroke(1.dp, AccentRed),
                shape = RoundedCornerShape(26.dp)
            ) {
                Text(
                    text = "CANCEL GENERATION",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

