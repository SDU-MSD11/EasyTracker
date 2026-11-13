package com.sdumsd11.easytracker.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle

/** Euro amount from minor units. Green when owed to you, red when you owe, neutral otherwise. */
@Composable
fun AmountText(amountCents: Long, modifier: Modifier = Modifier, style: TextStyle? = null) {
    val scheme = MaterialTheme.colorScheme
    val color = when {
        amountCents < 0 -> scheme.error
        amountCents > 0 -> scheme.tertiary
        else -> scheme.onSurface
    }
    val euros = kotlin.math.abs(amountCents) / 100.0
    val sign = if (amountCents < 0) "−" else ""
    Text(
        text = "$sign€${"%.2f".format(euros)}",
        color = color,
        style = style ?: MaterialTheme.typography.titleMedium,
        modifier = modifier,
    )
}
