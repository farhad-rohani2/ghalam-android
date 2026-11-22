package com.example.ghalam.teacher.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ghalam.core.ui.components.EmptyState
import com.example.ghalam.core.ui.components.OfflineBanner
import com.example.ghalam.core.ui.components.SectionCard
import com.example.ghalam.core.ui.components.SkeletonCard
import com.example.ghalam.teacher.data.Graph
import com.example.ghalam.teacher.data.local.SubmissionRow
import com.example.ghalam.teacher.ui.components.Avatar
import com.example.ghalam.teacher.ui.components.ChoiceChip
import com.example.ghalam.teacher.ui.components.FeedbackBadge
import com.example.ghalam.teacher.ui.components.ScreenHeader
import com.example.ghalam.teacher.ui.components.StatusPill
import com.example.ghalam.teacher.ui.viewmodel.TaskViewModel
import com.example.ghalam.teacher.ui.viewmodel.TaskViewModelFactory
import com.example.ghalam.teacher.util.fa
import com.example.ghalam.teacher.util.formatFileSize

/** One task: its description and who has submitted what (all from Room, so it works offline). */
@Composable
fun TaskScreen(
    taskId: Long,
    onBack: () -> Unit,
    onOpenStudent: (Long) -> Unit,
) {
    val vm: TaskViewModel = viewModel(key = "task-$taskId", factory = TaskViewModelFactory(taskId))
    val context = LocalContext.current
    val task by vm.task.collectAsState()
    val detail by vm.detail.collectAsState()
    val rows by vm.visible.collectAsState()
    val all by vm.all.collectAsState()
    val counts by vm.counts.collectAsState()
    val statusFilter by vm.statusFilter.collectAsState()
    val query by vm.query.collectAsState()
    val online by Graph.network.online.collectAsState()

    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(
            title = task?.lessonTitle ?: "تکلیف",
            subtitle = task?.let { "${it.classroomTitle} • ⏰ ${(it.deadlineJalali ?: "بدون مهلت").fa()}" },
            onBack = onBack,
        )

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!online) {
                item(key = "offline") { OfflineBanner(horizontalPadding = 0.dp) }
            }

            item(key = "detail") {
                SectionCard(title = "شرح تکلیف", emoji = "📝") {
                    Text(task?.description.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    val d = detail
                    if (d != null && d.expectedAnswer.isNotBlank()) {
                        Text("پاسخ مورد انتظار", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(d.expectedAnswer, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (d != null && d.files.isNotEmpty()) {
                        Text("فایل‌های تکلیف", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        d.files.forEach { f ->
                            Text(
                                "📎 ${f.name}" + formatFileSize(f.size).let { if (it.isEmpty()) "" else " ($it)" },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    if (d != null && d.links.isNotEmpty()) {
                        Text("لینک‌ها", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        d.links.mapNotNull { it.linkPage?.takeIf { l -> l.isNotBlank() } }.forEach { link ->
                            Text(
                                "🔗 $link",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.clickable {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    }
                                },
                            )
                        }
                    }
                }
            }

            item(key = "filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val total = counts.values.sum()
                        ChoiceChip("همه (${total.fa()})", statusFilter == null, { vm.statusFilter.value = null })
                        ChoiceChip("انجام شده (${(counts["done"] ?: 0).fa()})", statusFilter == "done", { vm.statusFilter.value = "done" })
                        ChoiceChip("بدون فایل (${(counts["no_file"] ?: 0).fa()})", statusFilter == "no_file", { vm.statusFilter.value = "no_file" })
                        ChoiceChip("ارسال نشده (${(counts["none"] ?: 0).fa()})", statusFilter == "none", { vm.statusFilter.value = "none" })
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { vm.query.value = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("جستجوی نام دانش‌آموز") },
                        shape = MaterialTheme.shapes.medium,
                    )
                }
            }

            val list = all
            when {
                list == null -> items(3) { SkeletonCard() }
                list.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        emoji = "📭",
                        title = "اطلاعات تحویل این تکلیف هنوز دریافت نشده",
                        subtitle = if (online) "از صفحه‌ی اصلی همگام‌سازی کنید." else "با اتصال به اینترنت دریافت می‌شود.",
                    )
                }
                rows.isEmpty() -> item(key = "none") { EmptyState(emoji = "🔎", title = "دانش‌آموزی با این فیلتر نیست") }
                else -> items(rows, key = { it.s.studentId }) { row ->
                    StudentRow(row, onClick = { onOpenStudent(row.s.studentId) })
                }
            }
        }
    }
}

@Composable
private fun StudentRow(row: SubmissionRow, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Avatar(row.s.photo, row.s.name, 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(row.s.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(row.s.status)
                    FeedbackBadge(row.feedbackState)
                }
                val meta = buildList {
                    if (row.s.filesCount > 0) add("📎 ${row.s.filesCount.fa()} فایل")
                    row.s.answeredAtJalali?.let { add(it.fa()) }
                }
                if (meta.isNotEmpty()) {
                    Text(
                        meta.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
