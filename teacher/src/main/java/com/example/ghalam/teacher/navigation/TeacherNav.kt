package com.example.ghalam.teacher.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ghalam.core.ui.components.CredentialsLoginScreen
import com.example.ghalam.teacher.ui.screens.TeacherHomeScreen
import com.example.ghalam.teacher.ui.viewmodel.TeacherSessionViewModel

/** Logged out -> login; logged in -> home. More destinations are added here later. */
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
        TeacherHomeScreen(onLogout = vm::logout)
    }
}
