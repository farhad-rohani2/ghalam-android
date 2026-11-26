package com.example.ghalam.core.ui.voice

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** آیکن‌های کوچک ضبط صدا (بدون نیاز به کتابخانهٔ آیکن اضافه). */
object VoiceIcons {
    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
        }.build()

    val Mic: ImageVector by lazy {
        icon(
            "Mic",
            "M12 14c1.66 0 2.99-1.34 2.99-3L15 5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5.3-3c0 3-2.54 5.1-5.3 5.1S6.7 14 6.7 11H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c3.28-.48 6-3.3 6-6.72h-1.7z",
        )
    }
    val Stop: ImageVector by lazy { icon("Stop", "M6 6h12v12H6z") }
    val Pause: ImageVector by lazy { icon("Pause", "M6 19h4V5H6v14zm8-14v14h4V5h-4z") }
    val Play: ImageVector by lazy { icon("Play", "M8 5v14l11-7z") }
    val Delete: ImageVector by lazy {
        icon("Delete", "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z")
    }
    val Check: ImageVector by lazy { icon("Check", "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z") }
}
