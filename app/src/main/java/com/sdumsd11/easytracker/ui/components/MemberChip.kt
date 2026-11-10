package com.sdumsd11.easytracker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sdumsd11.easytracker.domain.model.InviteStatus
import com.sdumsd11.easytracker.ui.theme.Danger100
import com.sdumsd11.easytracker.ui.theme.Danger800
import com.sdumsd11.easytracker.ui.theme.Success100
import com.sdumsd11.easytracker.ui.theme.Success800
import com.sdumsd11.easytracker.ui.theme.Warning100
import com.sdumsd11.easytracker.ui.theme.Warning800

/** Circular initial avatar (no icons dep in this slice). */
@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Int = 40,
    description: String? = null,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.size(size.dp)
            .semantics { if (description != null) contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name.trim().take(1).uppercase().ifEmpty { "?" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** Member row chip: avatar + name + optional invite-status badge. */
@Composable
fun MemberChip(name: String, modifier: Modifier = Modifier, status: InviteStatus? = null) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Avatar(name = name, size = 32, description = "Avatar for $name")
        Spacer(Modifier.width(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (status != null) StatusBadge(status)
    }
}

@Composable
private fun StatusBadge(status: InviteStatus) {
    val (bg, fg) = when (status) {
        InviteStatus.ACCEPTED -> Success100 to Success800
        InviteStatus.PENDING -> Warning100 to Warning800
        InviteStatus.DECLINED -> Danger100 to Danger800
    }
    Surface(shape = CircleShape, color = bg) {
        Text(
            text = status.name.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
