package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun PaymentHistoryScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val stub = listOf("€12.50 to Flatmates — success", "€8.00 to Summer trip — failed")
    ScreenScaffold(title = "Payment history", modifier = modifier) {
        if (stub.isEmpty()) Text("No payments yet.") else stub.forEach { Text(it) }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}
