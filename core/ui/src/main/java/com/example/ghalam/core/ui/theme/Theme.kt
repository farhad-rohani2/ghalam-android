package com.example.ghalam.core.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ghalam.core.ui.R

// Palette taken from the site (themeColor #6c7fd8, text #3d4750 / #686e7d, bg #f8f8fb, accent #fea99a)
val Primary = Color(0xFF6C7FD8)
val PrimaryDark = Color(0xFF4B5DBF)
val Violet = Color(0xFF8B7BE0)
val Coral = Color(0xFFFEA99A)
val Ink = Color(0xFF3D4750)
val InkSoft = Color(0xFF686E7D)
val Surface0 = Color(0xFFF8F8FB)
val Outline = Color(0xFFE8EAF2)
val Success = Color(0xFF1E8E5A)
val SuccessBg = Color(0xFFE3F6EC)
val Danger = Color(0xFFC0392B)
val DangerBg = Color(0xFFFDECEA)
val Warn = Color(0xFFB77A00)
val WarnBg = Color(0xFFFFF3D6)

val Vazir = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private fun TextStyle.vz() = copy(
    fontFamily = Vazir,
    letterSpacing = 0.sp,
    lineHeight = (fontSize.value * 1.65f).sp,
)

private val base = Typography()
private val AppTypography = Typography(
    displayLarge = base.displayLarge.vz(),
    displayMedium = base.displayMedium.vz(),
    displaySmall = base.displaySmall.vz(),
    headlineLarge = base.headlineLarge.vz(),
    headlineMedium = base.headlineMedium.vz(),
    headlineSmall = base.headlineSmall.vz(),
    titleLarge = base.titleLarge.vz(),
    titleMedium = base.titleMedium.vz(),
    titleSmall = base.titleSmall.vz(),
    bodyLarge = base.bodyLarge.vz(),
    bodyMedium = base.bodyMedium.vz(),
    bodySmall = base.bodySmall.vz(),
    labelLarge = base.labelLarge.vz(),
    labelMedium = base.labelMedium.vz(),
    labelSmall = base.labelSmall.vz(),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val Colors = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6EAFB),
    onPrimaryContainer = PrimaryDark,
    secondary = Coral,
    onSecondary = Ink,
    secondaryContainer = Color(0xFFFFE9E4),
    background = Surface0,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF1F2F8),
    onSurfaceVariant = InkSoft,
    outline = Color(0xFFC9CCD8),
    outlineVariant = Outline,
    error = Danger,
    errorContainer = DangerBg,
)

@Composable
fun GhalamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = AppTypography, shapes = AppShapes, content = content)
}

// ---------- per-lesson colour (stable for the same lesson title) ----------

data class Tone(val container: Color, val content: Color)

private val Tones = listOf(
    Tone(Color(0xFFE6EAFB), Color(0xFF4B5DBF)),
    Tone(Color(0xFFFFE9E4), Color(0xFFC2543F)),
    Tone(Color(0xFFE3F6EC), Color(0xFF1E8E5A)),
    Tone(Color(0xFFFFF3D6), Color(0xFFB07400)),
    Tone(Color(0xFFEAE3F8), Color(0xFF6A45B5)),
    Tone(Color(0xFFDDF3F7), Color(0xFF0F7C8F)),
    Tone(Color(0xFFFBE4F0), Color(0xFFB03A78)),
)

fun lessonTone(title: String): Tone = Tones[(title.hashCode() and Int.MAX_VALUE) % Tones.size]

// ---------- shimmer for loading skeletons ----------

fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX",
    )
    val base = Color(0xFFE9EBF3)
    val highlight = Color(0xFFF7F8FC)
    background(
        Brush.horizontalGradient(
            colors = listOf(base, highlight, base),
            startX = -400f + x * 1600f,
            endX = x * 1600f + 400f,
        )
    )
}
