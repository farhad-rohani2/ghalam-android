package com.example.ghalam.teacher.util

/** ASCII digits -> Persian digits (the server sends dates like 1405/09/02 with ASCII digits). */
fun String.fa(): String = buildString(length) {
    for (c in this@fa) append(if (c in '0'..'9') '۰' + (c - '0') else c)
}

fun Int.fa(): String = toString().fa()

fun Long.fa(): String = toString().fa()

/** For searching: Arabic ي / ك -> Persian ی / ک, lower case. */
fun String.normalizeFa(): String = trim().replace('ي', 'ی').replace('ك', 'ک').lowercase()

fun relativeTime(timeMs: Long, now: Long = System.currentTimeMillis()): String {
    if (timeMs <= 0L) return "هنوز انجام نشده"
    val minutes = (now - timeMs) / 60_000
    return when {
        minutes < 1 -> "همین الان"
        minutes < 60 -> "${minutes.fa()} دقیقه پیش"
        minutes < 60 * 24 -> "${(minutes / 60).fa()} ساعت پیش"
        else -> "${(minutes / (60 * 24)).fa()} روز پیش"
    }
}

fun formatFileSize(bytes: Long): String = when {
    bytes <= 0L -> ""
    bytes < 1024L -> "${bytes.fa()} بایت"
    bytes < 1024L * 1024 -> "${(bytes / 1024).fa()} کیلوبایت"
    else -> "${"%.1f".format(bytes / 1048576.0).fa()} مگابایت"
}
