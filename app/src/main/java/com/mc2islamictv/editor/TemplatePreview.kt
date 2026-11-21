package com.mc2islamictv.editor

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@Composable
fun MC2TemplatePreview(
    videoUri: String,
    description: String,
    teacher: String,
    modifier: Modifier = Modifier,
    zoom: Float = 1f,
    onZoomChange: (Float) -> Unit = {}
) {
    val black = Color(0xFF080808)
    val white = Color(0xFFF7F7F7)
    val line = Color(0xFFBDBDBD)
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(videoUri) {
        if (videoUri.isBlank()) null else ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUri)))
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player?.release() } }

    Column(
        modifier = modifier
            .background(black, RoundedCornerShape(18.dp))
            .border(1.dp, line, RoundedCornerShape(18.dp))
            .padding(7.dp)
    ) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(
                Modifier.weight(0.34f).fillMaxHeight()
                    .background(white, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .padding(9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    description.ifBlank { "B\n\nDESCRIPTION" },
                    color = black, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, maxLines = 10
                )
            }
            Box(
                Modifier.weight(0.66f).fillMaxHeight()
                    .background(Color.Black, RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                    .pointerInput(zoom) {
                        detectTransformGestures { _, _, scale, _ ->
                            onZoomChange((zoom * scale).coerceIn(1f, 3f))
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (player != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                player = player
                            }
                        },
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                        }
                    )
                } else {
                    Text("A  •  VIDEO PREVIEW", color = white, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Box(
            Modifier.fillMaxWidth().height(43.dp).background(white, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                teacher.ifBlank { "C  •  TEACHER NAME" },
                color = black, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                textAlign = TextAlign.Center, maxLines = 1
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier.fillMaxWidth().height(27.dp).background(black, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("▶  MC2 ISLAMIC TV", color = white, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}
