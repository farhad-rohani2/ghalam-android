package com.example.ghalam.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ghalam.core.ui.theme.shimmer
import com.example.ghalam.data.Graph
import com.example.ghalam.data.media.MediaKind
import com.example.ghalam.data.media.MediaState
import com.example.ghalam.data.media.MediaType
import com.example.ghalam.data.media.formatFileSize
import com.example.ghalam.data.model.RemoteFile
import com.example.ghalam.util.MediaBitmaps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * State of one remote file. Also asks the manager to restore it from disk / auto-download it
 * (re-evaluated when the user changes the size limit in settings).
 */
@Composable
fun rememberMediaState(file: RemoteFile): State<MediaState> {
    val media = Graph.media
    val limit by media.prefs.limitBytes.collectAsState()
    val key = remember(file.url) { media.keyOf(file) }
    val flow = remember(key) { media.stateOf(key) }
    val state = flow.collectAsState(initial = MediaState.Idle)
    LaunchedEffect(key, limit) { media.request(file) }
    return state
}

/** Two-column square tiles for photos, videos, PDFs and any other file. [onDelete] == null hides the delete button. */
@Composable
fun MediaGrid(
    files: List<RemoteFile>,
    deleting: Set<String>,
    onOpen: (files: List<RemoteFile>, index: Int) -> Unit,
    onDelete: ((RemoteFile) -> Unit)?,
) {
    if (files.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        files.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { f ->
                    key(f.url) {
                        MediaTile(
                            file = f,
                            modifier = Modifier.weight(1f),
                            isDeleting = f.uuid != null && f.uuid in deleting,
                            onOpen = { onOpen(files, files.indexOf(it).coerceAtLeast(0)) },
                            onDelete = onDelete,
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun MediaTile(
    file: RemoteFile,
    modifier: Modifier = Modifier,
    isDeleting: Boolean = false,
    onOpen: (RemoteFile) -> Unit,
    onDelete: ((RemoteFile) -> Unit)? = null,
) {
    val state = rememberMediaState(file).value
    val guess = remember(file.url, file.name) { MediaType.guessKind(file.name, file.url) }
    var openWhenReady by rememberSaveable(file.url) { mutableStateOf(false) }

    // the user pressed "show": open the viewer as soon as the download finishes
    LaunchedEffect(state, openWhenReady) {
        if (openWhenReady && state is MediaState.Ready) {
            openWhenReady = false
            onOpen(file)
        }
    }

    fun startDownload() {
        openWhenReady = true
        Graph.media.request(file, force = true)
    }

    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        when (state) {
            is MediaState.Ready -> ReadyCover(file, state) { onOpen(file) }
            is MediaState.Large -> LargeCover(file, guess, state.sizeBytes, ::startDownload)
            is MediaState.Downloading -> DownloadingCover(file, state.progress)
            is MediaState.Failed -> FailedCover(state, ::startDownload)
            MediaState.Idle -> Box(Modifier.fillMaxSize().shimmer())
        }

        if (onDelete != null && file.uuid != null) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(enabled = !isDeleting) { onDelete(file) },
                contentAlignment = Alignment.Center,
            ) {
                if (isDeleting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                else Icon(Icons.Filled.Close, "حذف", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- covers

private sealed interface Thumb {
    data object Loading : Thumb
    data object None : Thumb
    data class Ok(val bmp: ImageBitmap) : Thumb
}

@Composable
private fun ReadyCover(file: RemoteFile, ready: MediaState.Ready, onClick: () -> Unit) {
    val thumb by produceState<Thumb>(Thumb.Loading, ready.file.path) {
        value = withContext(Dispatchers.IO) {
            val bmp = when (ready.kind) {
                MediaKind.IMAGE -> MediaBitmaps.image(ready.file, 600)
                MediaKind.VIDEO -> MediaBitmaps.videoFrame(ready.file)
                MediaKind.PDF -> MediaBitmaps.pdfFirstPage(ready.file, 500)
                MediaKind.AUDIO, MediaKind.OTHER -> null
            }
            if (bmp != null) Thumb.Ok(bmp.asImageBitmap()) else Thumb.None
        }
    }

    Box(Modifier.fillMaxSize().clickable(onClick = onClick)) {
        when (val t = thumb) {
            is Thumb.Ok -> {
                Image(
                    bitmap = t.bmp,
                    contentDescription = file.name,
                    contentScale = if (ready.kind == MediaKind.PDF) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().background(if (ready.kind == MediaKind.PDF) Color.White else Color.Transparent),
                )
                if (ready.kind == MediaKind.VIDEO) {
                    Box(
                        Modifier.align(Alignment.Center).size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) { Text("▶", color = Color.White, style = MaterialTheme.typography.titleMedium) }
                }
                if (ready.kind != MediaKind.IMAGE) NameStrip(file.name, Modifier.align(Alignment.BottomCenter))
            }
            Thumb.Loading -> Box(Modifier.fillMaxSize().shimmer())
            Thumb.None -> GenericCover(
                emoji = ready.kind.emoji,
                name = file.name,
                note = if (ready.kind == MediaKind.IMAGE) "پیش‌نمایش ممکن نیست؛ برای باز کردن لمس کنید" else "برای باز کردن لمس کنید",
            )
        }
    }
}

@Composable
private fun NameStrip(name: String, modifier: Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(name, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GenericCover(emoji: String, name: String, note: String? = null, content: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Text(emoji, style = MaterialTheme.typography.headlineMedium)
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3,
            )
        }
        content?.invoke()
    }
}

@Composable
private fun LargeCover(file: RemoteFile, guess: MediaKind, size: Long, onShow: () -> Unit) {
    Box(Modifier.fillMaxSize().clickable(onClick = onShow)) {
        GenericCover(emoji = guess.emoji, name = file.name, note = formatFileSize(size)) {
            Button(onClick = onShow, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
                Text("نمایش", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DownloadingCover(file: RemoteFile, progress: Float?) {
    Column(
        Modifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        if (progress != null) CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
        else CircularProgressIndicator(Modifier.size(36.dp), strokeWidth = 3.dp)
        Text("در حال دریافت…", style = MaterialTheme.typography.labelMedium)
        Text(
            file.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FailedCover(state: MediaState.Failed, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Text(if (state.offline) "📡" else "⚠️", style = MaterialTheme.typography.headlineSmall)
        Text(
            state.message,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onRetry) { Text("تلاش مجدد", fontWeight = FontWeight.Bold) }
    }
}
