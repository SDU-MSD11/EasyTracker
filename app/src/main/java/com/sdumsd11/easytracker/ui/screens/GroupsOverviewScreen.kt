package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sdumsd11.easytracker.ui.components.AmountText
import com.sdumsd11.easytracker.ui.components.Avatar
import com.sdumsd11.easytracker.ui.components.EasyCard
import com.sdumsd11.easytracker.ui.components.EmptyState
import com.sdumsd11.easytracker.ui.theme.EasyTrackerTheme

/** Card-level summary; balances in minor units (positive = owed to you). */
data class GroupSummary(val id: String, val name: String, val balanceCents: Long)

@Composable
fun GroupsOverviewScreen(
    modifier: Modifier = Modifier,
    onGroupClick: (String) -> Unit,
    onCreateGroup: () -> Unit,
    groups: List<GroupSummary> = listOf(
        GroupSummary("flatmates", "Flatmates", 1250),
        GroupSummary("trip", "Summer trip", -800),
        GroupSummary("board-games", "Board games", 0),
    ),
    onAccountClick: (() -> Unit)? = null,
) {
    var tab by remember { mutableIntStateOf(0) }
    // Stub split until domain lists land; tab 1 shows the free/discoverable slice.
    val shown = if (tab == 0) groups else groups.filter { it.balanceCents == 0L }
    ScreenScaffold(
        title = "Groups",
        modifier = modifier,
        actions = {
            if (onAccountClick != null) {
                TextButton(onClick = onAccountClick) {
                    Avatar(name = "Guest", size = 32, description = "Open account")
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateGroup, content = {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            })
        },
        scrollable = false,
    ) {
        PrimaryTabRow(selectedTabIndex = tab, modifier = Modifier.fillMaxWidth()) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Joined") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Free") })
        }
        if (shown.isEmpty()) {
            EmptyState("Nothing here yet — create your first group.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(shown, key = { it.id }) { group ->
                    EasyCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(name = group.name, description = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        AmountText(group.balanceCents)
                        FilledTonalButton(
                            onClick = { onGroupClick(group.id) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Open") }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GroupsOverviewPreview() {
    EasyTrackerTheme { GroupsOverviewScreen(onGroupClick = {}, onCreateGroup = {}) }
}
