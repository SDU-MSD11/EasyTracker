package com.sdumsd11.easytracker.domain

import com.sdumsd11.easytracker.domain.model.Expense
import com.sdumsd11.easytracker.domain.model.UserId

/**
 * Honest Account chips: real group count + nets derived from cached expenses via
 * [DebtCalculator]. Fresh users (no expenses) get "No activity yet" — never mock debt.
 * Pure; unit-test directly.
 */
fun accountStats(groupCount: Int, myId: UserId, expenses: List<Expense>): List<String> {
    val groups = "$groupCount group" + if (groupCount == 1) "" else "s"
    if (expenses.isEmpty()) return listOf(groups, "No activity yet")
    val stats = mutableListOf(groups)
    val net = DebtCalculator.calculateBalances(expenses)[myId] ?: 0L
    stats += when {
        net < 0L -> "€${"%.2f".format(-net / 100.0)} owed"
        net > 0L -> "€${"%.2f".format(net / 100.0)} to receive"
        else -> "Settled up 🎉"
    }
    val paid = expenses.count { it.payerId == myId }
    if (paid > 0) stats += "$paid paid"
    return stats
}
