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
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
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

private data class PreviewTransformKey(
    val viewWidth: Int,
    val viewHeight: Int,
    val videoWidth: Int,
    val videoHeight: Int,
    val pixelWidthHeightRatio: Float,
    val zoom: Float,
    val panX: Float,
    val panY: Float,
    val crop: Float,
    val rotation: Float,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float
)

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
    // Remember the last state-driven transform so template animation recompositions
    // do not overwrite a gesture's live TextureView matrix with stale project values.
    val lastAppliedTransformKey = remember(videoUri) { mutableListOf<PreviewTransformKey?>(null) }
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
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var activePointerId = down.id
                        var gestureZoom = latestZoom
                        var gesturePanX = latestPanX
                        var gesturePanY = latestPanY
                        var changed = false

                        fun panLimits(forZoom: Float): Pair<Float, Float> {
                            val videoSize = player?.videoSize
                            val pixelRatio = videoSize?.pixelWidthHeightRatio
                                ?.takeIf { it.isFinite() && it > 0f } ?: 1f
                            val sourceWidth = videoSize?.width?.takeIf { it > 0 }
                                ?.toFloat()?.times(pixelRatio) ?: size.width.toFloat()
                            val sourceHeight = videoSize?.height?.takeIf { it > 0 }
                                ?.toFloat() ?: size.height.toFloat()
                            return VideoCompositionGeometry.calculatePanLimits(
                                sourceWidth = sourceWidth,
                                sourceHeight = sourceHeight,
                                viewportWidth = size.width.toFloat(),
                                viewportHeight = size.height.toFloat(),
                                rotationDegrees = latestProject.rotation,
                                zoom = forZoom,
                                crop = latestProject.crop
                            )
                        }

                        fun applyGestureTransform() {
                            val videoSize = player?.videoSize
                            previewTextureView?.let { view ->
                                applyPreviewTransform(
                                    view = view,
                                    videoWidth = videoSize?.width ?: 0,
                                    videoHeight = videoSize?.height ?: 0,
                                    pixelWidthHeightRatio = videoSize?.pixelWidthHeightRatio ?: 1f,
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
                        }

                        // Compose's restricted pointer scope supplies the long-
                        // press timeout safely. A second finger that cancels the
                        // hold is detected from the current event and starts pinch.
                        val longPress = awaitLongPressOrCancellation(down.id)
                        var event = currentEvent
                        val pinchStarted = event.changes.count { it.pressed } >= 2
                        if (longPress == null && !pinchStarted) {
                            return@awaitEachGesture
                        }
                        val longPressReached = longPress != null
                        var lastPosition = longPress?.position ?: down.position
                        if (longPressReached) longPress.consume()

                        while (event.changes.any { it.pressed }) {
                            var eventChanged = false
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                val scaleChange = event.calculateZoom()
                                val pan = event.calculatePan()
                                val nextZoom = (gestureZoom * scaleChange).coerceIn(1f, 4f)
                                val (limitX, limitY) = panLimits(nextZoom)
                                val nextPanX = (gesturePanX +
                                    pan.x / size.width / nextZoom * 1.5f).coerceIn(-limitX, limitX)
                                val nextPanY = (gesturePanY +
                                    pan.y / size.height / nextZoom * 1.5f).coerceIn(-limitY, limitY)
                                if (nextZoom != gestureZoom ||
                                    nextPanX != gesturePanX ||
                                    nextPanY != gesturePanY
                                ) {
                                    gestureZoom = nextZoom
                                    gesturePanX = nextPanX
                                    gesturePanY = nextPanY
                                    eventChanged = true
                                    event.changes.forEach { if (it.pressed) it.consume() }
                                }
                                pressed.firstOrNull { it.id == activePointerId }?.let {
                                    lastPosition = it.position
                                }
                            } else if (longPressReached && pressed.isNotEmpty()) {
                                val pointer = pressed.firstOrNull { it.id == activePointerId } ?: pressed.first().also {
                                    activePointerId = it.id
                                    lastPosition = it.position
                                }
                                val drag = pointer.position - lastPosition
                                if (kotlin.math.abs(drag.x) > 0.25f || kotlin.math.abs(drag.y) > 0.25f) {
                                    val (limitX, limitY) = panLimits(gestureZoom)
                                    val nextPanX = (gesturePanX +
                                        drag.x / size.width / gestureZoom * 1.5f).coerceIn(-limitX, limitX)
                                    val nextPanY = (gesturePanY +
                                        drag.y / size.height / gestureZoom * 1.5f).coerceIn(-limitY, limitY)
                                    if (nextPanX != gesturePanX || nextPanY != gesturePanY) {
                                        gesturePanX = nextPanX
                                        gesturePanY = nextPanY
                                        eventChanged = true
                                        pointer.consume()
                                    }
                                }
                                lastPosition = pointer.position
                            }
                            if (eventChanged) {
                                changed = true
                                applyGestureTransform()
                            }
                            event = awaitPointerEvent()
                        }
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
                        val key = PreviewTransformKey(
                            viewWidth = view.width,
                            viewHeight = view.height,
                            videoWidth = player.videoSize.width,
                            videoHeight = player.videoSize.height,
                            pixelWidthHeightRatio = player.videoSize.pixelWidthHeightRatio,
                            zoom = zoom,
                            panX = panX,
                            panY = panY,
                            crop = crop,
                            rotation = rotation,
                            brightness = brightness,
                            contrast = contrast,
                            saturation = saturation
                        )
                        if (lastAppliedTransformKey[0] != key) {
                            applyPreviewTransform(
                                view = view,
                                videoWidth = player.videoSize.width,
                                videoHeight = player.videoSize.height,
                                pixelWidthHeightRatio = player.videoSize.pixelWidthHeightRatio,
                                zoom = zoom,
                                panX = panX,
                                panY = panY,
                                crop = crop,
                                rotation = rotation,
                                brightness = brightness,
                                contrast = contrast,
                                saturation = saturation
                            )
                            lastAppliedTransformKey[0] = key
                        }
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
    pixelWidthHeightRatio: Float = 1f,
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

    // Media3 reports non-square-pixel (anamorphic) clips through this ratio.
    // Include it in the source display width before calculating fit/crop so
    // TextureView does not stretch those clips to the aperture's aspect ratio.
    val safePixelRatio = pixelWidthHeightRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    val sourceW = videoWidth.takeIf { it > 0 }?.toFloat()?.times(safePixelRatio) ?: vw
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
