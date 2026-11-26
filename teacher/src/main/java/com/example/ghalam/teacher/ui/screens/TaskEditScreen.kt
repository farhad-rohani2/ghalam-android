package com.example.ghalam.teacher.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import com.example.ghalam.core.ui.voice.VoiceRecorderSheet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ghalam.core.ui.components.OfflineBanner
import com.example.ghalam.core.ui.components.SectionCard
import com.example.ghalam.core.ui.components.SkeletonCard
import com.example.ghalam.teacher.data.Graph
import com.example.ghalam.teacher.ui.components.ChoiceChip
import com.example.ghalam.teacher.ui.components.ErrorBanner
import com.example.ghalam.teacher.ui.components.JalaliDateTimeDialog
import com.example.ghalam.teacher.ui.components.NoteField
import com.example.ghalam.teacher.ui.components.ScreenHeader
import com.example.ghalam.teacher.ui.viewmodel.LinkRow
import com.example.ghalam.teacher.ui.viewmodel.TaskEditViewModel
import com.example.ghalam.teacher.ui.viewmodel.TaskEditViewModelFactory
import com.example.ghalam.teacher.util.Jalali
import com.example.ghalam.teacher.util.fa
import com.example.ghalam.teacher.util.formatFileSize
import java.time.LocalDateTime
import java.util.UUID

/** New task (taskId = 0) or edit. Needs internet; the saved task appears in the list at once. */
@Composable
fun TaskEditScreen(
    taskId: Long,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val nonce = rememberSaveable { UUID.randomUUID().toString() } // a fresh form every time the screen opens
    val vm: TaskEditViewModel = viewModel(key = "edit-$taskId-$nonce", factory = TaskEditViewModelFactory(taskId))
    val context = LocalContext.current
    val form by vm.form.collectAsState()
    val classrooms by vm.classrooms.collectAsState()
    val knownTasks by vm.knownTasks.collectAsState()
    val existingFiles by vm.existingFiles.collectAsState()
    val loading by vm.loading.collectAsState()
    val saving by vm.saving.collectAsState()
    val error by vm.error.collectAsState()
    val errors by vm.fieldErrors.collectAsState()
    val result by vm.result.collectAsState()
    val online by Graph.network.online.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    var showRecorder by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        vm.addFiles(context.contentResolver, uris)
    }

    BackHandler(onBack = onBack)

    // a clean save leaves at once; with warnings the teacher reads them first
    val saved = result
    LaunchedEffect(saved) {
        if (saved != null && saved.failedLinks.isEmpty() && saved.fileError == null) onSaved(saved.task.id)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        ScreenHeader(
            title = if (vm.isNew) "تکلیف جدید" else "ویرایش تکلیف",
            onBack = onBack,
        )

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!online) item(key = "offline") {
                OfflineBanner(text = "📴 آفلاین هستید؛ ذخیره‌ی تکلیف به اینترنت نیاز دارد.", horizontalPadding = 0.dp)
            }
            error?.let { item(key = "error") { ErrorBanner(it) } }

            if (loading) {
                item(key = "loading") { SkeletonCard() }
                return@LazyColumn
            }

            // ---------------------------------------------------------------- class and lesson
            item(key = "where") {
                SectionCard(title = "کلاس و درس", emoji = "🏫") {
                    if (vm.isNew) {
                        if (classrooms.size > 1) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                classrooms.forEach { c ->
                                    ChoiceChip(c.title, form.classroomId == c.id, { vm.selectClassroom(c.id) })
                                }
                            }
                            errors["classroom_id"]?.let { FieldError(it) }
                        }
                        val lessons = vm.lessonsOf(form.classroomId)
                        if (form.classroomId != null) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                lessons.forEach { l -> ChoiceChip(l.title, form.lessonId == l.id, { vm.selectLesson(l.id) }) }
                            }
                        } else if (classrooms.size > 1) {
                            Text("ابتدا کلاس را انتخاب کنید.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        errors["lesson_id"]?.let { FieldError(it) }
                    } else {
                        val room = classrooms.firstOrNull { it.id == form.classroomId }
                        val lesson = vm.lessonsOf(form.classroomId).firstOrNull { it.id == form.lessonId }
                        Text("${room?.title.orEmpty()} • ${lesson?.title.orEmpty()}", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            // ---------------------------------------------------------------- text
            item(key = "description") {
                NoteField(
                    label = "شرح تکلیف",
                    value = form.description,
                    maxChars = 500,
                    minLines = 3,
                    error = errors["description"],
                ) { v -> vm.edit { it.copy(description = v) } }
            }

            item(key = "deadline") {
                SectionCard(title = "مهلت تحویل", emoji = "⏰") {
                    val deadline = form.deadline
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (deadline != null) Jalali.format(deadline) else "انتخاب نشده",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        OutlinedButton(onClick = { showPicker = true }) { Text("تغییر") }
                    }
                    errors["deadline_at"]?.let { FieldError(it) }
                }
            }

            item(key = "expected") {
                NoteField(
                    label = "پاسخ مورد انتظار (اختیاری)",
                    value = form.expectedAnswer,
                    minLines = 2,
                    error = errors["expected_answer"],
                ) { v -> vm.edit { it.copy(expectedAnswer = v) } }
            }

            // ---------------------------------------------------------------- dependencies
            val options = knownTasks.filter {
                it.classroomId == form.classroomId && it.lessonId == form.lessonId && it.id != taskId
            }.take(30)
            if (options.isNotEmpty()) item(key = "dependencies") {
                SectionCard(title = "وابسته به تکالیف قبلی (اختیاری)", emoji = "🔗") {
                    options.forEach { t ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.toggleDependency(t.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = t.id in form.dependencies, onCheckedChange = { vm.toggleDependency(t.id) })
                            Column(Modifier.weight(1f)) {
                                Text(t.description, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                t.deadlineJalali?.let {
                                    Text(it.fa(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    errors["dependencies"]?.let { FieldError(it) }
                }
            }

            // ---------------------------------------------------------------- links
            item(key = "links") {
                SectionCard(title = "لینک‌ها", emoji = "🌐") {
                    form.links.forEachIndexed { i, l ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            NoteField(
                                label = "آدرس دانلود",
                                value = l.download,
                                singleLine = true,
                                error = errors["links.$i.linkDownload"],
                            ) { v -> vm.updateLink(i, l.copy(download = v.trim())) }
                            NoteField(
                                label = "آدرس صفحه (اختیاری)",
                                value = l.page,
                                singleLine = true,
                                error = errors["links.$i.linkPage"],
                            ) { v -> vm.updateLink(i, l.copy(page = v.trim())) }
                            TextButton(onClick = { vm.removeLink(i) }) { Text("حذف این لینک", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    TextButton(onClick = vm::addLink) { Text("＋ افزودن لینک") }
                    Text(
                        "فایل لینک دانلود توسط سرور برداشته و به تکلیف پیوست می‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---------------------------------------------------------------- files
            item(key = "files") {
                SectionCard(title = "فایل‌های پیوست", emoji = "📎") {
                    existingFiles.forEach { f ->
                        val removed = f.id in form.removeFileIds
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "📎 ${f.name}",
                                Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textDecoration = if (removed) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (removed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                            TextButton(onClick = { vm.toggleRemoveExisting(f.id) }) { Text(if (removed) "بازگرداندن" else "حذف") }
                        }
                    }
                    form.newFiles.forEachIndexed { i, f ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "🆕 ${f.name}" + formatFileSize(f.size).let { if (it.isEmpty()) "" else " ($it)" },
                                Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(onClick = { vm.removeNewFile(i) }) { Text("✖") }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { filePicker.launch("*/*") }) { Text("＋ افزودن فایل") }
                        TextButton(onClick = { showRecorder = true }) { Text("🎙 ضبط صدا") }
                    }
                    errors["files"]?.let { FieldError(it) }
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
            Button(
                onClick = vm::submit,
                enabled = !saving && !loading,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(if (vm.isNew) "ساخت تکلیف" else "ذخیره‌ی تغییرات", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showRecorder) {
        VoiceRecorderSheet(
            title = "توضیح صوتی تکلیف",
            onDismiss = { showRecorder = false },
            onRecorded = { clip ->
                vm.addVoice(clip)
                showRecorder = false
            },
        )
    }

    if (showPicker) {
        JalaliDateTimeDialog(
            initial = form.deadline ?: LocalDateTime.now().plusDays(1).withHour(17).withMinute(0),
            onDismiss = { showPicker = false },
            onConfirm = { dt ->
                showPicker = false
                vm.edit { it.copy(deadline = dt) }
            },
        )
    }

    val notice = saved
    if (notice != null && (notice.failedLinks.isNotEmpty() || notice.fileError != null)) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("تکلیف ذخیره شد، اما…") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (notice.failedLinks.isNotEmpty()) {
                        Text("سرور این لینک‌ها را دانلود نکرد:")
                        notice.failedLinks.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    notice.fileError?.let { Text("فایل‌ها پیوست نشدند: $it") }
                    Text("می‌توانید با ویرایش تکلیف دوباره تلاش کنید.")
                }
            },
            confirmButton = { TextButton(onClick = { onSaved(notice.task.id) }) { Text("باشه") } },
        )
    }
}

@Composable
private fun FieldError(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}
