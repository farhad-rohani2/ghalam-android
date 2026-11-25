package com.example.ghalam.ui.screens

import com.example.ghalam.ui.components.AppIcons
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.border
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ghalam.core.ui.components.EmptyState
import com.example.ghalam.core.ui.components.OfflineBanner
import com.example.ghalam.core.ui.components.Pill
import com.example.ghalam.core.ui.components.SectionCard
import com.example.ghalam.core.ui.components.SkeletonCard
import com.example.ghalam.core.ui.theme.Danger
import com.example.ghalam.core.ui.theme.DangerBg
import com.example.ghalam.core.ui.theme.Primary
import com.example.ghalam.core.ui.theme.Success
import com.example.ghalam.core.ui.theme.SuccessBg
import com.example.ghalam.core.ui.theme.Warn
import com.example.ghalam.core.ui.theme.WarnBg
import com.example.ghalam.core.ui.theme.lessonTone
import com.example.ghalam.data.model.DepItem
import com.example.ghalam.data.model.DetailUi
import com.example.ghalam.data.model.LinkItem
import com.example.ghalam.data.model.QuestionUi
import com.example.ghalam.data.model.RemoteFile
import com.example.ghalam.ui.components.MediaGrid
import com.example.ghalam.ui.components.HeaderMenu
import com.example.ghalam.ui.viewmodel.DetailViewModel
import com.example.ghalam.util.Downloads
import java.io.File
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.ghalam.data.local.PendingAnswerEntity
import com.example.ghalam.data.local.PendingUploadEntity
import com.example.ghalam.data.local.UploadState
import kotlinx.coroutines.launch

@Composable
fun TaskDetailScreen(
    uuid: String,
    finishedHint: Boolean?,
    onBack: () -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenMedia: (List<RemoteFile>, Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val vm: DetailViewModel = viewModel(key = uuid, factory = DetailViewModel.factory(uuid))
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var targetQuestion by rememberSaveable { mutableIntStateOf(0) }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
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

    // whatever the student typed is written to the phone when the screen goes away
    DisposableEffect(vm) {
        onDispose { vm.flush() }
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
                    item { Hero(detail, finished, onBack, onRefresh = vm::load, onSettings = onOpenSettings, onProfile = onOpenProfile) }
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
                                        MediaGrid(q.teacherFiles, emptySet(), onOpen = onOpenMedia, onDelete = null)
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
                                    pending = state.uploads.filter { it.questionId == q.id },
                                    text = state.texts[q.id].orEmpty(),
                                    draft = state.drafts[q.id],
                                    deleting = state.deleting,
                                    onOpen = onOpenMedia,
                                    onDelete = { f -> toDelete = q.id to f },
                                    onCamera = { takePhoto(q.id) },
                                    onGallery = { pickFromGallery(q.id) },
                                    onTextChange = { t -> vm.onTextChange(q.id, t) },
                                    onRetryText = { vm.retryText(q.id) },
                                    onRetryUpload = vm::retryUpload,
                                    onDiscardUpload = vm::discard,
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

}

@Composable
private fun Padded(content: @Composable () -> Unit) {
    Box(Modifier.padding(horizontal = 16.dp)) { content() }
}

private fun hasHtmlText(html: String): Boolean =
    html.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").trim().isNotEmpty()

// ---------------------------------------------------------------- hero

@Composable
private fun Hero(detail: DetailUi, finished: Boolean, onBack: () -> Unit, onRefresh: () -> Unit, onSettings: () -> Unit, onProfile: () -> Unit) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeaderMenu(onProfile = onProfile, onSettings = onSettings)
                    RoundIcon(onRefresh) { Icon(Icons.Filled.Refresh, "بروزرسانی", tint = Color.White) }
                }
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
    pending: List<PendingUploadEntity>,
    text: String,
    draft: PendingAnswerEntity?,
    deleting: Set<String>,
    onOpen: (List<RemoteFile>, Int) -> Unit,
    onDelete: (RemoteFile) -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onTextChange: (String) -> Unit,
    onRetryText: () -> Unit,
    onRetryUpload: (PendingUploadEntity) -> Unit,
    onDiscardUpload: (PendingUploadEntity) -> Unit,
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
            MediaGrid(q.files, deleting, onOpen = onOpen, onDelete = onDelete)
            if (q.files.isEmpty() && pending.isEmpty()) {
                Text(
                    "از برگه‌ی تکلیف خود عکس بگیرید و بفرستید.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // photos that are saved on the phone and not on the server yet
            pending.forEach { item ->
                PendingUploadRow(item, onRetry = { onRetryUpload(item) }, onDiscard = { onDiscardUpload(item) })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AnswerActionTile(
                    icon = AppIcons.Camera,
                    title = "دوربین",
                    hint = "عکس بگیرید",
                    primary = true,
                    enabled = true,
                    onClick = onCamera,
                    modifier = Modifier.weight(1f),
                )
                AnswerActionTile(
                    icon = AppIcons.PhotoLibrary,
                    title = "گالری",
                    hint = "از گوشی انتخاب کنید",
                    primary = false,
                    enabled = true,
                    onClick = onGallery,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                label = { Text("پاسخ متنی (اختیاری)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            TextStatus(draft, onRetryText)
        }
    }
}

/** One photo that waits on the phone: small preview, what is happening to it, and the buttons that make sense. */
@Composable
private fun PendingUploadRow(
    item: PendingUploadEntity,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
) {
    val (label, bg, fg) = when (item.state) {
        UploadState.UPLOADING -> Triple("در حال ارسال…", WarnBg, Warn)
        UploadState.HELD -> Triple("منتظر ارسال دستی", WarnBg, Warn)
        UploadState.FAILED -> Triple("ارسال ناموفق", DangerBg, Danger)
        else -> Triple(if (item.attempts > 0) "در انتظار اینترنت" else "در انتظار ارسال", WarnBg, Warn)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = File(item.localPath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Pill(label, bg, fg)
            if (item.state == UploadState.FAILED && !item.lastError.isNullOrBlank()) {
                Text(
                    item.lastError,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (item.state == UploadState.HELD) TextButton(onClick = onRetry) { Text("ارسال") }
        if (item.state == UploadState.FAILED) TextButton(onClick = onRetry) { Text("تلاش مجدد") }
        if (item.state != UploadState.UPLOADING) {
            TextButton(onClick = onDiscard) { Text("حذف", color = Danger) }
        }
    }
}

/** Small line under the text box: waiting, failed (with retry) or saved. */
@Composable
private fun TextStatus(draft: PendingAnswerEntity?, onRetry: () -> Unit) {
    if (draft == null) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            draft.failed -> {
                Text(
                    "متن ارسال نشد: ${draft.lastError.orEmpty()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Danger,
                    modifier = Modifier.weight(1f, fill = false),
                )
                TextButton(onClick = onRetry) { Text("تلاش مجدد") }
            }
            draft.dirty -> Text(
                "متن در انتظار ارسال است",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Text(
                "متن ذخیره شد",
                style = MaterialTheme.typography.labelMedium,
                color = Success,
            )
        }
    }
}

@Composable
private fun AnswerActionTile(
    icon: ImageVector,
    title: String,
    hint: String,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    val content = if (primary) Color.White else Primary
    val background = if (primary) Brush.linearGradient(listOf(Primary, lerp(Primary, Color.Black, 0.25f)))
    else SolidColor(Primary.copy(alpha = 0.08f))
    Column(
        modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .background(background)
            .then(if (primary) Modifier else Modifier.border(1.dp, Primary.copy(alpha = 0.25f), shape))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(content.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(26.dp))
        }
        Text(title, color = content, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Text(hint, color = content.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
    }
}
