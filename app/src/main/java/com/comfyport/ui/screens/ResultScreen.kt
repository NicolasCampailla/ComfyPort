package com.comfyport.ui.screens

import com.comfyport.ui.components.ZoomableImage
import com.comfyport.ui.components.ZoomableImageViewer
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.launch

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.comfyport.data.AppSettings
import com.comfyport.theme.AccentGray
import com.comfyport.theme.CardGray
import com.comfyport.theme.DarkGray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    finalImageUrl: String,
    seed: Long,
    prompt: String = "",
    settings: AppSettings,
    batchImages: List<String> = emptyList(),
    onSaveClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onSaveImageClick: ((String) -> Unit)? = null,
    onShareImageClick: ((String) -> Unit)? = null,
    onBackClick: () -> Unit = {},
    onReRunClick: () -> Unit,
    onUsePromptClick: (String) -> Unit = {},
    viewModel: com.comfyport.ui.MainViewModel
) {
    val allImages = remember(finalImageUrl, batchImages) {
        if (batchImages.isNotEmpty()) batchImages else listOf(finalImageUrl)
    }
    val initialIndex = remember { allImages.indexOf(finalImageUrl).coerceAtLeast(0) }
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = initialIndex,
        pageCount = { allImages.size }
    )
    val coroutineScope = rememberCoroutineScope()
    var showControls by remember { mutableStateOf(true) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(false) }
    var currentZoomScale by remember { mutableStateOf(1f) }

    val currentImageUrl = allImages.getOrElse(pagerState.currentPage) { finalImageUrl }
    val currentGalleryItem = remember(currentImageUrl, viewModel.galleryItems) {
        viewModel.galleryItems.value.firstOrNull { it.imageUrl == currentImageUrl }
    }
    val activePrompt = currentGalleryItem?.prompt ?: prompt.ifBlank { viewModel.currentPrompt.value }
    val activeSeed = currentGalleryItem?.seed ?: seed
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val configuration = LocalConfiguration.current
    val isExpandedScreen = configuration.screenWidthDp >= 600

    val performSave = {
        if (onSaveImageClick != null) {
            onSaveImageClick(currentImageUrl)
        } else {
            onSaveClick()
        }
    }

    val performShare = {
        if (onShareImageClick != null) {
            onShareImageClick(currentImageUrl)
        } else {
            onShareClick()
        }
    }

    val displayPrompt = activePrompt

    BackHandler(enabled = showInfoSheet) {
        showInfoSheet = false
    }

    BackHandler(enabled = isGridView) {
        isGridView = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isGridView) {
            // ALL IMAGES BATCH GALLERY VIEW
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp)
            ) {
                // Top header for Grid View
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { isGridView = false },
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Single View", tint = Color.White)
                    }

                    Text(
                        text = "Batch Gallery (${allImages.size} images)",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    IconButton(
                        onClick = { showInfoSheet = true },
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Details", tint = Color.White)
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (isExpandedScreen) 3 else 2),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(allImages.size) { idx ->
                        val url = allImages[idx]
                        val isSelected = idx == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(CardGray)
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) Color.White else DarkGray,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    coroutineScope.launch {
                                        pagerState.scrollToPage(idx)
                                    }
                                    isGridView = false
                                }
                        ) {
                            AsyncImage(
                                model = url,
                                contentDescription = "Batch Image ${idx + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                color = Color.Black.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "#${idx + 1}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // FULL SCREEN SINGLE IMAGE SWIPE CANVAS
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = (currentZoomScale <= 1.05f)
            ) { pageIndex ->
                val pageImageUrl = allImages.getOrElse(pageIndex) { finalImageUrl }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    ZoomableImage(
                        model = pageImageUrl,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        shape = androidx.compose.ui.graphics.RectangleShape,
                        onTap = { showControls = !showControls },
                        onScaleChanged = { scale ->
                            if (pageIndex == pagerState.currentPage) {
                                currentZoomScale = scale
                            }
                        }
                    )
                }
            }

            // FLOATING TOP OVERLAY BAR
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back Button
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .size(42.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Batch View-All Grid Toggle Button
                        if (allImages.size > 1) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                modifier = Modifier.clickable { isGridView = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GridView,
                                        contentDescription = "View All Batch Images",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "${pagerState.currentPage + 1} / ${allImages.size} (View All)",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.width(42.dp))
                        }

                        // Details (Info) Button
                        IconButton(
                            onClick = { showInfoSheet = true },
                            modifier = Modifier
                                .size(42.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Details",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // FLOATING BOTTOM OVERLAY BAR (No pill, floating controls)
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Mini Filmstrip when multiple batch images exist
                    if (allImages.size > 1) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            itemsIndexed(allImages) { idx, url ->
                                val isSelected = idx == pagerState.currentPage
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(CardGray)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) Color.White else DarkGray,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(idx)
                                            }
                                        }
                                ) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = "Thumbnail ${idx + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }

                    // Floating Action Controls (No pill container, details button removed)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left side: Floating Download & Share buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            IconButton(
                                onClick = performSave,
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download Image",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            IconButton(
                                onClick = performShare,
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share Image",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Right side: Floating RE-RUN Button
                        Button(
                            onClick = {
                                onUsePromptClick(displayPrompt)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(26.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "RE-RUN",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // GENERATION DETAILS BOTTOM SHEET
        if (showInfoSheet) {
            ModalBottomSheet(
                onDismissRequest = { showInfoSheet = false },
                containerColor = CardGray,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Generation Details",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        IconButton(onClick = { showInfoSheet = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    if (displayPrompt.isNotBlank()) {
                        Surface(
                            color = DarkGray,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "PROMPT",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentGray
                                    )
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(displayPrompt))
                                            Toast.makeText(context, "Prompt copied to clipboard!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Copy Prompt",
                                            tint = Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                                Text(displayPrompt, color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }

                    // Metadata Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = DarkGray,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("SEED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentGray)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("$activeSeed", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        Surface(
                            color = DarkGray,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("RESOLUTION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentGray)
                                Spacer(modifier = Modifier.height(4.dp))
                                val resText = when (settings.resolutionMode) {
                                    "WORKFLOW" -> "Auto (Native / Image)"
                                    "CUSTOM" -> "${settings.customWidth}x${settings.customHeight}"
                                    else -> "${settings.resolutionMode} (${settings.aspectRatio.split(" ").firstOrNull() ?: ""})"
                                }
                                Text(resText, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    val activeWfName = settings.savedWorkflows.firstOrNull { it.id == settings.selectedWorkflowId }?.label
                        ?: if (settings.savedWorkflows.isNotEmpty()) settings.savedWorkflows.first().label else "Custom Workflow"
                    Surface(
                        color = DarkGray,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("WORKFLOW", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentGray)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(activeWfName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            showInfoSheet = false
                            onUsePromptClick(displayPrompt)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("USE THIS PROMPT TO GENERATE", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

