package com.example.ghalam.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ghalam.core.ui.components.SectionCard
import com.example.ghalam.core.ui.theme.Danger
import com.example.ghalam.core.ui.theme.Primary
import com.example.ghalam.data.Graph
import com.example.ghalam.data.media.MediaPrefs
import com.example.ghalam.data.media.formatFileSize
import com.example.ghalam.ui.components.PageHeader
import com.example.ghalam.ui.components.SubmitSettingsDialog
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Full-screen settings page (file download behaviour and storage). */
@Composable
fun MediaSettingsScreen(onBack: () -> Unit) {
    val media = Graph.media
    val limit by media.prefs.limitBytes.collectAsState()
    val used by media.usedBytes.collectAsState(initial = 0L)
    val scope = rememberCoroutineScope()
    val options = MediaPrefs.OPTIONS

    var index by remember(limit) { mutableFloatStateOf(closestIndex(options, limit).toFloat()) }
    var confirmClear by remember { mutableStateOf(false) }
    var submitSettings by remember { mutableStateOf(false) }
    val selected = options[index.roundToInt().coerceIn(0, options.lastIndex)]

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PageHeader(
            title = "تنظیمات",
            onBack = onBack,
            subtitle = "تعیین کنید فایل‌ها چطور روی گوشی ذخیره شوند.",
        )

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard("ارسال پاسخ‌ها", "📤") {
                Text(
                    "تعیین کنید عکس‌های پاسخ چه زمانی و با چه اینترنتی ارسال شوند.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { submitSettings = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("تنظیمات ارسال")
                }
            }

            SectionCard("دانلود خودکار فایل‌ها", "⬇️") {
                Text(
                    "فایل‌هایی که حجمشان از این مقدار بیشتر نباشد خودکار ذخیره می‌شوند تا بدون اینترنت هم در دسترس باشند. " +
                        "برای فایل‌های بزرگ‌تر دکمه‌ی «نمایش» نشان داده می‌شود.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    limitLabel(selected),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                )
                Slider(
                    value = index,
                    onValueChange = { index = it },
                    onValueChangeFinished = { media.prefs.setLimitBytes(selected) },
                    valueRange = 0f..options.lastIndex.toFloat(),
                    steps = options.size - 2,
                )
                if (selected >= 10 * MediaPrefs.MB) {
                    Text(
                        "با مقدار زیاد، مصرف اینترنت بیشتر می‌شود.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard("فضای ذخیره‌شده", "💾") {
                Text("حجم فایل‌های ذخیره‌شده روی گوشی: ${formatFileSize(used)}", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { confirmClear = true }, enabled = used > 0, modifier = Modifier.fillMaxWidth()) {
                    Text("پاک‌کردن فایل‌های ذخیره‌شده")
                }
            }
        }
    }

    if (submitSettings) SubmitSettingsDialog(onDismiss = { submitSettings = false })

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("پاک‌کردن فایل‌ها") },
            text = { Text("فایل‌های ذخیره‌شده از گوشی حذف می‌شوند و هنگام نیاز دوباره دریافت خواهند شد.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { media.clearAll() }
                }) { Text("پاک شود", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("انصراف") } },
        )
    }
}

private fun closestIndex(options: List<Long>, value: Long): Int =
    options.indices.minByOrNull { i ->
        val o = options[i]
        if (o == Long.MAX_VALUE || value == Long.MAX_VALUE) (if (o == value) 0L else Long.MAX_VALUE) else abs(o - value)
    } ?: 0

private fun limitLabel(v: Long): String = when (v) {
    0L -> "خاموش؛ هیچ فایلی خودکار دانلود نشود"
    Long.MAX_VALUE -> "همه‌ی فایل‌ها، بدون محدودیت حجم"
    else -> "تا ${formatFileSize(v)}"
}
