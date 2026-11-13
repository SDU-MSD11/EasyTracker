package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.domain.model.InviteStatus
import com.sdumsd11.easytracker.ui.components.AmountText
import com.sdumsd11.easytracker.ui.components.EmptyState
import com.sdumsd11.easytracker.ui.components.ErrorState
import com.sdumsd11.easytracker.ui.components.LoadingState
import com.sdumsd11.easytracker.ui.components.MemberChip
import com.sdumsd11.easytracker.ui.components.SectionCard
import com.sdumsd11.easytracker.ui.theme.EasyTrackerTheme

data class MemberUi(val id: String, val name: String, val status: InviteStatus? = null)

enum class HistoryKind { PAYMENT, DEBT }

/** Paid-so-far vs total for one debt line (minor units). VM-owned; screen only renders. */
data class PaidProgress(val paidCents: Long = 0L, val totalCents: Long = 0L)

/**
 * One per-group history row. All names/refs arrive display-ready from the VM owner —
 * this layer never renders a full id (falls back to ••••last4).
 */
data class GroupHistoryUi(
    val id: String,
    val kind: HistoryKind = HistoryKind.PAYMENT,
    val title: String = "",
    val payerName: String? = null,
    val ownerName: String? = null,
    val amountCents: Long = 0L,
    /** Pre-shortened tx ref (e.g. ••••1234); falls back to [id] short form. */
    val txRef: String? = null,
)

/**
 * Group Debt Overview. Members/debts come from the VM owner — this layer renders
 * loading / error / empty states and never ships hardcoded mock rows.
 */
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
    groupName: String? = null,
    members: List<MemberUi> = emptyList(),
    debts: List<String> = emptyList(),
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
    removeResult: RequestStatus<Unit>? = null,
    onRemoveMember: (String) -> Unit = {},
    onDismissRemoveResult: () -> Unit = {},
    history: List<GroupHistoryUi> = emptyList(),
    debtProgress: Map<String, PaidProgress> = emptyMap(),
    historyIsLoading: Boolean = false,
    historyError: String? = null,
    historyHasLoaded: Boolean = true,
) {
    // Success stays on-screen — the members flow re-emits from the refreshed cache.
    // Clear the stale Success so it never replays; errors stay visible until dismissed.
    if (removeResult is RequestStatus.Success) {
        LaunchedEffect(removeResult) { onDismissRemoveResult() }
    }
    ScreenScaffold(title = groupName ?: "Group", modifier = modifier, onBack = onBack) {
        when {
            isLoading -> LoadingState("Loading group…")
            errorMessage != null -> ErrorState(errorMessage, onRetry = onRetry)
            else -> GroupDetailContent(
                groupId = groupId,
                members = members,
                debts = debts,
                isOwner = isOwner,
                onMakePayment = onMakePayment,
                onEditGroup = onEditGroup,
                onSetupDebt = onSetupDebt,
                removeResult = removeResult,
                onRemoveMember = onRemoveMember,
                onDismissRemoveResult = onDismissRemoveResult,
                history = history,
                debtProgress = debtProgress,
                historyIsLoading = historyIsLoading,
                historyError = historyError,
                historyHasLoaded = historyHasLoaded,
                onHistoryRetry = onRetry,
            )
        }
    }
}

@Composable
private fun GroupDetailContent(
    groupId: String,
    members: List<MemberUi>,
    debts: List<String>,
    isOwner: Boolean,
    onMakePayment: (String) -> Unit,
    onEditGroup: (String) -> Unit,
    onSetupDebt: (String) -> Unit,
    removeResult: RequestStatus<Unit>?,
    onRemoveMember: (String) -> Unit,
    onDismissRemoveResult: () -> Unit,
    history: List<GroupHistoryUi> = emptyList(),
    debtProgress: Map<String, PaidProgress> = emptyMap(),
    historyIsLoading: Boolean = false,
    historyError: String? = null,
    historyHasLoaded: Boolean = true,
    onHistoryRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var pendingRemove: MemberUi? by remember { mutableStateOf(null) }
    val removing = removeResult is RequestStatus.Pending
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Members card: owners see status chips + invite/remove; members see plain rows.
        SectionCard(title = "Members", modifier = Modifier.fillMaxWidth()) {
            if (members.isEmpty()) {
                Text(
                    "No members yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                members.forEach { member ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MemberChip(
                            name = member.name,
                            status = if (isOwner) member.status else null,
                            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                        )
                        if (isOwner) {
                            TextButton(
                                onClick = { pendingRemove = member },
                                enabled = !removing,
                                modifier = Modifier.heightIn(min = 48.dp)
                                    .semantics { contentDescription = "Remove ${member.name}" },
                            ) { Text(if (removing) "…" else "Remove") }
                        }
                    }
                }
            }
            // Repo message verbatim (403 owner-only etc); dismiss clears the VM state.
            if (removeResult is RequestStatus.Failed) {
                Text(
                    removeResult.message ?: "Couldn't remove that member.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(
                    onClick = onDismissRemoveResult,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Dismiss") }
            }
            if (isOwner) {
                OutlinedButton(
                    onClick = { onEditGroup(groupId) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Add member") }
            }
        }
        // Overview card: totals + actions.
        SectionCard(title = "Overview", modifier = Modifier.fillMaxWidth()) {
            if (debts.isEmpty()) {
                EmptyState(
                    title = "No debts yet",
                    message = "All settled up — set up a debt to get started.",
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    debts.forEach { debt ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(debt, style = MaterialTheme.typography.bodyLarge)
                            debtProgress[debt]?.let { progress ->
                                Text(
                                    "Paid ${euros(progress.paidCents)} of ${euros(progress.totalCents)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (progress.totalCents > 0) {
                                    LinearProgressIndicator(
                                        progress = {
                                            (progress.paidCents.toFloat() / progress.totalCents.toFloat())
                                                .coerceIn(0f, 1f)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
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
        // History card: per-group payments + debts for owner/payer tracking.
        SectionCard(title = "History", modifier = Modifier.fillMaxWidth()) {
            when {
                historyIsLoading || (!historyHasLoaded && historyError == null && history.isEmpty()) ->
                    LoadingState("Loading history…")
                history.isEmpty() && historyError != null ->
                    ErrorState(historyError, onRetry = onHistoryRetry)
                history.isEmpty() -> EmptyState(
                    title = "No activity yet",
                    message = "Payments and debts in this group will show up here.",
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    history.forEach { entry ->
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.semantics {
                                contentDescription = "${entry.title} ${euros(entry.amountCents)}"
                            },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = entry.title.ifBlank {
                                        if (entry.kind == HistoryKind.DEBT) "Debt" else "Payment"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                AmountText(entry.amountCents)
                            }
                            Text(
                                text = historySubtitle(entry),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
    // Confirm every removal — including self — before hitting the repo.
    pendingRemove?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Remove ${target.name}?") },
            text = { Text("${target.name} will lose access to this group.") },
            confirmButton = {
                TextButton(
                    onClick = { onRemoveMember(target.id); pendingRemove = null },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingRemove = null },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Cancel") }
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GroupDetailOwnerPreview() {
    EasyTrackerTheme {
        GroupDetailScreen(
            groupId = "flatmates", groupName = "Flatmates", isOwner = true,
            onMakePayment = {}, onEditGroup = {}, onSetupDebt = {}, onBack = {},
            members = listOf(
                MemberUi("you", "You", InviteStatus.ACCEPTED),
                MemberUi("anna", "Anna", InviteStatus.ACCEPTED),
                MemberUi("ben", "Ben", InviteStatus.ACCEPTED),
            ),
            debts = listOf("Anna owes you €12.50"),
            debtProgress = mapOf("Anna owes you €12.50" to PaidProgress(500, 1250)),
            history = listOf(
                GroupHistoryUi("p1", HistoryKind.PAYMENT, "Dinner", "Anna", "You", 1250, "••••1234"),
                GroupHistoryUi("d1", HistoryKind.DEBT, "Cabin", "You", "Anna", 4300, null),
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GroupDetailMemberPreview() {
    EasyTrackerTheme {
        GroupDetailScreen(
            groupId = "flatmates", groupName = "Flatmates", isOwner = false,
            onMakePayment = {}, onEditGroup = {}, onSetupDebt = {}, onBack = {},
            members = listOf(MemberUi("you", "You"), MemberUi("anna", "Anna")),
        )
    }
}

/** Never a full id — display names win, ids collapse to ••••last4. */
private fun shortRef(id: String): String =
    if (id.length > 8) "••••${id.takeLast(4)}" else id.ifBlank { "••••????" }

private fun euros(cents: Long): String = "€${"%.2f".format(cents / 100.0)}"

private fun historySubtitle(entry: GroupHistoryUi): String {
    val payer = entry.payerName ?: "Someone"
    val owner = entry.ownerName
    val parties = if (owner != null) "$payer → $owner" else payer
    val tag = if (entry.kind == HistoryKind.DEBT) "Debt" else "Paid"
    return "$tag • $parties"
}
