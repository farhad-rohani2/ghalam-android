package com.example.ghalam.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Round "⋮" button for coloured headers; opens a drop-down with profile and settings. */
@Composable
fun HeaderMenu(
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.White.copy(alpha = 0.22f),
    contentColor: Color = Color.White,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(containerColor).clickable { open = true },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.MoreVert, "منو", tint = contentColor) }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("پروفایل") },
                leadingIcon = { Icon(Icons.Filled.Person, null) },
                onClick = { open = false; onProfile() },
            )
            DropdownMenuItem(
                text = { Text("تنظیمات") },
                leadingIcon = { Icon(Icons.Filled.Settings, null) },
                onClick = { open = false; onSettings() },
            )
        }
    }
}
