package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun SetupDebtScreen(
    modifier: Modifier = Modifier,
    groupId: String,
    debtId: String? = null,
    onDone: () -> Unit,
) {
    // TODO(api-integration): persist via ExpenseRepository (debtId != null = edit existing).
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val valid = amount.toDoubleOrNull()?.let { it > 0 } == true
    ScreenScaffold(title = "Set up debt", modifier = modifier) {
        Text("Group: $groupId")
        TextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        NavButton("Save debt", enabled = valid, onClick = onDone)
    }
}
