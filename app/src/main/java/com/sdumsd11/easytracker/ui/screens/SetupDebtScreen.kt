package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
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
import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.domain.model.Expense
import com.sdumsd11.easytracker.ui.components.RequestStatusBanner
import com.sdumsd11.easytracker.ui.theme.EasyTrackerTheme
import kotlin.math.roundToLong

/**
 * Set up debt. Persisting runs in the VM owner ([onSave] stores an expense split among
 * the group members); [saveResult] success fires [onDone] (debtId != null = edit existing).
 *
 * [participants] are real group members (id + display name) from GroupDetailViewModel;
 * only member ids are ever passed to [onSave] — never display names. Empty list means
 * the group hasn't loaded: Save stays disabled and an error shows instead of writing
 * an invalid payer id. Edit mode ([debtId] != null) prefills [initialAmountCents] /
 * [initialNote] when the owner passes them.
 *
 * Pending shows progress with the form disabled; failed shows the repo message with
 * Retry (re-submits the last values) and Dismiss ([onDismissError] clears the VM
 * result). Back stays available throughout — failure never traps the user.
 */
@Composable
fun SetupDebtScreen(
    modifier: Modifier = Modifier,
    groupId: String,
    debtId: String? = null,
    onDone: () -> Unit,
    saveResult: RequestStatus<Expense>? = null,
    onSave: (String, Long, String) -> Unit = { _, _, _ -> },
    groupName: String? = null,
    participants: List<MemberUi> = emptyList(),
    initialPayerId: String? = null,
    initialAmountCents: Long? = null,
    initialNote: String = "",
    onDismissError: () -> Unit = {},
) {
    var amount by remember(debtId) {
        mutableStateOf(initialAmountCents?.let { "%.2f".format(it / 100.0) } ?: "")
    }
    var note by remember(debtId) { mutableStateOf(initialNote) }
    var payerId by remember(participants, initialPayerId) {
        mutableStateOf(
            initialPayerId?.takeIf { id -> participants.any { it.id == id } }
                ?: participants.firstOrNull()?.id.orEmpty(),
        )
    }
    var lastSubmitted by remember { mutableStateOf<Triple<String, Long, String>?>(null) }
    val cents = amount.toDoubleOrNull()?.let { (it * 100).roundToLong() }
    val valid = cents != null && cents > 0L
    val isSaving = saveResult is RequestStatus.Pending
    val isFailed = saveResult is RequestStatus.Failed
    val byId = remember(participants) { participants.associateBy { it.id } }
    val payerValid = payerId.isNotBlank() && byId.containsKey(payerId)
    val membersMissing = participants.isEmpty()
    LaunchedEffect(saveResult) {
        if (saveResult is RequestStatus.Success) onDone()
    }
    ScreenScaffold(
        title = if (debtId == null) "Set up debt" else "Edit debt",
        modifier = modifier,
        onBack = onDone,
    ) {
        Text("Group: ${groupName ?: "Loading…"}")
        MemberDropdown(
            label = "Paid by",
            valueId = payerId,
            options = participants,
            enabled = !isSaving && !membersMissing,
            onSelect = { payerId = it },
        )
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount") },
            singleLine = true,
            enabled = !isSaving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note") },
            singleLine = true,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
        if (membersMissing) {
            Text(
                "Group members unavailable — cannot save a debt yet.",
                color = MaterialTheme.colorScheme.error,
            )
        }
        RequestStatusBanner(
            status = saveResult,
            pendingLabel = "Saving debt…",
            onRetry = if (isFailed) {
                { lastSubmitted?.let { onSave(it.first, it.second, it.third) } }
            } else {
                null
            },
            onDismiss = if (isFailed) onDismissError else null,
        )
        NavButton(
            if (isSaving) "Saving…" else "Save debt",
            enabled = valid && payerValid && !isSaving,
            onClick = {
                lastSubmitted = Triple(payerId, cents!!, note)
                onSave(payerId, cents, note)
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SetupDebtPreview() {
    EasyTrackerTheme {
        SetupDebtScreen(
            groupId = "g1", groupName = "Flatmates", onDone = {},
            participants = listOf(MemberUi("you", "You"), MemberUi("anna", "Anna")),
            initialPayerId = "you",
        )
    }
}
