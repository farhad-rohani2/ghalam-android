package com.example.ghalam.data.repository

import com.example.ghalam.core.network.data.model.AuthResult
import com.example.ghalam.core.network.data.model.Credentials
import com.example.ghalam.core.network.data.repository.AuthService
import com.example.ghalam.core.network.data.repository.UnsupportedCredentialsException
import com.example.ghalam.core.network.util.tokenOrNull
import com.example.ghalam.data.api.GhalamApi
import com.example.ghalam.data.api.LoginBody

/** Student login: national code -> user object containing "token". */
class StudentAuthService(private val api: GhalamApi) : AuthService {

    override suspend fun login(credentials: Credentials): AuthResult {
        val code = (credentials as? Credentials.NationalCode)?.code
            ?: throw UnsupportedCredentialsException(credentials)
        val res = api.login(LoginBody(code))
        return AuthResult(res.tokenOrNull().orEmpty())
    }

    override suspend fun logout() {
        api.logout().close()
    }
}
