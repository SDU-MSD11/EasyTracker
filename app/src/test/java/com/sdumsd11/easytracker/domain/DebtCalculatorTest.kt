package com.sdumsd11.easytracker.domain

import com.sdumsd11.easytracker.domain.model.Balance
import com.sdumsd11.easytracker.domain.model.Expense
import com.sdumsd11.easytracker.domain.model.Settlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [DebtCalculator]. No Android framework, no Robolectric.
 */
class DebtCalculatorTest {

    private fun expense(
        id: String = "e",
        payer: String = "a",
        cents: Long = 900L,
        split: List<String> = listOf("a", "b", "c"),
    ) = Expense(
        id = id,
        groupId = "g",
        payerId = payer,
        amountCents = cents,
        description = "test",
        splitAmong = split,
        createdAt = 1L,
    )

    /** Applies settlements back onto nets; a fully settled map filters down to empty. */
    private fun applySettlements(
        balances: Map<String, Long>,
        settlements: List<Settlement>,
    ): Map<String, Long> {
        val nets = balances.toMutableMap()
        for (s in settlements) {
            nets[s.fromId] = (nets[s.fromId] ?: 0L) + s.amountCents
            nets[s.toId] = (nets[s.toId] ?: 0L) - s.amountCents
        }
        return nets.filterValues { it != 0L }
    }

    @Test
    fun `equal split divides evenly`() {
        assertEquals(
            mapOf("a" to 600L, "b" to -300L, "c" to -300L),
            DebtCalculator.calculateBalances(listOf(expense(cents = 900L))),
        )
    }

    @Test
    fun `remainder cents go to earliest participants in split order`() {
        assertEquals(
            mapOf("a" to 666L, "b" to -333L, "c" to -333L),
            DebtCalculator.calculateBalances(listOf(expense(cents = 1000L))),
        )
        // Reversed split order: the first participant absorbs the extra cent.
        assertEquals(
            mapOf("a" to 667L, "b" to -333L, "c" to -334L),
            DebtCalculator.calculateBalances(
                listOf(expense(cents = 1000L, split = listOf("c", "b", "a"))),
            ),
        )
    }

    @Test
    fun `selected-members split only affects participants`() {
        assertEquals(
            mapOf("a" to 500L, "b" to -500L),
            DebtCalculator.calculateBalances(
                listOf(expense(payer = "a", cents = 1000L, split = listOf("a", "b"))),
            ),
        )
    }

    @Test
    fun `payer outside split is fully credited`() {
        assertEquals(
            mapOf("a" to 1000L, "b" to -500L, "c" to -500L),
            DebtCalculator.calculateBalances(
                listOf(expense(payer = "a", cents = 1000L, split = listOf("b", "c"))),
            ),
        )
    }

    @Test
    fun `balances always sum to zero`() {
        val amounts = listOf(1L, 2L, 3L, 7L, 100L, 999L, 1000L, 1001L, 123456L)
        val splits = listOf(listOf("a", "b", "c"), listOf("b", "c"), listOf("a"))
        for (cents in amounts) {
            for (split in splits) {
                val balances = DebtCalculator.calculateBalances(
                    listOf(expense(payer = "a", cents = cents, split = split)),
                )
                assertEquals("cents=$cents split=$split", 0L, balances.values.sum())
            }
        }
    }

    @Test
    fun `multiple expenses accumulate`() {
        val balances = DebtCalculator.calculateBalances(
            listOf(
                expense(id = "e1", payer = "a", cents = 900L),
                expense(id = "e2", payer = "b", cents = 600L),
            ),
        )
        assertEquals(mapOf("a" to 400L, "b" to 100L, "c" to -500L), balances)
    }

    @Test
    fun `empty expenses yield empty balances`() {
        assertEquals(emptyMap<String, Long>(), DebtCalculator.calculateBalances(emptyList()))
    }

    @Test
    fun `empty balances need no settlements`() {
        assertEquals(emptyList<Settlement>(), DebtCalculator.minimizeSettlements(emptyMap()))
    }

    @Test
    fun `single debt settles directly`() {
        assertEquals(
            listOf(Settlement(fromId = "b", toId = "a", amountCents = 500L)),
            DebtCalculator.minimizeSettlements(mapOf("a" to 500L, "b" to -500L)),
        )
    }

    @Test
    fun `three users settle in at most n-1 moves`() {
        assertEquals(
            listOf(
                Settlement(fromId = "c", toId = "a", amountCents = 500L),
                Settlement(fromId = "c", toId = "b", amountCents = 300L),
            ),
            DebtCalculator.minimizeSettlements(mapOf("a" to 500L, "b" to 300L, "c" to -800L)),
        )
    }

    @Test
    fun `settlement order is deterministic on ties`() {
        val balances = mapOf("b" to 100L, "a" to 100L, "c" to -200L)
        val first = DebtCalculator.minimizeSettlements(balances)
        val second = DebtCalculator.minimizeSettlements(balances)
        // Tied creditors break by user id: a before b.
        assertEquals(
            listOf(
                Settlement(fromId = "c", toId = "a", amountCents = 100L),
                Settlement(fromId = "c", toId = "b", amountCents = 100L),
            ),
            first,
        )
        assertEquals(first, second)
    }

    @Test
    fun `settlements clear all nets and stay within n-1`() {
        val cases = listOf(
            mapOf("a" to 500L, "b" to -500L),
            mapOf("a" to 500L, "b" to 300L, "c" to -800L),
            mapOf("a" to 400L, "b" to 300L, "c" to 200L, "d" to -350L, "e" to -550L),
            mapOf("a" to 667L, "b" to -333L, "c" to -334L),
        )
        for (balances in cases) {
            val settlements = DebtCalculator.minimizeSettlements(balances)
            assertTrue(
                "size ${settlements.size} > n-1 for $balances",
                settlements.size <= balances.size - 1,
            )
            assertEquals(
                "unsettled remainder for $balances",
                emptyMap<String, Long>(),
                applySettlements(balances, settlements),
            )
            assertTrue(settlements.all { it.amountCents > 0 && it.fromId != it.toId })
        }
    }

    @Test
    fun `summarize totals sorts balances and derives settlements`() {
        val expenses = listOf(
            expense(id = "e1", payer = "a", cents = 900L),
            expense(id = "e2", payer = "b", cents = 600L),
        )
        val summary = DebtCalculator.summarize(expenses)
        assertEquals(1500L, summary.totalCents)
        assertEquals(
            listOf(Balance("a", 400L), Balance("b", 100L), Balance("c", -500L)),
            summary.balances,
        )
        assertEquals(summary.balances.sortedByDescending { it.netCents }, summary.balances)
        assertEquals(
            DebtCalculator.minimizeSettlements(DebtCalculator.calculateBalances(expenses)),
            summary.settlements,
        )
    }

    @Test
    fun `summarize empty expenses`() {
        val summary = DebtCalculator.summarize(emptyList())
        assertEquals(0L, summary.totalCents)
        assertEquals(emptyList<Balance>(), summary.balances)
        assertEquals(emptyList<Settlement>(), summary.settlements)
    }
}
