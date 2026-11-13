package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.domain.model.Group
import com.sdumsd11.easytracker.ui.components.EasyCard
import com.sdumsd11.easytracker.ui.components.MemberChip
import com.sdumsd11.easytracker.ui.theme.EasyTrackerTheme

/**
 * Owner-gated rename + invite entry. The name field edits the real group name
 * ([groupName] from GroupDetailViewModel.group, never the group id); save goes
 * through GroupRepository.updateGroup via [onSave]. Non-owners see a locked note.
 */
@Composable
fun EditGroupScreen(
    modifier: Modifier = Modifier,
    groupId: String,
    isOwner: Boolean,
    onSetupDebt: (String) -> Unit,
    onBack: () -> Unit,
    members: List<MemberUi> = emptyList(),
    groupName: String? = null,
    saveResult: RequestStatus<Group>? = null,
    onSave: (String) -> Unit = {},
    onSaved: () -> Unit = onBack,
    inviteResult: RequestStatus<Unit>? = null,
    onInvite: (String) -> Unit = {},
    onDismissInviteResult: () -> Unit = {},
) {
    var name by rememberSaveable(groupName) { mutableStateOf(groupName.orEmpty()) }
    var inviteEmail by rememberSaveable { mutableStateOf("") }
    val saving = saveResult is RequestStatus.Pending
    val error = (saveResult as? RequestStatus.Failed)?.message
    val nameValid = name.isNotBlank()
    LaunchedEffect(saveResult) { if (saveResult is RequestStatus.Success) onSaved() }
    // Success clears the field; the refreshed members flow is the durable
    // confirmation. Clear the stale Success so it never replays; errors stay
    // visible until dismissed (mirrors GroupDetailScreen removeResult).
    if (inviteResult is RequestStatus.Success) {
        LaunchedEffect(inviteResult) {
            inviteEmail = ""
            onDismissInviteResult()
        }
    }
    val inviting = inviteResult is RequestStatus.Pending
    ScreenScaffold(title = groupName?.ifBlank { null } ?: "Edit group", modifier = modifier, onBack = onBack) {
        if (!isOwner) {
            EasyCard(modifier = Modifier.fillMaxWidth()) {
                Text("Only the group owner can edit this group.")
            }
        } else {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Group name") },
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            NavButton(
                if (saving) "Saving…" else "Save name",
                enabled = nameValid && !saving && name.trim() != groupName,
                onClick = { onSave(name.trim()) },
            )
            EasyCard(modifier = Modifier.fillMaxWidth()) {
                Text("Members", style = MaterialTheme.typography.titleMedium)
                members.forEach { MemberChip(name = it.name, modifier = Modifier.fillMaxWidth()) }
                OutlinedTextField(
                    value = inviteEmail,
                    onValueChange = { inviteEmail = it },
                    label = { Text("Invite by email") },
                    singleLine = true,
                    enabled = !inviting,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Repo message verbatim; dismiss clears the VM state.
                if (inviteResult is RequestStatus.Failed) {
                    Text(
                        inviteResult.message ?: "Couldn't invite that member.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(
                        onClick = onDismissInviteResult,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Dismiss") }
                }
                NavButton(
                    label = if (inviting) "Inviting…" else "Invite member",
                    enabled = inviteEmail.contains("@") && !inviting,
                    onClick = { onInvite(inviteEmail.trim()) },
                )
                SecondaryButton(label = "Set up a debt", onClick = { onSetupDebt(groupId) })
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EditGroupPreview() {
    EasyTrackerTheme {
        EditGroupScreen(
            groupId = "g1", groupName = "Flatmates", isOwner = true, onSetupDebt = {}, onBack = {},
            members = listOf(MemberUi("you", "You"), MemberUi("anna", "Anna")),
        )
    }
}
