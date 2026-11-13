package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.sdumsd11.easytracker.ui.components.AmountText
import com.sdumsd11.easytracker.ui.components.EasyCard
import com.sdumsd11.easytracker.ui.components.StatusIcon
import com.sdumsd11.easytracker.ui.theme.EasyTrackerTheme

/**
 * Payment success. [amountCents] and [txId] are the real payment props from the
 * VM/nav owner (defaults are preview samples only — the production path always
 * passes values through). Receipt export navigates to the Receipt screen;
 * Share fires the Android sharesheet — no no-op buttons.
 */
@Composable
fun PaymentSuccessScreen(
    modifier: Modifier = Modifier,
    groupId: String,
    onBackToGroup: () -> Unit,
    onExportReceipt: (String) -> Unit,
    amountCents: Long = 1250,
    txId: String = "",
    groupName: String? = null,
) {
    val context = LocalContext.current
    val group = displayGroupLabel(groupId, groupName).orEmpty()
    ScreenScaffold(title = "Payment successful", modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) { StatusIcon(success = true) }
        EasyCard(modifier = Modifier.fillMaxWidth()) {
            Text("Receipt", style = MaterialTheme.typography.titleMedium)
            AmountText(amountCents)
            if (group.isNotBlank()) {
                Text("Group: $group", style = MaterialTheme.typography.bodyMedium)
            }
            if (txId.isNotBlank()) {
                Text("Transaction: $txId", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "The recipient is notified automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        NavButton(label = "View receipt", onClick = { onExportReceipt(txId.ifBlank { groupId }) })
        SecondaryButton(
            label = "Share receipt",
            onClick = { sharePlainText(context, receiptText(amountCents, group, txId)) },
        )
        SecondaryButton(label = "Back to group", onClick = onBackToGroup)
    }
}

@Preview(showBackground = true)
@Composable
private fun PaymentSuccessPreview() {
    EasyTrackerTheme {
        PaymentSuccessScreen(
            groupId = "g1",
            groupName = "Flatmates",
            txId = "tx-42",
            amountCents = 1250,
            onBackToGroup = {},
            onExportReceipt = {},
        )
    }
}
