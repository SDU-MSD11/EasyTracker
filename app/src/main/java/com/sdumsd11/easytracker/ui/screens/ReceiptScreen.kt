package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.sdumsd11.easytracker.ui.components.AmountText
import com.sdumsd11.easytracker.ui.components.EasyCard
import com.sdumsd11.easytracker.ui.theme.EasyTrackerTheme

/**
 * Receipt. [amountCents] and [txId] are the real payment props from the VM/nav
 * owner (defaults are preview samples only). [txId] renders as the Transaction
 * line — never as the Group line; Group shows the resolved [groupName] (or a
 * short-id fallback when unknown). Share fires the Android
 * sharesheet with the receipt text — no dead export button.
 */
@Composable
fun ReceiptScreen(
    modifier: Modifier = Modifier,
    groupId: String,
    onDone: () -> Unit,
    amountCents: Long = 1250,
    txId: String = "",
    groupName: String? = null,
) {
    val context = LocalContext.current
    val group = displayGroupLabel(groupId, groupName)
    ScreenScaffold(title = "Receipt", modifier = modifier) {
        EasyCard(modifier = Modifier.fillMaxWidth()) {
            Text("Payment receipt", style = MaterialTheme.typography.titleMedium)
            AmountText(amountCents)
            if (group != null) {
                Text("Group: $group", style = MaterialTheme.typography.bodyMedium)
            }
            if (txId.isNotBlank()) {
                Text("Transaction: $txId", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Keep this receipt for your records.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        NavButton(
            label = "Share receipt",
            onClick = { sharePlainText(context, receiptText(amountCents, group.orEmpty(), txId)) },
        )
        SecondaryButton(label = "Done", onClick = onDone)
    }
}

@Preview(showBackground = true)
@Composable
private fun ReceiptPreview() {
    EasyTrackerTheme {
        ReceiptScreen(groupId = "g1", groupName = "Flatmates", txId = "tx-42", onDone = {})
    }
}
