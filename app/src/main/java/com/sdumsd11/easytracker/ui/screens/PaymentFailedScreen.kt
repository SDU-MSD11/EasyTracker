package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.sdumsd11.easytracker.ui.components.StatusIcon
import com.sdumsd11.easytracker.ui.theme.EasyTrackerTheme

@Composable
fun PaymentFailedScreen(
    modifier: Modifier = Modifier,
    groupId: String,
    onRetry: () -> Unit,
    onBackToGroup: () -> Unit,
    error: String? = null,
) {
    ScreenScaffold(title = "Payment failed", modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) { StatusIcon(success = false) }
        Text(
            text = error ?: "Your payment for $groupId did not go through. Nothing was charged.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        NavButton("Retry payment", onClick = onRetry)
        SecondaryButton("Back to group", onClick = onBackToGroup)
    }
}

@Preview(showBackground = true)
@Composable
private fun PaymentFailedPreview() {
    EasyTrackerTheme {
        PaymentFailedScreen(groupId = "flatmates", onRetry = {}, onBackToGroup = {})
    }
}
