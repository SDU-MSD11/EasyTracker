package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.sdumsd11.easytracker.ui.components.ErrorState
import com.sdumsd11.easytracker.ui.components.LoadingState
import com.sdumsd11.easytracker.ui.theme.EasyTrackerTheme

enum class TxStatus { SUCCESS, FAILED }

data class TxUi(
    val id: String,
    val title: String,
    val amountCents: Long,
    val status: TxStatus,
    val group: String,
    /** Resolved display name; falls back to [group] (currently the group id) until wired. */
    val groupName: String? = null,
)

/**
 * Payment history. Rows come from the VM owner (ExpenseRepository.observeAllExpenses) —
 * this layer renders loading / error / empty states and never ships hardcoded mock rows.
 *
 * The status filter is hoisted ([statusFilter] null + [onStatusFilterChange] null =
 * managed internally). Group headers resolve via [groupNames] (groupId → display
 * name), then [TxUi.groupName], then the raw id. [hasLoaded] is false until the
 * first successful load: a cold cache renders loading, never a false empty.
 */
@Composable
fun PaymentHistoryScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    transactions: List<TxUi> = emptyList(),
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
    statusFilter: TxStatus? = null,
    onStatusFilterChange: ((TxStatus?) -> Unit)? = null,
    groupNames: Map<String, String> = emptyMap(),
    onRefresh: () -> Unit = {},
    hasLoaded: Boolean = true,
) {
    var internalFilter by remember { mutableStateOf<TxStatus?>(null) }
    val filter = if (onStatusFilterChange == null) internalFilter else statusFilter
    fun select(next: TxStatus?) {
        internalFilter = next
        onStatusFilterChange?.invoke(next)
    }
    val shown = transactions.filter { filter == null || it.status == filter }
    fun displayGroup(tx: TxUi): String = groupNames[tx.group] ?: tx.groupName ?: tx.group
    ScreenScaffold(
        title = "Payment history",
        modifier = modifier,
        onBack = onBack,
        actions = {
            TextButton(
                onClick = onRefresh,
                enabled = !isLoading,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Refresh") }
        },
        scrollable = false,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filter == null, onClick = { select(null) }, label = { Text("All") })
            FilterChip(selected = filter == TxStatus.SUCCESS, onClick = { select(TxStatus.SUCCESS) }, label = { Text("Paid") })
            FilterChip(selected = filter == TxStatus.FAILED, onClick = { select(TxStatus.FAILED) }, label = { Text("Failed") })
        }
        when {
            transactions.isEmpty() && (isLoading || (!hasLoaded && errorMessage == null)) ->
                LoadingState("Loading payments…")
            transactions.isEmpty() && errorMessage != null ->
                ErrorState(errorMessage, onRetry = onRetry)
            transactions.isEmpty() -> EmptyState(
                title = "No payments yet.",
                message = "Payments you make or receive will show up here.",
            )
            shown.isEmpty() -> EmptyState(
                title = if (filter == TxStatus.FAILED) "No failed payments." else "No paid payments yet.",
                message = if (filter == TxStatus.FAILED) {
                    "Nothing failed — every recorded payment went through."
                } else {
                    "Successful payments will show up here."
                },
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(shown.groupBy(::displayGroup).toList(), key = { it.first }) { (group, txs) ->
                    Text(group, style = MaterialTheme.typography.titleSmall)
                    txs.forEach { tx ->
                        EasyCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = tx.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                AmountText(tx.amountCents)
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
    EasyTrackerTheme {
        PaymentHistoryScreen(
            onBack = {},
            transactions = listOf(
                TxUi("t1", "Dinner", 1250, TxStatus.SUCCESS, "g1", "Flatmates"),
                TxUi("t2", "Cabin", 4300, TxStatus.SUCCESS, "g2", "Summer trip"),
            ),
            groupNames = mapOf("g1" to "Flatmates", "g2" to "Summer trip"),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PaymentHistoryEmptyPreview() {
    EasyTrackerTheme { PaymentHistoryScreen(onBack = {}) }
}
