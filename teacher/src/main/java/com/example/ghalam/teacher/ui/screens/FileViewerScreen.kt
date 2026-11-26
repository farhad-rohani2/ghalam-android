package com.example.ghalam.teacher.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ghalam.core.ui.voice.VoicePlayerBar
import com.example.ghalam.teacher.data.Graph
import com.example.ghalam.teacher.data.local.MediaEntity
import com.example.ghalam.teacher.data.media.MediaKind
import com.example.ghalam.teacher.ui.components.HeaderAction
import com.example.ghalam.teacher.ui.components.ScreenHeader
import com.example.ghalam.teacher.util.fa
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** In-app viewer for a downloaded answer file: pinch-zoom images and PDFs page by page. */
@Composable
fun FileViewerScreen(fileKey: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val entity by produceState<MediaEntity?>(initialValue = null, fileKey) {
        value = Graph.db.mediaDao().get(fileKey)
    }
    val file = entity?.path?.let { Graph.media.fileFor(it) }?.takeIf { it.isFile }
    val kind = runCatching { MediaKind.valueOf(entity?.kind.orEmpty()) }.getOrDefault(MediaKind.OTHER)

    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        ScreenHeader(
            title = entity?.name ?: "فایل",
            onBack = onBack,
            actions = {
                if (file != null) {
                    HeaderAction("↗", onClick = {
                        if (!Graph.media.openExternally(context, file, entity?.mime ?: "application/octet-stream")) {
                            Toast.makeText(context, "برنامه‌ای برای باز کردن این فایل پیدا نشد", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                entity == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                file == null -> Message("این فایل روی گوشی نیست؛ از صفحه‌ی پاسخ دانلودش کنید.")
                kind == MediaKind.IMAGE -> ZoomableImage(file)
                kind == MediaKind.PDF -> PdfPages(file, context)
                kind == MediaKind.AUDIO -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    VoicePlayerBar(file)
                }
                else -> Message("این نوع فایل داخل برنامه نمایش داده نمی‌شود؛ با دکمه‌ی ↗ در برنامه‌ی دیگری باز کنید.")
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White)
    }
}

@Composable
private fun ZoomableImage(file: File) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    scale = 1f
                    offset = Offset.Zero
                })
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale <= 1f) Offset.Zero else offset + pan
                }
            },
    ) {
        AsyncImage(
            model = file,
            contentDescription = null,
            imageLoader = Graph.imageLoader,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

/** One PdfRenderer; it can only have one page open at a time, hence the lock. */
private class PdfDocument(file: File) : AutoCloseable {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)
    private val lock = Mutex()

    val pageCount: Int get() = renderer.pageCount

    suspend fun render(index: Int, widthPx: Int): Bitmap = lock.withLock {
        withContext(Dispatchers.IO) {
            renderer.openPage(index).use { page ->
                val height = (widthPx * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }
}

@Composable
private fun PdfPages(file: File, context: Context) {
    val doc = remember(file) { runCatching { PdfDocument(file) }.getOrNull() }
    DisposableEffect(doc) { onDispose { doc?.close() } }

    if (doc == null) {
        Message("این PDF باز نشد (شاید خراب یا رمزدار است). با دکمه‌ی ↗ در برنامه‌ی دیگری باز کنید.")
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items((0 until doc.pageCount).toList(), key = { it }) { index ->
                val bitmap by produceState<Bitmap?>(initialValue = null, index, widthPx) {
                    value = runCatching { doc.render(index, widthPx) }.getOrNull()
                }
                val page = bitmap
                if (page != null) {
                    Image(
                        bitmap = page.asImageBitmap(),
                        contentDescription = "صفحه ${(index + 1).fa()}",
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Box(Modifier.fillMaxWidth().height(360.dp).background(Color.DarkGray), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}
