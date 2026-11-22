package com.example.ghalam.teacher.util

import com.example.ghalam.core.network.data.api.ApiJson
import com.example.ghalam.core.network.util.userMessage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.HttpException

/** The `message` the server put in an error body (login failure, validation, 403 / 404 texts). */
private fun HttpException.serverMessage(): String? {
    val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
    if (body.isNullOrBlank()) return null
    return runCatching {
        ((ApiJson.parseToJsonElement(body) as? JsonObject)?.get("message") as? JsonPrimitive)?.contentOrNull
    }.getOrNull()?.takeIf { it.isNotBlank() }
}

/** Message for the user. Reads the error body, so call it once per exception. */
fun Throwable.teacherMessage(): String {
    if (this is HttpException) {
        if (code() == 429) return "تلاش‌های ناموفق زیاد بود؛ کمی صبر کنید و دوباره امتحان کنید"
        if (code() in intArrayOf(403, 404, 422)) serverMessage()?.let { return it }
    }
    return userMessage()
}
