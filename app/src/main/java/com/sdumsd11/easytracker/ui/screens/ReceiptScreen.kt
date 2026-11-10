package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.sdumsd11.easytracker.ui.components.AmountText
import com.sdumsd11.easytracker.ui.components.EasyCard
import com.sdumsd11.easytracker.ui.theme.EasyTrackerTheme

/** Receipt (PDF export optional/stub) reachable from PaymentSuccess. */
@Composable
fun ReceiptScreen(
    modifier: Modifier = Modifier,
    groupId: String,
    onDone: () -> Unit,
    amountCents: Long = 1250,
) {
    ScreenScaffold(title = "Receipt", modifier = modifier) {
        EasyCard(modifier = Modifier.fillMaxWidth()) {
            Text("Payment receipt", style = MaterialTheme.typography.titleMedium)
            AmountText(amountCents)
            Text("Group: $groupId", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Keep this receipt for your records.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        NavButton(label = "Export PDF (stub)", onClick = {})
        SecondaryButton(label = "Done", onClick = onDone)
    }
}

@Preview(showBackground = true)
@Composable
private fun ReceiptPreview() {
    EasyTrackerTheme { ReceiptScreen(groupId = "flatmates", onDone = {}) }
}
