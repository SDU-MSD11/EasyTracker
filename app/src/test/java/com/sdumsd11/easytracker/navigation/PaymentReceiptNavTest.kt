package com.sdumsd11.easytracker.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Payment/Receipt route builders (ship-blocker 1): the real payment props ride
 * the route (amount + group next to the txId); nulls stay omitted so legacy
 * callers and deep links without query args keep working.
 */
class PaymentReceiptNavTest {

    @Test
    fun `paymentSuccess omits nulls for legacy callers`() {
        assertEquals("payments/success/tx1", Routes.paymentSuccess("tx1"))
    }

    @Test
    fun `paymentSuccess carries real amount and group`() {
        assertEquals(
            "payments/success/tx1?amountCents=1250&groupId=g1",
            Routes.paymentSuccess("tx1", 1250L, "g1"),
        )
    }

    @Test
    fun `paymentSuccess carries amount without group`() {
        assertEquals(
            "payments/success/tx1?amountCents=1250",
            Routes.paymentSuccess("tx1", 1250L),
        )
    }

    @Test
    fun `receipt omits nulls for legacy callers`() {
        assertEquals("receipts", Routes.receipt())
        assertEquals("receipts?txId=tx1", Routes.receipt("tx1"))
    }

    @Test
    fun `receipt carries tx amount and group`() {
        assertEquals(
            "receipts?txId=tx1&amountCents=1250&groupId=g1",
            Routes.receipt("tx1", 1250L, "g1"),
        )
    }
}
