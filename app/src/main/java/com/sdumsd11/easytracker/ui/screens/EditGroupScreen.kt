package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Owner-gated: invite/kick + debt setup. Non-owners see a locked note. */
// TODO(api-integration): VM redirects non-owners to GroupDetail (isOwner param is a stub).
@Composable
fun EditGroupScreen(
    modifier: Modifier = Modifier,
    groupId: String,
    isOwner: Boolean,
    onSetupDebt: (String) -> Unit,
    onBack: () -> Unit,
) {
    ScreenScaffold(title = "Edit group", modifier = modifier) {
        if (!isOwner) {
            Text("Only the group owner can edit this group.")
        } else {
            NavButton(label = "Invite member (stub)", onClick = {})
            NavButton(label = "Kick member (stub)", onClick = {})
            NavButton(label = "Set up a debt", onClick = { onSetupDebt(groupId) })
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}
