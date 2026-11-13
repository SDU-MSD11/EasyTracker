package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sdumsd11.easytracker.ui.components.AmountText
import com.sdumsd11.easytracker.ui.components.Avatar
import com.sdumsd11.easytracker.ui.components.EasyCard
import com.sdumsd11.easytracker.ui.components.EmptyState
import com.sdumsd11.easytracker.ui.components.ErrorState
import com.sdumsd11.easytracker.ui.components.LoadingState
import com.sdumsd11.easytracker.ui.theme.EasyTrackerTheme

/** Card-level summary; balances in minor units (positive = owed to you). */
data class GroupSummary(val id: String, val name: String, val balanceCents: Long)

/**
 * Groups overview. Data wiring lives with the VM owner — this layer renders
 * loading / error / empty states and never ships hardcoded mock rows.
 *
 * Tabs are hoisted ([selectedTab] null = managed internally for previews and the
 * no-VM graph). Tab 1 shows [settledGroups] once the domain lists land; until
 * then it derives from [groups] — the split always reflects props, never stubs.
 * [hasLoaded] is false until the first successful load: a cold cache renders
 * loading, never a false "no groups" empty.
 */
@Composable
fun GroupsOverviewScreen(
    modifier: Modifier = Modifier,
    onGroupClick: (String) -> Unit,
    onCreateGroup: () -> Unit,
    groups: List<GroupSummary> = emptyList(),
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
    onAccountClick: (() -> Unit)? = null,
    selectedTab: Int? = null,
    onTabSelected: (Int) -> Unit = {},
    settledGroups: List<GroupSummary>? = null,
    onRefresh: () -> Unit = {},
    hasLoaded: Boolean = true,
) {
    var internalTab by remember { mutableIntStateOf(0) }
    val tab = selectedTab ?: internalTab
    fun select(index: Int) {
        internalTab = index
        onTabSelected(index)
    }
    val settled = settledGroups ?: groups.filter { it.balanceCents == 0L }
    val shown = if (tab == 0) groups else settled
    ScreenScaffold(
        title = "Groups",
        modifier = modifier,
        actions = {
            TextButton(
                onClick = onRefresh,
                enabled = !isLoading,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Refresh") }
            if (onAccountClick != null) {
                TextButton(
                    onClick = onAccountClick,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Avatar(name = "Guest", size = 32, description = "Open account")
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateGroup,
                modifier = Modifier.semantics { contentDescription = "Create group" },
                content = {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
                },
            )
        },
        scrollable = false,
    ) {
        PrimaryTabRow(selectedTabIndex = tab, modifier = Modifier.fillMaxWidth()) {
            Tab(selected = tab == 0, onClick = { select(0) }, text = { Text("Joined") })
            Tab(selected = tab == 1, onClick = { select(1) }, text = { Text("Settled up") })
        }
        when {
            isLoading -> LoadingState("Loading groups…")
            !hasLoaded && groups.isEmpty() && errorMessage == null -> LoadingState("Loading groups…")
            errorMessage != null -> ErrorState(errorMessage, onRetry = onRetry)
            shown.isEmpty() -> if (tab == 0) {
                EmptyState(
                    title = "No groups yet",
                    message = "Create your first group to start tracking shared expenses.",
                    actionLabel = "Create group",
                    onAction = onCreateGroup,
                )
            } else {
                EmptyState(
                    title = "Nothing settled up",
                    message = "Groups with no outstanding balance will show up here.",
                )
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(shown, key = { it.id }) { group ->
                    GroupCard(group.name, group.balanceCents, onOpen = { onGroupClick(group.id) })
                }
            }
        }
    }
}

@Composable
private fun GroupCard(name: String, balanceCents: Long, onOpen: () -> Unit) {
    val caption = when {
        balanceCents > 0 -> "You're owed"
        balanceCents < 0 -> "You owe"
        else -> "Settled up"
    }
    EasyCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(name = name, description = "Avatar for $name")
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AmountText(balanceCents)
        }
        FilledTonalButton(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("Open") }
    }
}

@Preview(showBackground = true)
@Composable
private fun GroupsOverviewPreview() {
    EasyTrackerTheme {
        GroupsOverviewScreen(
            onGroupClick = {},
            onCreateGroup = {},
            groups = listOf(
                GroupSummary("flatmates", "Flatmates", 1250),
                GroupSummary("trip", "Summer trip", -800),
                GroupSummary("board-games", "Board games", 0),
            ),
            settledGroups = listOf(GroupSummary("board-games", "Board games", 0)),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GroupsOverviewEmptyPreview() {
    EasyTrackerTheme { GroupsOverviewScreen(onGroupClick = {}, onCreateGroup = {}) }
}
