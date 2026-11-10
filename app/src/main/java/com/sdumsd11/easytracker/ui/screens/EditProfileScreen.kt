package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sdumsd11.easytracker.ui.components.Avatar
import com.sdumsd11.easytracker.ui.theme.EasyTrackerTheme

@Composable
fun EditProfileScreen(
    modifier: Modifier = Modifier,
    onDone: () -> Unit,
    onCancel: (() -> Unit)? = null,
    initialName: String = "",
    initialEmail: String = "",
    initialPhone: String = "",
) {
    var name by remember { mutableStateOf(initialName) }
    var email by remember { mutableStateOf(initialEmail) }
    var phone by remember { mutableStateOf(initialPhone) }
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
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone (optional)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        NavButton("Save", enabled = name.isNotBlank(), onClick = onDone)
        SecondaryButton("Cancel", onClick = onCancel ?: onDone)
    }
}

@Preview(showBackground = true)
@Composable
private fun EditProfilePreview() {
    EasyTrackerTheme { EditProfileScreen(onDone = {}) }
}
