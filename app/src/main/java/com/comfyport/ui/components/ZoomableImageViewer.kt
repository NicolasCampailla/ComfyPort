package com.comfyport.ui.components

import kotlinx.coroutines.launch

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

@Composable
fun ZoomableImageViewer(
    model: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.ui.graphics.RectangleShape,
    onTap: (() -> Unit)? = null,
    onScaleChanged: ((Float) -> Unit)? = null,
    onSuccess: () -> Unit = {}
) = ZoomableImage(
    model = model,
    modifier = modifier,
    contentScale = contentScale,
    shape = shape,
    onTap = onTap,
    onScaleChanged = onScaleChanged,
    onSuccess = onSuccess
)

@Composable
fun ZoomableImage(
    model: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.ui.graphics.RectangleShape,
    onTap: (() -> Unit)? = null,
    onScaleChanged: ((Float) -> Unit)? = null,
    onSuccess: () -> Unit = {}
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val coroutineScope = rememberCoroutineScope()
    val scaleAnimatable = remember { androidx.compose.animation.core.Animatable(1f) }

    // Reset zoom when image changes
    LaunchedEffect(model) {
        scale = 1f
        offset = Offset.Zero
        scaleAnimatable.snapTo(1f)
        onScaleChanged?.invoke(1f)
    }

    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = newScale
        onScaleChanged?.invoke(newScale)
        if (newScale > 1f) {
            val maxOffsetX = (newScale - 1f) * 600f
            val maxOffsetY = (newScale - 1f) * 900f
            offset = Offset(
                x = (offset.x + offsetChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                y = (offset.y + offsetChange.y).coerceIn(-maxOffsetY, maxOffsetY)
            )
        } else {
            offset = Offset.Zero
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .transformable(state = state, canPan = { scale > 1.05f }, enabled = true)
            .pointerInput(model) {
                detectTapGestures(
                    onDoubleTap = {
                        coroutineScope.launch {
                            if (scale > 1.2f) {
                                offset = Offset.Zero
                                scaleAnimatable.snapTo(scale)
                                scaleAnimatable.animateTo(1f, tween(250)) {
                                    scale = value
                                    onScaleChanged?.invoke(value)
                                }
                            } else {
                                scaleAnimatable.snapTo(scale)
                                scaleAnimatable.animateTo(2.5f, tween(250)) {
                                    scale = value
                                    onScaleChanged?.invoke(value)
                                }
                            }
                        }
                    },
                    onTap = {
                        onTap?.invoke()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = model,
            contentDescription = "Zoomable Output Image",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            contentScale = contentScale,
            onSuccess = { onSuccess() }
        )
    }
}

