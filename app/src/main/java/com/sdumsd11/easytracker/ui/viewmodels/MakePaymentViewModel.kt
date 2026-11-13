package com.sdumsd11.easytracker.ui.viewmodels

import androidx.lifecycle.viewModelScope
import com.sdumsd11.easytracker.core.BaseViewModel
import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.data.ExpenseRepository
import com.sdumsd11.easytracker.data.prefs.UserPreferences
import com.sdumsd11.easytracker.di.IoDispatcher
import com.sdumsd11.easytracker.domain.model.Settlement
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Real payment props the graph passes to PaymentSuccess/Receipt (never preview
 * defaults): the saved settlement's id + amount plus the originating group.
 */
data class PaymentResult(val txId: String, val amountCents: Long, val groupId: String)

/**
 * Records a repayment as a balancing expense; success data is the [PaymentResult]
 * the graph passes to PaymentSuccess. Null while idle — no spinner on first paint.
 */
// TODO(api-integration): POST /api/groups/:id/payments {toId, amountCents} -> {txId} when
// online; recordSettlement stays the offline path (balances update through the same pure path).
@HiltViewModel
class MakePaymentViewModel @Inject constructor(
    private val expenses: ExpenseRepository,
    private val prefs: UserPreferences,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : BaseViewModel() {

    private val _payResult = MutableStateFlow<RequestStatus<PaymentResult>?>(null)
    val payResult: StateFlow<RequestStatus<PaymentResult>?> = _payResult.asStateFlow()

    fun pay(groupId: String, toId: String, amountCents: Long, fromId: String? = null) =
        viewModelScope.launch(io) {
            _payResult.value = RequestStatus.Pending
            _payResult.value = runRequest {
                require(amountCents > 0L) { "Enter an amount greater than 0." }
                require(toId.isNotBlank()) { "Select a recipient." }
                val payer = fromId ?: prefs.profile.first()?.id
                    ?: throw IllegalStateException("Not logged in.")
                // Payer is the session user (screen shows it display-only): self-payment
                // is rejected here too, so a stale/mismatched caller can never record me→me.
                require(payer != toId) { "Payer and recipient must differ." }
                val settlement = Settlement(fromId = payer, toId = toId, amountCents = amountCents)
                when (val r = expenses.recordSettlement(groupId, settlement)) {
                    is RequestStatus.Success -> PaymentResult(r.data.id, r.data.amountCents, groupId)
                    is RequestStatus.Failed -> throw IllegalStateException(r.message ?: "Payment failed.")
                    is RequestStatus.Pending -> throw IllegalStateException("Payment pending.")
                }
            }
        }

    fun clearPayResult() {
        _payResult.value = null
    }
}
