package com.example.ghalam.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ghalam.core.ui.components.SectionCard
import com.example.ghalam.core.ui.theme.Danger
import com.example.ghalam.data.Graph
import com.example.ghalam.data.media.formatFileSize
import com.example.ghalam.ui.components.PageHeader

/** Full-screen profile page; the logout button lives here. */
@Composable
fun ProfileScreen(onBack: () -> Unit, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val used by Graph.media.usedBytes.collectAsState(initial = 0L)
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val version = remember { appVersion(ctx) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PageHeader(title = "پروفایل", onBack = onBack) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                    Text("🎓", style = MaterialTheme.typography.headlineMedium)
                }
                Column {
                    Text("دانش‌آموز", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("وارد حساب خود شده‌اید", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard("درباره‌ی برنامه", "ℹ️") {
                InfoRow("نسخه‌ی برنامه", version)
                InfoRow("فایل‌های ذخیره‌شده", formatFileSize(used))
            }

            Button(
                onClick = { confirm = true },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Danger),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                else Text("خروج از حساب", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("خروج از حساب؟") },
            text = { Text("با خروج، فایل‌های ذخیره‌شده‌ی روی گوشی هم پاک می‌شوند و برای ورود دوباره به اینترنت نیاز دارید.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    busy = true
                    onLogout()
                }) { Text("خروج", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("انصراف") } },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun appVersion(ctx: Context): String = runCatching {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
}.getOrNull() ?: "—"
