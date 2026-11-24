package com.mc2islamictv.editor

import android.net.Uri
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.TextureView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.AudioAttributes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.max

@Composable
@OptIn(UnstableApi::class)
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
    val previewProject = Project(
        name = "Preview",
        description = description,
        teacher = teacher,
        brightness = brightness,
        crop = crop,
        zoom = zoom,
        panX = panX,
        panY = panY,
        contrast = contrast,
        saturation = saturation,
        rotation = rotation
    )
    var previewTextureView by remember(videoUri) { mutableStateOf<TextureView?>(null) }
    val latestProject by rememberUpdatedState(previewProject)
    val latestZoom by rememberUpdatedState(zoom)
    val latestPanX by rememberUpdatedState(panX)
    val latestPanY by rememberUpdatedState(panY)
    val latestTransform by rememberUpdatedState(onTransformChange)
    var playerPositionMs by remember(videoUri) { mutableLongStateOf(0L) }

    val player = remember(videoUri) {
        if (videoUri.isBlank()) null else ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUri)))
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            setHandleAudioBecomingNoisy(true)
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
        modifier = modifier.aspectRatio(1f).clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF064226))
            .pointerInput(videoUri) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val left = size.width * VideoCompositionGeometry.APERTURE_LEFT
                    val right = left + size.width * VideoCompositionGeometry.APERTURE_WIDTH
                    val top = size.height * VideoCompositionGeometry.APERTURE_TOP
                    val bottom = top + size.height * VideoCompositionGeometry.APERTURE_HEIGHT
                    if (down.position.x < left || down.position.x > right ||
                        down.position.y < top || down.position.y > bottom
                    ) return@awaitEachGesture

                    var gestureZoom = latestZoom
                    var gesturePanX = latestPanX
                    var gesturePanY = latestPanY
                    var changed = false
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.pressed }) {
                            val scaleChange = event.calculateZoom()
                            val pan = event.calculatePan()
                            val nextZoom = (gestureZoom * scaleChange).coerceIn(1f, 4f)
                            val apertureWidthPx = size.width * VideoCompositionGeometry.APERTURE_WIDTH
                            val apertureHeightPx = size.height * VideoCompositionGeometry.APERTURE_HEIGHT
                            val nextPanX = if (nextZoom <= 1.01f) 0f else
                                (gesturePanX + pan.x / apertureWidthPx / nextZoom * 1.5f).coerceIn(-1f, 1f)
                            val nextPanY = if (nextZoom <= 1.01f) 0f else
                                (gesturePanY + pan.y / apertureHeightPx / nextZoom * 1.5f).coerceIn(-1f, 1f)

                            if (kotlin.math.abs(scaleChange - 1f) > 0.0005f ||
                                kotlin.math.abs(pan.x) > 0.25f ||
                                kotlin.math.abs(pan.y) > 0.25f
                            ) {
                                gestureZoom = nextZoom
                                gesturePanX = nextPanX
                                gesturePanY = nextPanY
                                val liveProject = latestProject.copy(
                                    zoom = gestureZoom,
                                    panX = gesturePanX,
                                    panY = gesturePanY
                                )
                                previewTextureView?.let { view ->
                                    applyTextureViewTransform(
                                        view,
                                        player?.videoSize?.width ?: 0,
                                        player?.videoSize?.height ?: 0,
                                        liveProject
                                    )
                                }
                                changed = true
                                event.changes.forEach { if (it.pressed) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (changed) latestTransform(gestureZoom, gesturePanX, gesturePanY)
                }
            }
    ) {
        if (player != null) {
            AndroidView(
                factory = { ctx -> TextureView(ctx).also { view ->
                    previewTextureView = view
                    player.setVideoTextureView(view)
                    view.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                } },
                update = { view ->
                    applyPreviewColor(
                        view = view,
                        brightness = brightness,
                        contrast = contrast,
                        saturation = saturation
                    )
                    applyTextureViewTransform(
                        view,
                        player.videoSize.width,
                        player.videoSize.height,
                        previewProject
                    )
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Gesture surface stays aligned to the visible aperture. The video itself
        // is positioned by the same Media3 transformation used for MP4 export.
        Box(
            modifier = Modifier
                .offset(
                    x = maxWidth * VideoCompositionGeometry.APERTURE_LEFT,
                    y = maxHeight * VideoCompositionGeometry.APERTURE_TOP
                )
                .size(
                    maxWidth * VideoCompositionGeometry.APERTURE_WIDTH,
                    maxHeight * VideoCompositionGeometry.APERTURE_HEIGHT
                )
                .clip(RoundedCornerShape(22.dp))
        )

        Image(
            bitmap = template,
            contentDescription = "MC2 Islamic animated template",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )
    }
}


/**
 * Apply the same aperture/fit/zoom/pan geometry directly to TextureView.
 * TextureView.setTransform updates the visible frame while a finger moves;
 * export continues to use the matching Media3 transformation.
 */
private fun applyTextureViewTransform(
    view: TextureView,
    sourceWidth: Int,
    sourceHeight: Int,
    project: Project
) {
    if (sourceWidth <= 0 || sourceHeight <= 0 || view.width <= 0 || view.height <= 0) return

    val apertureWidth = view.width * VideoCompositionGeometry.APERTURE_WIDTH
    val apertureHeight = view.height * VideoCompositionGeometry.APERTURE_HEIGHT
    val scale = VideoCompositionGeometry.calculateScale(
        sourceWidth = sourceWidth.toFloat(),
        sourceHeight = sourceHeight.toFloat(),
        viewportWidth = apertureWidth,
        viewportHeight = apertureHeight,
        rotationDegrees = project.rotation,
        zoom = project.zoom,
        crop = project.crop
    )
    val radians = Math.toRadians(project.rotation.toDouble())
    val cosine = kotlin.math.cos(radians).toFloat()
    val sine = kotlin.math.sin(radians).toFloat()
    val centerX = view.width * (VideoCompositionGeometry.APERTURE_LEFT + VideoCompositionGeometry.APERTURE_WIDTH / 2f)
    val centerY = view.height * (VideoCompositionGeometry.APERTURE_TOP + VideoCompositionGeometry.APERTURE_HEIGHT / 2f)
    val panX = project.panX.coerceIn(-1f, 1f) * VideoCompositionGeometry.USER_PAN_LIMIT * apertureWidth
    val panY = project.panY.coerceIn(-1f, 1f) * VideoCompositionGeometry.USER_PAN_LIMIT * apertureHeight
    val halfWidth = sourceWidth / 2f
    val halfHeight = sourceHeight / 2f

    val matrix = Matrix().apply {
        setValues(floatArrayOf(
            scale * cosine,
            -scale * sine,
            centerX + panX - scale * cosine * halfWidth + scale * sine * halfHeight,
            scale * sine,
            scale * cosine,
            centerY + panY - scale * sine * halfWidth - scale * cosine * halfHeight,
            0f, 0f, 1f
        ))
    }
    view.setTransform(matrix)
}

@OptIn(UnstableApi::class)
private fun applyPreviewColor(
    view: TextureView,
    brightness: Float,
    contrast: Float,
    saturation: Float
) {
    val contrastFactor = 1f + contrast.coerceIn(-1f, 1f)
    val brightnessOffset = brightness.coerceIn(-1f, 1f) * 255f
    val color = ColorMatrix().apply {
        setSaturation((1f + saturation.coerceIn(-100f, 100f) / 100f).coerceIn(0f, 2f))
        val adjustment = ColorMatrix(floatArrayOf(
            contrastFactor, 0f, 0f, 0f, brightnessOffset + 128f * (1f - contrastFactor),
            0f, contrastFactor, 0f, 0f, brightnessOffset + 128f * (1f - contrastFactor),
            0f, 0f, contrastFactor, 0f, brightnessOffset + 128f * (1f - contrastFactor),
            0f, 0f, 0f, 1f, 0f
        ))
        postConcat(adjustment)
    }
    val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(color)
    }
    view.setLayerPaint(paint)
}
