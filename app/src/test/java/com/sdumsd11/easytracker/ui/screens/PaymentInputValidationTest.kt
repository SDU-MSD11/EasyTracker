package com.sdumsd11.easytracker.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Host tests for MakePayment validation (blocker 2): the payer is fixed to the
 * session user (display-only), so the recipient must be a loaded member that
 * differs from the payer — a selectable payer the VM ignores can no longer
 * pass validation. Pure JVM: the validator is a top-level function, no Compose needed.
 */
class PaymentInputValidationTest {

    @Test
    fun membersMissingBlocksWithHonestError() {
        assertEquals(
            "Group members unavailable — cannot record a payment yet.",
            paymentInputError(
                recipientValid = false,
                amount = 10.0,
                payerId = "me",
                recipientId = "anna",
            ),
        )
    }

    @Test
    fun nonPositiveAmountBlocked() {
        assertEquals(
            "Enter an amount greater than 0.",
            paymentInputError(recipientValid = true, amount = null, payerId = "me", recipientId = "anna"),
        )
        assertEquals(
            "Enter an amount greater than 0.",
            paymentInputError(recipientValid = true, amount = 0.0, payerId = "me", recipientId = "anna"),
        )
        assertEquals(
            "Enter an amount greater than 0.",
            paymentInputError(recipientValid = true, amount = -5.0, payerId = "me", recipientId = "anna"),
        )
    }

    @Test
    fun selfPaymentBlockedAgainstFixedPayer() {
        assertEquals(
            "Payer and recipient must differ.",
            paymentInputError(recipientValid = true, amount = 10.0, payerId = "me", recipientId = "me"),
        )
    }

    @Test
    fun distinctRecipientPasses() {
        assertNull(
            paymentInputError(recipientValid = true, amount = 10.0, payerId = "me", recipientId = "anna"),
        )
    }

    @Test
    fun unknownPayerSkipsSelfCheckForVmTrustBoundary() {
        // Session still loading: the screen can't compare, the VM require() covers it.
        assertNull(
            paymentInputError(recipientValid = true, amount = 10.0, payerId = null, recipientId = "anna"),
        )
    }
}
