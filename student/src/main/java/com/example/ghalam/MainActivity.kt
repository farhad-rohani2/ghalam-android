package com.example.ghalam

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.ghalam.data.model.RemoteFile
import com.example.ghalam.ui.components.MediaViewerScreen
import com.example.ghalam.ui.screens.LoginScreen
import com.example.ghalam.ui.screens.MediaSettingsScreen
import com.example.ghalam.ui.screens.ProfileScreen
import com.example.ghalam.ui.screens.TaskDetailScreen
import com.example.ghalam.ui.screens.TaskListScreen
import com.example.ghalam.ui.viewmodel.TasksViewModel
import com.example.ghalam.core.ui.theme.GhalamTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // every screen starts with a coloured header -> light status-bar icons
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        setContent {
            GhalamTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    App()
                }
            }
        }
    }
}

private enum class SubScreen { None, Settings, Profile }

/** Files + start position of the full-screen media viewer. */
private class ViewerRequest(val files: List<RemoteFile>, val index: Int)

/** Survives rotation / process death: flat list of index + (url, name, uuid) triples. */
private val ViewerSaver = listSaver<ViewerRequest?, Any>(
    save = { r ->
        if (r == null) emptyList()
        else listOf<Any>(r.index) + r.files.flatMap { listOf(it.url, it.name, it.uuid.orEmpty()) }
    },
    restore = { l ->
        if (l.isEmpty()) null
        else ViewerRequest(
            files = l.drop(1).chunked(3).map { RemoteFile(it[0] as String, it[1] as String, (it[2] as String).ifEmpty { null }) },
            index = l[0] as Int,
        )
    },
)

/** Keeps content out of the side navigation bar / cutout in landscape (vertical insets are handled by the screens). */
@Composable
private fun Modifier.horizontalInsets(): Modifier =
    windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))

/** Swallows touches so nothing underneath a full-screen page reacts. */
private fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent().changes.forEach { it.consume() }
    }
}

@Composable
private fun FullScreenPage(visible: Boolean, background: ComposeColor, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + slideInVertically(tween(260)) { it / 12 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 12 },
    ) {
        Box(Modifier.fillMaxSize().background(background).blockTouches().horizontalInsets()) { content() }
    }
}

@Composable
fun App(vm: TasksViewModel = viewModel()) {
    val token by vm.token.collectAsState()
    val loginState by vm.loginState.collectAsState()

    // Hoisted here so the list keeps its items and scroll position while a detail is open.
    val tasks = vm.tasks.collectAsLazyPagingItems()
    val listState = rememberLazyGridState()
    var openUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var openFinished by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var sub by rememberSaveable { mutableStateOf(SubScreen.None) }
    var viewer by rememberSaveable(stateSaver = ViewerSaver) { mutableStateOf<ViewerRequest?>(null) }
    var lastViewer by remember { mutableStateOf<ViewerRequest?>(null) }
    if (viewer != null) lastViewer = viewer

    val signedIn = !token.isNullOrBlank()
    LaunchedEffect(signedIn) {
        if (!signedIn) {
            openUuid = null
            sub = SubScreen.None
            viewer = null
        }
    }

    val open = openUuid
    val surface = MaterialTheme.colorScheme.background

    Box(Modifier.fillMaxSize().background(surface)) {
        // base layer: login / list / detail. It stays composed under the full-screen pages below.
        Box(Modifier.fillMaxSize().horizontalInsets()) {
            when {
                !signedIn -> LoginScreen(
                    state = loginState,
                    onLogin = vm::login,
                    onManualToken = vm::saveToken,
                )

                open != null -> {
                    BackHandler { openUuid = null }
                    TaskDetailScreen(
                        uuid = open,
                        finishedHint = openFinished,
                        onBack = { openUuid = null },
                        onOpenTask = { openUuid = it; openFinished = null }, // prerequisite task
                        onOpenMedia = { files, index -> viewer = ViewerRequest(files, index) },
                        onOpenSettings = { sub = SubScreen.Settings },
                        onOpenProfile = { sub = SubScreen.Profile },
                    )
                }

                else -> TaskListScreen(
                    vm, tasks, listState,
                    onOpen = { id, finished ->
                        openFinished = finished
                        openUuid = id
                    },
                    onOpenSettings = { sub = SubScreen.Settings },
                    onOpenProfile = { sub = SubScreen.Profile },
                )
            }
        }

        if (signedIn) {
            FullScreenPage(visible = sub == SubScreen.Settings, background = surface) {
                BackHandler { sub = SubScreen.None }
                MediaSettingsScreen(onBack = { sub = SubScreen.None })
            }
            FullScreenPage(visible = sub == SubScreen.Profile, background = surface) {
                BackHandler { sub = SubScreen.None }
                ProfileScreen(onBack = { sub = SubScreen.None }, onLogout = vm::logout)
            }
            FullScreenPage(visible = viewer != null, background = ComposeColor.Black) {
                BackHandler { viewer = null }
                lastViewer?.let { r ->
                    MediaViewerScreen(files = r.files, startIndex = r.index, onClose = { viewer = null })
                }
            }
        }
    }
}
