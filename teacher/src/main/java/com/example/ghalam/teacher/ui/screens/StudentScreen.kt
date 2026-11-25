package com.example.ghalam.teacher.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.ghalam.core.ui.components.EmptyState
import com.example.ghalam.core.ui.components.OfflineBanner
import com.example.ghalam.core.ui.components.SectionCard
import com.example.ghalam.core.ui.theme.Danger
import com.example.ghalam.core.ui.theme.DangerBg
import com.example.ghalam.core.ui.theme.Success
import com.example.ghalam.core.ui.theme.SuccessBg
import com.example.ghalam.core.ui.theme.Warn
import com.example.ghalam.core.ui.theme.WarnBg
import com.example.ghalam.teacher.data.Graph
import com.example.ghalam.teacher.data.api.AnswerFileDto
import com.example.ghalam.teacher.data.api.FeedbackRowDto
import com.example.ghalam.teacher.data.local.FeedbackState
import com.example.ghalam.teacher.data.media.MediaKind
import com.example.ghalam.teacher.data.media.MediaKinds
import com.example.ghalam.teacher.data.sync.absoluteUrl
import com.example.ghalam.teacher.ui.components.Avatar
import com.example.ghalam.teacher.ui.components.HeaderAction
import com.example.ghalam.teacher.ui.components.LevelSelector
import com.example.ghalam.teacher.ui.components.NoteField
import com.example.ghalam.teacher.ui.components.ScreenHeader
import com.example.ghalam.teacher.ui.components.StatusPill
import com.example.ghalam.teacher.ui.viewmodel.StudentViewModel
import com.example.ghalam.teacher.ui.viewmodel.StudentViewModelFactory
import com.example.ghalam.teacher.util.fa
import com.example.ghalam.teacher.util.formatFileSize
import kotlinx.coroutines.launch

/**
 * One student's answer and the feedback form. Everything works offline: the answer and its files
 * come from the phone, "save" writes to Room, and the upload happens when there is internet.
 * Leaving the screen (back / previous / next) saves unsaved edits automatically.
 */
@Composable
fun StudentScreen(
    taskId: Long,
    studentId: Long,
    onBack: () -> Unit,
    onOpenStudent: (Long) -> Unit,
    onMessage: () -> Unit,
    onOpenViewer: (String) -> Unit,
) {
    val vm: StudentViewModel = viewModel(
        key = "student-$taskId-$studentId",
        factory = StudentViewModelFactory(taskId, studentId),
    )
    val scope = rememberCoroutineScope()
    val task by vm.task.collectAsState()
    val submission by vm.submission.collectAsState()
    val answer by vm.answer.collectAsState()
    val screen by vm.screen.collectAsState()
    val draft by vm.draft.collectAsState()
    val dirty by vm.dirty.collectAsState()
    val neighbors by vm.neighbors.collectAsState()
    val online by Graph.network.online.collectAsState()
    var showAddExtra by remember { mutableStateOf(false) }

    fun leave(then: () -> Unit) {
        scope.launch {
            vm.saveIfDirty()
            then()
        }
    }

    BackHandler { leave(onBack) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        ScreenHeader(
            title = submission?.name ?: "دانش‌آموز",
            subtitle = task?.let { "${it.lessonTitle} • ${it.description}" },
            onBack = { leave(onBack) },
            actions = { HeaderAction("✉️", onClick = { leave(onMessage) }) },
        )

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!online) {
                item(key = "offline") { OfflineBanner(horizontalPadding = 0.dp) }
            }

            item(key = "status") {
                val s = submission
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (s != null) {
                        Avatar(s.photo, s.name, 44.dp)
                        StatusPill(s.status)
                        s.answeredAtJalali?.let {
                            Text(it.fa(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ---------------------------------------------------------------- the answer
            val a = answer
            if (a == null) {
                item(key = "noanswer") {
                    EmptyState(
                        emoji = if (submission?.status == "none") "📭" else "⏳",
                        title = if (submission?.status == "none") "این دانش‌آموز هنوز چیزی ارسال نکرده" else "پاسخ هنوز دریافت نشده",
                        subtitle = if (submission?.status == "none") null else "پس از همگام‌سازی نمایش داده می‌شود.",
                    )
                }
            } else {
                item(key = "answertext") {
                    SectionCard(title = "پاسخ دانش‌آموز", emoji = "💬") {
                        val withText = a.answers.filter { it.text.isNotBlank() }
                        if (withText.isEmpty()) {
                            Text("متنی ثبت نشده است.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        withText.forEach { item ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                item.createdAtJalali?.let {
                                    Text(it.fa(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(item.text, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
                val files = a.answers.flatMap { it.files }
                if (files.isNotEmpty()) {
                    item(key = "files") {
                        SectionCard(title = "فایل‌های ارسالی", emoji = "📎") {
                            files.forEach { f -> AnswerFileItem(f, vm, onOpenViewer) }
                        }
                    }
                }
            }

            // ---------------------------------------------------------------- the feedback
            item(key = "feedback") {
                SectionCard(title = "بازخورد", emoji = "⭐") {
                    val form = draft
                    val levels = screen?.levels.orEmpty()
                    if (form == null) {
                        Text("در حال بارگذاری…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        SyncBanner(
                            state = screen?.state,
                            error = screen?.lastError,
                            dirty = dirty,
                            online = online,
                            onRetry = vm::retry,
                            onKeepMine = vm::keepMine,
                            onTakeServer = vm::takeServer,
                        )

                        Text("نمره‌ی کلی", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        LevelSelector(levels, form.overallLevel, { lvl -> vm.edit { it.copy(overallLevel = lvl) } })

                        if (form.rows.isNotEmpty()) {
                            Text("پارامترها", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            form.rows.forEach { row ->
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        (if (row.extra) "➕ " else "") + row.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    LevelSelector(
                                        levels,
                                        row.level,
                                        { lvl ->
                                            vm.edit { f ->
                                                f.copy(rows = f.rows.map { r -> if (r.key == row.key) r.copy(level = lvl) else r })
                                            }
                                        },
                                    )
                                }
                            }
                        }
                        TextButton(onClick = { showAddExtra = true }) { Text("＋ افزودن پارامتر") }

                        NoteField("نکات مثبت", form.positives) { v -> vm.edit { it.copy(positives = v) } }
                        NoteField("نکات قابل بهبود", form.negatives) { v -> vm.edit { it.copy(negatives = v) } }
                        NoteField("راهکار", form.suggestions) { v -> vm.edit { it.copy(suggestions = v) } }
                    }
                }
            }
        }

        // ---------------------------------------------------------------- bottom bar
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { neighbors.first?.let { id -> leave { onOpenStudent(id) } } },
                    enabled = neighbors.first != null,
                ) { Text("قبلی") }
                Button(
                    onClick = { scope.launch { vm.saveIfDirty() } },
                    enabled = dirty,
                    modifier = Modifier.weight(1f),
                ) { Text(if (dirty) "ذخیره" else "ذخیره شد ✓", fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = { neighbors.second?.let { id -> leave { onOpenStudent(id) } } },
                    enabled = neighbors.second != null,
                ) { Text("بعدی") }
            }
        }
    }

    if (showAddExtra) {
        AddExtraDialog(
            existing = draft?.rows?.map { it.title }.orEmpty(),
            onDismiss = { showAddExtra = false },
            onAdd = { title ->
                showAddExtra = false
                vm.edit { f ->
                    f.copy(rows = f.rows + FeedbackRowDto(key = "local:$title", title = title, extra = true, level = null))
                }
            },
        )
    }
}


@Composable
private fun AddExtraDialog(existing: List<String>, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    val clean = title.trim()
    val duplicate = clean in existing
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("پارامتر جدید") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(40) },
                    singleLine = true,
                    label = { Text("عنوان پارامتر") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (duplicate) Text("این پارامتر قبلاً هست.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(clean) }, enabled = clean.isNotEmpty() && !duplicate) { Text("افزودن") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun SyncBanner(
    state: String?,
    error: String?,
    dirty: Boolean,
    online: Boolean,
    onRetry: () -> Unit,
    onKeepMine: () -> Unit,
    onTakeServer: () -> Unit,
) {
    val text: String
    val bg: Color
    val fg: Color
    when {
        dirty -> {
            text = "✏️ تغییرات ذخیره‌نشده دارید؛ با «ذخیره» یا رفتن به صفحه‌ی دیگر ذخیره می‌شود."
            bg = WarnBg; fg = Warn
        }
        state == FeedbackState.SYNCED -> {
            text = "✅ با سرور همگام است."
            bg = SuccessBg; fg = Success
        }
        state == FeedbackState.PENDING -> {
            text = if (online) "⏳ روی گوشی ذخیره شد؛ در حال ارسال به سرور…"
            else "⏳ روی گوشی ذخیره شد؛ با اتصال به اینترنت ارسال می‌شود."
            bg = WarnBg; fg = Warn
        }
        state == FeedbackState.CONFLICT -> {
            text = "⚠️ این بازخورد در سرور هم تغییر کرده است. کدام نسخه بماند؟"
            bg = DangerBg; fg = Danger
        }
        state == FeedbackState.ERROR -> {
            text = "⚠️ سرور بازخورد را نپذیرفت: ${error.orEmpty()}"
            bg = DangerBg; fg = Danger
        }
        else -> return // nothing saved yet
    }
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(bg).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.bodyMedium)
        if (!dirty && state == FeedbackState.CONFLICT) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onKeepMine) { Text("نسخه‌ی من") }
                OutlinedButton(onClick = onTakeServer) { Text("نسخه‌ی سرور") }
            }
        }
        if (!dirty && state == FeedbackState.ERROR) {
            TextButton(onClick = onRetry) { Text("تلاش دوباره") }
        }
    }
}

/** A downloaded image is shown inline; other files get an "open" button; a missing one a "download" button. */
@Composable
private fun AnswerFileItem(file: AnswerFileDto, vm: StudentViewModel, onOpenViewer: (String) -> Unit) {
    val context = LocalContext.current
    val url = absoluteUrl(file.url)
    val row by Graph.media.observe(url).collectAsState(initial = null)
    val active by Graph.media.active.collectAsState()
    val errors by vm.fileErrors.collectAsState()

    val local = row?.path?.let { Graph.media.fileFor(it) }?.takeIf { it.isFile }
    val downloading = Graph.media.keyOf(url) in active
    val kind = local?.let { runCatching { MediaKind.valueOf(row?.kind.orEmpty()) }.getOrDefault(MediaKind.OTHER) }
        ?: MediaKinds.guess(file.name, file.mime)
    val mime = row?.mime ?: file.mime ?: MediaKinds.mimeOfExt(MediaKinds.extensionOf(file.name)) ?: "application/octet-stream"

    fun open() {
        if (local == null) return
        // images and PDFs open inside the app; everything else in another app
        if (kind == MediaKind.IMAGE || kind == MediaKind.PDF) {
            onOpenViewer(Graph.media.keyOf(url))
        } else if (!Graph.media.openExternally(context, local, mime)) {
            Toast.makeText(context, "برنامه‌ای برای باز کردن این فایل پیدا نشد", Toast.LENGTH_SHORT).show()
        }
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (local != null && kind == MediaKind.IMAGE) {
                AsyncImage(
                    model = local,
                    contentDescription = file.name,
                    imageLoader = Graph.imageLoader,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable { open() },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(kind.emoji, style = MaterialTheme.typography.titleMedium)
                Column(Modifier.weight(1f)) {
                    Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    val size = formatFileSize(if (file.size > 0) file.size else row?.sizeBytes ?: 0L)
                    val status = when {
                        local != null -> "روی گوشی ذخیره شده"
                        downloading -> "در حال دانلود…"
                        else -> "دانلود نشده"
                    }
                    Text(
                        listOf(size, status).filter { it.isNotEmpty() }.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    errors[url]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
                when {
                    local != null -> TextButton(onClick = ::open) { Text("باز کردن") }
                    downloading -> Unit
                    else -> TextButton(onClick = { vm.downloadFile(file) }) { Text("دانلود") }
                }
            }
        }
    }
}
