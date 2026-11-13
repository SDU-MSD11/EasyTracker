package com.sdumsd11.easytracker.ui.components

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.ui.StatusBus
import com.sdumsd11.easytracker.ui.StatusKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Inline pending/failed state for submit screens. Pending shows an accessible
 * progress bar (submit stays visible but disabled by the caller); failed shows
 * the repo message in [colorScheme.error] with Retry + Dismiss — Back/dismiss
 * never traps the user. Success needs no banner here: success navigates or
 * posts a Snackbar via [StatusBus].
 */
@Composable
fun RequestStatusBanner(
    status: RequestStatus<*>?,
    pendingLabel: String,
    modifier: Modifier = Modifier,
    successMessage: String? = null,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    when {
        status is RequestStatus.Pending -> {
            Column(
                modifier = modifier.semantics { contentDescription = pendingLabel },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = pendingLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        status is RequestStatus.Failed -> {
            val message = status.message ?: "Something went wrong. Nothing was lost."
            Column(
                modifier = modifier.semantics { contentDescription = "Request failed: $message" },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                if (onRetry != null) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Retry") }
                }
                if (onDismiss != null) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Dismiss") }
                }
            }
        }
        status is RequestStatus.Success && successMessage != null -> {
            Text(
                text = successMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier.semantics { contentDescription = "Request succeeded: $successMessage" },
            )
        }
    }
}

/**
 * Global listener: collects [StatusBus] events and shows a Snackbar for each.
 * [StatusKind.BackgroundSuccess] additionally fires haptic feedback plus a
 * short platform beep — both permission-free, and failures are swallowed so a
 * missing beeper can never crash the host.
 */
@Composable
fun BackgroundSuccessHost(bus: StatusBus, hostState: SnackbarHostState) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(bus, hostState) {
        bus.events.collect { event ->
            if (event.kind == StatusKind.BackgroundSuccess) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                beep()
            }
            hostState.showSnackbar(event.message)
        }
    }
}

private suspend fun beep() = withContext(Dispatchers.IO) {
    runCatching {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60).also { tone ->
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            Thread.sleep(250)
            tone.release()
        }
    }
}
