package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.ui.components.Avatar
import com.sdumsd11.easytracker.ui.components.RequestStatusBanner
import com.sdumsd11.easytracker.ui.theme.EasyTrackerTheme

/**
 * Edit profile. Saving runs in the VM owner ([onSave] persists name/contact onto the
 * current profile); [saveResult] success fires [onDone] (the graph confirms with a
 * Snackbar). Phone is local-only — the User model has no phone field.
 *
 * Pending disables the form and shows progress; failed shows the repo message with
 * Retry (re-submits the last values) and Dismiss ([onDismissError] clears the VM
 * result). Cancel stays enabled throughout — failure never traps the user.
 */
@Composable
fun EditProfileScreen(
    modifier: Modifier = Modifier,
    onDone: () -> Unit,
    onCancel: (() -> Unit)? = null,
    initialName: String = "",
    initialEmail: String = "",
    initialPhone: String = "",
    saveResult: RequestStatus<Unit>? = null,
    onSave: (String, String) -> Unit = { _, _ -> },
    onDismissError: () -> Unit = {},
) {
    var name by remember { mutableStateOf(initialName) }
    var email by remember { mutableStateOf(initialEmail) }
    var phone by remember { mutableStateOf(initialPhone) }
    var lastSubmitted by remember { mutableStateOf<Pair<String, String>?>(null) }
    val isSaving = saveResult is RequestStatus.Pending
    val isFailed = saveResult is RequestStatus.Failed
    LaunchedEffect(saveResult) {
        if (saveResult is RequestStatus.Success) onDone()
    }
    ScreenScaffold(title = "Edit profile", modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Avatar(name = name.ifBlank { "?" }, size = 72, description = "Your profile avatar")
            Text(
                text = name.ifBlank { "Your name" },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Display name") },
            singleLine = true,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            enabled = !isSaving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone (optional)") },
            singleLine = true,
            enabled = !isSaving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        RequestStatusBanner(
            status = saveResult,
            pendingLabel = "Saving your profile…",
            onRetry = if (isFailed) {
                { lastSubmitted?.let { onSave(it.first, it.second) } }
            } else {
                null
            },
            onDismiss = if (isFailed) onDismissError else null,
        )
        NavButton(
            if (isSaving) "Saving…" else "Save",
            enabled = name.isNotBlank() && !isSaving,
            onClick = {
                lastSubmitted = name to email
                onSave(name, email)
            },
        )
        SecondaryButton("Cancel", onClick = onCancel ?: onDone)
    }
}

@Preview(showBackground = true)
@Composable
private fun EditProfilePreview() {
    EasyTrackerTheme { EditProfileScreen(onDone = {}) }
}
