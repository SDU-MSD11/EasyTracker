package com.sdumsd11.easytracker.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sdumsd11.easytracker.R
import com.sdumsd11.easytracker.ui.theme.EasyTrackerTheme

/**
 * One-shot runtime-permission prompt (Android 13+). Rendered inline at the top of
 * Groups on first paint after login — a dismissible card, never a dialog.
 * [promptSeen] is DataStore-backed (never nags); denial marks it seen and routes
 * to the app Settings screen, where the system-toggle row takes over.
 */
@Composable
fun NotificationPermissionPrompt(
    promptSeen: Boolean,
    onPromptSeen: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || promptSeen) return
    if (
        ContextCompat.checkSelfPermission(
            LocalContext.current,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    ) return
    var visible by rememberSaveable { mutableStateOf(true) }
    if (!visible) return
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onPromptSeen()
        visible = false
        if (!granted) onOpenSettings()
    }
    NotificationPermissionCard(
        onAllow = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
        onDismiss = {
            onPromptSeen()
            visible = false
        },
        modifier = modifier,
    )
}

/** Stateless rationale card: icon + heading + dismiss, 48dp actions. */
@Composable
fun NotificationPermissionCard(
    onAllow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EasyCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.notifications),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = "Stay in the loop",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    painter = painterResource(R.drawable.cancel),
                    contentDescription = "Dismiss",
                )
            }
        }
        Text(
            text = "Allow notifications for updates about debts and payments.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = onAllow,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text("Allow") }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Not now") }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NotificationPermissionCardPreview() {
    EasyTrackerTheme {
        NotificationPermissionCard(onAllow = {}, onDismiss = {})
    }
}
