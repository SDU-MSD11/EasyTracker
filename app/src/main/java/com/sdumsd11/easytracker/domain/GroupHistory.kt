package com.sdumsd11.easytracker.domain

import com.sdumsd11.easytracker.domain.model.Expense
import com.sdumsd11.easytracker.domain.model.ExpenseId
import com.sdumsd11.easytracker.domain.model.GroupId
import com.sdumsd11.easytracker.domain.model.Settlement
import com.sdumsd11.easytracker.domain.model.UserId

/**
 * Per-group history + per-debt paid tracking, derived from cached expenses. Pure; names
 * resolve in the VM via its members flow (this layer only carries ids).
 *
 * Linkage note: settlement payments are stored as expenses (`recordSettlement` writes
 * payer = fromId, splitAmong = [toId], isSettlement = true), so a payment's id doubles
 * as its tx ref — the backend exposes no separate tx/debt-id field ([PaymentResult.txId]
 * is that id). Detection uses the stable [Expense.isSettlement] flag from the wire
 * `kind` field, never the description.
 */
enum class GroupHistoryKind { DEBT, PAYMENT }

/** Recipient of a payment, or the debt's first split participant other than the payer. */
private fun Expense.counterparty(): UserId =
    splitAmong.firstOrNull { it != payerId } ?: splitAmong.firstOrNull() ?: payerId

/** One history row: a debt created (plain expense) or a payment made (settlement expense). */
data class GroupHistoryEntry(
    val id: ExpenseId,
    val groupId: GroupId,
    val kind: GroupHistoryKind,
    val title: String,
    /** Debt creator / payment sender. */
    val payerId: UserId,
    /** Debt: first split participant other than the payer. Payment: the recipient. */
    val counterpartyId: UserId,
    val amountCents: Long,
    val createdAt: Long,
)

/** Paid-so-far vs total for one outstanding settlement line. */
data class SettlementProgress(
    val settlement: Settlement,
    val paidCents: Long,
    val totalCents: Long,
)

/** Newest-first history rows for one group. */
fun groupHistory(expenses: List<Expense>): List<GroupHistoryEntry> =
    expenses.sortedByDescending { it.createdAt }.map { e ->
        GroupHistoryEntry(
            id = e.id,
            groupId = e.groupId,
            kind = if (e.isSettlement) GroupHistoryKind.PAYMENT else GroupHistoryKind.DEBT,
            title = e.description,
            payerId = e.payerId,
            counterpartyId = e.counterparty(),
            amountCents = e.amountCents,
            createdAt = e.createdAt,
        )
    }

/**
 * Paid-vs-total per outstanding settlement line. The remaining leg reuses
 * [DebtCalculator] nets; the paid leg sums settlement payments on the same
 * from→to pair via the same counterparty rule as [groupHistory], so payments
 * attribute to the minimized lines the route renders (see [debtLine]).
 *
 * Fallback (documented): settlement expenses carry no debtId, so payments attribute at
 * group scope per pair — never to a single expense. Pair totals (remaining + paid) still
 * reconcile with [DebtCalculator] nets. Paid clamps to [0, total] so an overpayment
 * (>100%) never renders above the line total.
 */
fun settlementPaidProgress(expenses: List<Expense>): List<SettlementProgress> {
    val remaining = DebtCalculator.minimizeSettlements(DebtCalculator.calculateBalances(expenses))
    if (remaining.isEmpty()) return emptyList()
    val paidByPair = expenses.filter { it.isSettlement }
        .groupBy { it.payerId to it.counterparty() }
        .mapValues { (_, v) -> v.sumOf { it.amountCents } }
    return remaining.map { s ->
        val paid = (paidByPair[s.fromId to s.toId] ?: 0L)
        val total = s.amountCents + paid
        SettlementProgress(s, paidCents = paid.coerceIn(0L, total), totalCents = total)
    }
}

/** Never a raw full id as the primary line. Mirrors the screen short-ref. */
fun shortIdRef(id: String): String = if (id.length > 8) "••••${id.takeLast(4)}" else id.ifBlank { "••••????" }

/** Members-flow fallback for ids missing from the name map — never a full UUID. */
fun memberShortRef(id: String): String =
    if (id.length > 8) "Member ••••${id.takeLast(4)}" else id

fun displayNameOrRef(nameById: Map<UserId, String>, id: UserId): String =
    nameById[id] ?: memberShortRef(id)

/**
 * Single format for a settlement debt line. Shared by the route (debts list) and the VM
 * (progress-map keys) so the screen lookup hits.
 */
fun debtLine(fromName: String, toName: String, amountCents: Long): String =
    "$fromName owes $toName €${"%.2f".format(amountCents / 100.0)}"
