package com.example.ghalam.teacher.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ghalam.core.ui.components.EmptyState
import com.example.ghalam.core.ui.components.OfflineBanner
import com.example.ghalam.core.ui.components.Pill
import com.example.ghalam.core.ui.components.SkeletonCard
import com.example.ghalam.core.ui.theme.Warn
import com.example.ghalam.core.ui.theme.WarnBg
import com.example.ghalam.core.ui.theme.lessonTone
import com.example.ghalam.teacher.data.Graph
import com.example.ghalam.teacher.data.local.TaskRow
import com.example.ghalam.teacher.ui.components.ChoiceChip
import com.example.ghalam.teacher.ui.components.HeaderAction
import com.example.ghalam.teacher.ui.components.ScreenHeader
import com.example.ghalam.teacher.ui.viewmodel.HomeViewModel
import com.example.ghalam.teacher.ui.viewmodel.TimeFilter
import com.example.ghalam.teacher.util.fa
import com.example.ghalam.teacher.util.relativeTime

/** Home: the synced tasks (from Room, so it works offline) with the sync status on top. */
@Composable
fun TeacherHomeScreen(
    onOpenTask: (Long) -> Unit,
    onNewTask: () -> Unit,
    onReports: () -> Unit,
    onSettings: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    vm: HomeViewModel = viewModel(),
) {
    val visible by vm.visibleTasks.collectAsState()
    val all by vm.allTasks.collectAsState()
    val classrooms by vm.classrooms.collectAsState()
    val sync by vm.sync.collectAsState()
    val online by vm.online.collectAsState()
    val unsynced by vm.unsynced.collectAsState()
    val classroomFilter by vm.classroomFilter.collectAsState()
    val timeFilter by vm.timeFilter.collectAsState()

    val greeting = remember {
        Graph.prefs.userName.takeIf { it.isNotBlank() }?.let { "سلام $it 👋" } ?: "سلام 👋"
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(
            title = "پنل معلم",
            subtitle = greeting,
            actions = {
                HeaderAction("➕", onClick = onNewTask)
                HeaderAction("📋", onClick = onReports)
                HeaderAction("⚙️", onClick = onSettings)
                HeaderAction("🔄", onClick = vm::refresh)
            },
        )

        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "sync") {
                SyncCard(
                    running = sync.running,
                    message = sync.message,
                    lastSuccessAt = sync.lastSuccessAt,
                    error = sync.lastError,
                    unsynced = unsynced,
                )
            }
            if (!online) {
                item(key = "offline") {
                    OfflineBanner(
                        text = "📴 آفلاین هستید؛ داده‌های ذخیره‌شده نمایش داده می‌شود و بازخوردها پس از اتصال ارسال می‌شوند.",
                        horizontalPadding = 0.dp,
                    )
                }
            }
            if (!all.isNullOrEmpty()) {
                item(key = "filters") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (classrooms.size > 1) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ChoiceChip("همه کلاس‌ها", classroomFilter == null, { vm.classroomFilter.value = null })
                                classrooms.forEach { c ->
                                    ChoiceChip(c.title, classroomFilter == c.id, { vm.classroomFilter.value = c.id })
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChoiceChip("همه", timeFilter == TimeFilter.ALL, { vm.timeFilter.value = TimeFilter.ALL })
                            ChoiceChip("در جریان", timeFilter == TimeFilter.ACTIVE, { vm.timeFilter.value = TimeFilter.ACTIVE })
                            ChoiceChip("گذشته", timeFilter == TimeFilter.PAST, { vm.timeFilter.value = TimeFilter.PAST })
                        }
                    }
                }
            }

            val list = visible
            when {
                list == null || (list.isEmpty() && all.isNullOrEmpty() && sync.running) -> {
                    items(3) { SkeletonCard() }
                }
                list.isEmpty() && all.isNullOrEmpty() -> item(key = "empty") {
                    EmptyState(
                        emoji = "📭",
                        title = "هنوز تکلیفی دریافت نشده",
                        subtitle = if (online) "برای دریافت تکالیف دکمه‌ی همگام‌سازی را بزنید." else "با اتصال به اینترنت تکالیف دریافت می‌شوند.",
                    )
                }
                list.isEmpty() -> item(key = "none") {
                    EmptyState(emoji = "🔎", title = "تکلیفی با این فیلتر نیست")
                }
                else -> items(list, key = { it.task.id }) { row ->
                    TaskCard(row, onClick = { onOpenTask(row.task.id) })
                }
            }
        }
    }
}

@Composable
private fun SyncCard(running: Boolean, message: String?, lastSuccessAt: Long, error: String?, unsynced: Int) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (running) {
                Text("⏳ ${message ?: "در حال همگام‌سازی…"}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Text(
                    if (lastSuccessAt > 0) "🔄 آخرین همگام‌سازی: ${relativeTime(lastSuccessAt)}" else "🔄 هنوز همگام‌سازی نشده",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (unsynced > 0) {
                Text(
                    "📤 ${unsynced.fa()} بازخورد در انتظار ارسال است",
                    color = Warn,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (!error.isNullOrBlank() && !running) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TaskCard(row: TaskRow, onClick: () -> Unit) {
    val t = row.task
    val tone = lessonTone(t.lessonTitle)
    val overdue = t.deadlineEpoch != null && t.deadlineEpoch < System.currentTimeMillis()
    val fraction = if (t.statsTotal > 0) t.statsDone.toFloat() / t.statsTotal else 0f

    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(t.lessonTitle, tone.container, tone.content)
                Pill(t.classroomTitle, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                if (row.pendingCount > 0) Pill("⏳ ${row.pendingCount.fa()}", WarnBg, Warn)
            }
            Text(
                t.description,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "⏰ ${(t.deadlineJalali ?: "بدون مهلت").fa()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${t.statsDone.fa()} از ${t.statsTotal.fa()} انجام داده‌اند",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(progress = { fraction }, Modifier.fillMaxWidth())
        }
    }
}
