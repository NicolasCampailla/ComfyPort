package com.comfyport.ui.dialogs

import androidx.compose.foundation.shape.CircleShape

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.horizontalScroll
import com.comfyport.data.WorkflowNodeMapping
import com.comfyport.data.WorkflowNodeInfo
import com.comfyport.data.CustomWorkflowInput
import com.comfyport.data.NodeWidgetInfo
import com.comfyport.network.ComfyClient
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.viewinterop.AndroidView
import com.comfyport.network.UrlValidator
import com.comfyport.theme.AccentGray
import com.comfyport.theme.AccentRed
import com.comfyport.theme.CardGray
import com.comfyport.theme.DarkGray
import com.comfyport.theme.LightGray
import com.comfyport.theme.SuccessGreen
import com.comfyport.theme.LocalHighlightColor

@Composable
fun SpecBadge(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DarkGray,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, AccentGray.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text = label.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = AccentGray,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RoleChip(
    label: String,
    isAssigned: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        color = if (isAssigned) activeColor else DarkGray,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (isAssigned) activeColor else AccentGray.copy(alpha = 0.4f)),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isAssigned) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (isAssigned) Color.Black else Color.White,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun RoleMappingItem(
    title: String,
    roleColor: Color,
    assignedNode: WorkflowNodeInfo?,
    assignedId: String?,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkGray),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (assignedId != null) roleColor.copy(alpha = 0.6f) else DarkGray)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(color = roleColor, shape = CircleShape, modifier = Modifier.size(8.dp)) {}
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                }
                Spacer(modifier = Modifier.height(3.dp))
                if (assignedNode != null) {
                    Text(
                        "Node #${assignedNode.id}: ${assignedNode.title} (${assignedNode.type})",
                        fontSize = 11.sp,
                        color = roleColor,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (assignedId != null) {
                    Text("Node #$assignedId (Mapped)", fontSize = 11.sp, color = roleColor)
                } else {
                    Text("Auto-detect (Basic Mode)", fontSize = 11.sp, color = AccentGray, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                }
            }
            if (assignedId != null) {
                IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = AccentRed, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

private fun getComfyWireColor(type: String): Color {
    val t = type.uppercase()
    return when {
        t.contains("MODEL") -> Color(0xFFB366FF) // Purple
        t.contains("CLIP") -> Color(0xFFFFD700)  // Gold / Yellow
        t.contains("LATENT") || t.contains("SAMPLES") -> Color(0xFFFF69B4) // Pink
        t.contains("VAE") -> Color(0xFFFF4444)   // Red
        t.contains("IMAGE") -> Color(0xFF64B5F6) // Cyan / Sky Blue
        t.contains("CONDITIONING") || t.contains("POSITIVE") || t.contains("NEGATIVE") -> Color(0xFFFF8533) // Orange
        t.contains("MASK") -> Color(0xFF4ADE80)  // Lime Green
        t.contains("CONTROL_NET") -> Color(0xFF2DD4BF) // Teal
        t.contains("INT") || t.contains("FLOAT") || t.contains("NUMBER") -> Color(0xFF38BDF8) // Cyan / Blue
        else -> Color(0xFF94A3B8) // Slate Gray
    }
}

private fun getComfyNodeHeaderColor(type: String, title: String): Color {
    val t = (type + " " + title).uppercase()
    return when {
        t.contains("SAMPLER") -> Color(0xFF225533) // Forest Green (KSampler)
        t.contains("CLIPTEXT") || t.contains("PROMPT") || t.contains("CONDITIONING") -> Color(0xFF4D3D14) // Warm Amber
        t.contains("CHECKPOINT") || t.contains("LOADER") || t.contains("UNET") || t.contains("DIFFUSION") -> Color(0xFF2D354A) // Slate Indigo
        t.contains("LATENT") || t.contains("VAE") -> Color(0xFF462D4A) // Dark Plum
        t.contains("SAVE") || t.contains("PREVIEW") || t.contains("OUTPUT") -> Color(0xFF1C3B57) // Deep Teal
        t.contains("IMAGE") || t.contains("MASK") -> Color(0xFF2E4038) // Sage Dark
        t.contains("CONTROLNET") -> Color(0xFF1E3A42) // Teal Dark
        t.contains("LORA") -> Color(0xFF4A3428) // Rust Bronze
        else -> Color(0xFF353535) // ComfyUI Default Dark Charcoal
    }
}

@Composable
fun ComfyNodeCanvas(
    preview: ComfyClient.WorkflowPreviewData,
    mapping: WorkflowNodeMapping,
    onMappingChange: (WorkflowNodeMapping) -> Unit,
    isFullscreen: Boolean = false,
    onToggleFullscreen: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var zoom by remember { mutableFloatStateOf(0.75f) }
    var selectedNodeId by remember { mutableStateOf<String?>(null) }
    var isInitialized by remember { mutableStateOf(false) }

    // Interactive node positions map to support moving nodes on canvas
    val nodePositions = remember(preview.nodes) {
        mutableStateMapOf<String, Offset>().apply {
            preview.nodes.forEach { put(it.id, Offset(it.posX, it.posY)) }
        }
    }

    val minX = remember(preview.nodes, nodePositions) { preview.nodes.minOfOrNull { nodePositions[it.id]?.x ?: it.posX } ?: 0f }
    val minY = remember(preview.nodes, nodePositions) { preview.nodes.minOfOrNull { nodePositions[it.id]?.y ?: it.posY } ?: 0f }
    val maxX = remember(preview.nodes, nodePositions) { preview.nodes.maxOfOrNull { (nodePositions[it.id]?.x ?: it.posX) + it.width } ?: 800f }
    val maxY = remember(preview.nodes, nodePositions) { preview.nodes.maxOfOrNull { (nodePositions[it.id]?.y ?: it.posY) + it.height } ?: 600f }
    val graphW = (maxX - minX).coerceAtLeast(350f)
    val graphH = (maxY - minY).coerceAtLeast(250f)
    val graphCenterX = (minX + maxX) / 2f
    val graphCenterY = (minY + maxY) / 2f

    val textPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }
    val idPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }
    val slotPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            typeface = android.graphics.Typeface.SANS_SERIF
        }
    }
    val widgetBoxPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.FILL
        }
    }
    val widgetBorderPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
        }
    }
    val widgetTextPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }
    val widgetLabelPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }
    val rolePaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .background(Color(0xFF202020))
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp))
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        fun resetView() {
            if (widthPx > 0f && heightPx > 0f) {
                val fitZoom = ((widthPx * 0.88f) / graphW).coerceAtMost((heightPx * 0.88f) / graphH).coerceIn(0.12f, 1.35f)
                zoom = fitZoom
                panX = (widthPx / 2f) - (graphCenterX * fitZoom)
                panY = (heightPx / 2f) - (graphCenterY * fitZoom)
            }
        }

        LaunchedEffect(widthPx, heightPx, preview.nodes) {
            if (!isInitialized && widthPx > 0f && heightPx > 0f) {
                resetView()
                isInitialized = true
            }
        }

        // 1. Draw 2D Interactive Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(preview.nodes) {
                    detectTransformGestures { _, pan, zoomChange, _ ->
                        zoom = (zoom * zoomChange).coerceIn(0.10f, 2.8f)
                        panX += pan.x
                        panY += pan.y
                    }
                }
                .pointerInput(preview.nodes, zoom, panX, panY) {
                    detectTapGestures(
                        onDoubleTap = {
                            resetView()
                        },
                        onTap = { tapOffset ->
                            val graphX = (tapOffset.x - panX) / zoom
                            val graphY = (tapOffset.y - panY) / zoom
                            val hitNode = preview.nodes.findLast { n ->
                                val pos = nodePositions[n.id] ?: Offset(n.posX, n.posY)
                                val minRequiredH = 30f + 8f + maxOf(n.inputSlots.size, n.outputSlots.size).coerceAtMost(10) * 20f + 30f
                                val effH = maxOf(n.height, minRequiredH)
                                graphX >= pos.x && graphX <= (pos.x + n.width) &&
                                graphY >= pos.y && graphY <= (pos.y + effH)
                            }
                            selectedNodeId = if (selectedNodeId == hitNode?.id) null else hitNode?.id
                        }
                    )
                }
        ) {
            val canvasW = size.width
            val canvasH = size.height

            // A. ComfyUI Major Grid (every 100px)
            val majorStep = 100f * zoom
            if (majorStep > 22f) {
                val startMajorX = (panX % majorStep + majorStep) % majorStep
                val startMajorY = (panY % majorStep + majorStep) % majorStep
                var gx = startMajorX
                while (gx < canvasW) {
                    drawLine(
                        color = Color(0xFF282828),
                        start = Offset(gx, 0f),
                        end = Offset(gx, canvasH),
                        strokeWidth = 1f
                    )
                    gx += majorStep
                }
                var gy = startMajorY
                while (gy < canvasH) {
                    drawLine(
                        color = Color(0xFF282828),
                        start = Offset(0f, gy),
                        end = Offset(canvasW, gy),
                        strokeWidth = 1f
                    )
                    gy += majorStep
                }
            }

            // B. ComfyUI Minor Grid Dots (every 20px)
            val minorStep = 20f * zoom
            if (minorStep > 6f) {
                val startMinorX = (panX % minorStep + minorStep) % minorStep
                val startMinorY = (panY % minorStep + minorStep) % minorStep
                val dotRadius = (1.2f * zoom).coerceIn(0.8f, 2.2f)
                var gx = startMinorX
                while (gx < canvasW) {
                    var gy = startMinorY
                    while (gy < canvasH) {
                        drawCircle(
                            color = Color(0xFF2E2E2E),
                            radius = dotRadius,
                            center = Offset(gx, gy)
                        )
                        gy += minorStep
                    }
                    gx += minorStep
                }
            }

            // C. Draw LiteGraph Flowing Cubic Bezier Wires
            for (wire in preview.wires) {
                val fromNode = preview.nodes.firstOrNull { it.id == wire.fromNodeId }
                val toNode = preview.nodes.firstOrNull { it.id == wire.toNodeId }
                if (fromNode == null || toNode == null) continue

                val fromPos = nodePositions[wire.fromNodeId] ?: Offset(fromNode.posX, fromNode.posY)
                val toPos = nodePositions[wire.toNodeId] ?: Offset(toNode.posX, toNode.posY)

                val isConnectedToSelected = selectedNodeId != null &&
                        (wire.fromNodeId == selectedNodeId || wire.toNodeId == selectedNodeId)
                val wireColor = getComfyWireColor(wire.wireType)

                val startX = panX + (fromPos.x + fromNode.width) * zoom
                val startY = panY + (fromPos.y + 48f + wire.fromSlotIndex * 20f) * zoom

                val endX = panX + toPos.x * zoom
                val endY = panY + (toPos.y + 48f + wire.toSlotIndex * 20f) * zoom

                val dx = endX - startX
                val handleX = if (dx > 0) {
                    (dx * 0.5f).coerceAtLeast(35f * zoom)
                } else {
                    (Math.abs(dx) * 0.45f + 80f * zoom).coerceIn(60f * zoom, 240f * zoom)
                }

                val path = Path().apply {
                    moveTo(startX, startY)
                    cubicTo(
                        startX + handleX, startY,
                        endX - handleX, endY,
                        endX, endY
                    )
                }

                if (isConnectedToSelected) {
                    // Outer glow for active wire
                    drawPath(
                        path = path,
                        color = wireColor.copy(alpha = 0.35f),
                        style = Stroke(width = 8.0f * zoom)
                    )
                    drawPath(
                        path = path,
                        color = wireColor,
                        style = Stroke(width = 3.6f * zoom)
                    )
                    drawPath(
                        path = path,
                        color = Color.White.copy(alpha = 0.7f),
                        style = Stroke(width = 1.2f * zoom)
                    )
                } else {
                    drawPath(
                        path = path,
                        color = wireColor.copy(alpha = 0.82f),
                        style = Stroke(width = 2.2f * zoom)
                    )
                }
            }

            // D. Draw ComfyUI Node Blocks
            for (node in preview.nodes) {
                val pos = nodePositions[node.id] ?: Offset(node.posX, node.posY)
                val nodeScreenX = panX + pos.x * zoom
                val nodeScreenY = panY + pos.y * zoom
                val nodeScreenW = node.width * zoom

                val minRequiredH = 30f + 8f + maxOf(node.inputSlots.size, node.outputSlots.size).coerceAtMost(10) * 20f + 40f
                val effH = maxOf(node.height, minRequiredH)
                val nodeScreenH = effH * zoom

                // Viewport Culling
                if (nodeScreenX + nodeScreenW < -20 || nodeScreenX > canvasW + 20 ||
                    nodeScreenY + nodeScreenH < -20 || nodeScreenY > canvasH + 20) continue

                val isSelected = selectedNodeId == node.id
                val isPos = mapping.positivePromptNodeId == node.id
                val isNeg = mapping.negativePromptNodeId == node.id
                val isLat = mapping.emptyLatentNodeId == node.id
                val isSeed = mapping.seedNodeId == node.id
                val isImg = mapping.loadImageNodeId == node.id
                val isMask = mapping.loadImageMaskNodeId == node.id
                val isOut = mapping.outputNodeId == node.id

                val roleColor = when {
                    isPos -> SuccessGreen
                    isNeg -> AccentRed
                    isLat -> Color(0xFF00BCD4)
                    isSeed -> Color(0xFFFFB300)
                    isImg -> Color(0xFF2196F3)
                    isMask -> Color(0xFF9C27B0)
                    isOut -> Color(0xFFFF9800)
                    else -> null
                }

                val baseHeaderColor = getComfyNodeHeaderColor(node.type, node.title)
                val headerBgColor = roleColor ?: baseHeaderColor
                val borderColor = when {
                    isSelected -> Color(0xFFFFD54F) // ComfyUI signature yellow selection outline
                    roleColor != null -> roleColor
                    else -> Color(0xFF383838)
                }
                val borderWidth = when {
                    isSelected -> 2.8f * zoom
                    roleColor != null -> 2.0f * zoom
                    else -> 1.0f * zoom
                }

                val cornerRadius = CornerRadius(8f * zoom, 8f * zoom)
                val headerH = 30f * zoom

                // 1. Node Body Background
                drawRoundRect(
                    color = Color(0xFF242424),
                    topLeft = Offset(nodeScreenX, nodeScreenY),
                    size = Size(nodeScreenW, nodeScreenH),
                    cornerRadius = cornerRadius
                )

                // 2. Node Header Bar
                drawRoundRect(
                    color = headerBgColor,
                    topLeft = Offset(nodeScreenX, nodeScreenY),
                    size = Size(nodeScreenW, headerH),
                    cornerRadius = cornerRadius
                )
                // Fill lower corners of header bar so it seamlessly connects to body
                drawRect(
                    color = headerBgColor,
                    topLeft = Offset(nodeScreenX, nodeScreenY + headerH - (5f * zoom)),
                    size = Size(nodeScreenW, 5f * zoom)
                )
                // Header divider line
                drawLine(
                    color = Color(0xFF171717),
                    start = Offset(nodeScreenX, nodeScreenY + headerH),
                    end = Offset(nodeScreenX + nodeScreenW, nodeScreenY + headerH),
                    strokeWidth = 1f * zoom
                )

                // 3. Selection Glow and Node Border
                if (isSelected) {
                    drawRoundRect(
                        color = Color(0x44FFD54F),
                        topLeft = Offset(nodeScreenX - 2f * zoom, nodeScreenY - 2f * zoom),
                        size = Size(nodeScreenW + 4f * zoom, nodeScreenH + 4f * zoom),
                        cornerRadius = CornerRadius(10f * zoom, 10f * zoom),
                        style = Stroke(width = 3.5f * zoom)
                    )
                }
                drawRoundRect(
                    color = borderColor,
                    topLeft = Offset(nodeScreenX, nodeScreenY),
                    size = Size(nodeScreenW, nodeScreenH),
                    cornerRadius = cornerRadius,
                    style = Stroke(width = borderWidth.coerceAtLeast(1f))
                )

                // 4. Header Collapse / Status Dot
                drawCircle(
                    color = Color(0xFFAAAAAA),
                    radius = (3.5f * zoom).coerceIn(2f, 5f),
                    center = Offset(nodeScreenX + 13f * zoom, nodeScreenY + headerH / 2f)
                )

                // 5. Header ID Pill Badge (on right side)
                drawRoundRect(
                    color = Color(0x66000000),
                    topLeft = Offset(nodeScreenX + nodeScreenW - (34f * zoom), nodeScreenY + 6f * zoom),
                    size = Size(28f * zoom, headerH - 12f * zoom),
                    cornerRadius = CornerRadius(3f * zoom, 3f * zoom)
                )

                // 6. Socket Pins (Inputs on left edge)
                for (slot in node.inputSlots.take(12)) {
                    val pinY = nodeScreenY + (48f + slot.slotIndex * 20f) * zoom
                    val pinColor = getComfyWireColor(slot.type)
                    val isConnected = preview.wires.any { it.toNodeId == node.id && it.toSlotIndex == slot.slotIndex }

                    if (isConnected) {
                        drawCircle(
                            color = pinColor,
                            radius = (4.5f * zoom).coerceIn(2.5f, 6.5f),
                            center = Offset(nodeScreenX, pinY)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = (1.5f * zoom).coerceIn(1.0f, 2.2f),
                            center = Offset(nodeScreenX, pinY)
                        )
                    } else {
                        drawCircle(
                            color = Color(0xFF242424),
                            radius = (4.5f * zoom).coerceIn(2.5f, 6.5f),
                            center = Offset(nodeScreenX, pinY)
                        )
                        drawCircle(
                            color = pinColor,
                            radius = (4.5f * zoom).coerceIn(2.5f, 6.5f),
                            center = Offset(nodeScreenX, pinY),
                            style = Stroke(width = 1.8f * zoom)
                        )
                    }
                }

                // 7. Socket Pins (Outputs on right edge)
                for (slot in node.outputSlots.take(12)) {
                    val pinY = nodeScreenY + (48f + slot.slotIndex * 20f) * zoom
                    val pinColor = getComfyWireColor(slot.type)
                    val isConnected = preview.wires.any { it.fromNodeId == node.id && it.fromSlotIndex == slot.slotIndex }

                    if (isConnected) {
                        drawCircle(
                            color = pinColor,
                            radius = (4.5f * zoom).coerceIn(2.5f, 6.5f),
                            center = Offset(nodeScreenX + nodeScreenW, pinY)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = (1.5f * zoom).coerceIn(1.0f, 2.2f),
                            center = Offset(nodeScreenX + nodeScreenW, pinY)
                        )
                    } else {
                        drawCircle(
                            color = Color(0xFF242424),
                            radius = (4.5f * zoom).coerceIn(2.5f, 6.5f),
                            center = Offset(nodeScreenX + nodeScreenW, pinY)
                        )
                        drawCircle(
                            color = pinColor,
                            radius = (4.5f * zoom).coerceIn(2.5f, 6.5f),
                            center = Offset(nodeScreenX + nodeScreenW, pinY),
                            style = Stroke(width = 1.8f * zoom)
                        )
                    }
                }

                // 8. Render Crisp ComfyUI Typography & Widgets onto Canvas
                if (zoom > 0.28f) {
                    drawIntoCanvas { canvas ->
                        val nativeCanvas = canvas.nativeCanvas

                        // Header Title Text
                        textPaint.color = android.graphics.Color.WHITE
                        textPaint.textSize = (11.5f * zoom).coerceIn(9f, 20f)
                        val titleMaxLen = ((nodeScreenW - 55f * zoom) / (6.8f * zoom)).toInt().coerceIn(6, 32)
                        val truncatedTitle = if (node.title.length > titleMaxLen) node.title.take(titleMaxLen - 1) + "…" else node.title
                        nativeCanvas.drawText(
                            truncatedTitle,
                            nodeScreenX + 22f * zoom,
                            nodeScreenY + 19.5f * zoom,
                            textPaint
                        )

                        // Header ID Text
                        idPaint.color = android.graphics.Color.parseColor("#B0B0BC")
                        idPaint.textSize = (9f * zoom).coerceIn(7f, 15f)
                        idPaint.textAlign = android.graphics.Paint.Align.CENTER
                        nativeCanvas.drawText(
                            "#${node.id}",
                            nodeScreenX + nodeScreenW - 20f * zoom,
                            nodeScreenY + 19f * zoom,
                            idPaint
                        )

                        // Slot Names (Inputs & Outputs)
                        if (zoom > 0.38f) {
                            slotPaint.textSize = (9.0f * zoom).coerceIn(7f, 14f)

                            // Inputs (Left Aligned)
                            slotPaint.textAlign = android.graphics.Paint.Align.LEFT
                            slotPaint.color = android.graphics.Color.parseColor("#C4C4CF")
                            for (slot in node.inputSlots.take(12)) {
                                val pinY = nodeScreenY + (48f + slot.slotIndex * 20f + 3.5f) * zoom
                                val slotLabel = if (slot.name.length > 14) slot.name.take(13) + "…" else slot.name
                                nativeCanvas.drawText(
                                    slotLabel,
                                    nodeScreenX + 11f * zoom,
                                    pinY,
                                    slotPaint
                                )
                            }

                            // Outputs (Right Aligned)
                            slotPaint.textAlign = android.graphics.Paint.Align.RIGHT
                            for (slot in node.outputSlots.take(12)) {
                                val pinY = nodeScreenY + (48f + slot.slotIndex * 20f + 3.5f) * zoom
                                val slotLabel = if (slot.name.length > 14) slot.name.take(13) + "…" else slot.name
                                nativeCanvas.drawText(
                                    slotLabel,
                                    nodeScreenX + nodeScreenW - 11f * zoom,
                                    pinY,
                                    slotPaint
                                )
                            }
                        }

                        // Widgets & Parameter Insets
                        if (zoom > 0.45f) {
                            val slotCount = maxOf(node.inputSlots.size, node.outputSlots.size).coerceAtMost(10)
                            var widgetY = nodeScreenY + (48f + slotCount * 20f + 4f) * zoom

                            // Prompt text area widget preview
                            val promptText = node.inputs["text"] ?: node.inputs["value"]
                            if (!promptText.isNullOrBlank() && widgetY + 28f * zoom <= nodeScreenY + nodeScreenH - 4f * zoom) {
                                widgetBoxPaint.color = android.graphics.Color.parseColor("#171717")
                                widgetBorderPaint.color = android.graphics.Color.parseColor("#303030")
                                widgetBorderPaint.strokeWidth = 1f * zoom
                                val boxRect = android.graphics.RectF(
                                    nodeScreenX + 10f * zoom,
                                    widgetY,
                                    nodeScreenX + nodeScreenW - 10f * zoom,
                                    (widgetY + 34f * zoom).coerceAtMost(nodeScreenY + nodeScreenH - 6f * zoom)
                                )
                                nativeCanvas.drawRoundRect(boxRect, 4f * zoom, 4f * zoom, widgetBoxPaint)
                                nativeCanvas.drawRoundRect(boxRect, 4f * zoom, 4f * zoom, widgetBorderPaint)

                                widgetTextPaint.color = android.graphics.Color.parseColor("#E0E0E0")
                                widgetTextPaint.textSize = (8.5f * zoom).coerceIn(6.5f, 13f)
                                widgetTextPaint.textAlign = android.graphics.Paint.Align.LEFT
                                val maxChars = ((nodeScreenW - 24f * zoom) / (5.0f * zoom)).toInt().coerceIn(8, 40)
                                val line1 = if (promptText.length > maxChars) promptText.take(maxChars - 1) + "…" else promptText
                                nativeCanvas.drawText(
                                    line1,
                                    nodeScreenX + 14f * zoom,
                                    widgetY + 14f * zoom,
                                    widgetTextPaint
                                )
                                widgetY += 38f * zoom
                            }

                            // Key parameter widgets (seed, steps, cfg, sampler, ckpt)
                            val keyParams = node.inputs.filter { (k, _) ->
                                k in listOf("seed", "steps", "cfg", "sampler_name", "scheduler", "ckpt_name", "model_name", "width", "height")
                            }.entries.take(3)

                            for ((k, v) in keyParams) {
                                if (widgetY + 18f * zoom > nodeScreenY + nodeScreenH - 4f * zoom) break

                                widgetBoxPaint.color = android.graphics.Color.parseColor("#191919")
                                widgetBorderPaint.color = android.graphics.Color.parseColor("#2F2F2F")
                                widgetBorderPaint.strokeWidth = 1f * zoom
                                val boxRect = android.graphics.RectF(
                                    nodeScreenX + 10f * zoom,
                                    widgetY,
                                    nodeScreenX + nodeScreenW - 10f * zoom,
                                    widgetY + 16f * zoom
                                )
                                nativeCanvas.drawRoundRect(boxRect, 3f * zoom, 3f * zoom, widgetBoxPaint)
                                nativeCanvas.drawRoundRect(boxRect, 3f * zoom, 3f * zoom, widgetBorderPaint)

                                // Parameter label
                                widgetLabelPaint.color = android.graphics.Color.parseColor("#82828F")
                                widgetLabelPaint.textSize = (8f * zoom).coerceIn(6f, 12f)
                                widgetLabelPaint.textAlign = android.graphics.Paint.Align.LEFT
                                nativeCanvas.drawText(k, nodeScreenX + 14f * zoom, widgetY + 11.5f * zoom, widgetLabelPaint)

                                // Parameter value
                                widgetTextPaint.color = android.graphics.Color.parseColor("#EEEEEE")
                                widgetTextPaint.textSize = (8.5f * zoom).coerceIn(6.5f, 13f)
                                widgetTextPaint.textAlign = android.graphics.Paint.Align.RIGHT
                                val valStr = if (v.length > 18) v.take(17) + "…" else v
                                nativeCanvas.drawText(valStr, nodeScreenX + nodeScreenW - 14f * zoom, widgetY + 11.5f * zoom, widgetTextPaint)

                                widgetY += 19f * zoom
                            }

                            // Mapped Role Badge
                            if (roleColor != null) {
                                val roleLabel = when {
                                    isPos -> "★ POSITIVE PROMPT"
                                    isNeg -> "★ NEGATIVE PROMPT"
                                    isLat -> "★ EMPTY LATENT"
                                    isSeed -> "★ SEED"
                                    isImg -> "★ LOAD IMAGE"
                                    isMask -> "★ LOAD MASK"
                                    isOut -> "★ OUTPUT"
                                    else -> ""
                                }
                                rolePaint.color = android.graphics.Color.argb(
                                    (roleColor.alpha * 255).toInt(),
                                    (roleColor.red * 255).toInt(),
                                    (roleColor.green * 255).toInt(),
                                    (roleColor.blue * 255).toInt()
                                )
                                rolePaint.textSize = (9f * zoom).coerceIn(7f, 15f)
                                rolePaint.textAlign = android.graphics.Paint.Align.LEFT
                                nativeCanvas.drawText(
                                    roleLabel,
                                    nodeScreenX + 12f * zoom,
                                    nodeScreenY + nodeScreenH - 6f * zoom,
                                    rolePaint
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Real-time Mini-Map HUD (Bottom-Right)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = if (selectedNodeId != null) 92.dp else 10.dp)
                .size(115.dp, 80.dp),
            color = Color(0xEE181818),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF383838))
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                val mmW = size.width
                val mmH = size.height

                val mapScaleX = mmW / graphW
                val mapScaleY = mmH / graphH
                val mapScale = minOf(mapScaleX, mapScaleY)

                val offsetX = (mmW - graphW * mapScale) / 2f
                val offsetY = (mmH - graphH * mapScale) / 2f

                // Draw mini node blocks
                for (n in preview.nodes) {
                    val pos = nodePositions[n.id] ?: Offset(n.posX, n.posY)
                    val nx = offsetX + (pos.x - minX) * mapScale
                    val ny = offsetY + (pos.y - minY) * mapScale
                    val nw = (n.width * mapScale).coerceAtLeast(3f)
                    val nh = (n.height * mapScale).coerceAtLeast(2.5f)

                    val nColor = if (n.id == selectedNodeId) Color(0xFFFFD54F) else getComfyNodeHeaderColor(n.type, n.title)
                    drawRect(
                        color = nColor.copy(alpha = 0.85f),
                        topLeft = Offset(nx, ny),
                        size = Size(nw, nh)
                    )
                }

                // Draw camera viewport box
                val viewGraphLeft = (-panX) / zoom
                val viewGraphTop = (-panY) / zoom
                val viewGraphW = widthPx / zoom
                val viewGraphH = heightPx / zoom

                val camX = offsetX + (viewGraphLeft - minX) * mapScale
                val camY = offsetY + (viewGraphTop - minY) * mapScale
                val camW = (viewGraphW * mapScale).coerceAtMost(mmW)
                val camH = (viewGraphH * mapScale).coerceAtMost(mmH)

                drawRect(
                    color = Color(0xFF00D4FF),
                    topLeft = Offset(camX, camY),
                    size = Size(camW, camH),
                    style = Stroke(width = 1.5f)
                )
            }
        }

        // 3. Floating Toolbar Overlay (Top-Right)
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            color = Color(0xEE1E1E24),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF383844))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                IconButton(
                    onClick = { zoom = (zoom * 1.25f).coerceAtMost(2.8f) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Text(
                    text = "${(zoom * 100).toInt()}%",
                    fontSize = 10.sp,
                    color = Color.LightGray,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                IconButton(
                    onClick = { zoom = (zoom * 0.8f).coerceAtLeast(0.10f) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = Color.White, modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = { resetView() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Fit View", tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                }
                if (onToggleFullscreen != null) {
                    IconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isFullscreen) "Exit Fullscreen" else "Fullscreen",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // 4. ComfyUI Status Tag (Top-Left)
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
            color = Color(0xBB181820),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, Color(0xFF303038))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4ADE80))
                )
                Text(
                    "LiteGraph • ${preview.nodes.size} nodes • ${preview.wires.size} wires",
                    fontSize = 9.sp,
                    color = Color(0xFFB0B0C0)
                )
            }
        }

        // 5. Interactive Floating HUD (Bottom of Canvas when Node Selected)
        val selectedNode = remember(selectedNodeId, preview.nodes) {
            preview.nodes.firstOrNull { it.id == selectedNodeId }
        }
        if (selectedNode != null) {
            val isPos = mapping.positivePromptNodeId == selectedNode.id
            val isNeg = mapping.negativePromptNodeId == selectedNode.id
            val isLat = mapping.emptyLatentNodeId == selectedNode.id
            val isSeed = mapping.seedNodeId == selectedNode.id
            val isImg = mapping.loadImageNodeId == selectedNode.id
            val isMask = mapping.loadImageMaskNodeId == selectedNode.id
            val isOut = mapping.outputNodeId == selectedNode.id

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(8.dp),
                color = Color(0xF21C1C24),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.8f)),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
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
                                color = Color.Black,
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, Color(0xFFFFD54F))
                            ) {
                                Text(
                                    "#${selectedNode.id}",
                                    color = Color(0xFFFFD54F),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                            Text(
                                text = selectedNode.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "(${selectedNode.type})",
                                fontSize = 10.sp,
                                color = AccentGray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { selectedNodeId = null },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = LightGray, modifier = Modifier.size(14.dp))
                        }
                    }

                    // Role Mapping Action Chips Row (Ordered: input, output, pos, neg, latent, image, mask, seed)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val exposedForNode = mapping.customInputs.filter { it.nodeId == selectedNode.id }
                        val hasExposed = exposedForNode.isNotEmpty()
                        RoleChip(
                            label = if (hasExposed) "+ Input (${exposedForNode.size})" else "+ Input",
                            isAssigned = hasExposed,
                            activeColor = Color(0xFF00E5FF)
                        ) {
                            if (hasExposed) {
                                onMappingChange(mapping.copy(customInputs = mapping.customInputs.filterNot { it.nodeId == selectedNode.id }))
                            }
                        }
                        RoleChip(label = "+ Output", isAssigned = isOut, activeColor = Color(0xFFFF9800)) {
                            onMappingChange(mapping.copy(outputNodeId = if (isOut) null else selectedNode.id))
                        }
                        RoleChip(label = "+ Pos", isAssigned = isPos, activeColor = SuccessGreen) {
                            onMappingChange(mapping.copy(positivePromptNodeId = if (isPos) null else selectedNode.id))
                        }
                        RoleChip(label = "+ Neg", isAssigned = isNeg, activeColor = AccentRed) {
                            onMappingChange(mapping.copy(negativePromptNodeId = if (isNeg) null else selectedNode.id))
                        }
                        RoleChip(label = "+ Latent", isAssigned = isLat, activeColor = Color(0xFF00BCD4)) {
                            onMappingChange(mapping.copy(emptyLatentNodeId = if (isLat) null else selectedNode.id))
                        }
                        RoleChip(label = "+ Image", isAssigned = isImg, activeColor = Color(0xFF2196F3)) {
                            onMappingChange(mapping.copy(loadImageNodeId = if (isImg) null else selectedNode.id))
                        }
                        RoleChip(label = "+ Mask", isAssigned = isMask, activeColor = Color(0xFF9C27B0)) {
                            onMappingChange(mapping.copy(loadImageMaskNodeId = if (isMask) null else selectedNode.id))
                        }
                        RoleChip(label = "+ Seed", isAssigned = isSeed, activeColor = Color(0xFFFFB300)) {
                            onMappingChange(mapping.copy(seedNodeId = if (isSeed) null else selectedNode.id))
                        }
                        if (isPos || isNeg || isLat || isSeed || isImg || isMask || isOut || hasExposed) {
                            Surface(
                                color = Color.DarkGray,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.clickable {
                                    onMappingChange(
                                        mapping.copy(
                                            positivePromptNodeId = if (isPos) null else mapping.positivePromptNodeId,
                                            negativePromptNodeId = if (isNeg) null else mapping.negativePromptNodeId,
                                            emptyLatentNodeId = if (isLat) null else mapping.emptyLatentNodeId,
                                            seedNodeId = if (isSeed) null else mapping.seedNodeId,
                                            loadImageNodeId = if (isImg) null else mapping.loadImageNodeId,
                                            loadImageMaskNodeId = if (isMask) null else mapping.loadImageMaskNodeId,
                                            outputNodeId = if (isOut) null else mapping.outputNodeId,
                                            customInputs = mapping.customInputs.filterNot { it.nodeId == selectedNode.id }
                                        )
                                    )
                                }
                            ) {
                                Text(
                                    "Clear",
                                    fontSize = 10.sp,
                                    color = Color.LightGray,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class ComfyJsBridge(
    private val onNodeSelected: (id: String, title: String, type: String) -> Unit,
    private val onNodeDeselected: () -> Unit,
    private val onGraphLoaded: () -> Unit,
    private val onNodeSelectedWithWidgets: ((id: String, title: String, type: String, widgets: List<NodeWidgetInfo>) -> Unit)? = null
) {
    @JavascriptInterface
    fun onNodeSelected(id: String, title: String, type: String) {
        Handler(Looper.getMainLooper()).post {
            onNodeSelected.invoke(id, title, type)
        }
    }

    @JavascriptInterface
    fun onNodeSelectedWithWidgets(id: String, title: String, type: String, widgetsJson: String) {
        Handler(Looper.getMainLooper()).post {
            val widgetList: List<NodeWidgetInfo> = try {
                val listType = object : com.google.gson.reflect.TypeToken<List<NodeWidgetInfo>>() {}.type
                com.google.gson.Gson().fromJson(widgetsJson, listType) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            if (onNodeSelectedWithWidgets != null) {
                onNodeSelectedWithWidgets.invoke(id, title, type, widgetList)
            } else {
                onNodeSelected.invoke(id, title, type)
            }
        }
    }

    @JavascriptInterface
    fun onNodeDeselected() {
        Handler(Looper.getMainLooper()).post {
            onNodeDeselected.invoke()
        }
    }

    @JavascriptInterface
    fun onGraphLoaded() {
        Handler(Looper.getMainLooper()).post {
            onGraphLoaded.invoke()
        }
    }
}

data class WebSelectedNode(
    val id: String,
    val title: String,
    val type: String,
    val widgets: List<NodeWidgetInfo> = emptyList()
)

@Composable
fun ComfyWebViewPreview(
    preview: ComfyClient.WorkflowPreviewData,
    serverUrl: String,
    mapping: WorkflowNodeMapping,
    onMappingChange: (WorkflowNodeMapping) -> Unit,
    isFullscreen: Boolean = false,
    onToggleFullscreen: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedNode by remember { mutableStateOf<WebSelectedNode?>(null) }
    var showExposeInputDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isConnectionError by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var reloadTrigger by remember { mutableIntStateOf(0) }
    // Critical guard: ensures workflow is injected only ONCE per reload, preventing infinite reload loops
    var hasInjectedWorkflow by remember(reloadTrigger) { mutableStateOf(false) }

    val formattedServerUrl = remember(serverUrl) {
        if (serverUrl.isBlank()) "" else UrlValidator.normalizeUrl(serverUrl)
    }

    fun buildMappingJson(map: WorkflowNodeMapping): String {
        return com.google.gson.Gson().toJson(
            mapOf(
                "pos" to map.positivePromptNodeId,
                "neg" to map.negativePromptNodeId,
                "latent" to map.emptyLatentNodeId,
                "seed" to map.seedNodeId,
                "image" to map.loadImageNodeId,
                "mask" to map.loadImageMaskNodeId,
                "output" to map.outputNodeId
            )
        )
    }

    // Push mapping updates to the webview to update node highlight colors on the LiteGraph canvas
    LaunchedEffect(mapping, webViewInstance) {
        val mappingJson = buildMappingJson(mapping)
        webViewInstance?.evaluateJavascript("if (window.highlightMappedNodes) { window.highlightMappedNodes($mappingJson); }", null)
    }

    fun injectWorkflow(wv: WebView) {
        val escapedJson = com.google.gson.Gson().toJson(preview.rawJson)
        val initialMappingJson = buildMappingJson(mapping)
        val js = """
            (function() {
                // Guard: do not re-run if this workflow is already injected
                if (window._comfyportWorkflowLoaded) {
                    console.log("ComfyPort: Workflow already loaded, skipping re-injection.");
                    return;
                }
                if (window._comfyportInjecting) return;
                window._comfyportInjecting = true;

                // 1. Permanent aggressive CSS to hide ALL ComfyUI UI chrome and pin canvas
                var styleId = 'comfyport-clean-preview-style';
                var existingStyle = document.getElementById(styleId);
                if (!existingStyle) {
                    var style = document.createElement('style');
                    style.id = styleId;
                    style.textContent = `
                        /* Completely hide ComfyUI headers, menus, docks, sidebars, buttons, dialogs, toasts */
                        header, nav, aside, footer,
                        .comfy-menu, #comfy-menu, #comfy-floating-menu,
                        .comfyui-menu, .comfyui-body-top, .comfyui-body-bottom,
                        .comfyui-body-left, .comfyui-body-right,
                        .side-tool-bar-container, .side-bar-container, .side-bar-panel,
                        .side-bar, .side-bar-button, .dock-container,
                        .bottom-panel, .status-bar, .comfy-modal,
                        .comfyui-queue-button, .p-sidebar, .p-menubar,
                        .p-dialog, .p-toast, .p-tooltip, .p-splitter-gutter,
                        .litecontextmenu, .litesearchbox,
                        [class*="sidebar"], [class*="side-bar"], [class*="menu-bar"],
                        [class*="top-bar"], [class*="bottom-bar"], [class*="dock"],
                        [class*="workspace"], [class*="floating-menu"] {
                            display: none !important;
                            opacity: 0 !important;
                            visibility: hidden !important;
                            pointer-events: none !important;
                            width: 0px !important;
                            height: 0px !important;
                            max-width: 0px !important;
                            max-height: 0px !important;
                            overflow: hidden !important;
                            z-index: -9999 !important;
                        }

                        html, body {
                            overflow: hidden !important;
                            margin: 0px !important;
                            padding: 0px !important;
                            width: 100vw !important;
                            height: 100vh !important;
                            background-color: #202020 !important;
                            touch-action: none !important;
                            -webkit-user-select: none !important;
                            user-select: none !important;
                        }

                        #graph-canvas, canvas#graph-canvas, .graph-canvas {
                            position: fixed !important;
                            top: 0px !important;
                            left: 0px !important;
                            width: 100vw !important;
                            height: 100vh !important;
                            z-index: 99999 !important;
                            touch-action: none !important;
                            pointer-events: auto !important;
                        }
                    `;
                    (document.head || document.documentElement).appendChild(style);
                }

                // 2. Prevent right-click / context menus
                window.addEventListener('contextmenu', function(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    return false;
                }, true);

                // 3. Canvas isolation & resizing helper
                window.cleanComfyUIChrome = function() {
                    var cvEl = document.getElementById('graph-canvas') || (window.app && window.app.canvasEl);
                    if (cvEl) {
                        try {
                            if (document.body.children.length > 1 || document.body.firstElementChild !== cvEl) {
                                document.body.replaceChildren(cvEl);
                            }
                        } catch(e) {}
                        document.body.style.cssText = 'margin: 0px !important; padding: 0px !important; overflow: hidden !important; width: 100vw !important; height: 100vh !important; background-color: #202020 !important;';
                        cvEl.style.cssText = 'position: absolute !important; top: 0px !important; left: 0px !important; width: 100vw !important; height: 100vh !important; z-index: 1 !important; touch-action: none !important; pointer-events: auto !important;';
                    }
                };

                // 4. MutationObserver to continuously eliminate any dynamically mounted chrome
                if (!window._comfyportObserver) {
                    window._comfyportObserver = new MutationObserver(function() {
                        window.cleanComfyUIChrome();
                    });
                    window._comfyportObserver.observe(document.body || document.documentElement, { childList: true, subtree: true });
                }

                // 5. Setup global highlight function for mapped roles
                window.highlightMappedNodes = function(m) {
                    if (!window.app || !window.app.graph || !window.app.graph._nodes) return;
                    var nodes = window.app.graph._nodes;
                    var roleColors = {
                        pos: { color: "#1b5e20", boxcolor: "#4caf50" },
                        neg: { color: "#b71c1c", boxcolor: "#f44336" },
                        latent: { color: "#006064", boxcolor: "#00bcd4" },
                        seed: { color: "#e65100", boxcolor: "#ff9800" },
                        image: { color: "#0d47a1", boxcolor: "#2196f3" },
                        mask: { color: "#4a148c", boxcolor: "#9c27b0" },
                        output: { color: "#bf360c", boxcolor: "#ff5722" }
                    };
                    var nodeRoles = {};
                    if (m && m.pos) nodeRoles[String(m.pos)] = 'pos';
                    if (m && m.neg) nodeRoles[String(m.neg)] = 'neg';
                    if (m && m.latent) nodeRoles[String(m.latent)] = 'latent';
                    if (m && m.seed) nodeRoles[String(m.seed)] = 'seed';
                    if (m && m.image) nodeRoles[String(m.image)] = 'image';
                    if (m && m.mask) nodeRoles[String(m.mask)] = 'mask';
                    if (m && m.output) nodeRoles[String(m.output)] = 'output';

                    nodes.forEach(function(n) {
                        var idStr = String(n.id);
                        if (n._origColor === undefined) {
                            n._origColor = n.color || null;
                            n._origBoxColor = n.boxcolor || null;
                        }
                        if (nodeRoles[idStr]) {
                            var r = nodeRoles[idStr];
                            n.color = roleColors[r].color;
                            n.boxcolor = roleColors[r].boxcolor;
                        } else {
                            n.color = n._origColor;
                            n.boxcolor = n._origBoxColor;
                        }
                    });
                    if (window.app.canvas) {
                        window.app.canvas.setDirty(true, true);
                    }
                };

                // 6. Setup global zoom helper for +/- buttons
                window.zoomCanvas = function(factor) {
                    if (window.app && window.app.canvas && window.app.canvas.ds) {
                        var cv = window.app.canvas;
                        var center = [window.innerWidth / 2, window.innerHeight / 2];
                        cv.ds.changeScale(cv.ds.scale * factor, center);
                        cv.setDirty(true, true);
                        cv.draw(true, true);
                    }
                };

                // 7. Polling to inject workflow and set up LiteGraph listeners
                var attempts = 0;
                async function checkAndInject() {
                    attempts++;
                    if (window.app && window.app.graph) {
                        try {
                            window.cleanComfyUIChrome();
                            if (window.LiteGraph) {
                                window.LiteGraph.closeAllContextMenus = function() {};
                            }
                            if (window.app.canvas) {
                                var cv = window.app.canvas;
                                cv.showContextMenu = function() {};
                                cv.allow_searchbox = false;
                                cv.onDoubleClick = function() {};
                                cv.render_connections = true;
                                cv.render_curved_connections = true;
                                cv.highquality = true;

                                // Intercept node selection for our frontend selector
                                var origOnNodeSelected = cv.onNodeSelected;
                                cv.onNodeSelected = function(node) {
                                    if (origOnNodeSelected) {
                                        try { origOnNodeSelected.apply(this, arguments); } catch(e) {}
                                    }
                                    if (node && window.AndroidBridge) {
                                        var widgetsData = [];
                                        if (node.widgets && Array.isArray(node.widgets)) {
                                            for (var i = 0; i < node.widgets.length; i++) {
                                                var w = node.widgets[i];
                                                if (w && w.name) {
                                                    var wType = String(w.type || 'string').toLowerCase();
                                                    var wVal = (w.value !== undefined && w.value !== null) ? String(w.value) : '';
                                                    var opts = w.options || {};
                                                    var minVal = (opts.min !== undefined && opts.min !== null) ? Number(opts.min) : null;
                                                    var maxVal = (opts.max !== undefined && opts.max !== null) ? Number(opts.max) : null;
                                                    var stepVal = (opts.step !== undefined && opts.step !== null) ? Number(opts.step) : null;
                                                    var comboValues = [];
                                                    if (Array.isArray(opts.values)) {
                                                        comboValues = opts.values.map(function(v) { return String(v); });
                                                    }
                                                    widgetsData.push({
                                                        name: String(w.name),
                                                        type: wType,
                                                        value: wVal,
                                                        min: isNaN(minVal) ? null : minVal,
                                                        max: isNaN(maxVal) ? null : maxVal,
                                                        step: isNaN(stepVal) ? null : stepVal,
                                                        options: comboValues
                                                    });
                                                }
                                            }
                                        }
                                        if (typeof window.AndroidBridge.onNodeSelectedWithWidgets === 'function') {
                                            window.AndroidBridge.onNodeSelectedWithWidgets(
                                                String(node.id != null ? node.id : ''),
                                                String(node.title || node.type || ''),
                                                String(node.type || ''),
                                                JSON.stringify(widgetsData)
                                            );
                                        } else {
                                            window.AndroidBridge.onNodeSelected(
                                                String(node.id != null ? node.id : ''),
                                                String(node.title || node.type || ''),
                                                String(node.type || '')
                                            );
                                        }
                                    }
                                };

                                // Intercept empty-canvas tap to deselect
                                var origOnSelectionChange = cv.onSelectionChange;
                                cv.onSelectionChange = function(nodes) {
                                    if (origOnSelectionChange) {
                                        try { origOnSelectionChange.apply(this, arguments); } catch(e) {}
                                    }
                                    var count = nodes ? (nodes.length || Object.keys(nodes).length) : 0;
                                    if (count === 0 && window.AndroidBridge) {
                                        window.AndroidBridge.onNodeDeselected();
                                    }
                                };
                            }

                            var rawData = $escapedJson;
                            if (rawData) {
                                var parsed = typeof rawData === 'string' ? JSON.parse(rawData) : rawData;

                                // Native dispatch: handles both UI graph format and API prompt format
                                if (parsed && parsed.nodes && typeof window.app.loadGraphData === 'function') {
                                    await window.app.loadGraphData(parsed);
                                } else if (typeof window.app.loadApiJson === 'function') {
                                    await window.app.loadApiJson(parsed);
                                } else if (typeof window.app.handleFile === 'function') {
                                    var file = new File([typeof rawData === 'string' ? rawData : JSON.stringify(rawData)], "workflow.json", { type: "application/json" });
                                    await window.app.handleFile(file);
                                } else if (typeof window.app.loadGraphData === 'function') {
                                    await window.app.loadGraphData(parsed);
                                }

                                setTimeout(function() {
                                    if (window.app.graph && window.app.graph._nodes) {
                                        window.app.graph._nodes.forEach(function(n) {
                                            n.removable = false;
                                            n.clonable = false;
                                            n.block_delete = true;
                                        });
                                    }
                                    window.cleanComfyUIChrome();
                                    window.highlightMappedNodes($initialMappingJson);
                                    window._comfyportWorkflowLoaded = true;
                                    window._comfyportInjecting = false;
                                    if (window.AndroidBridge) window.AndroidBridge.onGraphLoaded();
                                }, 700);
                            }
                        } catch(err) {
                            window._comfyportInjecting = false;
                            console.error("Failed to inject workflow:", err);
                        }
                    } else if (attempts < 40) {
                        setTimeout(checkAndInject, 250);
                    } else {
                        window._comfyportInjecting = false;
                    }
                }
                checkAndInject();
            })();
        """.trimIndent()
        wv.evaluateJavascript(js, null)
    }

    Box(
        modifier = modifier
            .background(Color(0xFF202020))
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp))
    ) {
        if (formattedServerUrl.isBlank() || isConnectionError) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = AccentRed,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (formattedServerUrl.isBlank()) "No Server URL Configured" else "Unable to Reach ComfyUI Webpage",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (formattedServerUrl.isBlank()) {
                        "Please configure a valid server URL in Settings to preview workflows directly in the ComfyUI web interface."
                    } else {
                        "Could not connect to $formattedServerUrl. Ensure your ComfyUI server is running and accessible from your phone on the local network."
                    },
                    fontSize = 12.sp,
                    color = AccentGray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            isConnectionError = false
                            isLoading = true
                            hasInjectedWorkflow = false
                            reloadTrigger++
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retry Connection", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        } else {
            key(reloadTrigger) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            // Essential: prevent parent Compose / Dialog from stealing touch and pan gestures
                            setOnTouchListener { v, _ ->
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                                false
                            }

                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = false
                            settings.useWideViewPort = false
                            settings.allowFileAccess = true
                            settings.allowContentAccess = true
                            settings.builtInZoomControls = false
                            settings.displayZoomControls = false
                            settings.setSupportZoom(false) // Let LiteGraph canvas handle gestures directly
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                            addJavascriptInterface(
                                com.comfyport.network.api.ComfySessionInterpreter.bridge,
                                "ComfySessionBridge"
                            )
                            addJavascriptInterface(
                                ComfyJsBridge(
                                    onNodeSelected = { id, title, type ->
                                        selectedNode = WebSelectedNode(id, title, type, emptyList())
                                    },
                                    onNodeDeselected = {
                                        selectedNode = null
                                    },
                                    onGraphLoaded = {
                                        isLoading = false
                                    },
                                    onNodeSelectedWithWidgets = { id, title, type, widgets ->
                                        selectedNode = WebSelectedNode(id, title, type, widgets)
                                    }
                                ),
                                "AndroidBridge"
                            )

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    val earlyHideCss = """
                                        (function() {
                                            var s = document.createElement('style');
                                            s.textContent = 'header, nav, aside, footer, .comfy-menu, #comfy-menu, #comfy-floating-menu, .comfyui-menu, .comfyui-body-top, .side-bar, .side-tool-bar-container, .dock-container, .bottom-panel { display: none !important; }';
                                            (document.head || document.documentElement).appendChild(s);
                                        })();
                                    """.trimIndent()
                                    view?.evaluateJavascript(earlyHideCss, null)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isConnectionError = false
                                    // Inject once and only once per page load
                                    if (!hasInjectedWorkflow) {
                                        hasInjectedWorkflow = true
                                        view?.let { injectWorkflow(it) }
                                    }
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    if (request?.isForMainFrame == true) {
                                        isConnectionError = true
                                        isLoading = false
                                    }
                                }
                            }

                            webViewInstance = this
                            com.comfyport.network.api.ComfySessionInterpreter.registerActiveWebView(this)
                            loadUrl(formattedServerUrl)
                        }
                    },
                    update = { wv ->
                        if (webViewInstance != wv) {
                            webViewInstance = wv
                            com.comfyport.network.api.ComfySessionInterpreter.registerActiveWebView(wv)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Loading Progress Overlay
            if (isLoading) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp),
                    color = Color(0xDD181820),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFF33333E))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(0xFF00E5FF))
                        Text("Loading node visualization...", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Floating Controls Bar (Top-Right): ZOOM IN, ZOOM OUT, FIT VIEW, RELOAD, FULLSCREEN
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                color = Color(0xEE1E1E24),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF383844))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    // Zoom In button
                    Surface(
                        color = Color(0xFF2A2A35),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .size(28.dp)
                            .clickable {
                                webViewInstance?.evaluateJavascript("if (window.zoomCanvas) window.zoomCanvas(1.25);", null)
                            }
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "+",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Zoom Out button
                    Surface(
                        color = Color(0xFF2A2A35),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .size(28.dp)
                            .clickable {
                                webViewInstance?.evaluateJavascript("if (window.zoomCanvas) window.zoomCanvas(0.8);", null)
                            }
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "-",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Manual Reload button
                    IconButton(
                        onClick = {
                            hasInjectedWorkflow = false
                            reloadTrigger++
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload Workflow", tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                    }

                    if (onToggleFullscreen != null) {
                        IconButton(
                            onClick = onToggleFullscreen,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (isFullscreen) "Exit Fullscreen" else "Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Server Indicator Pill (Top-Left)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                color = Color(0xBB181820),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, Color(0xFF303038))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isLoading) Color(0xFFFFB300) else Color(0xFF4ADE80))
                    )
                    Text(
                        text = formattedServerUrl.removePrefix("http://").removePrefix("https://").take(24),
                        fontSize = 9.sp,
                        color = Color(0xFFB0B0C0)
                    )
                }
            }

            // Bottom Area: Floating Node Selection & Role Mapping HUD OR Interaction Hint
            val currNode = selectedNode
            if (currNode != null) {
                val isPos = mapping.positivePromptNodeId == currNode.id
                val isNeg = mapping.negativePromptNodeId == currNode.id
                val isLat = mapping.emptyLatentNodeId == currNode.id
                val isSeed = mapping.seedNodeId == currNode.id
                val isImg = mapping.loadImageNodeId == currNode.id
                val isMask = mapping.loadImageMaskNodeId == currNode.id
                val isOut = mapping.outputNodeId == currNode.id

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(8.dp),
                    color = Color(0xF51C1C24),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.8f)),
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
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
                                    color = Color.Black,
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, Color(0xFFFFD54F))
                                ) {
                                    Text(
                                        "#${currNode.id}",
                                        color = Color(0xFFFFD54F),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                                Text(
                                    text = currNode.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "(${currNode.type})",
                                    fontSize = 10.sp,
                                    color = AccentGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { selectedNode = null },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = LightGray, modifier = Modifier.size(14.dp))
                            }
                        }

                        // Role Mapping Action Chips Row (Ordered: input, output, pos, neg, latent, image, mask, seed)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val exposedForNode = mapping.customInputs.filter { it.nodeId == currNode.id }
                            val hasExposed = exposedForNode.isNotEmpty()
                            RoleChip(
                                label = if (hasExposed) "+ Input (${exposedForNode.size})" else "+ Input",
                                isAssigned = hasExposed,
                                activeColor = Color(0xFF00E5FF)
                            ) {
                                showExposeInputDialog = true
                            }
                            RoleChip(label = "+ Output", isAssigned = isOut, activeColor = Color(0xFFFF9800)) {
                                onMappingChange(mapping.copy(outputNodeId = if (isOut) null else currNode.id))
                            }
                            RoleChip(label = "+ Pos", isAssigned = isPos, activeColor = SuccessGreen) {
                                onMappingChange(mapping.copy(positivePromptNodeId = if (isPos) null else currNode.id))
                            }
                            RoleChip(label = "+ Neg", isAssigned = isNeg, activeColor = AccentRed) {
                                onMappingChange(mapping.copy(negativePromptNodeId = if (isNeg) null else currNode.id))
                            }
                            RoleChip(label = "+ Latent", isAssigned = isLat, activeColor = Color(0xFF00BCD4)) {
                                onMappingChange(mapping.copy(emptyLatentNodeId = if (isLat) null else currNode.id))
                            }
                            RoleChip(label = "+ Image", isAssigned = isImg, activeColor = Color(0xFF2196F3)) {
                                onMappingChange(mapping.copy(loadImageNodeId = if (isImg) null else currNode.id))
                            }
                            RoleChip(label = "+ Mask", isAssigned = isMask, activeColor = Color(0xFF9C27B0)) {
                                onMappingChange(mapping.copy(loadImageMaskNodeId = if (isMask) null else currNode.id))
                            }
                            RoleChip(label = "+ Seed", isAssigned = isSeed, activeColor = Color(0xFFFFB300)) {
                                onMappingChange(mapping.copy(seedNodeId = if (isSeed) null else currNode.id))
                            }
                            if (isPos || isNeg || isLat || isSeed || isImg || isMask || isOut || hasExposed) {
                                Surface(
                                    color = Color.DarkGray,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.clickable {
                                        onMappingChange(
                                            mapping.copy(
                                                positivePromptNodeId = if (isPos) null else mapping.positivePromptNodeId,
                                                negativePromptNodeId = if (isNeg) null else mapping.negativePromptNodeId,
                                                emptyLatentNodeId = if (isLat) null else mapping.emptyLatentNodeId,
                                                seedNodeId = if (isSeed) null else mapping.seedNodeId,
                                                loadImageNodeId = if (isImg) null else mapping.loadImageNodeId,
                                                loadImageMaskNodeId = if (isMask) null else mapping.loadImageMaskNodeId,
                                                outputNodeId = if (isOut) null else mapping.outputNodeId,
                                                customInputs = mapping.customInputs.filterNot { it.nodeId == currNode.id }
                                            )
                                        )
                                    }
                                ) {
                                    Text(
                                        "Clear",
                                        fontSize = 10.sp,
                                        color = Color.LightGray,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (!isLoading) {
                // Subtle interaction guidance hint when no node is selected
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                    color = Color(0xCC181820),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF2E2E38))
                ) {
                    Text(
                        text = "Tap node to assign role • Drag to pan • Pinch or +/- to zoom",
                        fontSize = 10.sp,
                        color = Color(0xFFA0A0B0),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (showExposeInputDialog && currNode != null) {
                val availableWidgets = remember(currNode) {
                    if (currNode.widgets.isNotEmpty()) {
                        currNode.widgets
                    } else {
                        val parsed = preview.nodes.firstOrNull { it.id == currNode.id }
                        parsed?.inputs?.map { (k, v) ->
                            NodeWidgetInfo(
                                name = k,
                                type = if (v.toIntOrNull() != null) "int" else if (v.toDoubleOrNull() != null) "float" else if (v == "true" || v == "false") "boolean" else "string",
                                value = v
                            )
                        } ?: emptyList()
                    }
                }

                AlertDialog(
                    onDismissRequest = { showExposeInputDialog = false },
                    containerColor = Color(0xFF1E1E28),
                    title = {
                        Column {
                            Text(
                                "Expose Inputs for #${currNode.id}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                currNode.title.ifBlank { currNode.type },
                                color = Color(0xFF00E5FF),
                                fontSize = 12.sp
                            )
                        }
                    },
                    text = {
                        if (availableWidgets.isEmpty()) {
                            Text(
                                "No configurable parameters or widgets found on this node.",
                                color = Color(0xFFA0A0B0),
                                fontSize = 13.sp
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Selected parameters will appear as custom controls on the main generation screen:",
                                    fontSize = 11.sp,
                                    color = Color(0xFFA0A0B0)
                                )
                                availableWidgets.forEach { w ->
                                    val isExposed = mapping.customInputs.any { it.nodeId == currNode.id && it.widgetName == w.name }
                                    Surface(
                                        color = if (isExposed) Color(0xFF282838) else Color(0xFF181820),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, if (isExposed) Color(0xFF00E5FF) else Color(0xFF333340)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (isExposed) {
                                                    val updated = mapping.customInputs.filterNot { it.nodeId == currNode.id && it.widgetName == w.name }
                                                    onMappingChange(mapping.copy(customInputs = updated))
                                                } else {
                                                    val wType = when {
                                                        w.type.contains("int", ignoreCase = true) || (w.type == "number" && !w.value.contains(".")) -> "INT"
                                                        w.type.contains("float", ignoreCase = true) || w.type == "number" -> "FLOAT"
                                                        w.type.contains("bool", ignoreCase = true) || w.type == "toggle" -> "BOOLEAN"
                                                        w.type.contains("combo", ignoreCase = true) || w.options.isNotEmpty() -> "COMBO"
                                                        else -> "STRING"
                                                    }
                                                    val newInput = CustomWorkflowInput(
                                                        nodeId = currNode.id,
                                                        nodeTitle = currNode.title.ifBlank { currNode.type },
                                                        nodeType = currNode.type,
                                                        widgetName = w.name,
                                                        widgetType = wType,
                                                        label = w.name,
                                                        value = w.value,
                                                        min = w.min,
                                                        max = w.max,
                                                        step = w.step,
                                                        options = w.options
                                                    )
                                                    onMappingChange(mapping.copy(customInputs = mapping.customInputs + newInput))
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        w.name,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 13.sp
                                                    )
                                                    Surface(
                                                        color = Color.Black.copy(alpha = 0.4f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            w.type.uppercase(),
                                                            fontSize = 9.sp,
                                                            color = Color(0xFF00E5FF),
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                                if (w.value.isNotBlank()) {
                                                    Text(
                                                        "Value: ${w.value.take(40)}",
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF888899)
                                                    )
                                                }
                                            }
                                            Checkbox(
                                                checked = isExposed,
                                                onCheckedChange = null,
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = Color(0xFF00E5FF),
                                                    checkmarkColor = Color.Black
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showExposeInputDialog = false }) {
                            Text("Done", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowCanvasDialog(
    preview: ComfyClient.WorkflowPreviewData,
    serverUrl: String = "",
    isActive: Boolean = false,
    onDismiss: () -> Unit,
    onActivate: (() -> Unit)? = null,
    onApplySpecs: (() -> Unit)? = null,
    onImport: (() -> Unit)? = null,
    onSaveMapping: ((String, WorkflowNodeMapping) -> Unit)? = null
) = WorkflowPreviewDialog(preview, serverUrl, isActive, onDismiss, onActivate, onApplySpecs, onImport, onSaveMapping)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowPreviewDialog(
    preview: ComfyClient.WorkflowPreviewData,
    serverUrl: String = "",
    isActive: Boolean = false,
    onDismiss: () -> Unit,
    onActivate: (() -> Unit)? = null,
    onApplySpecs: (() -> Unit)? = null,
    onImport: (() -> Unit)? = null,
    onSaveMapping: ((String, WorkflowNodeMapping) -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var isJsonExpanded by remember { mutableStateOf(false) }

    var selectedTab by remember { mutableStateOf(0) }
    var viewMode by remember { mutableStateOf("CANVAS") } // "CANVAS" or "LIST"
    var currentMapping by remember(preview.activeMapping) { mutableStateOf(preview.activeMapping ?: WorkflowNodeMapping()) }
    var isSaved by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardGray,
        shape = RoundedCornerShape(16.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxSize()
            .padding(6.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = preview.label.ifBlank { "Workflow Preview" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isActive) {
                            Surface(color = Color.White, shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    "ACTIVE",
                                    color = Color.Black,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        if (currentMapping.isCustomized) {
                            Surface(color = Color(0xFF673AB7), shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    "MAPPED (${currentMapping.customCount})",
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
                            text = if (preview.filename.isNotBlank()) preview.filename else "${preview.nodeCount} nodes detected",
                            fontSize = 11.sp,
                            color = AccentGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = LightGray, modifier = Modifier.size(18.dp))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Navigation Tabs: NODES / ROLES / SPECS
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DarkGray,
                    contentColor = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("NODES (${preview.nodes.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("ROLES", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                if (currentMapping.isCustomized) {
                                    Surface(color = Color(0xFF673AB7), shape = CircleShape) {
                                        Text(
                                            "${currentMapping.customCount}",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("SPECS", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                // TAB 0: Node Graph Explorer & Interactive Role Assigning
                if (selectedTab == 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // View Mode Switcher: Canvas Graph vs Node List
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = DarkGray,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF33333E))
                            ) {
                                Row(modifier = Modifier.padding(2.dp)) {
                                    Surface(
                                        color = if (viewMode == "CANVAS") Color(0xFF673AB7) else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.clickable { viewMode = "CANVAS" }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                            Text(if (serverUrl.isNotBlank()) "ComfyUI Web" else "Canvas Graph", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                    Surface(
                                        color = if (viewMode == "LIST") Color(0xFF673AB7) else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.clickable { viewMode = "LIST" }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.List, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                            Text("Node List", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }

                            Text(
                                text = "${preview.wires.size} wires • ${preview.nodes.size} nodes",
                                fontSize = 10.sp,
                                color = AccentGray
                            )
                        }

                        if (viewMode == "CANVAS") {
                            if (serverUrl.isNotBlank()) {
                                ComfyWebViewPreview(
                                    preview = preview,
                                    serverUrl = serverUrl,
                                    mapping = currentMapping,
                                    onMappingChange = {
                                        currentMapping = it
                                        isSaved = false
                                    },
                                    isFullscreen = true,
                                    onToggleFullscreen = null,
                                    modifier = Modifier.fillMaxWidth().weight(1f)
                                )
                            } else {
                                ComfyNodeCanvas(
                                    preview = preview,
                                    mapping = currentMapping,
                                    onMappingChange = {
                                        currentMapping = it
                                        isSaved = false
                                    },
                                    isFullscreen = true,
                                    onToggleFullscreen = null,
                                    modifier = Modifier.fillMaxWidth().weight(1f)
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 390.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Filter Chips
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("ALL", "PROMPTS", "LATENT", "SAMPLERS", "IMAGES", "OUTPUTS").forEach { filterName ->
                                        val isSelected = selectedFilter == filterName
                                        Surface(
                                            color = if (isSelected) Color.White else DarkGray,
                                            shape = RoundedCornerShape(16.dp),
                                            border = BorderStroke(1.dp, if (isSelected) Color.White else DarkGray),
                                            modifier = Modifier.clickable { selectedFilter = filterName }
                                        ) {
                                            Text(
                                                text = filterName,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color.Black else LightGray,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }

                                // Search Field
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Search by ID, title, or type...", fontSize = 11.sp, color = AccentGray) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AccentGray, modifier = Modifier.size(16.dp)) },
                                    trailingIcon = {
                                        if (searchQuery.isNotBlank()) {
                                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = AccentGray, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.White,
                                        unfocusedBorderColor = DarkGray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )

                                val filteredNodes = remember(preview.nodes, searchQuery, selectedFilter) {
                                    preview.nodes.filter { node ->
                                        val matchesSearch = searchQuery.isBlank() ||
                                                node.id.contains(searchQuery, ignoreCase = true) ||
                                                node.title.contains(searchQuery, ignoreCase = true) ||
                                                node.type.contains(searchQuery, ignoreCase = true) ||
                                                node.details.contains(searchQuery, ignoreCase = true)

                                        val matchesFilter = when (selectedFilter) {
                                            "PROMPTS" -> node.type.contains("Text", ignoreCase = true) || node.type.contains("CLIP", ignoreCase = true) || node.type.contains("Primitive", ignoreCase = true) || node.type.contains("String", ignoreCase = true)
                                            "LATENT" -> node.type.contains("Latent", ignoreCase = true) || node.type.contains("Empty", ignoreCase = true)
                                            "SAMPLERS" -> node.type.contains("Sampler", ignoreCase = true)
                                            "IMAGES" -> node.type.contains("LoadImage", ignoreCase = true) || node.type.contains("Image", ignoreCase = true) || node.type.contains("Mask", ignoreCase = true)
                                            "OUTPUTS" -> node.type.contains("SaveImage", ignoreCase = true) || node.type.contains("PreviewImage", ignoreCase = true) || node.type.contains("Save", ignoreCase = true)
                                            else -> true
                                        }
                                        matchesSearch && matchesFilter
                                    }
                                }

                                if (filteredNodes.isEmpty()) {
                                    Text(
                                        "No nodes match the filter or search criteria.",
                                        fontSize = 12.sp,
                                        color = LightGray,
                                        modifier = Modifier.padding(vertical = 16.dp)
                                    )
                                } else {
                                    filteredNodes.forEach { node ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = DarkGray),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, AccentGray.copy(alpha = 0.25f))
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                // Header: ID + Title + Type
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
                                                            color = Color.Black,
                                                            shape = RoundedCornerShape(4.dp),
                                                            border = BorderStroke(1.dp, AccentGray.copy(alpha = 0.4f))
                                                        ) {
                                                            Text(
                                                                text = "#${node.id}",
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 11.sp,
                                                                color = Color.White,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = node.title,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 13.sp,
                                                                color = Color.White,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = node.type,
                                                                fontSize = 10.sp,
                                                                color = AccentGray,
                                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }

                                                // Details / Inputs Preview
                                                if (node.details.isNotBlank()) {
                                                    Text(
                                                        text = node.details,
                                                        fontSize = 11.sp,
                                                        color = LightGray,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                // Incoming Links
                                                if (node.incomingLinks.isNotEmpty()) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                        node.incomingLinks.take(2).forEach { link ->
                                                            Text("← $link", fontSize = 10.sp, color = AccentGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        }
                                                    }
                                                }

                                                // Outgoing Links
                                                if (node.outgoingLinks.isNotEmpty()) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                        node.outgoingLinks.take(2).forEach { link ->
                                                            Text(link, fontSize = 10.sp, color = AccentGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        }
                                                    }
                                                }

                                                // Active Role Badges
                                                val isPos = currentMapping.positivePromptNodeId == node.id
                                                val isNeg = currentMapping.negativePromptNodeId == node.id
                                                val isLat = currentMapping.emptyLatentNodeId == node.id
                                                val isSeed = currentMapping.seedNodeId == node.id
                                                val isImg = currentMapping.loadImageNodeId == node.id
                                                val isMask = currentMapping.loadImageMaskNodeId == node.id
                                                val isOut = currentMapping.outputNodeId == node.id

                                                if (isPos || isNeg || isLat || isSeed || isImg || isMask || isOut) {
                                                    Row(
                                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        if (isPos) Surface(color = SuccessGreen.copy(0.2f), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, SuccessGreen)) {
                                                            Text("★ POSITIVE PROMPT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SuccessGreen, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                                        }
                                                        if (isNeg) Surface(color = AccentRed.copy(0.2f), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, AccentRed)) {
                                                            Text("★ NEGATIVE PROMPT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AccentRed, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                                        }
                                                        if (isLat) Surface(color = Color(0xFF00BCD4).copy(0.2f), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, Color(0xFF00BCD4))) {
                                                            Text("★ EMPTY LATENT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00BCD4), modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                                        }
                                                        if (isSeed) Surface(color = Color(0xFFFFB300).copy(0.2f), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, Color(0xFFFFB300))) {
                                                            Text("★ SEED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB300), modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                                        }
                                                        if (isImg) Surface(color = Color(0xFF2196F3).copy(0.2f), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, Color(0xFF2196F3))) {
                                                            Text("★ LOAD IMAGE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2196F3), modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                                        }
                                                        if (isMask) Surface(color = Color(0xFF9C27B0).copy(0.2f), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, Color(0xFF9C27B0))) {
                                                            Text("★ LOAD MASK", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9C27B0), modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                                        }
                                                        if (isOut) Surface(color = Color(0xFFFF9800).copy(0.2f), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, Color(0xFFFF9800))) {
                                                            Text("★ OUTPUT NODE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800), modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                                        }
                                                    }
                                                }

                                                // Role Assignment Chips Row
                                                Row(
                                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    RoleChip(label = "+ Pos", isAssigned = isPos, activeColor = SuccessGreen) {
                                                        currentMapping = currentMapping.copy(positivePromptNodeId = if (isPos) null else node.id)
                                                        isSaved = false
                                                    }
                                                    RoleChip(label = "+ Neg", isAssigned = isNeg, activeColor = AccentRed) {
                                                        currentMapping = currentMapping.copy(negativePromptNodeId = if (isNeg) null else node.id)
                                                        isSaved = false
                                                    }
                                                    RoleChip(label = "+ Latent", isAssigned = isLat, activeColor = Color(0xFF00BCD4)) {
                                                        currentMapping = currentMapping.copy(emptyLatentNodeId = if (isLat) null else node.id)
                                                        isSaved = false
                                                    }
                                                    RoleChip(label = "+ Seed", isAssigned = isSeed, activeColor = Color(0xFFFFB300)) {
                                                        currentMapping = currentMapping.copy(seedNodeId = if (isSeed) null else node.id)
                                                        isSaved = false
                                                    }
                                                    RoleChip(label = "+ Image", isAssigned = isImg, activeColor = Color(0xFF2196F3)) {
                                                        currentMapping = currentMapping.copy(loadImageNodeId = if (isImg) null else node.id)
                                                        isSaved = false
                                                    }
                                                    RoleChip(label = "+ Mask", isAssigned = isMask, activeColor = Color(0xFF9C27B0)) {
                                                        currentMapping = currentMapping.copy(loadImageMaskNodeId = if (isMask) null else node.id)
                                                        isSaved = false
                                                    }
                                                    RoleChip(label = "+ Output", isAssigned = isOut, activeColor = Color(0xFFFF9800)) {
                                                        currentMapping = currentMapping.copy(outputNodeId = if (isOut) null else node.id)
                                                        isSaved = false
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

                // TAB 1: Assigned Roles Overview
                if (selectedTab == 1) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Explicitly assign roles to specific nodes in this workflow. Any unassigned role will automatically use standard Basic Mode detection.",
                            fontSize = 12.sp,
                            color = LightGray
                        )

                        RoleMappingItem(
                            title = "Positive Prompt Node",
                            roleColor = SuccessGreen,
                            assignedNode = preview.nodes.firstOrNull { it.id == currentMapping.positivePromptNodeId },
                            assignedId = currentMapping.positivePromptNodeId,
                            onClear = { currentMapping = currentMapping.copy(positivePromptNodeId = null); isSaved = false }
                        )

                        RoleMappingItem(
                            title = "Negative Prompt Node",
                            roleColor = AccentRed,
                            assignedNode = preview.nodes.firstOrNull { it.id == currentMapping.negativePromptNodeId },
                            assignedId = currentMapping.negativePromptNodeId,
                            onClear = { currentMapping = currentMapping.copy(negativePromptNodeId = null); isSaved = false }
                        )

                        RoleMappingItem(
                            title = "Empty Latent / Resolution Node",
                            roleColor = Color(0xFF00BCD4),
                            assignedNode = preview.nodes.firstOrNull { it.id == currentMapping.emptyLatentNodeId },
                            assignedId = currentMapping.emptyLatentNodeId,
                            onClear = { currentMapping = currentMapping.copy(emptyLatentNodeId = null); isSaved = false }
                        )

                        RoleMappingItem(
                            title = "Seed Node",
                            roleColor = Color(0xFFFFB300),
                            assignedNode = preview.nodes.firstOrNull { it.id == currentMapping.seedNodeId },
                            assignedId = currentMapping.seedNodeId,
                            onClear = { currentMapping = currentMapping.copy(seedNodeId = null); isSaved = false }
                        )

                        RoleMappingItem(
                            title = "Load Image Node",
                            roleColor = Color(0xFF2196F3),
                            assignedNode = preview.nodes.firstOrNull { it.id == currentMapping.loadImageNodeId },
                            assignedId = currentMapping.loadImageNodeId,
                            onClear = { currentMapping = currentMapping.copy(loadImageNodeId = null); isSaved = false }
                        )

                        RoleMappingItem(
                            title = "Load Mask Node",
                            roleColor = Color(0xFF9C27B0),
                            assignedNode = preview.nodes.firstOrNull { it.id == currentMapping.loadImageMaskNodeId },
                            assignedId = currentMapping.loadImageMaskNodeId,
                            onClear = { currentMapping = currentMapping.copy(loadImageMaskNodeId = null); isSaved = false }
                        )

                        RoleMappingItem(
                            title = "Output Node",
                            roleColor = Color(0xFFFF9800),
                            assignedNode = preview.nodes.firstOrNull { it.id == currentMapping.outputNodeId },
                            assignedId = currentMapping.outputNodeId,
                            onClear = { currentMapping = currentMapping.copy(outputNodeId = null); isSaved = false }
                        )

                        if (currentMapping.customInputs.isNotEmpty()) {
                            Surface(
                                color = Color(0xFF1E1E28),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                                            Text("Exposed Parameters (${currentMapping.customInputs.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                        }
                                        TextButton(onClick = { currentMapping = currentMapping.copy(customInputs = emptyList()); isSaved = false }) {
                                            Text("Clear All", fontSize = 11.sp, color = Color(0xFFFF5252))
                                        }
                                    }
                                    currentMapping.customInputs.forEach { inp ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("${inp.label} (#${inp.nodeId})", fontSize = 11.sp, color = Color(0xFFD0D0E0))
                                            Text(inp.value, fontSize = 11.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }

                        if (currentMapping.isCustomized) {
                            OutlinedButton(
                                onClick = {
                                    currentMapping = ComfyClient.autoDetectNodeMapping(preview.rawJson)
                                    isSaved = false
                                },
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                                border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("RESET ALL ROLES TO AUTO-DETECT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // TAB 2: Generation Specs & Raw JSON
                if (selectedTab == 2) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Key Generation Specs Grid
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "GENERATION SPECS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentGray,
                                letterSpacing = 1.sp
                            )

                            // Row 1: Model
                            if (!preview.modelName.isNullOrBlank()) {
                                SpecBadge(
                                    label = "Model / Checkpoint",
                                    value = preview.modelName,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Row 2: Resolution & Batch Size
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val resText = if (preview.width != null && preview.height != null) {
                                    "${preview.width} × ${preview.height}"
                                } else {
                                    "Unspecified"
                                }
                                SpecBadge(
                                    label = "Resolution",
                                    value = resText,
                                    modifier = Modifier.weight(1f)
                                )

                                val batchText = if (preview.batchSize != null) {
                                    "${preview.batchSize} ${if (preview.batchSize == 1) "image" else "images"}"
                                } else {
                                    "Default (1)"
                                }
                                SpecBadge(
                                    label = "Batch Size",
                                    value = batchText,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Row 3: Steps & CFG Scale
                            if (preview.steps != null || preview.cfg != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (preview.steps != null) {
                                        SpecBadge(
                                            label = "Steps",
                                            value = "${preview.steps}",
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                    if (preview.cfg != null) {
                                        SpecBadge(
                                            label = "CFG Scale",
                                            value = String.format(java.util.Locale.US, "%.1f", preview.cfg),
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }

                            // Row 4: Sampler & Scheduler
                            if (!preview.samplerName.isNullOrBlank() || !preview.scheduler.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (!preview.samplerName.isNullOrBlank()) {
                                        SpecBadge(
                                            label = "Sampler",
                                            value = preview.samplerName,
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                    if (!preview.scheduler.isNullOrBlank()) {
                                        SpecBadge(
                                            label = "Scheduler",
                                            value = preview.scheduler,
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        // Positive Prompt
                        if (!preview.prompt.isNullOrBlank()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = DarkGray),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "POSITIVE PROMPT",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SuccessGreen,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            "COPY",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier
                                                .clickable {
                                                    clipboardManager.setText(AnnotatedString(preview.prompt))
                                                    Toast.makeText(context, "Prompt copied to clipboard", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(4.dp)
                                        )
                                    }
                                    Text(
                                        text = preview.prompt,
                                        fontSize = 12.sp,
                                        color = Color.White,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }

                        // Negative Prompt / Empty Conditioning
                        val hasNegative = !preview.negativePrompt.isNullOrBlank() || preview.hasEmptyConditioning
                        if (hasNegative) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = DarkGray),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                "NEGATIVE PROMPT",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AccentRed,
                                                letterSpacing = 1.sp
                                            )
                                            if (preview.hasEmptyConditioning) {
                                                Surface(
                                                    color = AccentRed.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "EMPTY CONDITIONING",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = AccentRed,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        if (!preview.hasEmptyConditioning && !preview.negativePrompt.isNullOrBlank()) {
                                            Text(
                                                "COPY",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier
                                                    .clickable {
                                                        clipboardManager.setText(AnnotatedString(preview.negativePrompt))
                                                        Toast.makeText(context, "Negative prompt copied", Toast.LENGTH_SHORT).show()
                                                    }
                                                    .padding(4.dp)
                                            )
                                        }
                                    }
                                    if (preview.hasEmptyConditioning) {
                                        Text(
                                            text = "EmptyConditioning node detected (zero/bypassed negative conditioning)",
                                            fontSize = 12.sp,
                                            color = LightGray,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            lineHeight = 16.sp
                                        )
                                    } else {
                                        Text(
                                            text = preview.negativePrompt ?: "",
                                            fontSize = 12.sp,
                                            color = LightGray,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }

                        // LoRAs
                        if (preview.loras.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "LORAS (${preview.loras.size})",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentGray,
                                    letterSpacing = 1.sp
                                )
                                preview.loras.forEach { lora ->
                                    Surface(
                                        color = DarkGray,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, AccentGray.copy(alpha = 0.3f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text("⚡", fontSize = 11.sp)
                                            Text(
                                                text = lora,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Raw JSON Viewer (Collapsible)
                        if (preview.rawJson.isNotBlank()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkGray)
                                        .clickable { isJsonExpanded = !isJsonExpanded }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isJsonExpanded) "HIDE RAW JSON" else "VIEW RAW WORKFLOW JSON",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            "COPY",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier
                                                .clickable {
                                                    clipboardManager.setText(AnnotatedString(preview.rawJson))
                                                    Toast.makeText(context, "Raw JSON copied to clipboard", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(4.dp)
                                        )
                                        Icon(
                                            if (isJsonExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                if (isJsonExpanded) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 200.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black)
                                            .padding(8.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Text(
                                            text = preview.rawJson,
                                            fontSize = 10.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            color = LightGray
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
            val highlight = LocalHighlightColor.current
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onSaveMapping != null && preview.id.isNotBlank()) {
                    Button(
                        onClick = {
                            onSaveMapping(preview.id, currentMapping)
                            isSaved = true
                            Toast.makeText(context, "Node mappings saved for '${preview.label}'!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSaved) SuccessGreen else highlight.primary,
                            contentColor = if (isSaved) Color.White else highlight.onPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(if (isSaved) Icons.Default.Check else Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isSaved) "NODE MAPPINGS SAVED" else "SAVE NODE MAPPINGS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                if (onImport != null) {
                    Button(
                        onClick = {
                            onImport()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = highlight.primary, contentColor = highlight.onPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("IMPORT THIS WORKFLOW", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Text("Close", color = AccentGray, fontSize = 13.sp)
                }
            }
        },
        dismissButton = null
    )
}

