package com.sdumsd11.easytracker.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.sdumsd11.easytracker.R

/** Status glyph: check_circle (success) or cancel (failure) drawable, 72dp. */
@Composable
fun StatusIcon(success: Boolean, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(if (success) R.drawable.check_circle else R.drawable.cancel),
        contentDescription = if (success) "Payment successful" else "Payment failed",
        tint = if (success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
        modifier = modifier.size(72.dp),
    )
}
