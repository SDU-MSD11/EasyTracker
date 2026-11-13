package com.sdumsd11.easytracker.domain.model

/**
 * Net position of one user within a group.
 * Positive = others owe them; negative = they owe others. All group balances sum to zero.
 */
data class Balance(val userId: UserId, val netCents: Long)

/** One repayment: [fromId] pays [toId] [amountCents] (minor units). */
data class Settlement(val fromId: UserId, val toId: UserId, val amountCents: Long) {
    init {
        require(amountCents > 0L) { "amountCents must be > 0" }
        require(fromId != toId) { "settlement must be between two users" }
    }
}

/** Transaction summary for a group: per-user nets, minimal repayments, total spend. */
data class DebtSummary(
    val balances: List<Balance>,
    val settlements: List<Settlement>,
    val totalCents: Long,
)
