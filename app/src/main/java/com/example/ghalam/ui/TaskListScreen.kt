package com.example.ghalam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.example.ghalam.data.Task

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    vm: TasksViewModel,
    tasks: LazyPagingItems<Task>,
    listState: LazyListState,
    onOpen: (String) -> Unit,
) {
    val lessons by vm.lessons.collectAsState()
    val selected by vm.selectedLessons.collectAsState()
    val onlyDeadline by vm.onlyWithDeadline.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تکالیف") },
                actions = {
                    TextButton(onClick = { tasks.refresh() }) { Text("بروزرسانی") }
                    TextButton(onClick = vm::logout) { Text("خروج") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = onlyDeadline,
                        onClick = vm::toggleDeadline,
                        label = { Text("دارای مهلت") },
                    )
                }
                items(lessons) { l ->
                    val id = l.id.toIntOrNull() ?: return@items
                    FilterChip(
                        selected = id in selected,
                        onClick = { vm.toggleLesson(id) },
                        label = { Text(l.label) },
                    )
                }
            }

            val refresh = tasks.loadState.refresh
            when {
                refresh is LoadState.Loading && tasks.itemCount == 0 ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

                refresh is LoadState.Error && tasks.itemCount == 0 ->
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "دریافت اطلاعات ناموفق بود.\n(${refresh.error.message})",
                            textAlign = TextAlign.Center,
                        )
                        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { tasks.retry() }) { Text("تلاش مجدد") }
                            TextButton(onClick = vm::logout) { Text("خروج") }
                        }
                    }

                tasks.itemCount == 0 && refresh is LoadState.NotLoading ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) { Text("تکلیفی پیدا نشد") }

                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(count = tasks.itemCount, key = tasks.itemKey { it.id }) { i ->
                        tasks[i]?.let { TaskCard(it, onOpen) }
                    }
                    if (tasks.loadState.append is LoadState.Loading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(8.dp), Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    if (tasks.loadState.append is LoadState.Error) {
                        item {
                            TextButton(onClick = { tasks.retry() }, modifier = Modifier.fillMaxWidth()) {
                                Text("خطا در دریافت ادامه لیست؛ تلاش مجدد")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TaskCard(task: Task, onOpen: (String) -> Unit) {
    Card(
        onClick = { if (task.uuid.isNotBlank()) onOpen(task.uuid) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = {}, label = { Text(task.lesson) })
                AssistChip(
                    onClick = {},
                    label = { Text(if (task.deadlineFinished) "مهلت تمام شده" else "مهلت: ${task.deadlineAt ?: "-"}") },
                )
            }
            Text(task.description.trim(), style = MaterialTheme.typography.titleMedium)
            Text(task.classroom, style = MaterialTheme.typography.bodySmall)
            Text("بروزرسانی: ${task.updatedAt}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
