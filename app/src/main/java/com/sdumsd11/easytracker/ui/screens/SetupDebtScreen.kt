package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.sdumsd11.easytracker.ui.theme.EasyTrackerTheme

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
    ScreenScaffold(
        title = if (debtId == null) "Set up debt" else "Edit debt",
        modifier = modifier,
        onBack = onDone,
    ) {
        Text("Group: $groupId")
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        NavButton("Save debt", enabled = valid, onClick = onDone)
    }
}

@Preview(showBackground = true)
@Composable
private fun SetupDebtPreview() {
    EasyTrackerTheme { SetupDebtScreen(groupId = "flatmates", onDone = {}) }
}
