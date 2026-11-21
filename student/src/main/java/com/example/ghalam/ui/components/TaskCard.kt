package com.example.ghalam.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.ghalam.core.ui.components.HoverImage
import com.example.ghalam.core.ui.components.Pill
import com.example.ghalam.core.ui.theme.Danger
import com.example.ghalam.core.ui.theme.DangerBg
import com.example.ghalam.core.ui.theme.Primary
import com.example.ghalam.core.ui.theme.Success
import com.example.ghalam.core.ui.theme.SuccessBg
import com.example.ghalam.core.ui.theme.lessonTone
import com.example.ghalam.data.Graph
import com.example.ghalam.data.model.Task
import kotlinx.coroutines.delay

// Same host as ApiConfig's base URL; only used when the server sends a relative media path.
private const val MEDIA_BASE_URL = "https://ghalam.razzar.ir/"

private val THUMB_SIZE = 64.dp

/** A touch must stay down this long before the second image shows (so scrolling doesn't flash it). */
private const val HOLD_TO_REVEAL_MS = 150L

private fun mediaUrl(path: String): String =
    if (path.startsWith("http://") || path.startsWith("https://")) path else MEDIA_BASE_URL + path.trimStart('/')

/**
 * Shows only what the teacher entered: lesson image (second image on hover / press-and-hold),
 * lesson, description (title), deadline, class.
 *
 * Touching or hovering ANYWHERE on the card swaps the thumbnail to its second image; a quick tap
 * still opens the task, a hold that revealed the image does not.
 *
 * The small image sits on the physical LEFT edge. The card height is still decided by the text
 * column, the thumbnail is just centered in it.
 */
@Composable
fun TaskCard(task: Task, onClick: () -> Unit) {
    val tone = lessonTone(task.lesson)
    val mainPath = task.media?.main?.takeIf { it.isNotBlank() }
    val hoverPath = task.media?.hover?.takeIf { it.isNotBlank() }
    // If only one of the two images exists it is shown as the plain (non-hover) image.
    val image = mainPath ?: hoverPath
    val hover = if (mainPath != null) hoverPath else null

    // Start = right in RTL, so the physical left is the end side there and the start side in LTR.
    val imageAtEnd = LocalLayoutDirection.current == LayoutDirection.Rtl

    val cardInteraction = remember { MutableInteractionSource() }
    val hovered by cardInteraction.collectIsHoveredAsState()
    val pressed by cardInteraction.collectIsPressedAsState()
    var held by remember { mutableStateOf(false) }
    val holdShown = remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (pressed) {
            holdShown.value = false
            delay(HOLD_TO_REVEAL_MS)
            held = true
            holdShown.value = true
        } else {
            held = false
        }
    }
    val revealed = hovered || held

    val thumb: @Composable RowScope.() -> Unit = {
        if (image != null) {
            HoverImage(
                imageUrl = mediaUrl(image),
                hoverUrl = hover?.let(::mediaUrl),
                imageLoader = Graph.imageLoader,
                contentDescription = task.lesson.ifBlank { null },
                modifier = Modifier.padding(horizontal = 12.dp).size(THUMB_SIZE).align(Alignment.CenterVertically),
                ratio = 1f,
                shape = RoundedCornerShape(12.dp),
                showIndicator = false,
                revealed = revealed,
                handleInput = false, // the whole card owns hover / press / click
            )
        }
    }

    Card(
        onClick = { if (!holdShown.value) onClick() },
        modifier = Modifier.fillMaxWidth().hoverable(cardInteraction),
        interactionSource = cardInteraction,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            if (!imageAtEnd) thumb()
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("مشاهده", color = Primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        // auto-mirrored: points to the left in RTL (the "forward" direction)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (imageAtEnd) thumb()
        }
    }
}
