package com.example.ghalam.teacher.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ghalam.core.ui.components.SectionCard
import com.example.ghalam.core.ui.theme.Danger
import com.example.ghalam.teacher.data.Graph
import com.example.ghalam.teacher.data.sync.SyncStatus
import com.example.ghalam.teacher.ui.components.ChoiceChip
import com.example.ghalam.teacher.ui.components.ScreenHeader
import com.example.ghalam.teacher.util.fa
import com.example.ghalam.teacher.util.formatFileSize
import com.example.ghalam.teacher.util.relativeTime
import kotlinx.coroutines.launch

/** Sync, downloaded files and the account. The settings are stored in [com.example.ghalam.teacher.data.SyncPrefs]. */
@Composable
fun SettingsScreen(onBack: () -> Unit, onLogout: () -> Unit) {
    val prefs = Graph.prefs
    val scope = rememberCoroutineScope()
    var auto by remember { mutableStateOf(prefs.mediaAuto) }
    var unmeteredOnly by remember { mutableStateOf(prefs.mediaUnmeteredOnly) }
    var limitMb by remember { mutableStateOf((prefs.mediaLimitBytes / MB).toInt()) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    val usedFlow = remember { Graph.db.mediaDao().observeUsedBytes() }
    val unsyncedFlow = remember { Graph.repo.unsyncedCount() }
    val used by usedFlow.collectAsState(initial = 0L)
    val unsynced by unsyncedFlow.collectAsState(initial = 0)
    val sync by SyncStatus.state.collectAsState()

    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(title = "تنظیمات", subtitle = Graph.prefs.userName.takeIf { it.isNotBlank() }, onBack = onBack)

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "sync") {
                SectionCard(title = "همگام‌سازی", emoji = "🔄") {
                    Text(
                        if (sync.running) "⏳ ${sync.message ?: "در حال همگام‌سازی…"}"
                        else if (sync.lastSuccessAt > 0) "آخرین همگام‌سازی: ${relativeTime(sync.lastSuccessAt)}"
                        else "هنوز همگام‌سازی نشده",
                    )
                    if (unsynced > 0) Text("📤 ${unsynced.fa()} بازخورد در انتظار ارسال است", color = MaterialTheme.colorScheme.error)
                    Text(
                        "برنامه خودش هر چند ساعت یک بار (با اینترنت) تکالیف ۳۰ روز اخیر و پاسخ‌های دانش‌آموزان را دریافت و بازخوردهای ذخیره‌شده را ارسال می‌کند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { Graph.scheduler.syncNow(full = true) }, enabled = !sync.running) {
                        Text("همگام‌سازی کامل اکنون")
                    }
                }
            }

            item(key = "files") {
                SectionCard(title = "فایل‌های دانش‌آموزان", emoji = "📎") {
                    SwitchRow("دانلود خودکار در پس‌زمینه", auto) {
                        auto = it
                        prefs.mediaAuto = it
                    }
                    SwitchRow("فقط روی Wi‑Fi (نه اینترنت همراه)", unmeteredOnly) {
                        unmeteredOnly = it
                        prefs.mediaUnmeteredOnly = it
                    }
                    Text("بزرگ‌ترین فایلی که خودکار دانلود می‌شود:", style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 25, 50, 100).forEach { mb ->
                            ChoiceChip("${mb.fa()} مگابایت", limitMb == mb, {
                                limitMb = mb
                                prefs.mediaLimitBytes = mb * MB
                            })
                        }
                    }
                    Text(
                        "فایل‌های بزرگ‌تر با دکمه‌ی «دانلود» در صفحه‌ی پاسخ دانلود می‌شوند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("فضای اشغال‌شده: ${formatFileSize(used).ifEmpty { "۰" }}", fontWeight = FontWeight.Medium)
                    OutlinedButton(onClick = { confirmClear = true }, enabled = used > 0) { Text("پاک کردن فایل‌های دانلودشده") }
                }
            }

            item(key = "account") {
                SectionCard(title = "حساب", emoji = "👤") {
                    OutlinedButton(onClick = { confirmLogout = true }) { Text("خروج از حساب", color = Danger) }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("پاک کردن فایل‌ها") },
            text = { Text("فایل‌های دانلودشده از گوشی پاک می‌شوند. هر وقت لازم باشد دوباره دانلود می‌شوند.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { Graph.media.clearAll() }
                }) { Text("پاک کن") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("انصراف") } },
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("خروج از حساب") },
            text = {
                Text(
                    if (unsynced > 0) "${unsynced.fa()} بازخورد هنوز به سرور ارسال نشده و با خروج از بین می‌رود. اگر می‌خواهید آن‌ها را نگه دارید، اول به اینترنت وصل شوید و همگام‌سازی کنید. خارج می‌شوید؟"
                    else "داده‌های ذخیره‌شده‌ی این حساب از روی گوشی پاک می‌شود.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmLogout = false; onLogout() }) { Text("خروج", color = Danger) } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("انصراف") } },
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private const val MB = 1024L * 1024L
