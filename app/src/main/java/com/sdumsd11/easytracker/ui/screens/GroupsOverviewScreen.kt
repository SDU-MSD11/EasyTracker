package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun GroupsOverviewScreen(
    modifier: Modifier = Modifier,
    onGroupClick: (String) -> Unit,
    onCreateGroup: () -> Unit,
) {
    // Stub data; real lists come from domain layer later.
    val joined = listOf("flatmates" to "Flatmates", "trip" to "Summer trip")
    val free = listOf("board-games" to "Board games")
    var tab by remember { mutableIntStateOf(0) }
    val shown = if (tab == 0) joined else free
    ScreenScaffold(title = "Groups", modifier = modifier) {
        PrimaryTabRow(selectedTabIndex = tab, modifier = Modifier.fillMaxWidth()) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Joined") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Free") })
        }
        shown.forEach { (id, name) -> NavButton(label = name, onClick = { onGroupClick(id) }) }
        if (shown.isEmpty()) Text("Nothing here yet.")
        NavButton(label = "+ Create group", onClick = onCreateGroup)
    }
}
