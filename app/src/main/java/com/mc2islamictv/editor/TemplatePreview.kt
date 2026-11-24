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
    val latestProject by rememberUpdatedState(previewProject)
    val latestZoom by rememberUpdatedState(zoom)
    val latestPanX by rememberUpdatedState(panX)
    val latestPanY by rememberUpdatedState(panY)
    val latestTransform by rememberUpdatedState(onTransformChange)
    var previewTextureView by remember(videoUri) { mutableStateOf<TextureView?>(null) }
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
            setVideoEffects(emptyList())
            prepare()
            playWhenReady = true
        }
    }
    // Preview adjustments are applied directly to TextureView. Rebuilding Media3's
    // video-effects pipeline on every slider movement can stall playback and freeze
    // the playhead. TextureView transforms keep playback/audio/timeline continuous.
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
        val videoWidth = maxWidth * VideoCompositionGeometry.APERTURE_WIDTH
        val videoHeight = maxHeight * VideoCompositionGeometry.APERTURE_HEIGHT
        Box(
            modifier = Modifier
                .offset(x = maxWidth * VideoCompositionGeometry.APERTURE_LEFT, y = maxHeight * VideoCompositionGeometry.APERTURE_TOP)
                .size(videoWidth, videoHeight)
                .clip(RoundedCornerShape(22.dp))
                .pointerInput(videoUri) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
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
                                val nextPanX = if (nextZoom <= 1.01f) 0f else
                                    (gesturePanX + pan.x / size.width / nextZoom * 1.5f).coerceIn(-1f, 1f)
                                val nextPanY = if (nextZoom <= 1.01f) 0f else
                                    (gesturePanY + pan.y / size.height / nextZoom * 1.5f).coerceIn(-1f, 1f)
                                if (kotlin.math.abs(scaleChange - 1f) > 0.0005f ||
                                    kotlin.math.abs(pan.x) > 0.25f ||
                                    kotlin.math.abs(pan.y) > 0.25f
                                ) {
                                    gestureZoom = nextZoom
                                    gesturePanX = nextPanX
                                    gesturePanY = nextPanY
                                    previewTextureView?.let { view ->
                                        applyPreviewTransform(
                                            view = view,
                                            videoWidth = player?.videoSize?.width ?: 0,
                                            videoHeight = player?.videoSize?.height ?: 0,
                                            zoom = gestureZoom,
                                            panX = gesturePanX,
                                            panY = gesturePanY,
                                            crop = latestProject.crop,
                                            rotation = latestProject.rotation,
                                            brightness = latestProject.brightness,
                                            contrast = latestProject.contrast,
                                            saturation = latestProject.saturation
                                        )
                                    }
                                    changed = true
                                    event.changes.forEach { if (it.pressed) it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        if (changed) latestTransform(gestureZoom, gesturePanX, gesturePanY)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (player != null) {
                AndroidView(
                    factory = { ctx -> TextureView(ctx).also { view ->
                        previewTextureView = view
                        player.setVideoTextureView(view)
                        view.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    } },
                    update = { view ->
                        applyPreviewTransform(
                            view = view,
                            videoWidth = player.videoSize.width,
                            videoHeight = player.videoSize.height,
                            zoom = zoom,
                            panX = panX,
                            panY = panY,
                            crop = crop,
                            rotation = rotation,
                            brightness = brightness,
                            contrast = contrast,
                            saturation = saturation
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Image(bitmap = template, contentDescription = "MC2 Islamic animated template", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())

    }
}

@OptIn(UnstableApi::class)
private fun applyPreviewTransform(
    view: TextureView,
    videoWidth: Int,
    videoHeight: Int,
    zoom: Float,
    panX: Float,
    panY: Float,
    crop: Float,
    rotation: Float,
    brightness: Float,
    contrast: Float,
    saturation: Float
) {
    val vw = view.width.toFloat()
    val vh = view.height.toFloat()
    if (vw <= 0f || vh <= 0f) return

    val sourceW = videoWidth.takeIf { it > 0 }?.toFloat() ?: vw
    val sourceH = videoHeight.takeIf { it > 0 }?.toFloat() ?: vh
    val scale = VideoCompositionGeometry.calculateScale(
        sourceWidth = sourceW,
        sourceHeight = sourceH,
        viewportWidth = vw,
        viewportHeight = vh,
        rotationDegrees = rotation,
        zoom = zoom,
        crop = crop
    )
    // TextureView maps its decoded texture into the view's rectangular bounds.
    // Convert the source-pixel scale independently on X and Y before rotating.
    // Export applies these same source-relative scales in its square-frame matrix;
    // using scale/scale here incorrectly treats view pixels as source pixels and
    // causes portrait/landscape clips to crop and zoom differently after export.
    val scaleX = scale * sourceW / vw
    val scaleY = scale * sourceH / vh
    val matrix = Matrix().apply {
        setScale(scaleX, scaleY, vw / 2f, vh / 2f)
        postRotate(rotation, vw / 2f, vh / 2f)
        postTranslate(panX.coerceIn(-1f, 1f) * vw * 0.45f, panY.coerceIn(-1f, 1f) * vh * 0.45f)
    }
    view.setTransform(matrix)

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
