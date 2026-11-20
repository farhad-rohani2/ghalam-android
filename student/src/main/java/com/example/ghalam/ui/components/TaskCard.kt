package com.example.ghalam.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ghalam.core.ui.components.Pill
import com.example.ghalam.core.ui.theme.Danger
import com.example.ghalam.core.ui.theme.DangerBg
import com.example.ghalam.core.ui.theme.Primary
import com.example.ghalam.core.ui.theme.Success
import com.example.ghalam.core.ui.theme.SuccessBg
import com.example.ghalam.core.ui.theme.lessonTone
import com.example.ghalam.data.model.Task

/** Shows only what the teacher entered: lesson, description (title), deadline, class. */
@Composable
fun TaskCard(task: Task, onClick: () -> Unit) {
    val tone = lessonTone(task.lesson)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(tone.content))
            Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    if (task.lesson.isNotBlank()) Pill(task.lesson, tone.container, tone.content) else Spacer(Modifier.width(1.dp))
                    if (!task.deadlineAt.isNullOrBlank()) {
                        if (task.deadlineFinished) Pill("مهلت گذشته", DangerBg, Danger)
                        else Pill("مهلت: ${task.deadlineAt}", SuccessBg, Success)
                    }
                }
                Text(
                    task.description.trim(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (task.classroom.isNotBlank()) "🏫 ${task.classroom}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text("مشاهده ‹", color = Primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                }
            }
        }
    }
}
