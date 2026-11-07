package com.sdumsd11.easytracker.ui.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun PaymentSuccessScreen(
    modifier: Modifier = Modifier,
    groupId: String,
    onBackToGroup: () -> Unit,
    onExportReceipt: (String) -> Unit,
) {
    ScreenScaffold(title = "Payment successful", modifier = modifier) {
        Text("Your payment for $groupId went through.")
        NavButton(label = "Export receipt (PDF)", onClick = { onExportReceipt(groupId) })
        NavButton(label = "Back to group", onClick = { onBackToGroup() })
    }
}
