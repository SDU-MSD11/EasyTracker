package com.sdumsd11.easytracker.ui.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Account profile / login stub. */
@Composable
fun AccountScreen(
    modifier: Modifier = Modifier,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    ScreenScaffold(title = "Account", modifier = modifier) {
        Text("Guest user (login stub).")
        NavButton(label = "Log in (stub)", onClick = {})
        NavButton(label = "Payment history", onClick = onHistory)
        NavButton(label = "Settings", onClick = onSettings)
    }
}
