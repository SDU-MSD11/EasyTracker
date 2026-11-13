package com.sdumsd11.easytracker.data.outbox

import com.sdumsd11.easytracker.data.findReusablePaymentMirror
import com.sdumsd11.easytracker.data.local.ExpenseEntity
import com.sdumsd11.easytracker.data.staleLocalMirrorIds
import com.sdumsd11.easytracker.domain.model.Settlement
import com.sdumsd11.easytracker.ui.StatusBus
import com.sdumsd11.easytracker.ui.StatusEvent
import com.sdumsd11.easytracker.ui.StatusKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Reviewer must-fix cover: stable payment keys, mirror reuse, reconciliation, StatusBus bridge. */
class OutboxFixesTest {

    private val settlement = Settlement(fromId = "u1", toId = "u2", amountCents = 500L)

    @Test
    fun paymentOpKey_isStableForSameSettlementIdentity() {
        assertEquals(
            "payment-g1-u1-u2-500",
            paymentOpKey("g1", settlement),
        )
        assertEquals(
            paymentOpKey("g1", settlement),
            paymentOpKey("g1", settlement.copy()),
        )
    }

    @Test
    fun paymentOpKey_distinguishesAmountRecipientAndGroup() {
        val base = paymentOpKey("g1", settlement)
        assertEquals("payment-g1-u1-u2-500", base)
        // Retry/double-tap of the same payment collapses to one unique work key…
        assertEquals(base, paymentOpKey("g1", Settlement("u1", "u2", 500L)))
        // …while genuinely different payments key differently.
        assertEquals(true, paymentOpKey("g1", Settlement("u1", "u2", 600L)) != base)
        assertEquals(true, paymentOpKey("g1", Settlement("u1", "u3", 500L)) != base)
        assertEquals(true, paymentOpKey("g2", settlement) != base)
    }

    private fun mirror(
        id: String,
        payer: String = "u1",
        to: String = "u2",
        amount: Long = 500L,
        description: String = "Settlement",
    ) = ExpenseEntity(
        id = id,
        groupId = "g1",
        payerId = payer,
        amountCents = amount,
        description = description,
        splitAmong = to,
        receiptUri = null,
        createdAt = 1L,
        isSettlement = true,
        isLocalMirror = true,
    )

    @Test
    fun findReusablePaymentMirror_reusesSameIdentity() {
        val mirrors = listOf(mirror("m1"), mirror("m2", amount = 999L))
        assertEquals("m1", findReusablePaymentMirror(mirrors, settlement)?.id)
    }

    @Test
    fun findReusablePaymentMirror_returnsNullWhenNoMatch() {
        assertNull(findReusablePaymentMirror(emptyList(), settlement))
        assertNull(
            findReusablePaymentMirror(
                listOf(mirror("m1", amount = 999L), mirror("m2", to = "u3")),
                settlement,
            ),
        )
        // A real debt titled "Settlement" is never mistaken for a payment mirror
        // (stable isSettlement flag, never the description text).
        assertNull(
            findReusablePaymentMirror(
                listOf(mirror("m1").copy(isSettlement = false)),
                settlement,
            ),
        )
    }

    @Test
    fun staleLocalMirrorIds_prunesOnlyAbsentMirrors() {
        val mirrors = listOf(mirror("keep"), mirror("drop"))
        assertEquals(listOf("drop"), staleLocalMirrorIds(mirrors, setOf("keep", "srv-1")))
        assertEquals(emptyList<String>(), staleLocalMirrorIds(mirrors, setOf("keep", "drop")))
        assertEquals(emptyList<String>(), staleLocalMirrorIds(emptyList(), setOf("srv-1")))
    }

    @Test
    fun statusBus_deliversBackgroundSuccessEvent() = runBlocking {
        val bus = StatusBus()
        bus.notify(StatusEvent("payment-g1-u1-u2-500", "Payment synced", StatusKind.BackgroundSuccess))
        val event = bus.events.first()
        assertEquals("payment-g1-u1-u2-500", event.key)
        assertEquals("Payment synced", event.message)
        assertEquals(StatusKind.BackgroundSuccess, event.kind)
    }
}
