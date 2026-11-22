package com.example.ghalam.teacher.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ghalam.core.ui.components.CredentialsLoginScreen
import com.example.ghalam.teacher.ui.screens.StudentScreen
import com.example.ghalam.teacher.ui.screens.TaskScreen
import com.example.ghalam.teacher.ui.screens.TeacherHomeScreen
import com.example.ghalam.teacher.ui.viewmodel.TeacherSessionViewModel

/** Logged out -> login; logged in -> home -> task -> student. */
@Composable
fun TeacherNav(vm: TeacherSessionViewModel = viewModel()) {
    val token by vm.token.collectAsState()
    val loginState by vm.loginState.collectAsState()

    if (token.isNullOrBlank()) {
        CredentialsLoginScreen(
            title = "پنل معلم",
            subtitle = "برای ادامه وارد شوید",
            emoji = "🍎",
            state = loginState,
            onLogin = vm::login,
        )
    } else {
        SignedInNav(onLogout = vm::logout)
    }
}

@Composable
private fun SignedInNav(onLogout: () -> Unit) {
    // survive rotation and process death
    var taskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var studentId by rememberSaveable { mutableStateOf<Long?>(null) }
    // keeps the scroll position of the home list while a task is open
    val homeListState = rememberLazyListState()

    // keeps content out of the side navigation bar / cutout in landscape (vertical insets are handled by the screens)
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
        val task = taskId
        val student = studentId
        when {
            task != null && student != null -> StudentScreen(
                taskId = task,
                studentId = student,
                onBack = { studentId = null },
                onOpenStudent = { studentId = it },
            )

            task != null -> TaskScreen(
                taskId = task,
                onBack = { taskId = null },
                onOpenStudent = { studentId = it },
            )

            else -> TeacherHomeScreen(
                onOpenTask = { taskId = it },
                onLogout = onLogout,
                listState = homeListState,
            )
        }
    }
}
