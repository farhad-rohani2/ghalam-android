package com.example.ghalam.teacher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ghalam.core.ui.components.Pill
import com.example.ghalam.core.ui.theme.Danger
import com.example.ghalam.core.ui.theme.DangerBg
import com.example.ghalam.core.ui.theme.Primary
import com.example.ghalam.core.ui.theme.PrimaryDark
import com.example.ghalam.core.ui.theme.Success
import com.example.ghalam.core.ui.theme.SuccessBg
import com.example.ghalam.core.ui.theme.Violet
import com.example.ghalam.core.ui.theme.Warn
import com.example.ghalam.core.ui.theme.WarnBg
import com.example.ghalam.core.ui.theme.lessonTone
import com.example.ghalam.teacher.data.Graph
import com.example.ghalam.teacher.data.api.LevelDto
import com.example.ghalam.teacher.data.local.FeedbackState

/** Coloured, rounded header of every teacher screen. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brush.linearGradient(listOf(PrimaryDark, Primary, Violet))),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (onBack != null) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت", tint = Color.White) }
            }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                if (subtitle != null) {
                    Text(
                        subtitle,
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    title,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            actions()
        }
    }
}

/** Round translucent button with an emoji, for the header. */
@Composable
fun HeaderAction(emoji: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, style = MaterialTheme.typography.titleMedium) }
}

@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** Student photo; the first letter shows while it loads (or when there is no photo / no cache offline). */
@Composable
fun Avatar(photo: String?, name: String, size: Dp = 44.dp) {
    val tone = lessonTone(name)
    Box(
        Modifier.size(size).clip(CircleShape).background(tone.container),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.trim().take(1), color = tone.content, fontWeight = FontWeight.Bold)
        if (!photo.isNullOrBlank()) {
            AsyncImage(
                model = photo,
                contentDescription = null,
                imageLoader = Graph.imageLoader,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

fun statusText(status: String): String = when (status) {
    "done" -> "انجام شده"
    "no_file" -> "پاسخ بدون فایل"
    else -> "ارسال نشده"
}

@Composable
fun StatusPill(status: String, modifier: Modifier = Modifier) {
    val (bg, fg) = when (status) {
        "done" -> SuccessBg to Success
        "no_file" -> WarnBg to Warn
        else -> DangerBg to Danger
    }
    Pill(statusText(status), bg, fg, modifier)
}

/** Where a student's feedback stands (null = no feedback saved). */
@Composable
fun FeedbackBadge(state: String?, modifier: Modifier = Modifier) {
    when (state) {
        null -> Unit
        FeedbackState.SYNCED -> Pill("✅ بازخورد", SuccessBg, Success, modifier)
        FeedbackState.PENDING -> Pill("⏳ در انتظار ارسال", WarnBg, Warn, modifier)
        FeedbackState.CONFLICT -> Pill("⚠️ تعارض", DangerBg, Danger, modifier)
        else -> Pill("⚠️ ارسال نشد", DangerBg, Danger, modifier)
    }
}

/** The four levels as tappable boxes; tapping the selected one clears it. */
@Composable
fun LevelSelector(
    levels: List<LevelDto>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        levels.forEach { l ->
            val isSelected = selected == l.level
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                    .then(
                        if (isSelected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                        else Modifier,
                    )
                    .clickable { onSelect(if (isSelected) null else l.level) }
                    .padding(vertical = 8.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(l.emoji, style = MaterialTheme.typography.titleMedium)
                Text(
                    l.label,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
