package com.comfyport.ui.dialogs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.comfyport.theme.AccentGray
import com.comfyport.theme.AccentRed
import com.comfyport.theme.CardGray
import com.comfyport.theme.DarkGray
import kotlin.math.max
import kotlin.math.min

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown

/**
 * Data holder for loaded image and its true dimensions (with EXIF orientation accounted for).
 */
data class LoadedImageData(
    val bitmap: Bitmap,
    val originalWidth: Int,
    val originalHeight: Int
)

/**
 * Utility to load an Android Bitmap from a given Uri with EXIF orientation correction.
 */
fun loadBitmapFromUri(context: android.content.Context, uri: Uri): LoadedImageData? {
    return try {
        val base = context.contentResolver.openInputStream(uri)?.use { stream ->
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeStream(stream, null, options)
        }?.copy(Bitmap.Config.ARGB_8888, true) ?: return null

        var oriented = base
        var isRotated90or270 = false
        try {
            context.contentResolver.openInputStream(uri)?.use { exifStream ->
                val exifInterface = android.media.ExifInterface(exifStream)
                val orientation = exifInterface.getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL
                )
                val matrix = android.graphics.Matrix()
                when (orientation) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> {
                        matrix.postRotate(90f)
                        isRotated90or270 = true
                    }
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> {
                        matrix.postRotate(180f)
                    }
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> {
                        matrix.postRotate(270f)
                        isRotated90or270 = true
                    }
                    android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                        matrix.postScale(-1f, 1f)
                    }
                    android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                        matrix.postScale(1f, -1f)
                    }
                }
                if (!matrix.isIdentity) {
                    val transformed = Bitmap.createBitmap(base, 0, 0, base.width, base.height, matrix, true)
                    if (transformed != base) {
                        oriented = transformed
                    }
                }
            }
        } catch (_: Throwable) {}

        val origW = if (isRotated90or270) base.height else base.width
        val origH = if (isRotated90or270) base.width else base.height
        LoadedImageData(oriented, origW, origH)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Interactive touch mask editor dialog for inpainting in ComfyUI workflows.
 * Allows drawing white mask on black background or erasing, with adjustable brush size and invert tool.
 */
@Composable
fun InteractiveMaskEditorDialog(
    imageUri: Uri,
    initialMask: Bitmap? = null,
    onDismiss: () -> Unit,
    onSaveMask: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var baseBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var maskBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var maskCanvas by remember { mutableStateOf<AndroidCanvas?>(null) }
    var redrawTrigger by remember { mutableIntStateOf(0) }
    var originalWidth by remember { mutableIntStateOf(0) }
    var originalHeight by remember { mutableIntStateOf(0) }

    var brushSize by remember { mutableFloatStateOf(40f) }
    var isEraseMode by remember { mutableStateOf(false) }
    var currentCursorPos by remember { mutableStateOf<Offset?>(null) }

    // Load base image and initialize mask bitmap
    LaunchedEffect(imageUri) {
        val loaded = loadBitmapFromUri(context, imageUri)
        if (loaded != null) {
            val loadedBmp = loaded.bitmap
            originalWidth = loaded.originalWidth
            originalHeight = loaded.originalHeight

            // Scale bitmap to reasonable max resolution for smooth drawing (max 1024px on largest side)
            val maxDim = max(loadedBmp.width, loadedBmp.height)
            val scale = if (maxDim > 1024) 1024f / maxDim else 1f
            val scaledWidth = (loadedBmp.width * scale).toInt().coerceAtLeast(64)
            val scaledHeight = (loadedBmp.height * scale).toInt().coerceAtLeast(64)

            val scaledBase = Bitmap.createScaledBitmap(loadedBmp, scaledWidth, scaledHeight, true)
            baseBitmap = scaledBase

            val mBmp = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
            val mCnv = AndroidCanvas(mBmp)

            if (initialMask != null) {
                val scaledInit = Bitmap.createScaledBitmap(initialMask, scaledWidth, scaledHeight, true)
                val initPixels = IntArray(scaledWidth * scaledHeight)
                scaledInit.getPixels(initPixels, 0, scaledWidth, 0, 0, scaledWidth, scaledHeight)
                for (i in initPixels.indices) {
                    val p = initPixels[i]
                    val a = (p ushr 24) and 0xFF
                    val r = (p ushr 16) and 0xFF
                    val g = (p ushr 8) and 0xFF
                    val b = p and 0xFF
                    val lum = (r * 299 + g * 587 + b * 114) / 1000
                    val intensity = if (a == 0) 0 else ((lum * a) / 255).coerceIn(0, 255)
                    initPixels[i] = (intensity shl 24) or 0x00FFFFFF
                }
                mBmp.setPixels(initPixels, 0, scaledWidth, 0, 0, scaledWidth, scaledHeight)
            } else {
                mCnv.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            }

            maskBitmap = mBmp
            maskCanvas = mCnv
            redrawTrigger++
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardGray)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                        }
                        Text(
                            text = "PAINT INPAINT MASK",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Invert Mask Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkGray)
                                .clickable {
                                    val bmp = maskBitmap ?: return@clickable
                                    val w = bmp.width
                                    val h = bmp.height
                                    val pixels = IntArray(w * h)
                                    bmp.getPixels(pixels, 0, w, 0, 0, w, h)
                                    for (i in pixels.indices) {
                                        val p = pixels[i]
                                        val a = (p ushr 24) and 0xFF
                                        val newA = 255 - a
                                        pixels[i] = (newA shl 24) or 0x00FFFFFF
                                    }
                                    bmp.setPixels(pixels, 0, w, 0, 0, w, h)
                                    redrawTrigger++
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("INVERT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
                        }

                        // Clear Mask Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkGray)
                                .clickable {
                                    val bmp = maskBitmap ?: return@clickable
                                    maskCanvas?.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                                    redrawTrigger++
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("CLEAR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentRed, maxLines = 1, softWrap = false)
                        }
                    }
                }

                // Center Drawing Canvas
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF101010)),
                    contentAlignment = Alignment.Center
                ) {
                    val base = baseBitmap
                    val mask = maskBitmap
                    val canvasObj = maskCanvas

                    if (base == null || mask == null || canvasObj == null) {
                        CircularProgressIndicator(color = Color.White)
                    } else {
                        // Drawing paint config
                        val drawPaint = remember(brushSize, isEraseMode) {
                            AndroidPaint().apply {
                                isAntiAlias = true
                                isDither = true
                                style = AndroidPaint.Style.STROKE
                                strokeJoin = AndroidPaint.Join.ROUND
                                strokeCap = AndroidPaint.Cap.ROUND
                                if (isEraseMode) {
                                    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                                } else {
                                    color = android.graphics.Color.WHITE
                                }
                            }
                        }

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(brushSize, isEraseMode, base) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        val viewW = size.width.toFloat()
                                        val viewH = size.height.toFloat()
                                        val imgW = base.width.toFloat()
                                        val imgH = base.height.toFloat()

                                        val scale = min(viewW / imgW, viewH / imgH)
                                        val offsetX = (viewW - imgW * scale) / 2f
                                        val offsetY = (viewH - imgH * scale) / 2f

                                        var lastImgX = ((down.position.x - offsetX) / scale).coerceIn(0f, imgW)
                                        var lastImgY = ((down.position.y - offsetY) / scale).coerceIn(0f, imgH)
                                        currentCursorPos = down.position

                                        drawPaint.strokeWidth = brushSize / scale
                                        drawPaint.style = AndroidPaint.Style.FILL
                                        canvasObj.drawCircle(lastImgX, lastImgY, (brushSize / scale) / 2f, drawPaint)
                                        drawPaint.style = AndroidPaint.Style.STROKE
                                        redrawTrigger++

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull() ?: break
                                            if (!change.pressed) break
                                            change.consume()

                                            val curX = ((change.position.x - offsetX) / scale).coerceIn(0f, imgW)
                                            val curY = ((change.position.y - offsetY) / scale).coerceIn(0f, imgH)
                                            currentCursorPos = change.position

                                            drawPaint.strokeWidth = brushSize / scale
                                            canvasObj.drawLine(lastImgX, lastImgY, curX, curY, drawPaint)
                                            drawPaint.style = AndroidPaint.Style.FILL
                                            canvasObj.drawCircle(curX, curY, (brushSize / scale) / 2f, drawPaint)
                                            drawPaint.style = AndroidPaint.Style.STROKE
                                            redrawTrigger++

                                            lastImgX = curX
                                            lastImgY = curY
                                        }
                                        currentCursorPos = null
                                    }
                                }
                        ) {
                            // Listen to redraw trigger
                            @Suppress("UNUSED_VARIABLE")
                            val trigger = redrawTrigger

                            val viewW = size.width
                            val viewH = size.height
                            val imgW = base.width.toFloat()
                            val imgH = base.height.toFloat()

                            val scale = min(viewW / imgW, viewH / imgH)
                            val offsetX = (viewW - imgW * scale) / 2f
                            val offsetY = (viewH - imgH * scale) / 2f

                            val dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt())
                            val dstSize = IntSize((imgW * scale).toInt(), (imgH * scale).toInt())

                            // 1. Draw base image
                            drawImage(
                                image = base.asImageBitmap(),
                                dstOffset = dstOffset,
                                dstSize = dstSize
                            )

                            // 2. Draw mask overlay with semi-translucent magenta/purple tint
                            drawImage(
                                image = mask.asImageBitmap(),
                                dstOffset = dstOffset,
                                dstSize = dstSize,
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                                    Color(0xBBFF0055),
                                    androidx.compose.ui.graphics.BlendMode.SrcAtop
                                )
                            )

                            // 3. Draw brush cursor ring if dragging
                            currentCursorPos?.let { pos ->
                                drawCircle(
                                    color = if (isEraseMode) AccentRed else Color.White,
                                    radius = brushSize / 2f,
                                    center = pos,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                                )
                            }
                        }
                    }
                }

                // Bottom Tool & Size Panel
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = CardGray,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Mode Tabs (Draw Mask vs Erase Mask)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (!isEraseMode) Color.White else DarkGray)
                                    .clickable { isEraseMode = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Brush,
                                        contentDescription = null,
                                        tint = if (!isEraseMode) Color.Black else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        "PAINT MASK",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (!isEraseMode) Color.Black else Color.White,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isEraseMode) AccentRed else DarkGray)
                                    .clickable { isEraseMode = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        "ERASE MASK",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        // Brush Size Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("BRUSH", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentGray, maxLines = 1, softWrap = false)
                            Slider(
                                value = brushSize,
                                onValueChange = { brushSize = it },
                                valueRange = 10f..120f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = DarkGray
                                )
                            )
                            // Brush preview dot
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(DarkGray, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size((brushSize * 0.22f).dp.coerceIn(4.dp, 26.dp))
                                        .background(if (isEraseMode) AccentRed else Color.White, CircleShape)
                                )
                            }
                        }

                        // Save & Apply Button
                        Button(
                            onClick = {
                                val mask = maskBitmap ?: return@Button
                                val w = mask.width
                                val h = mask.height
                                val resultBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                val pixels = IntArray(w * h)
                                mask.getPixels(pixels, 0, w, 0, 0, w, h)
                                for (i in pixels.indices) {
                                    val p = pixels[i]
                                    val a = (p ushr 24) and 0xFF
                                    val r = (p ushr 16) and 0xFF
                                    val g = (p ushr 8) and 0xFF
                                    val b = p and 0xFF
                                    val lum = (r * 299 + g * 587 + b * 114) / 1000
                                    val intensity = if (a == 0) 0 else ((lum * a) / 255).coerceIn(0, 255)
                                    // Save as standard grayscale mask: fully opaque ARGB with R=G=B=intensity
                                    pixels[i] = (255 shl 24) or (intensity shl 16) or (intensity shl 8) or intensity
                                }
                                resultBmp.setPixels(pixels, 0, w, 0, 0, w, h)

                                // Scale to exact original image dimensions if known!
                                val finalMaskBmp = if (originalWidth > 0 && originalHeight > 0 && (w != originalWidth || h != originalHeight)) {
                                    Bitmap.createScaledBitmap(resultBmp, originalWidth, originalHeight, true)
                                } else {
                                    resultBmp
                                }

                                onSaveMask(finalMaskBmp)
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(25.dp)
                        ) {
                            Text("APPLY MASK", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}
