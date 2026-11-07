package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Group Debt Overview. EmptyState when the group has no debts. */
// TODO(api-integration): owner-gate via VM (isOwner param is a stub until the VM lands).
@Composable
fun GroupDetailScreen(
    modifier: Modifier = Modifier,
    groupId: String,
    isOwner: Boolean,
    onMakePayment: (String) -> Unit,
    onEditGroup: (String) -> Unit,
    onSetupDebt: (String) -> Unit,
    onBack: () -> Unit,
) {
    val debts = if (groupId == "empty") emptyList() else listOf("Anna owes you €12.50")
    ScreenScaffold(title = "Group: $groupId", modifier = modifier) {
        if (debts.isEmpty()) {
            Text("No debts yet — all settled up.")
            if (isOwner) NavButton(label = "Set up a debt", onClick = { onSetupDebt(groupId) })
        } else {
            debts.forEach { Text(it) }
            NavButton(label = "Make payment", onClick = { onMakePayment(groupId) })
            if (isOwner) {
                NavButton(label = "Edit group (owner)", onClick = { onEditGroup(groupId) })
                NavButton(label = "Set up a debt", onClick = { onSetupDebt(groupId) })
            }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}
