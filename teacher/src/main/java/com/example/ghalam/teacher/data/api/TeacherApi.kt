package com.example.ghalam.teacher.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * TODO(teacher backend): PLACEHOLDERS. Put the real teacher login / logout paths, the JSON field
 * names of the login body and the pageId here (this is the only file that has to change).
 * The base URL (https://ghalam.razzar.ir/) comes from :core:network.
 */
object TeacherEndpoints {
    const val LOGIN = "api/TODO-teacher-login"
    const val LOGOUT = "api/TODO-teacher-logout"
    const val PAGE_LOGIN = 0
}

@Serializable
data class TeacherLoginBody(
    val username: String,
    val password: String,
    val pageId: Int = TeacherEndpoints.PAGE_LOGIN,
)

@Serializable
data class TeacherPageBody(val pageId: Int = TeacherEndpoints.PAGE_LOGIN)

interface TeacherApi {

    /** username + password -> user object containing "token". Sent without Authorization. */
    @POST(TeacherEndpoints.LOGIN)
    suspend fun login(@Body body: TeacherLoginBody): JsonObject

    @POST(TeacherEndpoints.LOGOUT)
    suspend fun logout(@Body body: TeacherPageBody = TeacherPageBody()): ResponseBody
}
