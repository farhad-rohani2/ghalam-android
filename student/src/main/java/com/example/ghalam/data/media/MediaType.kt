package com.example.ghalam.data.media

import java.io.File
import java.text.NumberFormat
import java.util.Locale

enum class MediaKind(val emoji: String) {
    IMAGE("🖼"),
    VIDEO("🎬"),
    PDF("📕"),
    AUDIO("🎵"),
    OTHER("📄"),
}

/** What a downloaded file really is (decided from its first bytes, not from its name). */
data class Sniffed(val kind: MediaKind, val mime: String, val ext: String)

object MediaType {
    private val IMAGE_EXT = setOf("jpg", "jpeg", "jfif", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "tif", "tiff", "svg")
    private val VIDEO_EXT = setOf("mp4", "m4v", "mov", "3gp", "webm", "mkv")
    private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "wav", "ogg", "oga", "opus", "flac", "amr")

    private val MIME_BY_EXT = mapOf(
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "jfif" to "image/jpeg",
        "png" to "image/png", "gif" to "image/gif", "webp" to "image/webp", "bmp" to "image/bmp",
        "heic" to "image/heic", "heif" to "image/heif", "avif" to "image/avif",
        "tif" to "image/tiff", "tiff" to "image/tiff", "svg" to "image/svg+xml",
        "mp4" to "video/mp4", "m4v" to "video/mp4", "mov" to "video/quicktime",
        "3gp" to "video/3gpp", "webm" to "video/webm", "mkv" to "video/x-matroska",
        "pdf" to "application/pdf",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "zip" to "application/zip", "rar" to "application/vnd.rar",
        "txt" to "text/plain", "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "aac" to "audio/aac",
        "wav" to "audio/wav", "ogg" to "audio/ogg", "oga" to "audio/ogg", "opus" to "audio/ogg",
        "flac" to "audio/flac", "amr" to "audio/amr",
    )

    fun extensionOf(name: String): String? =
        name.substringBefore('?').substringAfterLast('.', "").lowercase()
            .takeIf { it.length in 1..5 && it.all { c -> c.isLetterOrDigit() } }

    fun kindOfExt(ext: String?): MediaKind = when {
        ext == null -> MediaKind.OTHER
        ext in IMAGE_EXT -> MediaKind.IMAGE
        ext in VIDEO_EXT -> MediaKind.VIDEO
        ext in AUDIO_EXT -> MediaKind.AUDIO
        ext == "pdf" -> MediaKind.PDF
        else -> MediaKind.OTHER
    }

    fun kindOfMime(mime: String): MediaKind {
        val m = mime.lowercase()
        return when {
            m.startsWith("image/") -> MediaKind.IMAGE
            m.startsWith("video/") -> MediaKind.VIDEO
            m.startsWith("audio/") -> MediaKind.AUDIO
            m == "application/pdf" -> MediaKind.PDF
            else -> MediaKind.OTHER
        }
    }

    /** Cheap guess used before the file is downloaded (only for the placeholder icon). */
    fun guessKind(name: String, url: String): MediaKind {
        val byName = kindOfExt(extensionOf(name))
        return if (byName != MediaKind.OTHER) byName else kindOfExt(extensionOf(url.substringBefore('?')))
    }

    fun mimeFromExt(ext: String?): String? = ext?.let { MIME_BY_EXT[it] }

    fun extFromMime(mime: String): String? = MIME_BY_EXT.entries.firstOrNull { it.value == mime.lowercase() }?.key

    /** Reads the first bytes and recognises the common formats. null = unknown. */
    fun sniff(file: File): Sniffed? {
        val b = ByteArray(512)
        val n = runCatching { file.inputStream().use { it.read(b) } }.getOrDefault(-1)
        if (n < 4) return null

        fun at(i: Int) = if (i < n) b[i].toInt() and 0xFF else -1
        fun starts(vararg v: Int) = v.indices.all { at(it) == v[it] }
        fun ascii(from: Int, len: Int) = if (from + len <= n) String(b, from, len, Charsets.ISO_8859_1) else ""

        return when {
            starts(0xFF, 0xD8, 0xFF) -> Sniffed(MediaKind.IMAGE, "image/jpeg", "jpg")
            starts(0x89, 0x50, 0x4E, 0x47) -> Sniffed(MediaKind.IMAGE, "image/png", "png")
            starts(0x47, 0x49, 0x46, 0x38) -> Sniffed(MediaKind.IMAGE, "image/gif", "gif")
            ascii(0, 4) == "RIFF" && ascii(8, 4) == "WEBP" -> Sniffed(MediaKind.IMAGE, "image/webp", "webp")
            starts(0x42, 0x4D) && n > 26 -> Sniffed(MediaKind.IMAGE, "image/bmp", "bmp")
            starts(0x49, 0x49, 0x2A, 0x00) || starts(0x4D, 0x4D, 0x00, 0x2A) -> Sniffed(MediaKind.IMAGE, "image/tiff", "tif")
            ascii(0, 4) == "%PDF" -> Sniffed(MediaKind.PDF, "application/pdf", "pdf")
            ascii(0, 3) == "ID3" || starts(0xFF, 0xFB) || starts(0xFF, 0xF3) || starts(0xFF, 0xF2) ->
                Sniffed(MediaKind.AUDIO, "audio/mpeg", "mp3")
            ascii(0, 4) == "OggS" -> Sniffed(MediaKind.AUDIO, "audio/ogg", "ogg")
            ascii(0, 4) == "fLaC" -> Sniffed(MediaKind.AUDIO, "audio/flac", "flac")
            ascii(0, 4) == "RIFF" && ascii(8, 4) == "WAVE" -> Sniffed(MediaKind.AUDIO, "audio/wav", "wav")
            ascii(4, 4) == "ftyp" -> {
                val brand = ascii(8, 4)
                when {
                    brand in setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1") ->
                        Sniffed(MediaKind.IMAGE, "image/heic", "heic")
                    brand == "avif" || brand == "avis" -> Sniffed(MediaKind.IMAGE, "image/avif", "avif")
                    brand == "qt  " -> Sniffed(MediaKind.VIDEO, "video/quicktime", "mov")
                    brand.startsWith("3g") -> Sniffed(MediaKind.VIDEO, "video/3gpp", "3gp")
                    brand.startsWith("M4A") -> Sniffed(MediaKind.AUDIO, "audio/mp4", "m4a")
                    else -> Sniffed(MediaKind.VIDEO, "video/mp4", "mp4")
                }
            }
            starts(0x1A, 0x45, 0xDF, 0xA3) -> Sniffed(MediaKind.VIDEO, "video/webm", "webm")
            else -> {
                val head = String(b, 0, n, Charsets.UTF_8).trimStart('\uFEFF', ' ', '\n', '\r', '\t').lowercase()
                when {
                    head.startsWith("<!doctype html") || head.startsWith("<html") ->
                        Sniffed(MediaKind.OTHER, "text/html", "html")
                    head.startsWith("<svg") || (head.startsWith("<?xml") && "<svg" in head) ->
                        Sniffed(MediaKind.IMAGE, "image/svg+xml", "svg")
                    else -> null
                }
            }
        }
    }
}

/** "۱٫۵ مگابایت" */
fun formatFileSize(bytes: Long): String {
    val nf = NumberFormat.getInstance(Locale.forLanguageTag("fa")).apply {
        maximumFractionDigits = 1
        minimumFractionDigits = 0
    }
    return when {
        bytes < 1024L -> "${nf.format(bytes)} بایت"
        bytes < 1024L * 1024 -> "${nf.format(bytes / 1024.0)} کیلوبایت"
        bytes < 1024L * 1024 * 1024 -> "${nf.format(bytes / 1048576.0)} مگابایت"
        else -> "${nf.format(bytes / 1073741824.0)} گیگابایت"
    }
}
