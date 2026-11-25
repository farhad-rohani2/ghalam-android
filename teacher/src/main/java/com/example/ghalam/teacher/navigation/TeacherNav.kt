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
import com.example.ghalam.teacher.ui.screens.FileViewerScreen
import com.example.ghalam.teacher.ui.screens.MessageScreen
import com.example.ghalam.teacher.ui.screens.ReportsScreen
import com.example.ghalam.teacher.ui.screens.SettingsScreen
import com.example.ghalam.teacher.ui.screens.StudentScreen
import com.example.ghalam.teacher.ui.screens.TaskEditScreen
import com.example.ghalam.teacher.ui.screens.TaskScreen
import com.example.ghalam.teacher.ui.screens.TeacherHomeScreen
import com.example.ghalam.teacher.ui.viewmodel.TeacherSessionViewModel

/** Logged out -> login; logged in -> the screens below, with a back stack. */
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

/*
 * Routes (a string each, so the back stack survives rotation and process death):
 *   home | task:<id> | student:<taskId>:<studentId> | edit:<taskId, 0 = new> |
 *   reports:<taskId, 0 = all> | message:<taskId>:<studentId> | settings | viewer:<fileKey>
 */
@Composable
private fun SignedInNav(onLogout: () -> Unit) {
    var stack by rememberSaveable { mutableStateOf(arrayListOf("home")) }
    // keeps the scroll position of the home list while another screen is open
    val homeListState = rememberLazyListState()

    fun push(route: String) {
        stack = ArrayList(stack + route)
    }

    fun pop() {
        if (stack.size > 1) stack = ArrayList(stack.dropLast(1))
    }

    fun replaceTop(route: String) {
        stack = ArrayList(stack.dropLast(1) + route)
    }

    val parts = stack.last().split(":")
    val a = parts.getOrNull(1)?.toLongOrNull()
    val b = parts.getOrNull(2)?.toLongOrNull()

    // keeps content out of the side navigation bar / cutout in landscape (vertical insets are handled by the screens)
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
        when {
            parts[0] == "task" && a != null -> TaskScreen(
                taskId = a,
                onBack = ::pop,
                onOpenStudent = { push("student:$a:$it") },
                onEdit = { push("edit:$a") },
                onReport = { push("reports:$a") },
                onDeleted = ::pop,
            )

            parts[0] == "student" && a != null && b != null -> StudentScreen(
                taskId = a,
                studentId = b,
                onBack = ::pop,
                onOpenStudent = { replaceTop("student:$a:$it") }, // previous / next student
                onMessage = { push("message:$a:$b") },
                onOpenViewer = { push("viewer:$it") },
            )

            parts[0] == "edit" && a != null -> TaskEditScreen(
                taskId = a,
                onBack = ::pop,
                onSaved = { id -> if (a == 0L) replaceTop("task:$id") else pop() },
            )

            parts[0] == "reports" && a != null -> ReportsScreen(presetTaskId = a, onBack = ::pop)

            parts[0] == "message" && a != null && b != null -> MessageScreen(taskId = a, studentId = b, onBack = ::pop)

            parts[0] == "settings" -> SettingsScreen(onBack = ::pop, onLogout = onLogout)

            parts[0] == "viewer" && parts.getOrNull(1) != null -> FileViewerScreen(fileKey = parts[1], onBack = ::pop)

            else -> TeacherHomeScreen(
                onOpenTask = { push("task:$it") },
                onNewTask = { push("edit:0") },
                onReports = { push("reports:0") },
                onSettings = { push("settings") },
                listState = homeListState,
            )
        }
    }
}
