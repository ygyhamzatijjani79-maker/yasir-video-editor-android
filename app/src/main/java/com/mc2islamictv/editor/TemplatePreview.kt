package com.mc2islamictv.editor

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.view.TextureView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.max

@Composable
fun MC2TemplatePreview(
    videoUri: String,
    description: String,
    teacher: String,
    modifier: Modifier = Modifier,
    zoom: Float = 1f,
    panX: Float = 0f,
    panY: Float = 0f,
    crop: Float = 0f,
    brightness: Float = 0f,
    contrast: Float = 0f,
    saturation: Float = 0f,
    rotation: Float = 0f,
    speed: Float = 1f,
    volume: Float = 1f,
    muted: Boolean = false,
    isPlaying: Boolean = true,
    onPlaybackUpdate: (Long, Long) -> Unit = { _, _ -> },
    seekPositionMs: Long = -1L,
    onTransformChange: (Float, Float, Float) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val latestZoom by rememberUpdatedState(zoom)
    val latestPanX by rememberUpdatedState(panX)
    val latestPanY by rememberUpdatedState(panY)
    val latestTransform by rememberUpdatedState(onTransformChange)
    var playerPositionMs by remember(videoUri) { mutableLongStateOf(0L) }

    val player = remember(videoUri) {
        if (videoUri.isBlank()) null else ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUri)))
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            prepare()
            playWhenReady = true
        }
    }
    LaunchedEffect(player, isPlaying, speed, volume, muted) {
        player?.playbackParameters = androidx.media3.common.PlaybackParameters(speed.coerceIn(0.5f, 2f))
        player?.volume = if (muted) 0f else volume.coerceIn(0f, 1f)
        player?.playWhenReady = isPlaying
    }
    LaunchedEffect(player, seekPositionMs) {
        if (player != null && seekPositionMs >= 0L && kotlin.math.abs(player.currentPosition - seekPositionMs) > 250L) player.seekTo(seekPositionMs)
    }
    LaunchedEffect(player) {
        if (player == null) {
            onPlaybackUpdate(0L, 0L)
        } else {
            while (isActive) {
                playerPositionMs = player.currentPosition.coerceAtLeast(0L)
                onPlaybackUpdate(playerPositionMs, player.duration.takeIf { it > 0 } ?: 0L)
                delay(100)
            }
        }
    }
    DisposableEffect(player) { onDispose { player?.release() } }

    // Animation follows the real video playhead and freezes when playback pauses.
    val phase = (playerPositionMs / 9_000f) % 1f
    val template = remember(description, teacher, phase) {
        MC2ExportRenderer.createTemplateBitmap(
            Project(name = "Preview", description = description, teacher = teacher),
            width = 540,
            height = 540,
            phase = phase,
        ).asImageBitmap()
    }

    BoxWithConstraints(
        modifier = modifier.aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(Color(0xFF064226))
    ) {
        val videoWidth = maxWidth * 0.575f
        val videoHeight = maxHeight * 0.70f
        Box(
            modifier = Modifier
                .offset(x = maxWidth * 0.38f, y = maxHeight * 0.055f)
                .size(videoWidth, videoHeight)
                .clip(RoundedCornerShape(22.dp))
                .pointerInput(videoUri) {
                    detectTransformGestures { _, pan, scaleChange, _ ->
                        val nextZoom = (latestZoom * scaleChange).coerceIn(1f, 4f)
                        val nextPanX = if (nextZoom <= 1.01f) 0f else (latestPanX + pan.x / size.width / nextZoom * 1.5f).coerceIn(-1f, 1f)
                        val nextPanY = if (nextZoom <= 1.01f) 0f else (latestPanY + pan.y / size.height / nextZoom * 1.5f).coerceIn(-1f, 1f)
                        latestTransform(nextZoom, nextPanX, nextPanY)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (player != null) {
                val totalCrop = (crop.coerceIn(0f, 0.4f) + ((1f - (1f / zoom.coerceIn(1f, 4f))) / 2f)).coerceIn(0f, 0.46f)
                val totalScale = 1f / (1f - 2f * totalCrop).coerceAtLeast(0.08f)
                AndroidView(
                    factory = { ctx -> TextureView(ctx).also { view ->
                        player.setVideoTextureView(view)
                        view.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    } },
                    update = { view ->
                        val matrix = ColorMatrix()
                        val sat = ColorMatrix().apply { setSaturation((1f + saturation / 100f).coerceIn(0f, 2f)) }
                        matrix.postConcat(sat)
                        val c = (1f + contrast).coerceIn(0f, 2f)
                        val b = brightness.coerceIn(-1f, 1f) * 255f
                        val contrastMatrix = ColorMatrix(floatArrayOf(
                            c,0f,0f,0f,b, 0f,c,0f,0f,b, 0f,0f,c,0f,b, 0f,0f,0f,1f,0f
                        ))
                        matrix.postConcat(contrastMatrix)
                        view.setLayerPaint(Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
                    },
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = totalScale
                        scaleY = totalScale
                        translationX = panX * size.width * 0.45f
                        translationY = panY * size.height * 0.45f
                        rotationZ = rotation
                    }
                )
            }
        }
        Image(bitmap = template, contentDescription = "MC2 Islamic animated template", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())

    }
}
