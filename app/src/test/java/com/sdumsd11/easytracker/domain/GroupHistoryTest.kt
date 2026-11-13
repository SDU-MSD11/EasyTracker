package com.sdumsd11.easytracker.domain

import com.sdumsd11.easytracker.domain.model.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for per-group history + per-debt paid tracking. */
class GroupHistoryTest {

    private fun expense(
        id: String,
        payer: String,
        cents: Long,
        split: List<String>,
        description: String = "Dinner",
        createdAt: Long = 1L,
        isSettlement: Boolean = false,
    ) = Expense(
        id = id,
        groupId = "g",
        payerId = payer,
        amountCents = cents,
        description = description,
        splitAmong = split,
        createdAt = createdAt,
        isSettlement = isSettlement,
    )

    @Test
    fun `history is newest first with debt vs payment kinds`() {
        val debt = expense("d1", "you", 4_300L, listOf("you", "anna"), createdAt = 1L)
        val pay = expense(
            "p1", "anna", 500L, listOf("you"), description = "Settlement", createdAt = 2L,
            isSettlement = true,
        )
        val history = groupHistory(listOf(debt, pay))
        assertEquals(listOf("p1", "d1"), history.map { it.id })
        assertEquals(GroupHistoryKind.PAYMENT, history[0].kind)
        assertEquals(GroupHistoryKind.DEBT, history[1].kind)
        // Payment counterparty is the recipient; debt counterparty skips the payer.
        assertEquals("you", history[0].counterpartyId)
        assertEquals("anna", history[1].counterpartyId)
    }

    @Test
    fun `settlement progress pairs remaining nets with same-pair payments`() {
        val debt = expense("d1", "you", 4_300L, listOf("you", "anna"), createdAt = 1L)
        val pay = expense(
            "p1", "anna", 500L, listOf("you"), description = "Settlement", createdAt = 2L,
            isSettlement = true,
        )
        val progress = settlementPaidProgress(listOf(debt, pay))
        assertEquals(1, progress.size)
        // Net left: anna -> you 1650; paid 500 of 2150 total.
        assertEquals(1_650L, progress[0].settlement.amountCents)
        assertEquals(500L, progress[0].paidCents)
        assertEquals(2_150L, progress[0].totalCents)
    }

    @Test
    fun `lone settlement opens a fresh unpaid line regardless of description`() {
        val pay = expense(
            "p1", "anna", 500L, listOf("you"), description = "Bank transfer", createdAt = 1L,
            isSettlement = true,
        )
        // anna covered you's share -> you owe anna 500; the payment created the line,
        // it did not repay it.
        val progress = settlementPaidProgress(listOf(pay))
        assertEquals(1, progress.size)
        assertEquals(0L, progress[0].paidCents)
        assertEquals(500L, progress[0].totalCents)
        assertEquals(emptyList<SettlementProgress>(), settlementPaidProgress(emptyList()))
    }

    @Test
    fun `debt titled Settlement without the flag stays a debt`() {
        // Stable kind, not the description: a real debt named "Settlement" must not
        // masquerade as a payment in history or paid progress.
        val debt = expense("d1", "you", 1_000L, listOf("you", "anna"), description = "Settlement")
        val history = groupHistory(listOf(debt))
        assertEquals(GroupHistoryKind.DEBT, history[0].kind)
        val progress = settlementPaidProgress(listOf(debt))
        assertEquals(1, progress.size)
        assertEquals(0L, progress[0].paidCents)
        assertEquals(500L, progress[0].totalCents)
    }

    @Test
    fun `overpayment never renders paid above total`() {
        // anna overpays 1500 on a 500 debt: nets flip, the rendered line reverses, and
        // the stale pair payment must not inflate the new line past its total.
        val debt = expense("d1", "you", 1_000L, listOf("you", "anna"), createdAt = 1L)
        val over = expense(
            "p1", "anna", 1_500L, listOf("you"), description = "Settlement", createdAt = 2L,
            isSettlement = true,
        )
        val progress = settlementPaidProgress(listOf(debt, over))
        assertEquals(1, progress.size)
        assertEquals("you", progress[0].settlement.fromId)
        assertEquals("anna", progress[0].settlement.toId)
        assertEquals(1_000L, progress[0].totalCents)
        assertEquals(0L, progress[0].paidCents)
        assertTrue(progress.all { it.paidCents <= it.totalCents })
    }

    @Test
    fun `short refs never expose full ids`() {
        val long = "550e8400-e29b-41d4-a716-446655440000"
        assertEquals("••••0000", shortIdRef(long))
        assertEquals("Member ••••0000", displayNameOrRef(emptyMap(), long))
        assertEquals("Anna", displayNameOrRef(mapOf("a" to "Anna"), "a"))
        assertEquals("you owes Anna €12.50", debtLine("you", "Anna", 1_250L))
    }
}
