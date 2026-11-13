package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sdumsd11.easytracker.ui.components.Avatar
import com.sdumsd11.easytracker.ui.components.SectionCard
import com.sdumsd11.easytracker.ui.theme.EasyTrackerTheme

/** Account profile. Display name + stats come from the authenticated session/VMs;
 * defaults are the honest empty state (never mock debt). */
@Composable
fun AccountScreen(
    modifier: Modifier = Modifier,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onSignOut: () -> Unit,
    displayName: String = "",
    stats: List<String> = emptyList(),
) {
    ScreenScaffold(title = "Account", modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Avatar(name = displayName, size = 72, description = "Profile avatar for $displayName")
            Text(
                text = displayName,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            stats.ifEmpty { listOf("No activity yet") }.forEach { stat ->
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stat,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    )
                }
            }
        }
        SectionCard(title = "Payments", modifier = Modifier.fillMaxWidth()) {
            Text(
                "See every payment you made or received.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SecondaryButton(label = "Payment history", onClick = onHistory)
        }
        SectionCard(title = "Preferences", modifier = Modifier.fillMaxWidth()) {
            Text(
                "Profile, notifications and theme.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SecondaryButton(label = "Settings", onClick = onSettings)
        }
        SectionCard(title = "Session", modifier = Modifier.fillMaxWidth()) {
            SecondaryButton(label = "Sign out", onClick = onSignOut)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountPreview() {
    EasyTrackerTheme {
        AccountScreen(
            onHistory = {},
            onSettings = {},
            onSignOut = {},
            displayName = "Crab",
            stats = listOf("1 group", "No activity yet"),
        )
    }
}
