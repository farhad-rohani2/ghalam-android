package com.example.ghalam.core.ui.voice

import android.media.MediaPlayer
import android.media.PlaybackParams
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.ghalam.core.ui.theme.Primary
import java.io.File
import kotlinx.coroutines.delay

private val SPEEDS = listOf(1f, 1.5f, 2f)

/** "۰۳:۲۵" */
fun formatVoiceTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val s = "%02d:%02d".format(total / 60, total % 60)
    return s.map { if (it in '0'..'9') ('۰' + (it - '0')) else it }.joinToString("")
}

/** پخش‌کنندهٔ جمع‌وجور برای فایل صوتی روی گوشی: پخش/توقف، نوار پیشرفت، سرعت ۱× / ۱٫۵× / ۲×. */
@Composable
fun VoicePlayerBar(file: File, modifier: Modifier = Modifier) {
    var playing by remember(file.path) { mutableStateOf(false) }
    var position by remember(file.path) { mutableIntStateOf(0) }
    var duration by remember(file.path) { mutableIntStateOf(0) }
    var speedIndex by remember(file.path) { mutableIntStateOf(0) }
    var dragging by remember(file.path) { mutableFloatStateOf(-1f) }

    val player = remember(file.path) {
        runCatching {
            MediaPlayer().apply {
                setDataSource(file.path)
                prepare()
            }
        }.getOrNull()
    }
    val failed = player == null
    DisposableEffect(player) {
        player?.let { p ->
            duration = runCatching { p.duration }.getOrDefault(0)
            p.setOnCompletionListener {
                playing = false
                position = 0
            }
        }
        onDispose { player?.let { runCatching { it.stop() }; runCatching { it.release() } } }
    }

    fun applySpeed() {
        val p = player ?: return
        runCatching { p.playbackParams = PlaybackParams().setSpeed(SPEEDS[speedIndex]) }
    }

    LaunchedEffect(playing, player) {
        while (playing && player != null) {
            position = runCatching { player.currentPosition }.getOrDefault(0)
            delay(120)
        }
    }

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Primary.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Primary)
                .clickable(enabled = !failed) {
                    val p = player ?: return@clickable
                    if (playing) {
                        runCatching { p.pause() }
                        playing = false
                    } else {
                        runCatching { p.start() }
                        applySpeed()
                        playing = true
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (playing) VoiceIcons.Pause else VoiceIcons.Play, if (playing) "توقف" else "پخش", tint = Color.White)
        }

        if (failed) {
            Text("پخش این صدا ممکن نیست", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        } else {
            // خط زمان همیشه چپ‌به‌راست است
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Slider(
                    value = if (dragging >= 0f) dragging else position.toFloat(),
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        val target = dragging.toInt()
                        dragging = -1f
                        runCatching { player?.seekTo(target) }
                        position = target
                    },
                    valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                formatVoiceTime(if (playing || position > 0) position.toLong() else duration.toLong()),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                if (SPEEDS[speedIndex] == 1f) "۱×" else if (SPEEDS[speedIndex] == 1.5f) "۱٫۵×" else "۲×",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        speedIndex = (speedIndex + 1) % SPEEDS.size
                        if (playing) applySpeed()
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
