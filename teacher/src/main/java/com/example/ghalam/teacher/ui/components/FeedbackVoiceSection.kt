package com.example.ghalam.teacher.ui.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ghalam.core.ui.components.SectionCard
import com.example.ghalam.core.ui.voice.VoiceClip
import com.example.ghalam.core.ui.voice.VoicePlayerBar
import com.example.ghalam.core.ui.voice.VoiceRecorderSheet
import com.example.ghalam.core.ui.voice.formatVoiceTime
import com.example.ghalam.teacher.data.Graph
import com.example.ghalam.teacher.data.api.FeedbackMediaDto
import com.example.ghalam.teacher.data.sync.absoluteUrl
import com.example.ghalam.teacher.ui.viewmodel.FeedbackMediaHolder
import kotlinx.coroutines.CancellationException

/** "بازخورد صوتی": record, send, listen and delete voice feedback (needs the internet). */
@Composable
fun FeedbackVoiceSection(taskId: Long, holder: FeedbackMediaHolder, title: String = "بازخورد صوتی") {
    val media by holder.media.collectAsState()
    val busy by holder.busy.collectAsState()
    val error by holder.error.collectAsState()
    val pending by holder.pending.collectAsState()
    val online by Graph.network.online.collectAsState()
    var recording by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<FeedbackMediaDto?>(null) }

    SectionCard(title = title, emoji = "🎙") {
        media.filter { it.collection == "voice" }.forEach { m ->
            VoiceItem(taskId, m, enabled = !busy, onDelete = { toDelete = m })
        }

        pending?.let { clip ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "🎙 ${formatVoiceTime(clip.durationMs)} — " + if (busy) "در حال ارسال…" else "ارسال نشد",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else {
                    TextButton(onClick = holder::retry) { Text("ارسال دوباره") }
                    TextButton(onClick = holder::discard) { Text("حذف") }
                }
            }
        }

        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

        if (!online) {
            Text(
                "ارسال و شنیدن بازخورد صوتی به اینترنت نیاز دارد.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedButton(onClick = { recording = true }, enabled = !busy && pending == null) { Text("🎙 ضبط بازخورد صوتی") }
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
            title = { Text("حذف بازخورد صوتی") },
            text = { Text("این صدا حذف شود؟") },
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

/** One saved recording: downloaded once, then played from the phone. */
@Composable
private fun VoiceItem(taskId: Long, m: FeedbackMediaDto, enabled: Boolean, onDelete: () -> Unit) {
    val url = absoluteUrl(m.url)
    val row by Graph.media.observe(url).collectAsState(initial = null)
    val local = row?.path?.let { Graph.media.fileFor(it) }?.takeIf { it.isFile }
    var failed by remember(url) { mutableStateOf(false) }
    var attempt by remember(url) { mutableIntStateOf(0) }

    LaunchedEffect(url, attempt, local == null) {
        if (local == null && m.url.isNotBlank()) {
            failed = false
            try {
                Graph.media.ensure(url, m.name.ifBlank { "voice.m4a" }, taskId, Long.MAX_VALUE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
            }
        }
    }

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
