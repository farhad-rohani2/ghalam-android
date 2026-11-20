package com.example.ghalam.teacher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ghalam.core.ui.components.EmptyState
import com.example.ghalam.core.ui.theme.Primary
import com.example.ghalam.core.ui.theme.PrimaryDark
import com.example.ghalam.core.ui.theme.Violet

/** Placeholder home: the real teacher screens are added in the next steps. */
@Composable
fun TeacherHomeScreen(onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(Brush.linearGradient(listOf(PrimaryDark, Primary, Violet))),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("سلام 👋", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                    Text("پنل معلم", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onLogout) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "خروج", tint = Color.White)
                }
            }
        }
        EmptyState(
            emoji = "🛠️",
            title = "اسکلت اپ معلم آماده است",
            subtitle = "صفحه‌های معلم در مرحله‌های بعد اضافه می‌شوند",
            modifier = Modifier.padding(top = 48.dp),
        )
    }
}
