package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sdumsd11.easytracker.ui.components.EasyCard
import com.sdumsd11.easytracker.ui.theme.EasyTrackerTheme

@Composable
fun SettingsScreen(modifier: Modifier = Modifier, onEditProfile: () -> Unit) {
    var newExpense by remember { mutableStateOf(true) }
    var reminders by remember { mutableStateOf(false) }
    var settlements by remember { mutableStateOf(false) }
    var darkTheme by remember { mutableStateOf(false) }
    ScreenScaffold(title = "Settings", modifier = modifier) {
        EasyCard(modifier = Modifier.fillMaxWidth()) {
            Text("Profile", style = MaterialTheme.typography.titleMedium)
            SecondaryButton("Edit profile", onClick = onEditProfile)
        }
        EasyCard(modifier = Modifier.fillMaxWidth()) {
            Text("Notifications", style = MaterialTheme.typography.titleMedium)
            SwitchRow("New expenses", newExpense) { newExpense = it }
            SwitchRow("Reminders", reminders) { reminders = it }
            SwitchRow("Settlement updates", settlements) { settlements = it }
        }
        EasyCard(modifier = Modifier.fillMaxWidth()) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            SwitchRow("Dark theme (stub)", darkTheme) { darkTheme = it }
        }
    }
}

/** Accessible switch row: whole row toggles, label carries the meaning. */
@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    EasyTrackerTheme { SettingsScreen(onEditProfile = {}) }
}
