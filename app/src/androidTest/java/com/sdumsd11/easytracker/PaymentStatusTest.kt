package com.sdumsd11.easytracker

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.ui.screens.MakePaymentScreen
import com.sdumsd11.easytracker.ui.screens.MemberUi
import com.sdumsd11.easytracker.ui.screens.PaymentFailedScreen
import com.sdumsd11.easytracker.ui.screens.PaymentSuccessScreen
import com.sdumsd11.easytracker.ui.viewmodels.PaymentResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented check 4: after sending a request the user lands on a processing
 * screen where pending/success/failed status is visible.
 *
 * Renders the real screens with each [RequestStatus] — no Hilt, no nav, no network.
 * Offline *reads* (check 3) are covered cheaply in the unit [OfflineResilienceTest];
 * real airplane-mode toggling stays manual (see report).
 */
@RunWith(AndroidJUnit4::class)
class PaymentStatusTest {

    @get:Rule
    val compose = createComposeRule()

    private val participants = listOf(MemberUi("me", "Me"), MemberUi("anna", "Anna"))

    @Test
    fun pendingPayment_showsProcessingStatus() {
        compose.setContent {
            MakePaymentScreen(
                groupId = "g1",
                groupName = "Flatmates",
                onResult = { _, _ -> },
                onBack = {},
                payResult = RequestStatus.Pending,
                participants = participants,
                payer = MemberUi("me", "Me"),
            )
        }
        compose.onNodeWithText("Processing payment").assertIsDisplayed()
    }

    @Test
    fun successfulPayment_showsReceiptStatus() {
        compose.setContent {
            PaymentSuccessScreen(
                groupId = "g1",
                groupName = "Flatmates",
                txId = "tx-1",
                amountCents = 500L,
                onBackToGroup = {},
                onExportReceipt = {},
            )
        }
        compose.onNodeWithText("Payment successful").assertIsDisplayed()
        compose.onNodeWithText("Transaction: tx-1").assertIsDisplayed()
    }

    @Test
    fun failedPayment_showsErrorAndRetry() {
        compose.setContent {
            PaymentFailedScreen(
                groupId = "g1",
                error = "Insufficient funds",
                onRetry = {},
                onBackToGroup = {},
            )
        }
        compose.onNodeWithText("Payment failed").assertIsDisplayed()
        compose.onNodeWithText("Insufficient funds").assertIsDisplayed()
        compose.onNodeWithText("Retry payment").assertIsDisplayed()
    }

    @Test
    fun failedResult_routesToFailureCallback() {
        var routed: PaymentResult? = null
        var ok = true
        compose.setContent {
            MakePaymentScreen(
                groupId = "g1",
                onResult = { result, success ->
                    routed = result
                    ok = success
                },
                onBack = {},
                payResult = RequestStatus.Failed("boom"),
                participants = participants,
                payer = MemberUi("me", "Me"),
            )
        }
        compose.waitForIdle()
        assertFalse(ok)
        assertNotNull(routed)
    }
}
