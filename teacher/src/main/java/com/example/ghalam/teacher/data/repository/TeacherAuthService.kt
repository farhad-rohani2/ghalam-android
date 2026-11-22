package com.example.ghalam.teacher.data.repository

import android.os.Build
import com.example.ghalam.core.network.data.model.AuthResult
import com.example.ghalam.core.network.data.model.Credentials
import com.example.ghalam.core.network.data.repository.AuthService
import com.example.ghalam.core.network.data.repository.UnsupportedCredentialsException
import com.example.ghalam.core.network.util.tokenOrNull
import com.example.ghalam.teacher.data.api.LoginBody
import com.example.ghalam.teacher.data.api.TeacherApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Teacher login: username (name / e-mail / phone) + password -> {"data":{"token":..,"user":{..}}}.
 * [onUser] is told who signed in before the token is stored, so Graph can wipe the previous
 * account's data when someone else logs in.
 */
class TeacherAuthService(
    private val api: TeacherApi,
    private val onUser: suspend (id: Long, name: String) -> Unit,
) : AuthService {

    override suspend fun login(credentials: Credentials): AuthResult {
        val c = credentials as? Credentials.UsernamePassword
            ?: throw UnsupportedCredentialsException(credentials)
        val res = api.login(LoginBody(username = c.username, password = c.password, deviceName = deviceName()))
        val token = res.tokenOrNull().orEmpty()
        if (token.isNotBlank()) {
            val user = (res["data"] as? JsonObject)?.get("user") as? JsonObject
            val id = (user?.get("id") as? JsonPrimitive)?.longOrNull ?: 0L
            val name = (user?.get("name") as? JsonPrimitive)?.contentOrNull.orEmpty()
            onUser(id, name)
        }
        return AuthResult(token)
    }

    override suspend fun logout() {
        api.logout().close()
    }

    /** One token per device on the server: logging in again on the same phone replaces the old one. */
    private fun deviceName(): String =
        ("android-" + Build.MODEL).filter { it.isLetterOrDigit() || it == '-' || it == ' ' }.take(40)
}
