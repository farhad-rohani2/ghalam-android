package com.example.ghalam.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ghalam.data.DepItem
import com.example.ghalam.data.DetailUi
import com.example.ghalam.data.Graph
import com.example.ghalam.data.LinkItem
import com.example.ghalam.data.QuestionUi
import com.example.ghalam.data.RemoteFile
import com.example.ghalam.ui.theme.Danger
import com.example.ghalam.ui.theme.DangerBg
import com.example.ghalam.ui.theme.Primary
import com.example.ghalam.ui.theme.Success
import com.example.ghalam.ui.theme.SuccessBg
import com.example.ghalam.ui.theme.Warn
import com.example.ghalam.ui.theme.WarnBg
import com.example.ghalam.ui.theme.lessonTone
import com.example.ghalam.ui.theme.shimmer
import com.example.ghalam.util.Downloads
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun TaskDetailScreen(
    uuid: String,
    finishedHint: Boolean?,
    onBack: () -> Unit,
    onOpenTask: (String) -> Unit,
) {
    val vm: DetailViewModel = viewModel(key = uuid, factory = DetailViewModel.factory(uuid))
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var targetQuestion by rememberSaveable { mutableIntStateOf(0) }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var preview by remember { mutableStateOf<RemoteFile?>(null) }
    var toDelete by remember { mutableStateOf<Pair<Int, RemoteFile>?>(null) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) vm.upload(targetQuestion, uris)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri
        if (ok && u != null) vm.upload(targetQuestion, listOf(u))
    }

    fun pickFromGallery(questionId: Int) {
        targetQuestion = questionId
        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun takePhoto(questionId: Int) {
        targetQuestion = questionId
        val dir = File(ctx.cacheDir, "images").apply { mkdirs() }
        val file = File.createTempFile("cam_", ".jpg", dir)
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        cameraUri = uri
        camera.launch(uri)
    }

    fun toast(msg: String) {
        scope.launch { snackbar.showSnackbar(msg) }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val detail = state.detail
        when {
            detail == null && state.loading -> DetailSkeleton(onBack)

            detail == null -> Column(Modifier.fillMaxSize()) {
                PlainTopBar(onBack)
                EmptyState(
                    emoji = "😕",
                    title = "دریافت تکلیف ناموفق بود",
                    subtitle = state.error,
                    modifier = Modifier.padding(top = 48.dp),
                ) { Button(onClick = vm::load, modifier = Modifier.padding(top = 8.dp)) { Text("تلاش مجدد") } }
            }

            else -> {
                val finished = finishedHint ?: (detail.deadline?.contains("پیش") == true)
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 32.dp + navBottom),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item { Hero(detail, finished, onBack, onRefresh = vm::load) }
                    if (state.fromCache) item { OfflineBanner() }

                    if (detail.dependencies.isNotEmpty()) {
                        item {
                            Padded {
                                SectionCard("پیش‌نیازها", "🧩") {
                                    detail.dependencies.forEach { d -> DependencyRow(d, onOpenTask) }
                                }
                            }
                        }
                    }

                    detail.questions.forEach { q ->
                        if (hasHtmlText(q.expectedHtml)) {
                            item(key = "exp-${q.id}") {
                                Padded {
                                    SectionCard("توضیحات معلم", "📝") {
                                        val text = remember(q.expectedHtml) { AnnotatedString.fromHtml(q.expectedHtml) }
                                        Text(text, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                        }
                        if (q.teacherFiles.isNotEmpty()) {
                            item(key = "tf-${q.id}") {
                                Padded {
                                    SectionCard("فایل‌های پیوست", "📎") {
                                        val images = q.teacherFiles.filter { it.isImage }
                                        val others = q.teacherFiles.filterNot { it.isImage }
                                        PhotoGrid(images, emptySet(), onOpen = { preview = it }, onDelete = null)
                                        others.forEach { f ->
                                            FileRow(f) {
                                                toast(if (Downloads.enqueue(ctx, f)) "دانلود شروع شد (پوشه‌ی Downloads/Ghalam)" else "دانلود ممکن نشد")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (q.links.isNotEmpty()) {
                            item(key = "lk-${q.id}") {
                                Padded {
                                    SectionCard("لینک‌ها", "🔗") {
                                        q.links.forEach { l -> LinkRow(l) { if (!Downloads.openLink(ctx, l.url)) toast("لینک باز نشد") } }
                                    }
                                }
                            }
                        }
                    }

                    detail.questions.forEach { q ->
                        item(key = "ans-${q.id}") {
                            Padded {
                                AnswerSection(
                                    q = q,
                                    uploading = state.uploading,
                                    deleting = state.deleting,
                                    onOpen = { preview = it },
                                    onDelete = { f -> toDelete = q.id to f },
                                    onCamera = { takePhoto(q.id) },
                                    onGallery = { pickFromGallery(q.id) },
                                )
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
    }

    toDelete?.let { (qid, file) ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("حذف عکس") },
            text = { Text("این عکس از پاسخ شما حذف شود؟") },
            confirmButton = { TextButton(onClick = { vm.delete(qid, file); toDelete = null }) { Text("حذف", color = Danger) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("انصراف") } },
        )
    }

    preview?.let { ImagePreview(it) { preview = null } }
}

@Composable
private fun Padded(content: @Composable () -> Unit) {
    Box(Modifier.padding(horizontal = 16.dp)) { content() }
}

private fun hasHtmlText(html: String): Boolean =
    html.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").trim().isNotEmpty()

// ---------------------------------------------------------------- hero

@Composable
private fun Hero(detail: DetailUi, finished: Boolean, onBack: () -> Unit, onRefresh: () -> Unit) {
    val tone = lessonTone(detail.lesson)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(
                Brush.linearGradient(
                    listOf(lerp(tone.content, Color.White, 0.12f), lerp(tone.content, Color.Black, 0.25f)),
                )
            ),
    ) {
        Column(Modifier.statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RoundIcon(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت", tint = Color.White) }
                RoundIcon(onRefresh) { Icon(Icons.Filled.Refresh, "بروزرسانی", tint = Color.White) }
            }
            Column(Modifier.padding(horizontal = 12.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (detail.lesson.isNotBlank()) Pill(detail.lesson, Color.White.copy(alpha = 0.22f), Color.White)
                Text(
                    detail.title,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    detail.deadline?.let {
                        if (finished) Pill("مهلت گذشته · $it", DangerBg, Danger)
                        else Pill("مهلت: $it", SuccessBg, Success)
                    }
                    if (detail.classroom.isNotBlank()) Pill("🏫 ${detail.classroom}", Color.White.copy(alpha = 0.22f), Color.White)
                }
            }
        }
    }
}

@Composable
private fun RoundIcon(onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

@Composable
private fun PlainTopBar(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") }
    }
}

@Composable
private fun DetailSkeleton(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)).background(Primary),
        ) {
            Column(Modifier.statusBarsPadding().padding(12.dp)) {
                RoundIcon(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت", tint = Color.White) }
                Spacer(Modifier.height(80.dp))
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) { SkeletonCard() }
        }
    }
}

// ---------------------------------------------------------------- teacher blocks

@Composable
private fun DependencyRow(dep: DepItem, onOpen: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = dep.uuid != null) { dep.uuid?.let(onOpen) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(dep.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (dep.uuid != null) Text("‹", color = Primary, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun FileRow(file: RemoteFile, onDownload: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onDownload)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text("📄")
        }
        Text(file.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("دانلود", color = Primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LinkRow(link: LinkItem, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("🔗")
        Text(
            link.title,
            Modifier.weight(1f),
            color = Primary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------- student answer

@Composable
private fun AnswerSection(
    q: QuestionUi,
    uploading: Int,
    deleting: Set<String>,
    onOpen: (RemoteFile) -> Unit,
    onDelete: (RemoteFile) -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
) {
    SectionCard(
        title = "پاسخ شما",
        emoji = "📸",
        trailing = {
            if (q.slot != null) {
                if (q.files.isNotEmpty()) Pill("✅ ارسال شده", SuccessBg, Success)
                else Pill("⏳ ارسال نشده", WarnBg, Warn)
            }
        },
    ) {
        if (q.slot == null) {
            Text(
                "امکان ارسال پاسخ برای این تکلیف وجود ندارد.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            PhotoGrid(q.files, deleting, onOpen = onOpen, onDelete = onDelete)
            if (q.files.isEmpty()) {
                Text(
                    "از برگه‌ی تکلیف خود عکس بگیرید و بفرستید.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onCamera, enabled = uploading == 0, modifier = Modifier.weight(1f).height(50.dp), shape = MaterialTheme.shapes.medium) {
                    Text("📷  دوربین", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onGallery, enabled = uploading == 0, modifier = Modifier.weight(1f).height(50.dp), shape = MaterialTheme.shapes.medium) {
                    Text("🖼  گالری", fontWeight = FontWeight.Bold)
                }
            }
            if (uploading > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("در حال ارسال… ($uploading)", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Two-column square thumbnails; [onDelete] == null hides the delete button (teacher files). */
@Composable
private fun PhotoGrid(
    files: List<RemoteFile>,
    deleting: Set<String>,
    onOpen: (RemoteFile) -> Unit,
    onDelete: ((RemoteFile) -> Unit)?,
) {
    if (files.isEmpty()) return
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        files.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { f ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Transparent)
                            .shimmer()
                            .clickable { onOpen(f) },
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(ctx).data(f.fullUrl).crossfade(true).build(),
                            contentDescription = f.name,
                            imageLoader = Graph.imageLoader,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (onDelete != null && f.uuid != null) {
                            Box(
                                Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .clickable(enabled = f.uuid !in deleting) { onDelete(f) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (f.uuid in deleting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                else Icon(Icons.Filled.Close, "حذف", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ImagePreview(file: RemoteFile, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                },
        ) {
            AsyncImage(
                model = file.fullUrl,
                contentDescription = file.name,
                imageLoader = Graph.imageLoader,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, "بستن", tint = Color.White) }
        }
    }
}
