package com.mc2islamictv.editor

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
data class Project(val id: String = UUID.randomUUID().toString(), val name: String, val video: String = "", val description: String = "", val teacher: String = "", val overlays: List<TextClip> = emptyList(), val updated: Long = System.currentTimeMillis())

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

    MaterialTheme(colorScheme = lightColorScheme(primary = Emerald, secondary = Gold, background = Cream, surface = Cream)) {
        Surface(Modifier.fillMaxSize(), color = Cream) {
            if (current == null) {
                HomeScreen(projects, onNew = { current = Project(name = "Project ${projects.size + 1}") },
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
                    exportMessage = exportMessage
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(projects: List<Project>, onNew: () -> Unit, onOpen: (Project) -> Unit, onDelete: (Project) -> Unit, onShare: (Project) -> Unit) {
    Column(Modifier.fillMaxSize().background(Emerald).padding(18.dp)) {
        Text("MC2 ISLAMIC TV", color = Gold, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text("Offline Islamic Video Studio", color = Cream, fontSize = 13.sp)
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Recent Projects", color = Cream, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            FilledTonalIconButton(onClick = onNew, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Gold, contentColor = Emerald)) { Icon(Icons.Default.Add, "New project") }
        }
        Spacer(Modifier.height(12.dp))
        if (projects.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Babu project tukuna", color = Cream, fontSize = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onNew, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Emerald)) { Text("＋  Create New Video") }
                }
            }
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(projects, key = { it.id }) { p ->
                Card(colors = CardDefaults.cardColors(containerColor = Cream), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clickable { onOpen(p) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = Emerald, fontWeight = FontWeight.Bold)
                            Text(if (p.teacher.isBlank()) "No teacher name" else p.teacher, color = Ink, fontSize = 12.sp)
                        }
                        IconButton(onClick = { onShare(p) }) { Icon(Icons.Default.Share, "Share", tint = Emerald) }
                        IconButton(onClick = { onDelete(p) }) { Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFB3261E)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorScreen(project: Project, onBack: () -> Unit, onChange: (Project) -> Unit, onPickVideo: () -> Unit, onExport: () -> Unit, exporting: Boolean, exportMessage: String) {
    var newOverlay by remember { mutableStateOf("") }
    var showOverlayDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Cream).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Projects", color = Emerald) }
            Spacer(Modifier.weight(1f))
            Text("MC2 ISLAMIC TV", color = Emerald, fontWeight = FontWeight.ExtraBold)
        }
        MC2TemplatePreview(
            videoUri = project.video,
            description = project.description,
            teacher = project.teacher,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onPickVideo, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Emerald)) { Text("Import Video") }
            Button(onClick = { showOverlayDialog = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Emerald)) { Text("+ Add Text") }
        }
        OutlinedTextField(value = project.description, onValueChange = { onChange(project.copy(description = it)) }, label = { Text("B — Description") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        OutlinedTextField(value = project.teacher, onValueChange = { onChange(project.copy(teacher = it)) }, label = { Text("C — Sunan malami") }, modifier = Modifier.fillMaxWidth())
        Text("TIMELINE", color = Emerald, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
        TimelineRow("VIDEO", if (project.video.isBlank()) "No clip" else "Imported video")
        TimelineRow("DESCRIPTION", if (project.description.isBlank()) "Empty" else "Text • full duration")
        TimelineRow("TEACHER", if (project.teacher.isBlank()) "Empty" else "Text • full duration")
        project.overlays.forEach { clip ->
            OverlayTrackEditor(
                clip = clip,
                onUpdate = { changed -> onChange(project.copy(overlays = project.overlays.map { if (it.id == clip.id) changed else it })) },
                onDelete = { onChange(project.copy(overlays = project.overlays.filterNot { it.id == clip.id })) }
            )
        }
        if (project.overlays.isEmpty()) Text("Ƙara rubutu daban-daban daga + Add Text.", color = Ink, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onExport, enabled = !exporting, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Emerald)) { Text(if (exporting) "Exporting…" else "EXPORT MP4  ↓") }
        if (exportMessage.isNotBlank()) Text(exportMessage, color = Emerald, fontSize = 12.sp, modifier = Modifier.padding(6.dp))
    }
    if (showOverlayDialog) AlertDialog(
        onDismissRequest = { showOverlayDialog = false },
        title = { Text("Add timeline text") },
        text = { OutlinedTextField(value = newOverlay, onValueChange = { newOverlay = it }, label = { Text("Rubutu") }) },
        confirmButton = { TextButton(onClick = { if (newOverlay.isNotBlank()) onChange(project.copy(overlays = project.overlays + TextClip(text = newOverlay, startMs = 0, endMs = 5000))); newOverlay = ""; showOverlayDialog = false }) { Text("Add") } },
        dismissButton = { TextButton(onClick = { showOverlayDialog = false }) { Text("Cancel") } }
    )
}

@Composable
private fun OverlayTrackEditor(clip: TextClip, onUpdate: (TextClip) -> Unit, onDelete: () -> Unit) {
    var startText by remember(clip.id) { mutableStateOf((clip.startMs / 1000L).toString()) }
    var endText by remember(clip.id) { mutableStateOf((clip.endMs / 1000L).toString()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(Color(0xFFE9DFC6), RoundedCornerShape(9.dp)).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TEXT TRACK", color = Emerald, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Delete, "Delete text clip", tint = Color(0xFFB3261E)) }
        }
        Text(clip.text, color = Ink, fontSize = 12.sp, maxLines = 2)
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
private fun TimelineRow(label: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).background(Color(0xFFE9DFC6), RoundedCornerShape(7.dp)).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Emerald, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp))
        Text(detail, color = Ink, fontSize = 10.sp)
    }
}

private fun loadProjects(prefs: android.content.SharedPreferences): List<Project> {
    val raw = prefs.getString("projects", "") ?: ""
    return raw.split("\n").filter { it.isNotBlank() }.mapNotNull { line ->
        runCatching {
            val f = line.split("|")
            val clips = if (f.size > 6) decodeClips(dec(f[6])) else emptyList()
            Project(id = f[0], name = dec(f[1]), video = dec(f[2]), description = dec(f[3]), teacher = dec(f[4]), updated = f[5].toLong(), overlays = clips)
        }.getOrNull()
    }.sortedByDescending { it.updated }
}
private fun saveProjects(list: List<Project>, prefs: android.content.SharedPreferences) {
    prefs.edit().putString("projects", list.joinToString("\n") { "${it.id}|${enc(it.name)}|${enc(it.video)}|${enc(it.description)}|${enc(it.teacher)}|${it.updated}|${enc(encodeClips(it.overlays))}" }).apply()
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
        try {
            val input = Uri.parse(project.video)
            val output = File(cacheDir, "mc2_${project.id}.mp4")
            if (output.exists()) output.delete()
            val transformer = Transformer.Builder(this@exportMp4).build()
            transformer.addListener(object : Transformer.Listener {
                override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val values = ContentValues().apply {
                            put(MediaStore.Video.Media.DISPLAY_NAME, "MC2_${project.name.replace(" ", "_")}.mp4")
                            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MC2 ISLAMIC TV")
                        }
                        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                        if (uri != null) contentResolver.openOutputStream(uri)?.use { out -> output.inputStream().use { it.copyTo(out) } }
                        withContext(Dispatchers.Main) { done(if (uri != null) "MP4 saved to Movies/MC2 ISLAMIC TV" else "Could not save MP4") }
                    }
                }
                override fun onError(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult, exportException: androidx.media3.transformer.ExportException) {
                    done("Export failed: ${exportException.message ?: "unknown error"}")
                }
            })
            val mediaItem = MediaItem.fromUri(input)
            val editedItem = EditedMediaItem.Builder(mediaItem)
                .setEffects(Effects(emptyList(), MC2ExportRenderer.buildEffects(project)))
                .build()
            transformer.start(editedItem, output.absolutePath)
            // Keep the exporting state active until onCompleted/onError fires.
        } catch (e: Exception) { done("Export error: ${e.message}") }
    }
}
