package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.sdumsd11.easytracker.ui.components.AmountText
import com.sdumsd11.easytracker.ui.components.EasyCard
import com.sdumsd11.easytracker.ui.components.StatusIcon
import com.sdumsd11.easytracker.ui.theme.EasyTrackerTheme

@Composable
fun PaymentSuccessScreen(
    modifier: Modifier = Modifier,
    groupId: String,
    onBackToGroup: () -> Unit,
    onExportReceipt: (String) -> Unit,
    amountCents: Long = 1250,
) {
    ScreenScaffold(title = "Payment successful", modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) { StatusIcon(success = true) }
        EasyCard(modifier = Modifier.fillMaxWidth()) {
            Text("Receipt", style = MaterialTheme.typography.titleMedium)
            AmountText(amountCents)
            Text("Group: $groupId", style = MaterialTheme.typography.bodyMedium)
            Text(
                "The recipient is notified automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        NavButton(label = "Export receipt (PDF)", onClick = { onExportReceipt(groupId) })
        SecondaryButton(label = "Back to group", onClick = onBackToGroup)
    }
}

@Preview(showBackground = true)
@Composable
private fun PaymentSuccessPreview() {
    EasyTrackerTheme {
        PaymentSuccessScreen(groupId = "flatmates", onBackToGroup = {}, onExportReceipt = {})
    }
}
