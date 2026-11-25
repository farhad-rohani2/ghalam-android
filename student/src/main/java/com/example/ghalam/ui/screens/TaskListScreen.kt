package com.example.ghalam.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.example.ghalam.data.Graph
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.example.ghalam.core.ui.components.EmptyState
import com.example.ghalam.core.ui.components.OfflineBanner
import com.example.ghalam.core.ui.components.SkeletonCard
import com.example.ghalam.core.ui.theme.Primary
import com.example.ghalam.core.ui.theme.PrimaryDark
import com.example.ghalam.core.ui.theme.Violet
import com.example.ghalam.data.model.Task
import com.example.ghalam.ui.components.TaskCard
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.example.ghalam.ui.components.HeaderMenu
import com.example.ghalam.ui.viewmodel.TasksViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskListScreen(
    vm: TasksViewModel,
    tasks: LazyPagingItems<Task>,
    listState: LazyGridState,
    onOpen: (uuid: String, finished: Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    // per-task count of answers that are not sent yet (badge on the card)
    val outboxSummary by remember { Graph.outbox.summary().map { list -> list.associateBy { it.taskUuid } } }
        .collectAsState(initial = emptyMap())
    val filters by vm.filters.collectAsState()
    val selected by vm.selected.collectAsState()
    val active by vm.active.collectAsState()
    val query by vm.query.collectAsState()
    var showSheet by remember { mutableStateOf(false) }

    // Search bar + filter chips hide while the list scrolls down and come back on scroll up / at the top.
    var barsVisible by remember { mutableStateOf(true) }
    val queryNow by rememberUpdatedState(query)
    val scrollWatcher = remember {
        object : NestedScrollConnection {
            private var acc = 0f
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy != 0f) {
                    if (acc != 0f && (acc > 0f) != (dy > 0f)) acc = 0f
                    acc += dy
                    if (acc < -60f && queryNow.isBlank()) {
                        barsVisible = false
                        acc = 0f
                    } else if (acc > 60f) {
                        barsVisible = true
                        acc = 0f
                    }
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
            .collect { atTop -> if (atTop) barsVisible = true }
    }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).nestedScroll(scrollWatcher)) {
        Header(
            query = query,
            onQuery = vm::setQuery,
            filterCount = active.size,
            hasFilters = filters.isNotEmpty(),
            onFilter = { showSheet = true },
            onProfile = onOpenProfile,
            onSettings = onOpenSettings,
            searchVisible = barsVisible,
        )

        AnimatedVisibility(
            visible = active.isNotEmpty() && barsVisible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(active, key = { it.key + "|" + it.id }) { a ->
                    InputChip(
                        selected = true,
                        onClick = { vm.remove(a.key, a.id) },
                        label = { Text(a.label) },
                        trailingIcon = { Icon(Icons.Filled.Close, null, Modifier.size(16.dp)) },
                    )
                }
                item {
                    TextButton(onClick = vm::clearFilters) { Text("پاک کردن همه") }
                }
            }
        }

        val refresh = tasks.loadState.refresh
        when {
            refresh is LoadState.Loading && tasks.itemCount == 0 ->
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(4) { SkeletonCard() }
                }

            refresh is LoadState.Error && tasks.itemCount == 0 ->
                EmptyState(
                    emoji = "😕",
                    title = "دریافت اطلاعات ناموفق بود",
                    subtitle = refresh.error.message,
                    modifier = Modifier.padding(top = 48.dp),
                ) {
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { tasks.retry() }) { Text("تلاش مجدد") }
                        TextButton(onClick = vm::logout) { Text("خروج") }
                    }
                }

            tasks.itemCount == 0 && refresh is LoadState.NotLoading ->
                EmptyState(
                    emoji = "🎒",
                    title = "تکلیفی پیدا نشد",
                    subtitle = if (active.isNotEmpty() || query.isNotBlank()) "فیلترها یا جستجو را تغییر دهید" else "فعلاً تکلیفی برای شما ثبت نشده است",
                    modifier = Modifier.padding(top = 48.dp),
                ) {
                    if (active.isNotEmpty() || query.isNotBlank()) {
                        Button(onClick = vm::clearFilters, modifier = Modifier.padding(top = 8.dp)) { Text("حذف فیلترها") }
                    }
                }

            else -> PullToRefreshBox(
                isRefreshing = refresh is LoadState.Loading && tasks.itemCount > 0,
                onRefresh = { tasks.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                // 1 column on a portrait phone, 2+ in landscape / on tablets
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 340.dp),
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp + navBottom),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (refresh is LoadState.Error) {
                        item(key = "offline-banner", span = { GridItemSpan(maxLineSpan) }) {
                            OfflineBanner(
                                text = "📴 اتصال برقرار نیست؛ لیست ذخیره‌شده نمایش داده می‌شود.",
                                horizontalPadding = 0.dp,
                            )
                        }
                    }
                    items(count = tasks.itemCount, key = tasks.itemKey { it.id }) { i ->
                        tasks[i]?.let { t -> TaskCard(t, pending = outboxSummary[t.uuid]) { onOpen(t.uuid, t.deadlineFinished) } }
                    }
                    if (tasks.loadState.append is LoadState.Loading) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(8.dp), Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                            }
                        }
                    }
                    if (tasks.loadState.append is LoadState.Error) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            TextButton(onClick = { tasks.retry() }, modifier = Modifier.fillMaxWidth()) {
                                Text("خطا در دریافت ادامه لیست؛ تلاش مجدد")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp + navBottom),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("فیلترها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = vm::clearFilters) { Text("پاک کردن همه") }
                }
                filters.forEach { f ->
                    val options = remember(f) { f.parsedOptions() }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(f.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            options.forEach { o ->
                                FilterChip(
                                    selected = o.id in selected[f.key].orEmpty(),
                                    onClick = { vm.toggle(f, o.id) },
                                    label = { Text(o.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Primary,
                                        selectedLabelColor = Color.White,
                                    ),
                                )
                            }
                        }
                    }
                }
                Button(onClick = { showSheet = false }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text("نمایش نتایج", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun Header(
    query: String,
    onQuery: (String) -> Unit,
    filterCount: Int,
    hasFilters: Boolean,
    onFilter: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    searchVisible: Boolean,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brush.linearGradient(listOf(PrimaryDark, Primary, Violet))),
    ) {
        Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("سلام 👋", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                    Text("تکالیف من", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                HeaderMenu(onProfile = onProfile, onSettings = onSettings)
            }
            AnimatedVisibility(
                visible = searchVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextField(
                            value = query,
                            onValueChange = onQuery,
                            placeholder = { Text("جستجوی تکلیف…") },
                            leadingIcon = { Icon(Icons.Filled.Search, null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, "پاک کردن") }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        if (hasFilters) {
                            Box(
                                Modifier
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.22f))
                                    .clickable(onClick = onFilter)
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("فیلتر", color = Color.White, fontWeight = FontWeight.Medium)
                                    if (filterCount > 0) {
                                        Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                                            Text("$filterCount", color = PrimaryDark, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
