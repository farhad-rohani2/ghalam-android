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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ghalam.core.ui.components.OfflineBanner
import com.example.ghalam.core.ui.components.SectionCard
import com.example.ghalam.teacher.data.Graph
import com.example.ghalam.teacher.ui.components.ChoiceChip
import com.example.ghalam.teacher.ui.components.ErrorBanner
import com.example.ghalam.teacher.ui.components.ResultCard
import com.example.ghalam.teacher.ui.components.ScreenHeader
import com.example.ghalam.teacher.ui.viewmodel.ReportSource
import com.example.ghalam.teacher.ui.viewmodel.ReportsViewModel
import com.example.ghalam.teacher.ui.viewmodel.ReportsViewModelFactory
import com.example.ghalam.teacher.util.fa
import java.util.UUID

/**
 * Task-delivery reports (the text for the class group). [presetTaskId] != 0 = the report of that
 * one task. The server composes the text, so this needs internet.
 */
@Composable
fun ReportsScreen(presetTaskId: Long, onBack: () -> Unit) {
    val nonce = rememberSaveable { UUID.randomUUID().toString() }
    val vm: ReportsViewModel = viewModel(key = "reports-$presetTaskId-$nonce", factory = ReportsViewModelFactory(presetTaskId))
    val modes by vm.modes.collectAsState()
    val mode by vm.mode.collectAsState()
    val source by vm.source.collectAsState()
    val classrooms by vm.classrooms.collectAsState()
    val classroomFilter by vm.classroomFilter.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val selected by vm.selectedIds.collectAsState()
    val text by vm.text.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val online by Graph.network.online.collectAsState()

    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(title = "گزارش تکالیف", subtitle = if (presetTaskId != 0L) "گزارش یک تکلیف" else null, onBack = onBack)

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!online) item(key = "offline") {
                OfflineBanner(text = "📴 آفلاین هستید؛ متن گزارش را سرور می‌سازد و به اینترنت نیاز دارد.", horizontalPadding = 0.dp)
            }

            if (presetTaskId == 0L) {
                item(key = "source") {
                    SectionCard(title = "گزارش کدام تکالیف؟", emoji = "🗓") {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChoiceChip("مهلت امروز", source == ReportSource.TODAY, { vm.source.value = ReportSource.TODAY })
                            ChoiceChip("مهلت فردا", source == ReportSource.TOMORROW, { vm.source.value = ReportSource.TOMORROW })
                            ChoiceChip("انتخاب تکالیف", source == ReportSource.SELECTED, { vm.source.value = ReportSource.SELECTED })
                        }
                        if (classrooms.size > 1) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ChoiceChip("همه کلاس‌ها", classroomFilter == null, { vm.classroomFilter.value = null })
                                classrooms.forEach { c -> ChoiceChip(c.title, classroomFilter == c.id, { vm.classroomFilter.value = c.id }) }
                            }
                        }
                    }
                }
                if (source == ReportSource.SELECTED) {
                    item(key = "tasks-title") {
                        Text(
                            "تکالیف (حداکثر ۳۰) — انتخاب‌شده: ${selected.size.fa()}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(tasks.size, key = { tasks[it].task.id }) { index ->
                        val t = tasks[index].task
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.toggleTask(t.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = t.id in selected, onCheckedChange = { vm.toggleTask(t.id) })
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${t.lessonTitle} — ${t.description}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                t.deadlineJalali?.let {
                                    Text(it.fa(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            item(key = "modes") {
                SectionCard(title = "شکل گزارش", emoji = "🧾") {
                    modes.forEach { (key, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.mode.value = key },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = mode == key, onClick = { vm.mode.value = key })
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item(key = "generate") {
                Button(onClick = vm::generate, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text("ساخت گزارش", fontWeight = FontWeight.Bold)
                }
            }
            error?.let { item(key = "error") { ErrorBanner(it) } }
            text?.let { item(key = "result") { ResultCard(it) } }
        }
    }
}
