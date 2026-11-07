package com.sdumsd11.easytracker.ui.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun PaymentFailedScreen(
    modifier: Modifier = Modifier,
    groupId: String,
    onRetry: () -> Unit,
    onBackToGroup: () -> Unit,
) {
    ScreenScaffold(title = "Payment failed", modifier = modifier) {
        Text("Your payment for $groupId did not go through. Nothing was charged.")
        NavButton("Retry payment", onClick = onRetry)
        NavButton("Back to group", onClick = onBackToGroup)
    }
}
