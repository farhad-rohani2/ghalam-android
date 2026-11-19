package com.example.ghalam.util

import retrofit2.HttpException
import java.io.IOException

fun Throwable.userMessage(): String = when (this) {
    is HttpException -> when (code()) {
        401 -> "نشست منقضی شده؛ دوباره وارد شوید"
        403 -> "دسترسی مجاز نیست (403)"
        404 -> "پیدا نشد (404)"
        413 -> "حجم فایل زیاد است (413)"
        422 -> "درخواست نامعتبر است (422)"
        else -> "خطای سرور (${code()})"
    }
    is IOException -> "اتصال اینترنت را بررسی کنید"
    else -> message ?: "خطای ناشناخته"
}

/** Persian/Arabic-Indic digits -> ASCII, so a national code typed on a Persian keyboard works. */
fun String.toAsciiDigits(): String = buildString {
    for (c in this@toAsciiDigits.trim()) {
        append(
            when (c) {
                in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
                in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                else -> c
            }
        )
    }
}
