package com.example.ghalam.teacher.data.media

import java.io.File

enum class MediaKind(val emoji: String) {
    IMAGE("🖼"),
    VIDEO("🎬"),
    PDF("📕"),
    AUDIO("🎵"),
    OTHER("📄"),
}

data class Sniffed(val kind: MediaKind, val mime: String, val ext: String)

object MediaKinds {
    private val IMAGE_EXT = setOf("jpg", "jpeg", "jfif", "png", "gif", "webp", "bmp", "heic", "heif", "avif")
    private val VIDEO_EXT = setOf("mp4", "m4v", "mov", "3gp", "webm", "mkv")
    private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "wav", "ogg", "oga", "opus", "flac", "amr")

    fun extensionOf(name: String): String? =
        name.substringBefore('?').substringAfterLast('.', "").lowercase()
            .takeIf { it.length in 1..5 && it.all { c -> c.isLetterOrDigit() } }

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

    fun kindOfExt(ext: String?): MediaKind = when {
        ext == null -> MediaKind.OTHER
        ext in IMAGE_EXT -> MediaKind.IMAGE
        ext in VIDEO_EXT -> MediaKind.VIDEO
        ext in AUDIO_EXT -> MediaKind.AUDIO
        ext == "pdf" -> MediaKind.PDF
        else -> MediaKind.OTHER
    }

    /** Before the file is downloaded: from the server's mime, else from the name. */
    fun guess(name: String, mime: String?): MediaKind {
        val byMime = mime?.let { kindOfMime(it) } ?: MediaKind.OTHER
        return if (byMime != MediaKind.OTHER) byMime else kindOfExt(extensionOf(name))
    }

    fun mimeOfExt(ext: String?): String? = when (ext) {
        "jpg", "jpeg", "jfif" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "pdf" -> "application/pdf"
        "mp4", "m4v" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        else -> null
    }

    /** Reads the first bytes and recognises the common formats (null = unknown). */
    fun sniff(file: File): Sniffed? {
        val b = ByteArray(32)
        val n = runCatching { file.inputStream().use { it.read(b) } }.getOrDefault(-1)
        if (n < 4) return null

        fun starts(vararg v: Int) = v.indices.all { i -> i < n && (b[i].toInt() and 0xFF) == v[i] }
        fun ascii(from: Int, len: Int) = if (from + len <= n) String(b, from, len, Charsets.ISO_8859_1) else ""

        return when {
            starts(0xFF, 0xD8, 0xFF) -> Sniffed(MediaKind.IMAGE, "image/jpeg", "jpg")
            starts(0x89, 0x50, 0x4E, 0x47) -> Sniffed(MediaKind.IMAGE, "image/png", "png")
            starts(0x47, 0x49, 0x46, 0x38) -> Sniffed(MediaKind.IMAGE, "image/gif", "gif")
            ascii(0, 4) == "RIFF" && ascii(8, 4) == "WEBP" -> Sniffed(MediaKind.IMAGE, "image/webp", "webp")
            ascii(0, 4) == "%PDF" -> Sniffed(MediaKind.PDF, "application/pdf", "pdf")
            ascii(4, 4) == "ftyp" -> when (ascii(8, 4)) {
                "heic", "heix", "mif1" -> Sniffed(MediaKind.IMAGE, "image/heic", "heic")
                "avif" -> Sniffed(MediaKind.IMAGE, "image/avif", "avif")
                else -> Sniffed(MediaKind.VIDEO, "video/mp4", "mp4")
            }
            ascii(0, 5).lowercase().startsWith("<!doc") || ascii(0, 5).lowercase().startsWith("<html") ->
                Sniffed(MediaKind.OTHER, "text/html", "html")
            else -> null
        }
    }
}
