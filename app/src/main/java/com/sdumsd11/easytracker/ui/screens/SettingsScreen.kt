package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sdumsd11.easytracker.ui.components.SectionCard
import com.sdumsd11.easytracker.ui.theme.EasyTrackerTheme

/**
 * Settings. Switch state is hoisted — the settings-VM owner passes values in;
 * defaults keep previews and the no-VM graph working.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit = {},
    newExpense: Boolean = true,
    onNewExpenseChange: (Boolean) -> Unit = {},
    reminders: Boolean = false,
    onRemindersChange: (Boolean) -> Unit = {},
    settlements: Boolean = false,
    onSettlementsChange: (Boolean) -> Unit = {},
    darkTheme: Boolean = false,
    onDarkThemeChange: (Boolean) -> Unit = {},
) {
    ScreenScaffold(title = "Settings", modifier = modifier) {
        SectionCard(title = "Profile", modifier = Modifier.fillMaxWidth()) {
            SecondaryButton("Edit profile", onClick = onEditProfile)
            SecondaryButton("Sign out", onClick = onSignOut)
        }
        SectionCard(title = "Notifications", modifier = Modifier.fillMaxWidth()) {
            SwitchRow("New expenses", newExpense, onNewExpenseChange)
            SwitchRow("Reminders", reminders, onRemindersChange)
            SwitchRow("Settlement updates", settlements, onSettlementsChange)
        }
        SectionCard(title = "Appearance", modifier = Modifier.fillMaxWidth()) {
            SwitchRow("Dark theme", darkTheme, onDarkThemeChange)
        }
    }
}

/** Accessible switch row: whole 48dp row toggles, label carries the meaning. */
@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
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
