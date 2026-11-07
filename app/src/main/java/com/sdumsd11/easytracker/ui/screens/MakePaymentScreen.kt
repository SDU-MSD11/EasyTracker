package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay

private enum class PayState { Idle, Processing }

/** ProcessPayment state machine: Idle -> Processing -> Success/Failed (stub gateway). */
// TODO(api-integration): record settlement via repo and observe its RequestStatus instead of the
// stub delay/gateway rule below.
@Composable
fun MakePaymentScreen(
    modifier: Modifier = Modifier,
    groupId: String,
    onResult: (String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var amount by remember { mutableStateOf("") }
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
    ScreenScaffold(title = "Make payment", modifier = modifier) {
        Text("Group: $groupId")
        TextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount") },
            singleLine = true,
            enabled = state == PayState.Idle,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state == PayState.Processing) {
            CircularProgressIndicator(
                modifier = Modifier.semantics { contentDescription = "Processing payment" },
            )
        } else {
            NavButton(label = "Pay", onClick = {
                val value = amount.toDoubleOrNull()
                if (value == null || value <= 0) error = "Enter an amount greater than 0."
                else {
                    error = null
                    state = PayState.Processing
                }
            })
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}
