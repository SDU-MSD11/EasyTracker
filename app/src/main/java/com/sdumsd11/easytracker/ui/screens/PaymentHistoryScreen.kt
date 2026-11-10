package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sdumsd11.easytracker.ui.components.AmountText
import com.sdumsd11.easytracker.ui.components.EasyCard
import com.sdumsd11.easytracker.ui.components.EmptyState
import com.sdumsd11.easytracker.ui.theme.EasyTrackerTheme

enum class TxStatus { SUCCESS, FAILED }

data class TxUi(val id: String, val title: String, val amountCents: Long, val status: TxStatus, val group: String)

@Composable
fun PaymentHistoryScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    transactions: List<TxUi> = listOf(
        TxUi("t1", "Dinner", 1250, TxStatus.SUCCESS, "Flatmates"),
        TxUi("t2", "Groceries", 800, TxStatus.FAILED, "Flatmates"),
        TxUi("t3", "Cabin", 4300, TxStatus.SUCCESS, "Summer trip"),
    ),
) {
    var filter by remember { mutableStateOf<TxStatus?>(null) }
    val shown = transactions.filter { filter == null || it.status == filter }
    ScreenScaffold(title = "Payment history", modifier = modifier, onBack = onBack, scrollable = false) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") })
            FilterChip(selected = filter == TxStatus.SUCCESS, onClick = { filter = TxStatus.SUCCESS }, label = { Text("Paid") })
            FilterChip(selected = filter == TxStatus.FAILED, onClick = { filter = TxStatus.FAILED }, label = { Text("Failed") })
        }
        if (shown.isEmpty()) {
            EmptyState("No payments yet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(shown.groupBy { it.group }.toList(), key = { it.first }) { (group, txs) ->
                    Text(group, style = MaterialTheme.typography.titleSmall)
                    txs.forEach { tx ->
                        EasyCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = tx.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                AmountText(if (tx.status == TxStatus.FAILED) 0L else tx.amountCents)
                            }
                            Text(
                                text = if (tx.status == TxStatus.SUCCESS) "Paid" else "Failed — nothing charged",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (tx.status == TxStatus.SUCCESS) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PaymentHistoryPreview() {
    EasyTrackerTheme { PaymentHistoryScreen(onBack = {}) }
}
