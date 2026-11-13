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
import com.sdumsd11.easytracker.ui.components.RequestStatusBanner
import com.sdumsd11.easytracker.ui.theme.EasyTrackerTheme
import com.sdumsd11.easytracker.ui.viewmodels.PaymentResult
import kotlin.math.roundToLong

/**
 * Payment validation: the recipient must be a loaded member and differ from the fixed
 * session payer (the VM/backend always records the session user as payer — recording
 * someone else's payment as your own would silently misattribute it, so the payer
 * picker is display-only and the recipient must not be you).
 */
internal fun paymentInputError(
    recipientValid: Boolean,
    amount: Double?,
    payerId: String?,
    recipientId: String,
): String? = when {
    !recipientValid -> "Group members unavailable — cannot record a payment yet."
    amount == null || amount <= 0 -> "Enter an amount greater than 0."
    payerId != null && payerId == recipientId -> "Payer and recipient must differ."
    else -> null
}

/**
 * Make payment. The payer is fixed to the session user (display-only): the VM owner
 * ([onPay]) records the repayment via ExpenseRepository.recordSettlement and the backend
 * derives the payer from the JWT, so a selectable payer would lie — [payerId] can
 * never pass validation while the VM ignores it. [payResult] success carries the
 * [PaymentResult] the graph passes to PaymentSuccess (mirrors the LoginScreen
 * result convention).
 *
 * [participants] are real group members (id + display name) from GroupDetailViewModel;
 * only member ids are ever passed to [onPay] — never display names. Empty list means
 * the group hasn't loaded: Pay stays disabled and an error shows instead of writing
 * an invalid recipient id.
 */
@Composable
fun MakePaymentScreen(
    modifier: Modifier = Modifier,
    groupId: String,
    onResult: (PaymentResult, Boolean) -> Unit,
    onBack: () -> Unit,
    payResult: RequestStatus<PaymentResult>? = null,
    onPay: (String, Long) -> Unit = { _, _ -> },
    participants: List<MemberUi> = emptyList(),
    groupName: String? = null,
    payer: MemberUi? = null,
) {
    var amount by remember { mutableStateOf("") }
    var recipientId by remember(participants, payer) {
        mutableStateOf(
            (participants.firstOrNull { it.id != payer?.id } ?: participants.firstOrNull())?.id.orEmpty(),
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    val processing = payResult is RequestStatus.Pending
    val byId = remember(participants) { participants.associateBy { it.id } }
    // Guard: never emit a display name or unknown id as the recipient.
    val recipientValid = recipientId.isNotBlank() && byId.containsKey(recipientId)
    val membersMissing = participants.isEmpty()

    LaunchedEffect(payResult) {
        when (payResult) {
            is RequestStatus.Success -> onResult(payResult.data, true)
            is RequestStatus.Failed -> onResult(PaymentResult(groupId, 0L, groupId), false)
            else -> Unit
        }
    }
    ScreenScaffold(title = "Make payment", modifier = modifier, onBack = onBack) {
        Text("Group: ${groupName ?: "Loading…"}", style = MaterialTheme.typography.bodyMedium)
        Text("Paying as ${payer?.name ?: "…"}", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount") },
            singleLine = true,
            enabled = !processing,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        MemberDropdown(
            label = "Recipient",
            valueId = recipientId,
            options = participants,
            enabled = !processing && !membersMissing,
            onSelect = { recipientId = it },
        )
        if (membersMissing) {
            Text(
                "Group members unavailable — cannot record a payment yet.",
                color = MaterialTheme.colorScheme.error,
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        // Failure routes to the dedicated PaymentFailed screen via onResult (Retry +
        // Back to group there); the banner only renders the pending pass so the failed
        // state is never flashed-then-yanked here.
        RequestStatusBanner(
            status = payResult?.takeUnless { it is RequestStatus.Failed },
            pendingLabel = "Processing payment…",
        )
        NavButton(label = if (processing) "Processing…" else "Pay", enabled = !membersMissing && !processing, onClick = {
            val value = amount.toDoubleOrNull()
            error = paymentInputError(recipientValid, value, payer?.id, recipientId)
            if (error == null) onPay(recipientId, (value!! * 100).roundToLong())
        })
        // Cancel stays enabled while processing — Back (top bar) too — so a slow
        // gateway never traps the user on this screen.
        SecondaryButton(label = "Cancel", onClick = onBack)
    }
}

@Preview(showBackground = true)
@Composable
private fun MakePaymentPreview() {
    EasyTrackerTheme {
        MakePaymentScreen(
            groupId = "g1",
            groupName = "Flatmates",
            onResult = { _, _ -> },
            onBack = {},
            participants = listOf(
                MemberUi("you", "You"),
                MemberUi("anna", "Anna"),
                MemberUi("ben", "Ben"),
            ),
            payer = MemberUi("you", "You"),
        )
    }
}
