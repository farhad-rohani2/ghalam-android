package com.example.ghalam.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ghalam.data.Graph
import kotlinx.coroutines.launch

/** How answers are sent. Written as a dialog so it can later move into a full settings page unchanged. */
@Composable
fun SubmitSettingsDialog(onDismiss: () -> Unit) {
    val prefs = Graph.prefs
    val scope = rememberCoroutineScope()

    var auto by remember { mutableStateOf(prefs.autoUpload) }
    var wifi by remember { mutableStateOf(prefs.wifiOnly) }
    var limit by remember { mutableStateOf(prefs.limitAuto) }
    var limitMb by remember { mutableFloatStateOf(prefs.limitAutoMb.toFloat()) }
    var notify by remember { mutableStateOf(prefs.notifyResult) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تنظیمات ارسال") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingRow(
                    title = "ارسال خودکار عکس‌ها",
                    subtitle = "عکس‌ها در پس‌زمینه و به‌محض وجود اینترنت ارسال می‌شوند. متن پاسخ همیشه خودکار ارسال می‌شود.",
                    checked = auto,
                ) {
                    auto = it
                    prefs.autoUpload = it
                    if (it) scope.launch { Graph.outbox.releaseHeld(force = false) }
                }
                SettingRow(
                    title = "فقط با وای‌فای",
                    subtitle = "اگر خاموش باشد با هر اینترنتی (از جمله دیتای همراه) ارسال می‌شود.",
                    checked = wifi,
                ) {
                    wifi = it
                    prefs.wifiOnly = it
                    Graph.scheduler.replaceWorker()
                    Graph.kickUpload()
                }
                SettingRow(
                    title = "محدودیت حجم برای ارسال خودکار",
                    subtitle = "فایل‌های بزرگ‌تر از حد زیر منتظر دکمهٔ «ارسال» می‌مانند. پیش‌فرض: بدون محدودیت.",
                    checked = limit,
                ) {
                    limit = it
                    prefs.limitAuto = it
                    if (!it) scope.launch { Graph.outbox.releaseHeld(force = false) }
                }
                if (limit) {
                    Text("حداکثر حجم ارسال خودکار: ${limitMb.toInt()} مگابایت", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = limitMb,
                        onValueChange = {
                            limitMb = it
                            prefs.limitAutoMb = it.toInt()
                        },
                        valueRange = 5f..50f,
                        steps = 8,
                    )
                }
                SettingRow(
                    title = "اعلان نتیجهٔ ارسال",
                    subtitle = "فقط وقتی برنامه بسته است و اعلان‌ها در تنظیمات گوشی فعال باشند.",
                    checked = notify,
                ) {
                    notify = it
                    prefs.notifyResult = it
                }
                Text(
                    "فایل بزرگ‌تر از ۶۰ مگابایت هرگز ارسال نمی‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
    )
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
