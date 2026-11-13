package com.sdumsd11.easytracker.domain.model

/** Stable expense identifier. */
typealias ExpenseId = String

/**
 * Pure domain expense. Money is **minor units** ([amountCents]) to avoid float errors.
 *
 * @param splitAmong participants sharing the cost; payer need not be included.
 * @param receiptUri optional attachment reference only (content URI string, nullable).
 * @param createdAt epoch millis (Long keeps minSdk 24 safe without desugaring).
 * @param isSettlement stable repayment marker from the wire `kind` field — never inferred
 * from the description, so a real debt titled "Settlement" stays a debt.
 */
data class Expense(
    val id: ExpenseId,
    val groupId: GroupId,
    val payerId: UserId,
    val amountCents: Long,
    val description: String,
    val splitAmong: List<UserId>,
    val receiptUri: String? = null,
    val createdAt: Long = 0L,
    val isSettlement: Boolean = false,
) {
    init {
        require(amountCents >= 0L) { "amountCents must be >= 0" }
        require(splitAmong.isNotEmpty()) { "splitAmong must not be empty" }
    }
}
