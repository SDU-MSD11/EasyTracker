package com.sdumsd11.easytracker.domain

import com.sdumsd11.easytracker.domain.model.Expense
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure JVM tests for [accountStats]: fresh users never see mock debt. */
class AccountStatsTest {

    private fun expense(
        id: String = "e",
        payer: String = "a",
        cents: Long = 9_000L,
        split: List<String> = listOf("a", "b"),
    ) = Expense(
        id = id,
        groupId = "g",
        payerId = payer,
        amountCents = cents,
        description = "test",
        splitAmong = split,
        createdAt = 1L,
    )

    @Test
    fun `fresh user with no groups and no expenses sees honest zero state`() {
        assertEquals(listOf("0 groups", "No activity yet"), accountStats(0, "crab", emptyList()))
    }

    @Test
    fun `fresh user with a group but no expenses shows no fake debt`() {
        assertEquals(listOf("1 group", "No activity yet"), accountStats(1, "crab", emptyList()))
    }

    @Test
    fun `user who owes shows real net derived from expenses`() {
        // b pays 9000 split [a, b] -> a owes 4500.
        val expenses = listOf(expense(payer = "b", split = listOf("a", "b")))
        assertEquals(listOf("1 group", "€45.00 owed"), accountStats(1, "a", expenses))
    }

    @Test
    fun `user who is owed shows amount to receive plus paid count`() {
        // a pays 9000 split [a, b] -> b owes a 4500, a paid once.
        val expenses = listOf(expense(payer = "a", split = listOf("a", "b")))
        assertEquals(listOf("2 groups", "€45.00 to receive", "1 paid"), accountStats(2, "a", expenses))
    }

    @Test
    fun `settled user with activity sees settled plus paid count`() {
        val expenses = listOf(
            expense(id = "e1", payer = "a", cents = 1_000L),
            expense(id = "e2", payer = "b", cents = 1_000L),
        )
        assertEquals(listOf("1 group", "Settled up 🎉", "1 paid"), accountStats(1, "a", expenses))
    }
}
