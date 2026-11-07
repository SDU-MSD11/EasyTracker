package com.sdumsd11.easytracker.ui.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SettingsScreen(modifier: Modifier = Modifier, onEditProfile: () -> Unit) {
    ScreenScaffold(title = "Settings", modifier = modifier) {
        Text("Theme follows system (stub).")
        NavButton("Edit profile", onClick = onEditProfile)
    }
}
