package com.example.ghalam.teacher.data.repository

import com.example.ghalam.core.network.data.model.AuthResult
import com.example.ghalam.core.network.data.model.Credentials
import com.example.ghalam.core.network.data.repository.AuthService
import com.example.ghalam.core.network.data.repository.UnsupportedCredentialsException
import com.example.ghalam.core.network.util.tokenOrNull
import com.example.ghalam.teacher.data.api.TeacherApi
import com.example.ghalam.teacher.data.api.TeacherLoginBody

/** Teacher login: username + password -> user object containing "token". */
class TeacherAuthService(private val api: TeacherApi) : AuthService {

    override suspend fun login(credentials: Credentials): AuthResult {
        val c = credentials as? Credentials.UsernamePassword
            ?: throw UnsupportedCredentialsException(credentials)
        val res = api.login(TeacherLoginBody(username = c.username, password = c.password))
        return AuthResult(res.tokenOrNull().orEmpty())
    }

    override suspend fun logout() {
        api.logout().close()
    }
}
