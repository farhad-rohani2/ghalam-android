package com.example.ghalam.core.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ghalam.core.ui.theme.Danger
import com.example.ghalam.core.ui.theme.Primary
import kotlinx.coroutines.delay

private enum class Phase { NEED_PERMISSION, READY, RECORDING, PAUSED, REVIEW, ERROR }

private const val BARS = 36

/**
 * پنل پایینی ضبط صدا (مشترک بین اپ دانش‌آموز و معلم).
 * ضبط → توقف → پیش‌نمایش → «تأیید» یا «ضبط دوباره». با تأیید، [onRecorded] فایل M4A را می‌دهد.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRecorderSheet(
    onDismiss: () -> Unit,
    onRecorded: (VoiceClip) -> Unit,
    title: String = "ضبط صدا",
) {
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder(context) }
    val levels = remember { mutableStateListOf<Float>().apply { repeat(BARS) { add(0f) } } }
    var elapsed by remember { mutableLongStateOf(0L) }
    var clip by remember { mutableStateOf<VoiceClip?>(null) }
    var errorText by remember { mutableStateOf("") }

    fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    var phase by remember { mutableStateOf(if (hasPermission()) Phase.READY else Phase.NEED_PERMISSION) }

    fun finish() {
        val c = recorder.stop()
        if (c == null) {
            errorText = "ضبط خیلی کوتاه بود؛ دوباره امتحان کنید."
            phase = Phase.READY
        } else {
            clip = c
            elapsed = c.durationMs
            phase = Phase.REVIEW
        }
    }

    fun begin() {
        levels.indices.forEach { levels[it] = 0f }
        errorText = ""
        val ok = recorder.start(onMaxReached = { finish() })
        if (ok) phase = Phase.RECORDING
        else {
            errorText = "شروع ضبط ممکن نشد؛ شاید برنامهٔ دیگری از میکروفون استفاده می‌کند."
            phase = Phase.ERROR
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        phase = if (granted) Phase.READY else Phase.NEED_PERMISSION
        if (!granted) errorText = "برای ضبط صدا باید اجازهٔ میکروفون را بدهید."
    }

    LaunchedEffect(Unit) { if (phase == Phase.NEED_PERMISSION) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    // موج زنده و زمان
    LaunchedEffect(phase) {
        while (phase == Phase.RECORDING || phase == Phase.PAUSED) {
            elapsed = recorder.elapsedMs()
            if (phase == Phase.RECORDING) {
                levels.removeAt(0)
                levels.add(recorder.level())
            }
            delay(90)
        }
    }

    // بستن پنل وسط ضبط: ضبط ناقص دور ریخته می‌شود
    DisposableEffect(Unit) { onDispose { if (recorder.isRecording) recorder.cancel() } }

    ModalBottomSheet(
        onDismissRequest = {
            if (recorder.isRecording) recorder.cancel()
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Text(
                formatVoiceTime(elapsed),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = if (phase == Phase.RECORDING) Danger else MaterialTheme.colorScheme.onSurface,
            )

            if (phase == Phase.RECORDING || phase == Phase.PAUSED || phase == Phase.READY) {
                Waveform(levels, active = phase == Phase.RECORDING)
            }

            val maxMin = VoiceConfig.MAX_DURATION_MS / 60_000
            val hint = when (phase) {
                Phase.NEED_PERMISSION -> "برای ضبط، اجازهٔ میکروفون لازم است."
                Phase.READY -> "دکمهٔ میکروفون را بزنید و صحبت کنید (حداکثر ${maxMin.toString().toFa()} دقیقه)"
                Phase.RECORDING -> "در حال ضبط…"
                Phase.PAUSED -> "ضبط متوقف است"
                Phase.REVIEW -> "پیش‌نمایش را گوش کنید"
                Phase.ERROR -> ""
            }
            if (hint.isNotEmpty()) {
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            if (errorText.isNotEmpty()) {
                Text(errorText, style = MaterialTheme.typography.bodyMedium, color = Danger, textAlign = TextAlign.Center)
            }

            when (phase) {
                Phase.NEED_PERMISSION, Phase.ERROR -> Button(onClick = {
                    if (hasPermission()) begin() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text(if (phase == Phase.ERROR) "تلاش دوباره" else "اجازه دادن") }

                Phase.READY -> RoundButton(VoiceIcons.Mic, "شروع ضبط", Primary, 72.dp) { begin() }

                Phase.RECORDING, Phase.PAUSED -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    RoundButton(VoiceIcons.Delete, "دور انداختن", MaterialTheme.colorScheme.surfaceVariant, 52.dp, Danger) {
                        recorder.cancel()
                        elapsed = 0L
                        phase = Phase.READY
                    }
                    RoundButton(VoiceIcons.Stop, "پایان ضبط", Danger, 72.dp) { finish() }
                    if (phase == Phase.RECORDING) {
                        RoundButton(VoiceIcons.Pause, "مکث", MaterialTheme.colorScheme.surfaceVariant, 52.dp, Primary) {
                            recorder.pause()
                            phase = Phase.PAUSED
                        }
                    } else {
                        RoundButton(VoiceIcons.Mic, "ادامه", Primary, 52.dp) {
                            recorder.resume()
                            phase = Phase.RECORDING
                        }
                    }
                }

                Phase.REVIEW -> {
                    val c = clip
                    if (c != null) {
                        VoicePlayerBar(c.file)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = {
                                    c.file.delete()
                                    clip = null
                                    elapsed = 0L
                                    phase = Phase.READY
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("ضبط دوباره") }
                            Button(onClick = { onRecorded(c) }, modifier = Modifier.weight(1f)) {
                                Icon(VoiceIcons.Check, null, modifier = Modifier.size(18.dp))
                                Text("  تأیید", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun String.toFa(): String = map { if (it in '0'..'9') ('۰' + (it - '0')) else it }.joinToString("")

@Composable
private fun RoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    background: Color,
    size: androidx.compose.ui.unit.Dp,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(size).clip(CircleShape).background(background).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(size * 0.45f))
    }
}

@Composable
private fun Waveform(levels: List<Float>, active: Boolean) {
    val color = if (active) Danger else Primary
    Canvas(Modifier.fillMaxWidth().height(56.dp)) {
        val gap = 4.dp.toPx()
        val barW = (size.width - gap * (levels.size - 1)) / levels.size
        levels.forEachIndexed { i, l ->
            val h = (size.height * (0.08f + 0.92f * l)).coerceAtLeast(4.dp.toPx())
            drawRoundRect(
                color = color.copy(alpha = 0.35f + 0.65f * (i + 1f) / levels.size),
                topLeft = Offset(i * (barW + gap), (size.height - h) / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
        }
    }
}
