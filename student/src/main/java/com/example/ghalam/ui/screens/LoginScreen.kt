package com.example.ghalam.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ghalam.core.ui.theme.Primary
import com.example.ghalam.core.ui.theme.PrimaryDark
import com.example.ghalam.core.ui.theme.Violet
import com.example.ghalam.ui.viewmodel.LoginState

@Composable
fun LoginScreen(
    state: LoginState,
    onLogin: (String) -> Unit,
    onManualToken: (String) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(PrimaryDark, Primary, Violet))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(84.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Text("🎒", style = MaterialTheme.typography.displaySmall)
            }
            Text(
                "تکالیف قلم",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                "تکلیف‌هایت را ببین و پاسخ‌ها را بفرست",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!manual) {
                        Text("ورود با کد ملی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it },
                            label = { Text("کد ملی") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !state.loading,
                            shape = MaterialTheme.shapes.medium,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onLogin(code) }),
                        )
                        state.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = { onLogin(code) },
                            enabled = code.isNotBlank() && !state.loading,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            if (state.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                            else Text("ورود", fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { manual = true }, modifier = Modifier.fillMaxWidth()) { Text("ورود با توکن") }
                    } else {
                        Text("ورود با توکن", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "توکن حساب را وارد کنید. روی همین دستگاه رمزنگاری‌شده ذخیره می‌شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Start,
                        )
                        OutlinedTextField(
                            value = token,
                            onValueChange = { token = it },
                            label = { Text("توکن") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                        )
                        Button(
                            onClick = { onManualToken(token) },
                            enabled = token.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = MaterialTheme.shapes.medium,
                        ) { Text("ذخیره و ادامه", fontWeight = FontWeight.Bold) }
                        TextButton(onClick = { manual = false }, modifier = Modifier.fillMaxWidth()) { Text("ورود با کد ملی") }
                    }
                }
            }
        }
    }
}
