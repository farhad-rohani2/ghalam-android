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

/** A server error split into the overall message and the message of each invalid field (422). */
data class ServerError(val message: String, val fields: Map<String, String> = emptyMap())

/** Reads the error body once; for anything that is not an HTTP error only the plain message is known. */
fun Throwable.toServerError(): ServerError {
    if (this !is HttpException) return ServerError(userMessage())
    if (code() == 429) return ServerError("درخواست‌های زیاد بود؛ کمی صبر کنید و دوباره امتحان کنید")
    val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
    val obj = runCatching { ApiJson.parseToJsonElement(body.orEmpty()) as? JsonObject }.getOrNull()
        ?: return ServerError(userMessage())
    val message = (obj["message"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: userMessage()
    val fields = (obj["errors"] as? JsonObject)?.mapNotNull { (key, value) ->
        val first = (value as? kotlinx.serialization.json.JsonArray)?.firstOrNull() as? JsonPrimitive
        first?.contentOrNull?.let { key to it }
    }?.toMap().orEmpty()
    return ServerError(message, fields)
}
