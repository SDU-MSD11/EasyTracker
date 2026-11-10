package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.sdumsd11.easytracker.ui.components.EasyCard
import com.sdumsd11.easytracker.ui.components.MemberChip
import com.sdumsd11.easytracker.ui.theme.EasyTrackerTheme

/** Owner-gated: invite/kick + debt setup. Non-owners see a locked note. */
// TODO(api-integration): VM redirects non-owners to GroupDetail (isOwner param is a stub).
@Composable
fun EditGroupScreen(
    modifier: Modifier = Modifier,
    groupId: String,
    isOwner: Boolean,
    onSetupDebt: (String) -> Unit,
    onBack: () -> Unit,
    members: List<MemberUi> = listOf(
        MemberUi("you", "You"),
        MemberUi("anna", "Anna"),
    ),
) {
    var inviteEmail by remember { mutableStateOf("") }
    ScreenScaffold(title = "Edit group", modifier = modifier, onBack = onBack) {
        if (!isOwner) {
            EasyCard(modifier = Modifier.fillMaxWidth()) {
                Text("Only the group owner can edit this group.")
            }
        } else {
            OutlinedTextField(
                value = groupId,
                onValueChange = {},
                label = { Text("Group name") },
                singleLine = true,
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            )
            EasyCard(modifier = Modifier.fillMaxWidth()) {
                Text("Members", style = MaterialTheme.typography.titleMedium)
                members.forEach { MemberChip(name = it.name, modifier = Modifier.fillMaxWidth()) }
                OutlinedTextField(
                    value = inviteEmail,
                    onValueChange = { inviteEmail = it },
                    label = { Text("Invite by email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                NavButton(label = "Invite member", onClick = { inviteEmail = "" }, enabled = inviteEmail.contains("@"))
                SecondaryButton(label = "Set up a debt", onClick = { onSetupDebt(groupId) })
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EditGroupPreview() {
    EasyTrackerTheme {
        EditGroupScreen(groupId = "flatmates", isOwner = true, onSetupDebt = {}, onBack = {})
    }
}
