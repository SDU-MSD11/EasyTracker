package com.sdumsd11.easytracker.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Locks the notification tap-to-destination contract (poster builds these Uris). */
class NotificationDeepLinkTest {

    @Test
    fun `uri wraps full route strings`() {
        assertEquals("easytracker://groups?tab=joined", NotificationDeepLinks.groups())
        assertEquals("easytracker://groups/g1", NotificationDeepLinks.groupDetail("g1"))
        assertEquals("easytracker://account/history", NotificationDeepLinks.paymentHistory())
        assertEquals("easytracker://settings", NotificationDeepLinks.settings())
        assertEquals(
            "easytracker://receipts?txId=tx1&amountCents=1250&groupId=g1",
            NotificationDeepLinks.receipt("tx1", 1250L, "g1"),
        )
    }

    @Test
    fun `new expense and summary open their group`() {
        assertEquals("groups/g1", NotificationDeepLinks.routeForKind("new-expense", "g1"))
        assertEquals(Routes.groups(), NotificationDeepLinks.routeForKind("new-expense"))
        assertEquals("groups/g1", NotificationDeepLinks.routeForKind("summary", "g1"))
    }

    @Test
    fun `debt update and retry success open payment history`() {
        assertEquals(
            Routes.PaymentHistory,
            NotificationDeepLinks.routeForKind("debt-update", "g1"),
        )
        assertEquals(
            Routes.PaymentHistory,
            NotificationDeepLinks.routeForKind("retry-success"),
        )
    }

    @Test
    fun `reminder and unknown kinds land on groups root`() {
        assertEquals(Routes.groups(), NotificationDeepLinks.routeForKind("reminder", "g1"))
        assertEquals(Routes.groups(), NotificationDeepLinks.routeForKind("bogus", "g1"))
    }

    @Test
    fun `validation passes known routes through`() {
        assertEquals(
            "groups/g1",
            NotificationDeepLinks.routeFromDeepLink("easytracker://groups/g1"),
        )
        assertEquals(
            "account/history",
            NotificationDeepLinks.routeFromDeepLink("easytracker://account/history"),
        )
        assertEquals(
            "receipts?txId=tx1",
            NotificationDeepLinks.routeFromDeepLink("easytracker://receipts?txId=tx1"),
        )
    }

    @Test
    fun `missing or malformed uris fall back to splash`() {
        assertNull(NotificationDeepLinks.routeFromDeepLink(null))
        assertNull(NotificationDeepLinks.routeFromDeepLink(""))
        assertNull(NotificationDeepLinks.routeFromDeepLink("easytracker://"))
        assertNull(NotificationDeepLinks.routeFromDeepLink("easytracker://groups/"))
        assertNull(NotificationDeepLinks.routeFromDeepLink("easytracker://groups/?tab=joined"))
        assertNull(NotificationDeepLinks.routeFromDeepLink("https://example.com/groups/g1"))
        assertNull(NotificationDeepLinks.routeFromDeepLink("easytracker://nope/g1"))
    }
}
