package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sdumsd11.easytracker.domain.model.InviteStatus
import com.sdumsd11.easytracker.ui.components.Avatar
import com.sdumsd11.easytracker.ui.components.EasyCard
import com.sdumsd11.easytracker.ui.components.EmptyState
import com.sdumsd11.easytracker.ui.components.MemberChip
import com.sdumsd11.easytracker.ui.theme.EasyTrackerTheme

data class MemberUi(val id: String, val name: String, val status: InviteStatus? = null)

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
    members: List<MemberUi> = listOf(
        MemberUi("you", "You", InviteStatus.ACCEPTED),
        MemberUi("anna", "Anna", InviteStatus.ACCEPTED),
        MemberUi("ben", "Ben", InviteStatus.PENDING),
    ),
    debts: List<String> = if (groupId == "empty") emptyList() else listOf("Anna owes you €12.50"),
) {
    ScreenScaffold(title = "Group", modifier = modifier, onBack = onBack) {
        // Members card: owners see status chips + invite button; members see plain rows.
        EasyCard(modifier = Modifier.fillMaxWidth()) {
            Text(groupId, style = MaterialTheme.typography.titleLarge)
            members.forEach { member ->
                MemberChip(
                    name = member.name,
                    status = if (isOwner) member.status else null,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
            if (isOwner) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Invite",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { onEditGroup(groupId) },
                        modifier = Modifier.size(48.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) { Avatar(name = "+", size = 24, description = "Invite member") }
                }
            }
        }
        // Overview card: totals + actions.
        EasyCard(modifier = Modifier.fillMaxWidth()) {
            Text("Overview", style = MaterialTheme.typography.titleMedium)
            if (debts.isEmpty()) {
                EmptyState("No debts yet — all settled up.")
            } else {
                debts.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }
            FilledTonalButton(
                onClick = { onMakePayment(groupId) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Make payment") }
            if (isOwner) {
                SecondaryButton("Edit group", onClick = { onEditGroup(groupId) })
                SecondaryButton("Set up a debt", onClick = { onSetupDebt(groupId) })
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GroupDetailOwnerPreview() {
    EasyTrackerTheme {
        GroupDetailScreen(
            groupId = "flatmates", isOwner = true,
            onMakePayment = {}, onEditGroup = {}, onSetupDebt = {}, onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GroupDetailMemberPreview() {
    EasyTrackerTheme {
        GroupDetailScreen(
            groupId = "flatmates", isOwner = false,
            onMakePayment = {}, onEditGroup = {}, onSetupDebt = {}, onBack = {},
        )
    }
}
