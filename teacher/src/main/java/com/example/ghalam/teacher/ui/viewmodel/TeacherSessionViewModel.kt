package com.example.ghalam.teacher.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ghalam.core.network.data.model.Credentials
import com.example.ghalam.core.network.data.repository.MissingTokenException
import com.example.ghalam.core.ui.components.LoginUiState
import com.example.ghalam.teacher.data.Graph
import com.example.ghalam.teacher.util.teacherMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TeacherSessionViewModel : ViewModel() {

    val token: StateFlow<String?> = Graph.token
    val loginState = MutableStateFlow(LoginUiState())

    fun login(username: String, password: String) {
        val user = username.trim()
        if (user.isBlank() || password.isBlank() || loginState.value.loading) return
        viewModelScope.launch {
            loginState.value = LoginUiState(loading = true)
            try {
                Graph.auth.login(Credentials.UsernamePassword(user, password))
                loginState.value = LoginUiState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: MissingTokenException) {
                loginState.value = LoginUiState(error = "پاسخ ورود توکن نداشت؛ نام کاربری و رمز را بررسی کنید")
            } catch (e: Exception) {
                loginState.value = LoginUiState(error = e.teacherMessage())
            }
        }
    }

    /** Also removes this account's data from the phone (the screen asks for confirmation first). */
    fun logout() {
        viewModelScope.launch {
            Graph.signOut()
            loginState.value = LoginUiState()
        }
    }
}
