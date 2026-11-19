package com.example.ghalam

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.ghalam.ui.LoginScreen
import com.example.ghalam.ui.TaskDetailScreen
import com.example.ghalam.ui.TaskListScreen
import com.example.ghalam.ui.TasksViewModel
import com.example.ghalam.ui.theme.GhalamTheme

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

@Composable
fun App(vm: TasksViewModel = viewModel()) {
    val token by vm.token.collectAsState()
    val loginState by vm.loginState.collectAsState()

    // Hoisted here so the list keeps its items and scroll position while a detail is open.
    val tasks = vm.tasks.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    var openUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var openFinished by rememberSaveable { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(token) { if (token.isNullOrBlank()) openUuid = null }

    val open = openUuid
    when {
        token.isNullOrBlank() -> LoginScreen(
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
            )
        }

        else -> TaskListScreen(vm, tasks, listState, onOpen = { id, finished ->
            openFinished = finished
            openUuid = id
        })
    }
}
