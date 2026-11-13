package com.sdumsd11.easytracker.domain

import com.sdumsd11.easytracker.domain.model.Expense
import org.junit.Assert.assertEquals
import org.junit.Test

/** Edge guards for [accountStats]: plurals, strangers, and paid-count honesty. */
class AccountStatsEdgeTest {

    private fun expense(
        id: String = "e",
        payer: String = "a",
        cents: Long = 9_000L,
        split: List<String> = listOf("a", "b"),
    ) = Expense(id, "g", payer, cents, "t", split, createdAt = 1L)

    @Test
    fun `pluralization is exact`() {
        assertEquals("1 group", accountStats(1, "x", emptyList())[0])
        assertEquals("0 groups", accountStats(0, "x", emptyList())[0])
        assertEquals("2 groups", accountStats(2, "x", emptyList())[0])
    }

    @Test
    fun `stranger to expenses shows settled plus no paid count`() {
        val expenses = listOf(expense(payer = "a", split = listOf("a", "b")))
        assertEquals(listOf("1 group", "Settled up 🎉"), accountStats(1, "ghost", expenses))
    }

    @Test
    fun `multiple expenses accumulate into one net plus paid count`() {
        // a pays 9000 split [a,b] -> b owes 4500; then b pays 3000 split [a,b] -> a owes 1500.
        // Net for a: +4500 -1500 = +3000 to receive; a paid once.
        val expenses = listOf(
            expense(id = "e1", payer = "a", cents = 9_000L),
            expense(id = "e2", payer = "b", cents = 3_000L),
        )
        assertEquals(listOf("1 group", "€30.00 to receive", "1 paid"), accountStats(1, "a", expenses))
        assertEquals(listOf("1 group", "€30.00 owed", "1 paid"), accountStats(1, "b", expenses))
    }

    @Test
    fun `payer outside split keeps full credit without paid inflation`() {
        // c pays 1000 for [a, b]: c is owed 1000, paid once; a owes 500.
        val expenses = listOf(expense(id = "e1", payer = "c", cents = 1_000L, split = listOf("a", "b")))
        assertEquals(listOf("0 groups", "€10.00 to receive", "1 paid"), accountStats(0, "c", expenses))
        assertEquals(listOf("0 groups", "€5.00 owed"), accountStats(0, "a", expenses))
    }
}
