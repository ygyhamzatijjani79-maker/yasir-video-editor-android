package com.mc2islamictv.editor

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private val Emerald = Color(0xFF064B2B)
private val Gold = Color(0xFFD9A928)
private val Cream = Color(0xFFFFF3D6)
private val Ink = Color(0xFF14251B)

data class TextClip(val id: String = UUID.randomUUID().toString(), val text: String, val startMs: Long, val endMs: Long)
data class Project(val id: String = UUID.randomUUID().toString(), val name: String, val video: String = "", val description: String = "", val teacher: String = "", val overlays: List<TextClip> = emptyList(), val brightness: Float = 0f, val crop: Float = 0f, val zoom: Float = 1f, val panX: Float = 0f, val panY: Float = 0f, val contrast: Float = 0f, val saturation: Float = 0f, val rotation: Float = 0f, val speed: Float = 1f, val volume: Float = 1f, val trimStartMs: Long = 0L, val trimEndMs: Long = 0L, val muted: Boolean = false, val updated: Long = System.currentTimeMillis())

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MC2App() }
    }
}

@Composable
private fun MC2App() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("mc2_projects", Context.MODE_PRIVATE) }
    var projects by remember { mutableStateOf(loadProjects(prefs)) }
    var current by remember { mutableStateOf<Project?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf("") }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
    val importScope = rememberCoroutineScope()
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val baseProject = current ?: Project(name = "New Islamic Video")
            importScope.launch {
                val copiedFile = withContext(Dispatchers.IO) { copyImportedVideo(context, uri, baseProject.id) }
                if (copiedFile != null) {
                    val p = baseProject.copy(video = Uri.fromFile(copiedFile).toString(), updated = System.currentTimeMillis())
                    current = p
                    projects = upsertProject(projects, p, prefs)
                }
            }
        }
    }

    MaterialTheme(colorScheme = if (darkMode) darkColorScheme(primary = Color.White, secondary = Color.LightGray, background = Color(0xFF101010), surface = Color(0xFF181818)) else lightColorScheme(primary = Color.Black, secondary = Color.DarkGray, background = Color.White, surface = Color(0xFFF5F5F5))) {
        Surface(Modifier.fillMaxSize(), color = if (darkMode) Color(0xFF101010) else Color.White) {
            if (current == null) {
                HomeScreen(projects, darkMode = darkMode, onToggleTheme = { darkMode = !darkMode; prefs.edit().putBoolean("dark_mode", darkMode).apply() }, onNew = { current = Project(name = "Project ${projects.size + 1}") },
                    onOpen = { current = it }, onDelete = { p -> projects = projects.filterNot { it.id == p.id }; saveProjects(projects, prefs); deleteProjectFiles(context, p) },
                    onShare = { p -> shareProject(context, p) })
            } else {
                EditorScreen(
                    project = current!!,
                    onBack = { projects = upsertProject(projects, current!!, prefs); current = null },
                    onChange = { current = it; projects = upsertProject(projects, it, prefs) },
                    onPickVideo = { videoPicker.launch(arrayOf("video/*")) },
                    onExport = {
                        val p = current!!
                        if (p.video.isBlank()) exportMessage = "Da farko zaɓi video."
                        else {
                            exporting = true
                            exportMessage = "Ana shirya MP4…"
                            (context as? MainActivity)?.exportMp4(p, { message -> exportMessage = message; exporting = false })
                        }
                    },
                    exporting = exporting,
                    exportMessage = exportMessage, darkMode = darkMode, onToggleTheme = { darkMode = !darkMode; prefs.edit().putBoolean("dark_mode", darkMode).apply() }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    projects: List<Project>, darkMode: Boolean, onToggleTheme: () -> Unit,
    onNew: () -> Unit, onOpen: (Project) -> Unit, onDelete: (Project) -> Unit, onShare: (Project) -> Unit
) {
    val bg = if (darkMode) Color(0xFF101010) else Color.White
    val fg = if (darkMode) Color.White else Color(0xFF111111)
    val card = if (darkMode) Color(0xFF202020) else Color(0xFFF2F2F2)
    Column(Modifier.fillMaxSize().background(bg).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("MC2 ISLAMIC TV", color = fg, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                Text("VIDEO STUDIO  •  OFFLINE", color = fg.copy(alpha = .58f), fontSize = 11.sp)
            }
            TextButton(onClick = onToggleTheme) { Text(if (darkMode) "LIGHT" else "DARK", color = fg) }
        }
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recent projects", color = fg, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(onClick = onNew, colors = ButtonDefaults.buttonColors(containerColor = fg, contentColor = bg)) { Icon(Icons.Default.Add, null); Text(" New") }
        }
        Spacer(Modifier.height(14.dp))
        if (projects.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Your studio is ready.", color = fg, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text("Create your first Islamic video project.", color = fg.copy(alpha = .6f))
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onNew, colors = ButtonDefaults.buttonColors(containerColor = fg, contentColor = bg)) { Text("＋  Create project") }
                }
            }
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(projects, key = { it.id }) { p ->
                Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clickable { onOpen(p) }) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = fg, fontWeight = FontWeight.Bold)
                            Text(if (p.teacher.isBlank()) "Untitled video" else p.teacher, color = fg.copy(alpha = .6f), fontSize = 12.sp)
                        }
                        IconButton(onClick = { onShare(p) }) { Icon(Icons.Default.Share, "Share", tint = fg) }
                        IconButton(onClick = { onDelete(p) }) { Icon(Icons.Default.Delete, "Delete", tint = fg) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorScreen(
    project: Project, onBack: () -> Unit, onChange: (Project) -> Unit, onPickVideo: () -> Unit,
    onExport: () -> Unit, exporting: Boolean, exportMessage: String,
    darkMode: Boolean, onToggleTheme: () -> Unit
) {
    var newOverlay by remember { mutableStateOf("") }
    var showOverlayDialog by remember { mutableStateOf(false) }
    val bg = if (darkMode) Color(0xFF101010) else Color.White
    val fg = if (darkMode) Color.White else Color(0xFF111111)
    val panel = if (darkMode) Color(0xFF202020) else Color(0xFFF1F1F1)
    val field = if (darkMode) Color(0xFF181818) else Color(0xFFFAFAFA)
    Column(Modifier.fillMaxSize().background(bg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Projects", color = fg) }
            Spacer(Modifier.weight(1f))
            Text("MC2 STUDIO", color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            TextButton(onClick = onToggleTheme) { Text(if (darkMode) "☼" else "◐", color = fg) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            MC2TemplatePreview(
                videoUri = project.video, description = project.description, teacher = project.teacher,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                zoom = project.zoom, panX = project.panX, panY = project.panY, onTransformChange = { z, x, y -> onChange(project.copy(zoom = z, panX = x, panY = y)) }
            )
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(onClick = onPickVideo, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = fg, contentColor = bg)) { Text("＋  Import video") }
                OutlinedButton(onClick = { showOverlayDialog = true }, modifier = Modifier.weight(1f)) { Text("＋  Add text", color = fg) }
            }
            Text("PROJECT CONTENT", color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            OutlinedTextField(value = project.description, onValueChange = { onChange(project.copy(description = it)) }, label = { Text("B  •  Description") }, modifier = Modifier.fillMaxWidth(), minLines = 2, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = fg, unfocusedTextColor = fg, focusedContainerColor = field, unfocusedContainerColor = field))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = project.teacher, onValueChange = { onChange(project.copy(teacher = it)) }, label = { Text("C  •  Teacher name") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = fg, unfocusedTextColor = fg, focusedContainerColor = field, unfocusedContainerColor = field))
            Text("VIDEO ADJUSTMENTS", color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
            SettingSlider("Zoom / Fill", project.zoom, 1f..3f, fg, panel, { onChange(project.copy(zoom = it)) }, String.format("%.1f×", project.zoom))
            SettingSlider("Crop", project.crop, 0f..0.35f, fg, panel, { onChange(project.copy(crop = it)) }, String.format("%.0f%%", project.crop * 100))
            SettingSlider("Lighting", project.brightness, -1f..1f, fg, panel, { onChange(project.copy(brightness = it)) }, String.format("%.1f", project.brightness))
            SettingSlider("Contrast", project.contrast, -1f..1f, fg, panel, { onChange(project.copy(contrast = it)) }, String.format("%.1f", project.contrast))
            SettingSlider("Saturation", project.saturation, -100f..100f, fg, panel, { onChange(project.copy(saturation = it)) }, String.format("%.0f", project.saturation))
            SettingSlider("Rotation", project.rotation, 0f..360f, fg, panel, { onChange(project.copy(rotation = it)) }, String.format("%.0f°", project.rotation))
            SettingSlider("Playback speed", project.speed, 0.5f..2f, fg, panel, { onChange(project.copy(speed = it)) }, String.format("%.1f×", project.speed))
            SettingSlider("Audio volume", project.volume, 0f..2f, fg, panel, { onChange(project.copy(volume = it, muted = false)) }, String.format("%.0f%%", project.volume * 100f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mute original audio", color = fg, modifier = Modifier.weight(1f))
                Switch(checked = project.muted, onCheckedChange = { onChange(project.copy(muted = it)) })
            }
            OutlinedButton(onClick = { onChange(project.copy(zoom = 1f, panX = 0f, panY = 0f, crop = 0f, brightness = 0f, contrast = 0f, saturation = 0f, rotation = 0f, speed = 1f, volume = 1f, muted = false)) }, modifier = Modifier.fillMaxWidth()) { Text("Reset adjustments") }
            Text("TIMELINE", color = fg, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            TimelineRow("VIDEO", if (project.video.isBlank()) "No video imported" else "Source video ready", fg, panel)
            TimelineRow("B  DESCRIPTION", if (project.description.isBlank()) "No description" else "Visible throughout video", fg, panel)
            TimelineRow("C  TEACHER", if (project.teacher.isBlank()) "No teacher name" else "Visible throughout video", fg, panel)
            project.overlays.forEach { clip ->
                OverlayTrackEditor(clip, fg, panel,
                    onUpdate = { changed -> onChange(project.copy(overlays = project.overlays.map { if (it.id == clip.id) changed else it })) },
                    onDelete = { onChange(project.copy(overlays = project.overlays.filterNot { it.id == clip.id })) })
            }
            Spacer(Modifier.height(12.dp))
        }
        Column(Modifier.fillMaxWidth().background(panel).padding(12.dp)) {
            if (exportMessage.isNotBlank()) Text(exportMessage, color = fg, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            Button(onClick = onExport, enabled = !exporting, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = fg, contentColor = bg)) {
                Text(if (exporting) "RENDERING MP4…" else "EXPORT  •  MP4", fontWeight = FontWeight.Bold)
            }
        }
    }
    if (showOverlayDialog) AlertDialog(
        onDismissRequest = { showOverlayDialog = false }, title = { Text("Add timeline text") },
        text = { OutlinedTextField(value = newOverlay, onValueChange = { newOverlay = it }, label = { Text("Text") }) },
        confirmButton = { TextButton(onClick = { if (newOverlay.isNotBlank()) onChange(project.copy(overlays = project.overlays + TextClip(text = newOverlay, startMs = 0, endMs = 5000))); newOverlay = ""; showOverlayDialog = false }) { Text("Add") } },
        dismissButton = { TextButton(onClick = { showOverlayDialog = false }) { Text("Cancel") } }
    )
}

@Composable
private fun SettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, fg: Color, panel: Color, onChange: (Float) -> Unit, valueLabel: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).background(panel, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = fg, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(valueLabel, color = fg.copy(alpha = .65f), fontSize = 12.sp)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, colors = SliderDefaults.colors(thumbColor = fg, activeTrackColor = fg, inactiveTrackColor = fg.copy(alpha = .2f)))
    }
}

@Composable
private fun OverlayTrackEditor(clip: TextClip, fg: Color, panel: Color, onUpdate: (TextClip) -> Unit, onDelete: () -> Unit) {
    var startText by remember(clip.id) { mutableStateOf((clip.startMs / 1000L).toString()) }
    var endText by remember(clip.id) { mutableStateOf((clip.endMs / 1000L).toString()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(panel, RoundedCornerShape(9.dp)).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TEXT TRACK", color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Delete, "Delete text clip", tint = fg) }
        }
        Text(clip.text, color = fg, fontSize = 12.sp, maxLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = startText, onValueChange = { value ->
                startText = value
                value.toLongOrNull()?.let { seconds -> onUpdate(clip.copy(startMs = (seconds.coerceAtLeast(0) * 1000L).coerceAtMost(clip.endMs))) }
            }, label = { Text("Start (sec)") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = endText, onValueChange = { value ->
                endText = value
                value.toLongOrNull()?.let { seconds -> onUpdate(clip.copy(endMs = (seconds.coerceAtLeast(0) * 1000L).coerceAtLeast(clip.startMs))) }
            }, label = { Text("End (sec)") }, modifier = Modifier.weight(1f), singleLine = true)
        }
    }
}

@Composable
private fun TimelineRow(label: String, detail: String, fg: Color, panel: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(panel, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(120.dp))
        Text(detail, color = fg.copy(alpha = .7f), fontSize = 11.sp)
    }
}

private fun loadProjects(prefs: android.content.SharedPreferences): List<Project> {
    val raw = prefs.getString("projects", "") ?: ""
    return raw.split("\n").filter { it.isNotBlank() }.mapNotNull { line ->
        runCatching {
            val f = line.split("|")
            val clips = if (f.size > 6) decodeClips(dec(f[6])) else emptyList()
            Project(id = f[0], name = dec(f[1]), video = dec(f[2]), description = dec(f[3]), teacher = dec(f[4]), updated = f[5].toLong(), overlays = clips, brightness = if (f.size > 7) f[7].toFloatOrNull() ?: 0f else 0f, crop = if (f.size > 8) f[8].toFloatOrNull() ?: 0f else 0f, zoom = if (f.size > 9) f[9].toFloatOrNull() ?: 1f else 1f, panX = if (f.size > 10) f[10].toFloatOrNull() ?: 0f else 0f, panY = if (f.size > 11) f[11].toFloatOrNull() ?: 0f else 0f, contrast = if (f.size > 12) f[12].toFloatOrNull() ?: 0f else 0f, saturation = if (f.size > 13) f[13].toFloatOrNull() ?: 0f else 0f, rotation = if (f.size > 14) f[14].toFloatOrNull() ?: 0f else 0f, speed = if (f.size > 15) f[15].toFloatOrNull() ?: 1f else 1f, volume = if (f.size > 16) f[16].toFloatOrNull() ?: 1f else 1f, trimStartMs = if (f.size > 17) f[17].toLongOrNull() ?: 0L else 0L, trimEndMs = if (f.size > 18) f[18].toLongOrNull() ?: 0L else 0L, muted = if (f.size > 19) f[19].toBoolean() else false)
        }.getOrNull()
    }.sortedByDescending { it.updated }
}
private fun saveProjects(list: List<Project>, prefs: android.content.SharedPreferences) {
    prefs.edit().putString("projects", list.joinToString("\n") { "${it.id}|${enc(it.name)}|${enc(it.video)}|${enc(it.description)}|${enc(it.teacher)}|${it.updated}|${enc(encodeClips(it.overlays))}|${it.brightness}|${it.crop}|${it.zoom}|${it.panX}|${it.panY}|${it.contrast}|${it.saturation}|${it.rotation}|${it.speed}|${it.volume}|${it.trimStartMs}|${it.trimEndMs}|${it.muted}" }).apply()
}
private fun upsertProject(list: List<Project>, p: Project, prefs: android.content.SharedPreferences): List<Project> {
    val updated = (list.filterNot { it.id == p.id } + p.copy(updated = System.currentTimeMillis())).sortedByDescending { it.updated }
    saveProjects(updated, prefs); return updated
}
private fun enc(s: String) = android.util.Base64.encodeToString(s.toByteArray(), android.util.Base64.NO_WRAP)
private fun dec(s: String) = String(android.util.Base64.decode(s, android.util.Base64.DEFAULT))
private fun encodeClips(clips: List<TextClip>): String {
    val array = org.json.JSONArray()
    clips.forEach { clip ->
        array.put(org.json.JSONObject().put("id", clip.id).put("text", clip.text).put("start", clip.startMs).put("end", clip.endMs))
    }
    return array.toString()
}
private fun decodeClips(raw: String): List<TextClip> = runCatching {
    val array = org.json.JSONArray(raw)
    (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        TextClip(item.getString("id"), item.getString("text"), item.getLong("start"), item.getLong("end"))
    }
}.getOrDefault(emptyList())
private fun copyImportedVideo(context: Context, source: Uri, projectId: String): File? = runCatching {
    val folder = File(context.filesDir, "projects/$projectId").apply { mkdirs() }
    val destination = File(folder, "source_video")
    context.contentResolver.openInputStream(source)?.use { input ->
        destination.outputStream().use { output -> input.copyTo(output) }
    } ?: return null
    destination
}.getOrNull()

private fun deleteProjectFiles(context: Context, project: Project) {
    File(context.filesDir, "projects/${project.id}").deleteRecursively()
}
private fun shareProject(context: Context, project: Project) {
    if (project.video.isBlank()) return
    val sourceFile = File(Uri.parse(project.video).path ?: return)
    if (!sourceFile.exists()) return
    val contentUri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", sourceFile)
    val intent = Intent(Intent.ACTION_SEND).apply { type = "video/*"; putExtra(Intent.EXTRA_STREAM, contentUri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    context.startActivity(Intent.createChooser(intent, "Share video"))
}

@OptIn(UnstableApi::class)
private fun MainActivity.exportMp4(project: Project, done: (String) -> Unit) {
    lifecycleScope.launch {
        val output = File(cacheDir, "mc2_${project.id}.mp4")
        try {
            if (output.exists()) output.delete()
            val transformer = Transformer.Builder(this@exportMp4).build()
            transformer.addListener(object : Transformer.Listener {
                override fun onCompleted(
                    composition: androidx.media3.transformer.Composition,
                    exportResult: androidx.media3.transformer.ExportResult
                ) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        var savedUri: Uri? = null
                        try {
                            val safeName = project.name
                                .replace(Regex("[^A-Za-z0-9_-]+"), "_")
                                .trim('_')
                                .ifBlank { "Video" }
                            val values = ContentValues().apply {
                                put(MediaStore.Video.Media.DISPLAY_NAME, "MC2_${safeName}_${System.currentTimeMillis()}.mp4")
                                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MC2 ISLAMIC TV")
                                put(MediaStore.Video.Media.IS_PENDING, 1)
                            }
                            savedUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                                ?: throw IllegalStateException("Could not create MP4 file in Movies")
                            val stream = contentResolver.openOutputStream(savedUri!!)
                                ?: throw IllegalStateException("Could not open MP4 output stream")
                            stream.use { out -> output.inputStream().use { input -> input.copyTo(out) } }
                            val publish = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                            contentResolver.update(savedUri!!, publish, null, null)
                            withContext(Dispatchers.Main) {
                                done("An adana MP4 a Movies/MC2 ISLAMIC TV")
                            }
                        } catch (e: Exception) {
                            savedUri?.let { runCatching { contentResolver.delete(it, null, null) } }
                            withContext(Dispatchers.Main) {
                                done("An kasa ajiye MP4: ${e.message ?: "unknown error"}")
                            }
                        } finally {
                            output.delete()
                        }
                    }
                }

                override fun onError(
                    composition: androidx.media3.transformer.Composition,
                    exportResult: androidx.media3.transformer.ExportResult,
                    exportException: androidx.media3.transformer.ExportException
                ) {
                    output.delete()
                    done("Export failed: ${exportException.message ?: "unknown error"}")
                }
            })
            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(project.trimStartMs.coerceAtLeast(0L))
                .apply { if (project.trimEndMs > project.trimStartMs) setEndPositionMs(project.trimEndMs) }
                .build()
            val sourceItem = MediaItem.Builder().setUri(Uri.parse(project.video))
                .setClippingConfiguration(clipping).build()
            val gain = androidx.media3.common.audio.GainProcessor(
                androidx.media3.common.audio.DefaultGainProvider.Builder(if (project.muted) 0f else project.volume.coerceIn(0f, 2f)).build()
            )
            val speedProvider = object : androidx.media3.common.audio.SpeedProvider {
                override fun getSpeed(timeUs: Long): Float = project.speed.coerceIn(0.5f, 2f)
                override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = androidx.media3.common.C.TIME_UNSET
            }
            val speedEffects = Effects.createExperimentalSpeedChangingEffect(speedProvider)
            val editedItem = EditedMediaItem.Builder(sourceItem)
                .setEffects(Effects(listOf(gain, speedEffects.first), MC2ExportRenderer.buildEffects(project) + speedEffects.second))
                .build()
            transformer.start(editedItem, output.absolutePath)
        } catch (e: Exception) {
            output.delete()
            done("Export error: ${e.message ?: "unknown error"}")
        }
    }
}
