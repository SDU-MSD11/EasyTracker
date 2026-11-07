package com.sdumsd11.easytracker.domain

import com.sdumsd11.easytracker.domain.model.Balance
import com.sdumsd11.easytracker.domain.model.DebtSummary
import com.sdumsd11.easytracker.domain.model.Expense
import com.sdumsd11.easytracker.domain.model.Settlement
import com.sdumsd11.easytracker.domain.model.UserId

/**
 * Pure debt math for shared expenses. No Android dependencies; unit-test directly.
 *
 * All amounts are minor units (cents). Payer may be inside or outside [Expense.splitAmong].
 *
 * Example: A pays 1000 for [A, B, C] -> A +667 (1000 - 333 - 1 remainder cent),
 * B -333, C -334 (first participants in split order absorb the remainder cent).
 */
object DebtCalculator {

    /**
     * Net per user: positive = owed to them, negative = they owe. Sums to zero.
     * Remainder cents from uneven splits go to the earliest participants in split order.
     */
    fun calculateBalances(expenses: List<Expense>): Map<UserId, Long> {
        val balances = mutableMapOf<UserId, Long>()
        for (e in expenses) {
            val n = e.splitAmong.size
            if (n == 0) continue
            val share = e.amountCents / n
            var remainder = (e.amountCents % n).toInt()
            balances[e.payerId] = (balances[e.payerId] ?: 0L) + e.amountCents
            for (participant in e.splitAmong) {
                val owed = share + if (remainder > 0) {
                    remainder--
                    1L
                } else {
                    0L
                }
                balances[participant] = (balances[participant] ?: 0L) - owed
            }
        }
        return balances.filterValues { it != 0L }
    }

    /**
     * Greedy min-cash-flow: repeatedly matches the largest creditor against the largest
     * debtor for `min(credit, debt)` until all nets clear. Result has at most n-1 entries
     * for n unsettled users when balances sum to zero. Ordering is deterministic
     * (amount, then user id) so tests/snapshots are stable.
     */
    fun minimizeSettlements(balances: Map<UserId, Long>): List<Settlement> {
        val credit = balances.filterValues { it > 0L }
            .entries.sortedWith(compareByDescending<Map.Entry<UserId, Long>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }.toMutableList()
        val debt = balances.filterValues { it < 0L }
            .entries.sortedWith(compareBy<Map.Entry<UserId, Long>> { it.value }.thenBy { it.key })
            .map { it.key to -it.value }.toMutableList()
        val out = mutableListOf<Settlement>()
        var i = 0
        var j = 0
        while (i < credit.size && j < debt.size) {
            val (creditor, creditAmt) = credit[i]
            val (debtor, debtAmt) = debt[j]
            val paid = minOf(creditAmt, debtAmt)
            if (paid <= 0L) break
            out += Settlement(fromId = debtor, toId = creditor, amountCents = paid)
            credit[i] = creditor to creditAmt - paid
            debt[j] = debtor to debtAmt - paid
            if (credit[i].second == 0L) i++
            if (debt[j].second == 0L) j++
        }
        return out
    }

    /** One-shot summary for group-detail / balances routes. */
    fun summarize(expenses: List<Expense>): DebtSummary {
        val balances = calculateBalances(expenses)
        return DebtSummary(
            balances = balances.map { (id, net) -> Balance(id, net) }
                .sortedByDescending { it.netCents },
            settlements = minimizeSettlements(balances),
            totalCents = expenses.sumOf { it.amountCents },
        )
    }
}
