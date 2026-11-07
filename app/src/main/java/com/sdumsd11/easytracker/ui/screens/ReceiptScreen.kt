package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Receipt (PDF export optional/stub) reachable from PaymentSuccess. */
@Composable
fun ReceiptScreen(modifier: Modifier = Modifier, groupId: String, onDone: () -> Unit) {
    ScreenScaffold(title = "Receipt", modifier = modifier) {
        Text("Payment receipt for $groupId (stub).")
        NavButton(label = "Export PDF (stub)", onClick = {})
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}
