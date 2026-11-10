package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.domain.model.User
import com.sdumsd11.easytracker.ui.theme.EasyTrackerTheme

/** Login. Follows the CreateGroup form language: title, labeled fields, primary + secondary. */
@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    result: RequestStatus<User>? = null,
    onSubmit: (email: String, password: String) -> Unit,
    onRegisterClick: () -> Unit,
    onAuthenticated: () -> Unit = {},
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    if (result is RequestStatus.Success) {
        LaunchedEffect(result) { onAuthenticated() }
    }
    val pending = result is RequestStatus.Pending
    val valid = email.contains("@") && password.length >= 8
    ScreenScaffold(title = "Log in", modifier = modifier) {
        Text(
            "Welcome back — split expenses with your groups.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            enabled = !pending,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            enabled = !pending,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        if (result is RequestStatus.Failed) {
            Text(result.message ?: "Authentication failed.", color = MaterialTheme.colorScheme.error)
        }
        NavButton(
            label = if (pending) "Please wait…" else "Log in",
            enabled = valid && !pending,
            onClick = { onSubmit(email.trim(), password) },
        )
        TextButton(onClick = onRegisterClick, enabled = !pending, modifier = Modifier.fillMaxWidth()) {
            Text("No account yet? Create one")
        }
    }
}

/** Register. Same form language as login + display name. */
@Composable
fun RegisterScreen(
    modifier: Modifier = Modifier,
    result: RequestStatus<User>? = null,
    onSubmit: (name: String, email: String, password: String) -> Unit,
    onLoginClick: () -> Unit,
    onAuthenticated: () -> Unit = {},
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    if (result is RequestStatus.Success) {
        LaunchedEffect(result) { onAuthenticated() }
    }
    val pending = result is RequestStatus.Pending
    val valid = name.isNotBlank() && email.contains("@") && password.length >= 8
    ScreenScaffold(title = "Create account", modifier = modifier) {
        Text(
            "One account for every group you join.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Display name") },
            singleLine = true,
            enabled = !pending,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            enabled = !pending,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password (min. 8 characters)") },
            singleLine = true,
            enabled = !pending,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        if (result is RequestStatus.Failed) {
            Text(result.message ?: "Authentication failed.", color = MaterialTheme.colorScheme.error)
        }
        NavButton(
            label = if (pending) "Please wait…" else "Create account",
            enabled = valid && !pending,
            onClick = { onSubmit(name.trim(), email.trim(), password) },
        )
        TextButton(onClick = onLoginClick, enabled = !pending, modifier = Modifier.fillMaxWidth()) {
            Text("Already have an account? Log in")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginPreview() {
    EasyTrackerTheme { LoginScreen(onSubmit = { _, _ -> }, onRegisterClick = {}) }
}

@Preview(showBackground = true)
@Composable
private fun RegisterPreview() {
    EasyTrackerTheme { RegisterScreen(onSubmit = { _, _, _ -> }, onLoginClick = {}) }
}
