package com.mc2islamictv.editor

import android.net.Uri
import android.view.TextureView
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

@Composable
fun MC2TemplatePreview(
    videoUri: String,
    description: String,
    teacher: String,
    modifier: Modifier = Modifier,
    zoom: Float = 1f,
    panX: Float = 0f,
    panY: Float = 0f,
    speed: Float = 1f,
    volume: Float = 1f,
    muted: Boolean = false,
    onTransformChange: (Float, Float, Float) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val player = remember(videoUri) {
        if (videoUri.isBlank()) null else ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUri)))
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            prepare()
            playWhenReady = true
            playbackParameters = androidx.media3.common.PlaybackParameters(speed.coerceIn(0.5f, 2f))
            this.volume = if (muted) 0f else volume.coerceIn(0f, 1f)
        }
    }
    LaunchedEffect(player, speed, volume, muted) {
        player?.playbackParameters = androidx.media3.common.PlaybackParameters(speed.coerceIn(0.5f, 2f))
        player?.volume = if (muted) 0f else volume.coerceIn(0f, 1f)
    }
    DisposableEffect(player) { onDispose { player?.release() } }
    val template = remember(description, teacher) {
        MC2ExportRenderer.createTemplateBitmap(Project(name = "Preview", description = description, teacher = teacher)).asImageBitmap()
    }

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF064226))
    ) {
        val videoWidth = maxWidth * 0.575f
        val videoHeight = maxHeight * 0.68f
        Box(
            modifier = Modifier
                .offset(x = maxWidth * 0.38f, y = maxHeight * 0.055f)
                .size(videoWidth, videoHeight)
                .clip(RoundedCornerShape(24.dp))
                .pointerInput(zoom, panX, panY) {
                    detectTransformGestures { _, pan, scaleChange, _ ->
                        val nextZoom = (zoom * scaleChange).coerceIn(1f, 4f)
                        val nextPanX = if (nextZoom <= 1.01f) 0f else (panX + pan.x / size.width / nextZoom * 2f).coerceIn(-1f, 1f)
                        val nextPanY = if (nextZoom <= 1.01f) 0f else (panY + pan.y / size.height / nextZoom * 2f).coerceIn(-1f, 1f)
                        onTransformChange(nextZoom, nextPanX, nextPanY)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (player != null) {
                AndroidView(
                    factory = { ctx -> TextureView(ctx).also { player.setVideoTextureView(it) } },
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = panX * size.width * 0.45f
                        translationY = panY * size.height * 0.45f
                    }
                )
            }
        }
        Image(
            bitmap = template,
            contentDescription = "MC2 Islamic video template",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )
    }
}
