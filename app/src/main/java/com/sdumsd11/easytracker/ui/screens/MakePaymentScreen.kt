package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.sdumsd11.easytracker.ui.components.LoadingState
import com.sdumsd11.easytracker.ui.theme.EasyTrackerTheme
import kotlinx.coroutines.delay

private enum class PayState { Idle, Processing }

/** ProcessPayment state machine: Idle -> Processing -> Success/Failed (stub gateway). */
// TODO(api-integration): record settlement via repo and observe its RequestStatus instead of the
// stub delay/gateway rule below.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MakePaymentScreen(
    modifier: Modifier = Modifier,
    groupId: String,
    onResult: (String, Boolean) -> Unit,
    onBack: () -> Unit,
    participants: List<String> = listOf("You", "Anna", "Ben"),
) {
    var amount by remember { mutableStateOf("") }
    var payer by remember { mutableStateOf(participants.first()) }
    var recipient by remember(participants) { mutableStateOf(participants.getOrElse(1) { participants.first() }) }
    var state by remember { mutableStateOf(PayState.Idle) }
    var error by remember { mutableStateOf<String?>(null) }

    if (state == PayState.Processing) {
        LaunchedEffect(amount) {
            delay(1200)
            // Stub gateway rule: amounts <= 1000 succeed. Replace with domain result later.
            onResult(groupId, amount.toDoubleOrNull()?.let { it <= 1000 } == true)
            state = PayState.Idle
        }
    }
    ScreenScaffold(title = "Make payment", modifier = modifier, onBack = onBack) {
        Text("Group: $groupId", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount") },
            singleLine = true,
            enabled = state == PayState.Idle,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        PersonDropdown(
            label = "Payer",
            value = payer,
            options = participants,
            enabled = state == PayState.Idle,
            onSelect = { payer = it },
        )
        PersonDropdown(
            label = "Recipient",
            value = recipient,
            options = participants,
            enabled = state == PayState.Idle,
            onSelect = { recipient = it },
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state == PayState.Processing) {
            LoadingState("Processing payment")
        } else {
            NavButton(label = "Pay", onClick = {
                val value = amount.toDoubleOrNull()
                error = when {
                    value == null || value <= 0 -> "Enter an amount greater than 0."
                    payer == recipient -> "Payer and recipient must differ."
                    else -> null
                }
                if (error == null) state = PayState.Processing
            })
            SecondaryButton(label = "Cancel", onClick = onBack)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonDropdown(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(option); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MakePaymentPreview() {
    EasyTrackerTheme { MakePaymentScreen(groupId = "flatmates", onResult = { _, _ -> }, onBack = {}) }
}
