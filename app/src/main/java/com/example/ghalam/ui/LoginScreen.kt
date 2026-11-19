package com.example.ghalam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    state: LoginState,
    onLogin: (String) -> Unit,
    onManualToken: (String) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("ورود", style = MaterialTheme.typography.headlineMedium)

        if (!manual) {
            Text("کد ملی خود را وارد کنید.", modifier = Modifier.padding(vertical = 12.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("کد ملی") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.loading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onLogin(code) }),
            )
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            Button(
                onClick = { onLogin(code) },
                enabled = code.isNotBlank() && !state.loading,
                modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
            ) {
                if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("ورود")
            }
            TextButton(onClick = { manual = true }, modifier = Modifier.fillMaxWidth()) {
                Text("ورود با توکن")
            }
        } else {
            Text(
                "توکن (Bearer) حساب خود را وارد کنید. توکن رمزنگاری‌شده روی همین دستگاه ذخیره می‌شود.",
                modifier = Modifier.padding(vertical = 12.dp),
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("توکن") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(
                onClick = { onManualToken(token) },
                enabled = token.isNotBlank(),
                modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
            ) { Text("ذخیره و ادامه") }
            TextButton(onClick = { manual = false }, modifier = Modifier.fillMaxWidth()) {
                Text("ورود با کد ملی")
            }
        }
    }
}
