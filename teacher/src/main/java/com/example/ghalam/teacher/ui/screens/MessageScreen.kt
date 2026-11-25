package com.example.ghalam.teacher.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ghalam.core.ui.components.OfflineBanner
import com.example.ghalam.core.ui.components.SectionCard
import com.example.ghalam.core.ui.components.SkeletonCard
import com.example.ghalam.teacher.data.Graph
import com.example.ghalam.teacher.ui.components.ChoiceChip
import com.example.ghalam.teacher.ui.components.ErrorBanner
import com.example.ghalam.teacher.ui.components.NoteField
import com.example.ghalam.teacher.ui.components.ResultCard
import com.example.ghalam.teacher.ui.components.ScreenHeader
import com.example.ghalam.teacher.ui.viewmodel.MessageViewModel
import com.example.ghalam.teacher.ui.viewmodel.MessageViewModelFactory
import com.example.ghalam.teacher.util.fa
import java.util.UUID

/**
 * A message to one student from a template; the server fills in the student's real data. The text
 * is then copied / shared into the messenger (Shad, Eitaa, Bale ...). Needs internet.
 */
@Composable
fun MessageScreen(taskId: Long, studentId: Long, onBack: () -> Unit) {
    val nonce = rememberSaveable { UUID.randomUUID().toString() }
    val vm: MessageViewModel = viewModel(key = "message-$taskId-$studentId-$nonce", factory = MessageViewModelFactory(taskId, studentId))
    val submission by vm.submission.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val templates by vm.templates.collectAsState()
    val tokens by vm.tokens.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val current by vm.current.collectAsState()
    val body by vm.body.collectAsState()
    val selectedTaskIds by vm.selectedTaskIds.collectAsState()
    val result by vm.result.collectAsState()
    val busy by vm.busy.collectAsState()
    val online by Graph.network.online.collectAsState()
    var newTemplate by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        ScreenHeader(title = "پیام به دانش‌آموز", subtitle = submission?.name, onBack = onBack)

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!online) item(key = "offline") {
                OfflineBanner(text = "📴 آفلاین هستید؛ ساخت پیام به اینترنت نیاز دارد.", horizontalPadding = 0.dp)
            }
            error?.let { item(key = "error") { ErrorBanner(it) } }

            if (loading) {
                item(key = "loading") { SkeletonCard() }
                return@LazyColumn
            }
            if (templates.isEmpty()) {
                item(key = "retry") { OutlinedButton(onClick = vm::load) { Text("تلاش دوباره") } }
                return@LazyColumn
            }

            item(key = "templates") {
                SectionCard(title = "قالب پیام", emoji = "✉️") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        templates.forEach { t -> ChoiceChip(t.label, t.key == current?.key, { vm.select(t.key) }) }
                    }
                }
            }

            item(key = "body") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NoteField(label = "متن پیام", value = body, minLines = 5, maxChars = 3000) { vm.setBody(it) }
                    if (tokens.isNotEmpty()) {
                        Text("افزودن مقدار خودکار به انتهای متن:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            tokens.keys.forEach { token -> ChoiceChip(token, false, { vm.insertToken(token) }) }
                        }
                    }
                }
            }

            item(key = "template-actions") {
                val template = current
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::saveTemplate, enabled = !busy && online && vm.modified()) { Text("ذخیره‌ی قالب") }
                    if (template != null && (template.custom || template.body != template.defaultBody)) {
                        OutlinedButton(onClick = vm::resetOrDelete, enabled = !busy && online) {
                            Text(if (template.custom) "حذف قالب" else "بازنشانی به پیش‌فرض")
                        }
                    }
                    OutlinedButton(onClick = { newTemplate = true }, enabled = !busy && online) { Text("＋ قالب جدید") }
                }
            }

            if (body.contains("{تکالیف}") && tasks.isNotEmpty()) item(key = "tasks") {
                SectionCard(title = "تکالیف داخل پیام", emoji = "📚") {
                    tasks.forEach { t ->
                        Row(Modifier.fillMaxWidth().clickable { vm.toggleTask(t.id) }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = t.id in selectedTaskIds, onCheckedChange = { vm.toggleTask(t.id) })
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${t.lesson} — ${t.description}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    t.statusLabel + (t.deadlineJalali?.let { " • ${it.fa()}" } ?: ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "compose") {
                Button(onClick = vm::compose, enabled = !busy && online, modifier = Modifier.fillMaxWidth()) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text("ساخت پیام", fontWeight = FontWeight.Bold)
                }
            }
            result?.let { item(key = "result") { ResultCard(it) } }
        }
    }

    if (newTemplate) {
        var label by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newTemplate = false },
            title = { Text("قالب جدید") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NoteField(label = "عنوان قالب", value = label, singleLine = true, maxChars = 40) { label = it }
                    Text("متن فعلی پیام به‌عنوان متن قالب ذخیره می‌شود.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { newTemplate = false; vm.createTemplate(label) },
                    enabled = label.isNotBlank(),
                ) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { newTemplate = false }) { Text("انصراف") } },
        )
    }
}
