package com.mc2islamictv.editor

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Live layout preview of the supplied MC2 reference:
 * A = uploaded video viewport, B = description panel, C = teacher name.
 * The channel strip is fixed and intentionally has no editing control.
 * This is a preview layer; export compositing is a separate pending milestone.
 */
@Composable
fun MC2TemplatePreview(videoUri: String, description: String, teacher: String, modifier: Modifier = Modifier) {
    val green = Color(0xFF064B2B)
    val gold = Color(0xFFD9A928)
    val cream = Color(0xFFFFF1D2)
    Column(modifier.background(green, RoundedCornerShape(18.dp)).border(2.dp, gold, RoundedCornerShape(18.dp)).padding(7.dp)) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.weight(0.38f).fillMaxHeight().background(cream, RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp)).border(2.dp, gold, RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp)).padding(8.dp), contentAlignment = Alignment.Center) {
                Text(if (description.isBlank()) "B\n\nDescription" else description, color = green, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 9)
            }
            Box(Modifier.weight(0.62f).fillMaxHeight().background(Color(0xFF092E1C), RoundedCornerShape(topEnd = 15.dp)).border(2.dp, gold, RoundedCornerShape(topEnd = 15.dp)), contentAlignment = Alignment.Center) {
                if (videoUri.isNotBlank()) {
                    AndroidView(factory = { ctx -> VideoView(ctx).apply {
                        setVideoURI(Uri.parse(videoUri))
                        setOnPreparedListener { player -> player.isLooping = true; start() }
                    } }, update = { view -> if (view.tag != videoUri) { view.tag = videoUri; view.setVideoURI(Uri.parse(videoUri)) } }, modifier = Modifier.fillMaxSize())
                } else Text("A\nUPLOAD VIDEO", color = cream, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(48.dp).background(green, RoundedCornerShape(12.dp)).border(2.dp, gold, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Text(if (teacher.isBlank()) "C  •  SUNAN MALAMI" else teacher, color = cream, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, textAlign = TextAlign.Center, maxLines = 1)
        }
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().height(30.dp).background(cream, RoundedCornerShape(18.dp)).border(2.dp, gold, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            Text("▶  |  MC2 ISLAMIC TV", color = green, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
    }
}
