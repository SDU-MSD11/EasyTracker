package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.annotation.DrawableRes
import com.sdumsd11.easytracker.R
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
    /** Null hides the row (pre-33 or already granted); false shows the system-settings CTA. */
    systemNotificationsOn: Boolean? = null,
    onOpenSystemSettings: () -> Unit = {},
) {
    ScreenScaffold(title = "Settings", modifier = modifier) {
        SectionCard(title = "Profile", modifier = Modifier.fillMaxWidth()) {
            SecondaryButton("Edit profile", icon = R.drawable.edit, onClick = onEditProfile)
            SecondaryButton("Sign out", icon = R.drawable.logout, onClick = onSignOut)
        }
        SectionCard(title = "Notifications", icon = R.drawable.notifications, modifier = Modifier.fillMaxWidth()) {
            if (systemNotificationsOn == false) {
                SecondaryButton(
                    "Turn on in system settings",
                    icon = R.drawable.notifications_active,
                    onClick = onOpenSystemSettings,
                )
            }
            SwitchRow(
                "New debts",
                "Updates when a new expense lands in your groups",
                newExpense,
                onNewExpenseChange,
            )
            SwitchRow(
                "Payment updates",
                "Updates when settlements change balances",
                settlements,
                onSettlementsChange,
            )
            SwitchRow(
                "Reminders",
                "Nudges about outstanding debts",
                reminders,
                onRemindersChange,
                icon = R.drawable.notifications_active,
            )
        }
        SectionCard(title = "Appearance", modifier = Modifier.fillMaxWidth()) {
            SwitchRow("Dark theme", "Darker colors across the app", darkTheme, onDarkThemeChange)
        }
    }
}

/** Accessible switch row: whole 48dp row toggles, label carries the meaning. */
@Composable
private fun SwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    @DrawableRes icon: Int? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(24.dp).padding(end = 8.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    EasyTrackerTheme { SettingsScreen(onEditProfile = {}) }
}
