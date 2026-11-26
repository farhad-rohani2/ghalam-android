package com.example.ghalam.teacher.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ghalam.core.network.util.userMessage
import com.example.ghalam.core.ui.components.SectionCard
import com.example.ghalam.core.ui.voice.VoiceClip
import com.example.ghalam.core.ui.voice.VoicePlayerBar
import com.example.ghalam.core.ui.voice.VoiceRecorderSheet
import com.example.ghalam.core.ui.voice.formatVoiceTime
import com.example.ghalam.teacher.data.Graph
import com.example.ghalam.teacher.data.api.FeedbackMediaDto
import com.example.ghalam.teacher.data.local.MediaQueueState
import com.example.ghalam.teacher.data.local.QueuedMediaEntity
import com.example.ghalam.teacher.data.media.MediaKind
import com.example.ghalam.teacher.data.media.MediaKinds
import com.example.ghalam.teacher.data.sync.absoluteUrl
import com.example.ghalam.teacher.ui.viewmodel.FeedbackMediaHolder
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Voice / file feedback of one feedback. A new recording is saved on the phone at once and uploaded
 * when there is internet (the queue shown here); what is already on the server can be played, opened
 * and deleted (that needs the internet).
 * [onOpenViewer] opens images and PDFs inside the app; without it they open in another app.
 */
@Composable
fun FeedbackVoiceSection(
    taskId: Long,
    holder: FeedbackMediaHolder,
    title: String = "بازخورد صوتی",
    onOpenViewer: ((String) -> Unit)? = null,
) {
    val media by holder.media.collectAsState()
    val queue by holder.queue.collectAsState()
    val busy by holder.busy.collectAsState()
    val error by holder.error.collectAsState()
    val online by Graph.network.online.collectAsState()
    var recording by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<FeedbackMediaDto?>(null) }

    SectionCard(title = title, emoji = "🎙") {
        media.forEach { m ->
            val kind = MediaKinds.guess(m.name, m.mime.takeIf { it.isNotBlank() && it != "application/x-empty" })
            if (m.collection == "voice" || kind == MediaKind.AUDIO) {
                VoiceItem(taskId, m, enabled = !busy, onDelete = { toDelete = m })
            } else {
                FileItem(taskId, m, kind, enabled = !busy, onDelete = { toDelete = m }, onOpenViewer = onOpenViewer)
            }
        }

        queue.forEach { q -> QueuedItem(q, holder) }

        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

        if (!online && media.isEmpty() && queue.isEmpty()) {
            Text(
                "بدون اینترنت هم می‌توانید ضبط کنید؛ با اتصال، خودکار ارسال می‌شود.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedButton(onClick = { recording = true }) { Text("🎙 ضبط بازخورد صوتی") }
    }

    if (recording) {
        VoiceRecorderSheet(
            title = title,
            onDismiss = { recording = false },
            onRecorded = { clip: VoiceClip ->
                recording = false
                holder.add(clip)
            },
        )
    }

    toDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("حذف فایل") },
            text = { Text("این فایل از بازخورد حذف شود؟") },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    holder.remove(m.id)
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("انصراف") } },
        )
    }
}

/** A recording that has not reached the server yet: can be heard, retried or discarded. */
@Composable
private fun QueuedItem(q: QueuedMediaEntity, holder: FeedbackMediaHolder) {
    val failed = q.state == MediaQueueState.ERROR
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        VoicePlayerBar(File(q.localPath))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (failed) "⚠️ ارسال نشد: ${q.lastError ?: ""}" else "⏳ ${formatVoiceTime(q.durationMs)} — در صف ارسال",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (failed) TextButton(onClick = { holder.retry(q.id) }) { Text("ارسال دوباره") }
            TextButton(onClick = { holder.discard(q.id) }) { Text("حذف") }
        }
    }
}

/** Downloads a server file once; returns the local copy (null while it is not there). */
@Composable
private fun rememberLocalCopy(taskId: Long, m: FeedbackMediaDto, autoDownload: Boolean, attempt: Int, onFailed: (Boolean) -> Unit): File? {
    val url = absoluteUrl(m.url)
    val row by Graph.media.observe(url).collectAsState(initial = null)
    val local = row?.path?.let { Graph.media.fileFor(it) }?.takeIf { it.isFile }
    LaunchedEffect(url, attempt, local == null) {
        if (local == null && autoDownload && m.url.isNotBlank()) {
            onFailed(false)
            try {
                Graph.media.ensure(url, m.name.ifBlank { "voice.m4a" }, taskId, Long.MAX_VALUE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailed(true)
            }
        }
    }
    return local
}

/** One saved recording: downloaded once, then played from the phone. */
@Composable
private fun VoiceItem(taskId: Long, m: FeedbackMediaDto, enabled: Boolean, onDelete: () -> Unit) {
    var failed by remember(m.url) { mutableStateOf(false) }
    var attempt by remember(m.url) { mutableIntStateOf(0) }
    val local = rememberLocalCopy(taskId, m, autoDownload = true, attempt = attempt, onFailed = { failed = it })

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            local != null -> VoicePlayerBar(local, Modifier.weight(1f))
            failed -> {
                Text("دریافت صدا ممکن نشد", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { attempt++ }) { Text("تلاش دوباره") }
            }
            else -> {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("  در حال دریافت صدا…", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            }
        }
        TextButton(onClick = onDelete, enabled = enabled) { Text("🗑") }
    }
}

/** A non-voice file attached from the web panel: download on demand, open, delete. */
@Composable
private fun FileItem(
    taskId: Long,
    m: FeedbackMediaDto,
    kind: MediaKind,
    enabled: Boolean,
    onDelete: () -> Unit,
    onOpenViewer: ((String) -> Unit)?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val url = absoluteUrl(m.url)
    var downloading by remember(url) { mutableStateOf(false) }
    val local = rememberLocalCopy(taskId, m, autoDownload = false, attempt = 0, onFailed = {})
    val mime = MediaKinds.extensionOf(m.name)?.let { MediaKinds.mimeOfExt(it) }
        ?: m.mime.ifBlank { "application/octet-stream" }

    fun open(file: File) {
        if (onOpenViewer != null && (kind == MediaKind.IMAGE || kind == MediaKind.PDF)) {
            onOpenViewer(Graph.media.keyOf(url))
        } else if (!Graph.media.openExternally(context, file, mime)) {
            Toast.makeText(context, "برنامه‌ای برای باز کردن این فایل پیدا نشد", Toast.LENGTH_SHORT).show()
        }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${kind.emoji} ${m.name}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        if (downloading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            TextButton(onClick = {
                if (local != null) open(local)
                else scope.launch {
                    downloading = true
                    try {
                        Graph.media.ensure(url, m.name, taskId, Long.MAX_VALUE)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Toast.makeText(context, e.userMessage(), Toast.LENGTH_SHORT).show()
                    } finally {
                        downloading = false
                    }
                }
            }) { Text(if (local != null) "باز کردن" else "دانلود") }
        }
        TextButton(onClick = onDelete, enabled = enabled) { Text("🗑") }
    }
}
