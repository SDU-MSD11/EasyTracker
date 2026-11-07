package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun EditProfileScreen(modifier: Modifier = Modifier, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    ScreenScaffold(title = "Edit profile", modifier = modifier) {
        TextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Display name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        NavButton("Save", enabled = name.isNotBlank(), onClick = onDone)
    }
}
