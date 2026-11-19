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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ghalam.data.Graph
import com.example.ghalam.data.RemoteFile
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(uuid: String, onBack: () -> Unit) {
    val vm: DetailViewModel = viewModel(key = uuid, factory = DetailViewModel.factory(uuid))
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

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

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("جزئیات تکلیف") },
                navigationIcon = { TextButton(onClick = onBack) { Text("بازگشت") } },
                actions = { TextButton(onClick = vm::load) { Text("بروزرسانی") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val detail = state.detail
            when {
                detail == null && state.loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                detail == null ->
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(state.error ?: "خطا", textAlign = TextAlign.Center)
                        Button(onClick = vm::load, modifier = Modifier.padding(top = 16.dp)) { Text("تلاش مجدد") }
                    }

                else -> LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(detail.title, style = MaterialTheme.typography.titleLarge)
                                if (detail.description.isNotBlank() && detail.description != detail.title) {
                                    Text(detail.description)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (detail.lesson.isNotBlank()) AssistChip(onClick = {}, label = { Text(detail.lesson) })
                                    if (detail.classroom.isNotBlank()) AssistChip(onClick = {}, label = { Text(detail.classroom) })
                                }
                                detail.deadline?.let { Text("مهلت: $it", style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }

                    detail.questions.forEach { q ->
                        item(key = "q-${q.id}") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (q.prompt.isNotBlank()) Text(q.prompt, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (q.slot == null) "امکان ارسال پاسخ برای این تکلیف وجود ندارد"
                                    else "پاسخ شما (${q.files.size} عکس)",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        items(q.files, key = { "f-${q.id}-${it.uuid ?: it.url}" }) { f ->
                            FileCard(
                                file = f,
                                deleting = f.uuid != null && f.uuid in state.deleting,
                                onOpen = { preview = f },
                                onDelete = { toDelete = q.id to f },
                            )
                        }
                        if (q.slot != null) {
                            item(key = "a-${q.id}") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { takePhoto(q.id) }, enabled = state.uploading == 0) { Text("دوربین") }
                                        OutlinedButton(onClick = { pickFromGallery(q.id) }, enabled = state.uploading == 0) { Text("گالری") }
                                    }
                                    if (state.uploading > 0) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                            Text("در حال ارسال… (${state.uploading})")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { (qid, file) ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("حذف عکس") },
            text = { Text("این عکس از پاسخ شما حذف شود؟") },
            confirmButton = {
                TextButton(onClick = { vm.delete(qid, file); toDelete = null }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("انصراف") } },
        )
    }

    preview?.let { file -> ImagePreview(file) { preview = null } }
}

@Composable
private fun FileCard(file: RemoteFile, deleting: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Box {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(file.imageUrl).crossfade(true).build(),
                contentDescription = file.name,
                imageLoader = Graph.imageLoader,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 320.dp)
                    .clickable(onClick = onOpen),
            )
            if (deleting) {
                CircularProgressIndicator(Modifier.align(Alignment.TopStart).padding(12.dp).size(24.dp), strokeWidth = 2.dp)
            } else if (file.uuid != null) {
                TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.TopStart)) { Text("حذف") }
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
                model = file.imageUrl,
                contentDescription = file.name,
                imageLoader = Graph.imageLoader,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
                Text("بستن", color = Color.White)
            }
        }
    }
}
