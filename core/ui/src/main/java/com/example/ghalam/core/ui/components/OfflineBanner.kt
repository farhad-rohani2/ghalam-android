package com.example.ghalam.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ghalam.core.ui.theme.Warn
import com.example.ghalam.core.ui.theme.WarnBg

/** Small notice shown when the screen is displaying saved (cached) data. */
@Composable
fun OfflineBanner(
    text: String = "📴 نسخه‌ی ذخیره‌شده نمایش داده می‌شود؛ ممکن است قدیمی باشد.",
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
) {
    Box(
        modifier
            .padding(horizontal = horizontalPadding)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WarnBg)
            .padding(12.dp),
    ) {
        Text(text, color = Warn, style = MaterialTheme.typography.bodyMedium)
    }
}
