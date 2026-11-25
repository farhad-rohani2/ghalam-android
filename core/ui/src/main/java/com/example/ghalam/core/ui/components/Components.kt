package com.example.ghalam.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.example.ghalam.core.ui.theme.shimmer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            color = content,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** White rounded card with an emoji + title row, used for every block of the detail screen. */
@Composable
fun SectionCard(
    title: String,
    emoji: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(emoji, style = MaterialTheme.typography.titleMedium)
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.width(80.dp).height(22.dp).clip(RoundedCornerShape(50)).shimmer())
                Box(Modifier.width(110.dp).height(22.dp).clip(RoundedCornerShape(50)).shimmer())
            }
            Box(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(8.dp)).shimmer())
            Box(Modifier.fillMaxWidth(0.6f).height(18.dp).clip(RoundedCornerShape(8.dp)).shimmer())
            Spacer(Modifier.height(2.dp))
            Box(Modifier.width(120.dp).height(14.dp).clip(RoundedCornerShape(8.dp)).shimmer())
        }
    }
}

@Composable
fun EmptyState(emoji: String, title: String, subtitle: String? = null, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(emoji, style = MaterialTheme.typography.displayMedium)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        action?.invoke()
    }
}

// ---------------------------------------------------------------- HoverImage

/** How long a finger must stay down before the second image is revealed (so scrolling doesn't flash it). */
private const val HOLD_TO_REVEAL_MS = 150L

/**
 * A lesson image that swaps to a second image on hover.
 *
 *  - Mouse / stylus / ChromeOS: real hover (pointer enter / exit).
 *  - Touch has no hover, so pressing and holding reveals the second image until the finger lifts.
 *    A hold never counts as a tap, so [onClick] is not fired after a reveal.
 *
 * Both images are composed at once (the hover one at alpha 0), so the second image is already loaded
 * when it is revealed and the swap is a smooth crossfade without a loading flash.
 *
 * States: shimmer while the main image loads; an emoji placeholder if it fails; if only the hover
 * image fails it is silently dropped and the main image stays.
 *
 * [showIndicator] draws the two-dot badge; turn it off for small thumbnails.
 *
 * [revealed] lets a parent drive the swap (e.g. the whole card is touched / hovered). With
 * [handleInput] = false the image ignores pointer input itself and the parent owns hover, press and
 * click; [onClick] is then unused.
 *
 * RTL: no manual mirroring is needed. The indicator is placed with [Alignment.BottomEnd] (it follows
 * the layout direction) and the images themselves are never mirrored.
 */
@Composable
fun HoverImage(
    imageUrl: String,
    hoverUrl: String?,
    imageLoader: ImageLoader,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    ratio: Float = 16f / 9f,
    shape: Shape = MaterialTheme.shapes.large,
    showIndicator: Boolean = true,
    revealed: Boolean = false,
    handleInput: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var held by remember { mutableStateOf(false) }
    val holdShown = remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)

    var mainState by remember(imageUrl) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    var hoverFailed by remember(hoverUrl) { mutableStateOf(false) }

    val hasHover = !hoverUrl.isNullOrBlank() && !hoverFailed
    val showHover = hasHover && (hovered || held || revealed)
    val hoverAlpha by animateFloatAsState(if (showHover) 1f else 0f, tween(250), label = "hoverAlpha")

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (handleInput) {
                    Modifier
                        .hoverable(interaction)
                        .pointerInput(hasHover) {
                            detectTapGestures(
                                onPress = {
                                    holdShown.value = false
                                    if (hasHover) {
                                        coroutineScope {
                                            val reveal = launch {
                                                delay(HOLD_TO_REVEAL_MS)
                                                holdShown.value = true
                                                held = true
                                            }
                                            tryAwaitRelease()
                                            reveal.cancel()
                                            held = false
                                        }
                                    }
                                },
                                onTap = { if (!holdShown.value) currentOnClick?.invoke() },
                            )
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        if (mainState is AsyncImagePainter.State.Error) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("🖼️", style = MaterialTheme.typography.headlineMedium)
            }
        } else {
            AsyncImage(
                model = remember(imageUrl) { ImageRequest.Builder(context).data(imageUrl).crossfade(true).build() },
                contentDescription = contentDescription,
                imageLoader = imageLoader,
                contentScale = ContentScale.Crop,
                onState = { mainState = it },
                modifier = Modifier.fillMaxSize(),
            )
            if (mainState is AsyncImagePainter.State.Loading) {
                Box(Modifier.fillMaxSize().shimmer())
            }
        }

        if (hasHover) {
            AsyncImage(
                model = remember(hoverUrl) { ImageRequest.Builder(context).data(hoverUrl).crossfade(true).build() },
                contentDescription = null,
                imageLoader = imageLoader,
                contentScale = ContentScale.Crop,
                onState = { if (it is AsyncImagePainter.State.Error) hoverFailed = true },
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = hoverAlpha },
            )
            if (showIndicator && mainState !is AsyncImagePainter.State.Error) {
                Row(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    HoverDot(active = !showHover)
                    HoverDot(active = showHover)
                }
            }
        }
    }
}

@Composable
private fun HoverDot(active: Boolean) {
    Box(
        Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (active) 1f else 0.45f)),
    )
}
