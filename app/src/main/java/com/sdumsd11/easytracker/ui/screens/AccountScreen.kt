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
import com.sdumsd11.easytracker.ui.components.EasyCard
import com.sdumsd11.easytracker.ui.theme.EasyTrackerTheme

/** Account profile. */
@Composable
fun AccountScreen(
    modifier: Modifier = Modifier,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    displayName: String = "Guest",
    stats: List<String> = listOf("3 groups", "€45 owed", "12 paid"),
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
            stats.forEach { stat ->
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
        EasyCard(modifier = Modifier.fillMaxWidth()) {
            Text("Payments", style = MaterialTheme.typography.titleMedium)
            Text(
                "See every payment you made or received.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SecondaryButton(label = "Payment history", onClick = onHistory)
        }
        EasyCard(modifier = Modifier.fillMaxWidth()) {
            Text("Preferences", style = MaterialTheme.typography.titleMedium)
            Text(
                "Profile, notifications and theme.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SecondaryButton(label = "Settings", onClick = onSettings)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountPreview() {
    EasyTrackerTheme { AccountScreen(onHistory = {}, onSettings = {}) }
}
