package com.example.ghalam.ui.components

import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Slider
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.border
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.ghalam.data.Graph
import com.example.ghalam.data.media.MediaKind
import com.example.ghalam.data.media.MediaState
import com.example.ghalam.data.media.MediaType
import com.example.ghalam.data.media.formatFileSize
import com.example.ghalam.data.model.RemoteFile
import com.example.ghalam.util.LocalPdf
import com.example.ghalam.util.MediaBitmaps
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen media viewer. Swipe between all files of the group, tap to hide/show the bars
 * (the content then gets the whole screen). Photos zoom, videos play with Media3, PDFs scroll.
 * [extraActions] adds buttons to the bottom bar (room for a future "note / annotate" action).
 */
@Composable
fun MediaViewerScreen(
    files: List<RemoteFile>,
    startIndex: Int,
    onClose: () -> Unit,
    extraActions: @Composable RowScope.() -> Unit = {},
) {
    if (files.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, files.lastIndex)) { files.size }
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    LaunchedEffect(pager.currentPage) { zoomed = false }

    val current = files[pager.currentPage.coerceIn(0, files.lastIndex)]
    val currentState by rememberMediaState(current)
    val ready = currentState as? MediaState.Ready

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 76.dp
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1020), Color.Black)))) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !zoomed,
            pageSpacing = 16.dp,
        ) { page ->
            MediaPage(
                file = files[page],
                active = pager.currentPage == page,
                chrome = chrome,
                topInset = topInset,
                bottomInset = bottomInset,
                onToggleChrome = { chrome = !chrome },
                onZoomChange = { if (pager.currentPage == page) zoomed = it },
            )
        }

        AnimatedVisibility(
            visible = chrome,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
        ) {
            ViewerTopBar(current, ready, pager.currentPage, files.size, onClose)
        }

        if (ready != null) {
            AnimatedVisibility(
                visible = chrome,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                Box(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color(0xCC141A2B))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ViewerAction(AppIcons.OpenInNew, "باز کردن با") { openWithOtherApp(ctx, ready) }
                        ViewerAction(AppIcons.Download, "ذخیره") {
                            scope.launch {
                                val ok = Graph.media.saveToDownloads(ready, current.name)
                                Toast.makeText(ctx, if (ok) "ذخیره شد (Downloads/Ghalam)" else "ذخیره ممکن نشد", Toast.LENGTH_SHORT).show()
                            }
                        }
                        extraActions()
                    }
                }
            }
        }
    }
}

private fun openWithOtherApp(ctx: Context, ready: MediaState.Ready) {
    if (!Graph.media.openExternally(ctx, ready)) {
        Toast.makeText(ctx, "برنامه‌ای برای باز کردن این فایل پیدا نشد؛ آن را در Downloads ذخیره کنید", Toast.LENGTH_LONG).show()
    }
}

// ---------------------------------------------------------------- chrome

@Composable
private fun ViewerTopBar(file: RemoteFile, ready: MediaState.Ready?, index: Int, count: Int, onClose: () -> Unit) {
    val kind = ready?.kind ?: MediaType.guessKind(file.name, file.url)
    val subtitle = buildString {
        append(kindLabel(kind))
        if (ready != null) append(" · ").append(formatFileSize(ready.file.length()))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent))),
    ) {
        Row(
            Modifier.statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, "بستن", tint = Color.White) }

            Column(Modifier.weight(1f)) {
                Text(
                    file.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(subtitle, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
            }

            if (count > 1) {
                val nf = NumberFormat.getInstance(Locale.forLanguageTag("fa"))
                Text(
                    "${nf.format(index + 1)} از ${nf.format(count)}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private fun kindLabel(kind: MediaKind) = when (kind) {
    MediaKind.IMAGE -> "عکس"
    MediaKind.VIDEO -> "ویدیو"
    MediaKind.AUDIO -> "صدا"
    MediaKind.PDF -> "PDF"
    MediaKind.OTHER -> "فایل"
}

@Composable
private fun ViewerAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

// ---------------------------------------------------------------- one page

@Composable
private fun MediaPage(
    file: RemoteFile,
    active: Boolean,
    chrome: Boolean,
    topInset: Dp,
    bottomInset: Dp,
    onToggleChrome: () -> Unit,
    onZoomChange: (Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    val state by rememberMediaState(file)
    var unsupported by remember(file.url) { mutableStateOf(false) }
    val s = state
    val ready = s as? MediaState.Ready

    Box(Modifier.fillMaxSize()) {
        when {
            ready != null && !unsupported -> when (ready.kind) {
                MediaKind.IMAGE -> ZoomableImage(ready.file, file.name, onToggleChrome, onZoomChange) { unsupported = true }
                MediaKind.VIDEO, MediaKind.AUDIO -> MediaPlayer(
                    file = ready.file,
                    isAudio = ready.kind == MediaKind.AUDIO,
                    active = active,
                    chrome = chrome,
                    topInset = topInset,
                    bottomInset = bottomInset,
                    onToggleChrome = onToggleChrome,
                ) { unsupported = true }
                MediaKind.PDF -> PdfViewer(ready.file, topInset, bottomInset, onToggleChrome) { unsupported = true }
                MediaKind.OTHER -> UnsupportedBody(ready.kind) { openWithOtherApp(ctx, ready) }
            }
            ready != null -> UnsupportedBody(ready.kind) { openWithOtherApp(ctx, ready) }
            s is MediaState.Downloading -> CenterColumn {
                if (s.progress != null) CircularProgressIndicator(progress = { s.progress }, color = Color.White)
                else CircularProgressIndicator(color = Color.White)
                Text("در حال دریافت…", color = Color.White)
            }
            s is MediaState.Large -> CenterColumn {
                Text(MediaType.guessKind(file.name, file.url).emoji, style = MaterialTheme.typography.displayMedium)
                Text(file.name, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Text(formatFileSize(s.sizeBytes), color = Color.White.copy(alpha = 0.7f))
                Button(onClick = { Graph.media.request(file, force = true) }) { Text("دریافت و نمایش", fontWeight = FontWeight.Bold) }
            }
            s is MediaState.Failed -> CenterColumn {
                Text(if (s.offline) "📡" else "⚠️", style = MaterialTheme.typography.displayMedium)
                Text(s.message, color = Color.White, textAlign = TextAlign.Center)
                Button(onClick = { Graph.media.request(file, force = true) }) { Text("تلاش مجدد", fontWeight = FontWeight.Bold) }
            }
            else -> CenterColumn { CircularProgressIndicator(color = Color.White) }
        }
    }
}

@Composable
private fun CenterColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) { content() }
}

@Composable
private fun UnsupportedBody(kind: MediaKind, onOpenWithOtherApp: () -> Unit) {
    CenterColumn {
        Text(if (kind == MediaKind.OTHER) kind.emoji else "😕", style = MaterialTheme.typography.displayMedium)
        Text(
            "پیش‌نمایش این فایل در برنامه ممکن نیست",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            "می‌توانید آن را با یکی از برنامه‌های گوشی (مثل گالری، مرورگر، Word یا PDF‌خوان) باز کنید.",
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onOpenWithOtherApp) { Text("باز کردن با برنامه‌ی دیگر", fontWeight = FontWeight.Bold) }
    }
}

// ---------------------------------------------------------------- image

@Composable
private fun ZoomableImage(
    file: File,
    name: String,
    onTap: () -> Unit,
    onZoomChange: (Boolean) -> Unit,
    onFail: () -> Unit,
) {
    val onFailNow by rememberUpdatedState(onFail)
    val bmp by produceState<ImageBitmap?>(null, file.path) {
        val b = withContext(Dispatchers.IO) { MediaBitmaps.image(file, 2600) }
        if (b == null) onFailNow() else value = b.asImageBitmap()
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(scale) { onZoomChange(scale > 1.01f) }

    val b = bmp
    if (b == null) {
        CenterColumn { CircularProgressIndicator(color = Color.White) }
        return
    }
    Image(
        bitmap = b,
        contentDescription = name,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                // A one-finger drag at normal size is NOT consumed here, so the pager can swipe.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pinching = event.changes.count { it.pressed } >= 2
                        if (pinching || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val newScale = (scale * zoom).coerceIn(1f, 6f)
                            val maxX = boxSize.width * (newScale - 1f) / 2f
                            val maxY = boxSize.height * (newScale - 1f) / 2f
                            scale = newScale
                            offset = if (newScale == 1f) Offset.Zero else Offset(
                                (offset.x + pan.x).coerceIn(-maxX, maxX),
                                (offset.y + pan.y).coerceIn(-maxY, maxY),
                            )
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
    )
}

// ---------------------------------------------------------------- video

@Composable
private fun MediaPlayer(
    file: File,
    isAudio: Boolean,
    active: Boolean,
    chrome: Boolean,
    topInset: Dp,
    bottomInset: Dp,
    onToggleChrome: () -> Unit,
    onFail: () -> Unit,
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onFailNow by rememberUpdatedState(onFail)

    val player = remember(file.path) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
        }
    }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(player, active) { player.playWhenReady = active }
    LaunchedEffect(player) {
        while (true) {
            if (!dragging) position = player.currentPosition
            duration = player.duration.takeIf { it > 0 } ?: 0L
            delay(250)
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onFailNow()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_PAUSE) player.pause() }
        player.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
    }

    // The PlayerView has no controller / touch handling of its own: a View that eats touches would
    // block the pager's swipe. Tap = show/hide bars, double tap = play/pause.
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onToggleChrome() }, onDoubleTap = { togglePlay() }) },
    ) {
        if (isAudio) {
            CenterColumn {
                Text("🎵", style = MaterialTheme.typography.displayLarge)
                Text("فایل صوتی", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.titleMedium)
            }
        } else {
            AndroidView(
                factory = { c -> PlayerView(c).apply { this.player = player; useController = false } },
                modifier = Modifier.fillMaxSize().padding(top = topInset, bottom = bottomInset),
            )
        }

        if (!playing) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { togglePlay() },
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.PlayArrow, "پخش", tint = Color.White, modifier = Modifier.size(40.dp)) }
        }

        AnimatedVisibility(
            visible = chrome,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            // seek bar always runs left to right
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xCC141A2B))
                        .padding(start = 8.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).clickable { togglePlay() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (playing) AppIcons.Pause else AppIcons.PlayArrow, if (playing) "توقف" else "پخش", tint = Color.White)
                    }
                    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                    Text(formatTime(if (dragging) (dragFraction * duration).toLong() else position), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = if (dragging) dragFraction else fraction,
                        onValueChange = {
                            dragging = true
                            dragFraction = it
                        },
                        onValueChangeFinished = {
                            if (duration > 0) player.seekTo((dragFraction * duration).toLong())
                            position = (dragFraction * duration).toLong()
                            dragging = false
                        },
                        enabled = duration > 0,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                            disabledThumbColor = Color.White.copy(alpha = 0.5f),
                            disabledActiveTrackColor = Color.White.copy(alpha = 0.5f),
                            disabledInactiveTrackColor = Color.White.copy(alpha = 0.2f),
                        ),
                    )
                    Text(formatTime(duration), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
}

// ---------------------------------------------------------------- pdf

@Composable
private fun PdfViewer(file: File, topInset: Dp, bottomInset: Dp, onTap: () -> Unit, onFail: () -> Unit) {
    val onFailNow by rememberUpdatedState(onFail)
    val doc = remember(file.path) { runCatching { LocalPdf(file) }.getOrNull() }
    DisposableEffect(doc) { onDispose { doc?.close() } }

    if (doc == null || doc.pageCount == 0) {
        LaunchedEffect(file.path) { onFailNow() }
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val sidePx = with(density) { 8.dp.roundToPx() }
        val pageWidthPx = with(density) { maxWidth.roundToPx() } - 2 * sidePx
        LazyColumn(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = topInset, bottom = bottomInset),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(doc.pageCount) { index -> PdfPage(doc, index, pageWidthPx) }
        }
    }
}

@Composable
private fun PdfPage(doc: LocalPdf, index: Int, widthPx: Int) {
    val bmp by produceState<ImageBitmap?>(null, doc, index, widthPx) {
        value = withContext(Dispatchers.IO) { doc.render(index, widthPx)?.asImageBitmap() }
    }
    val b = bmp
    if (b != null) {
        Image(
            b,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)),
        )
    } else {
        Box(Modifier.fillMaxWidth().aspectRatio(0.707f).clip(RoundedCornerShape(6.dp)).background(Color.White))
    }
}
