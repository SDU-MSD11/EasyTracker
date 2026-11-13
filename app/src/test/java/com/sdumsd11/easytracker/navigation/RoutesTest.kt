package com.sdumsd11.easytracker.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/** Locks the route contract: builders must emit the pattern arg keys in one place. */
class RoutesTest {

    @Test
    fun `groups defaults to joined tab`() {
        assertEquals("groups?tab=joined", Routes.groups())
        assertEquals("groups?tab=free", Routes.groups(GroupsTab.FREE))
    }

    @Test
    fun `group detail and edit share the group id`() {
        assertEquals("groups/g1", Routes.groupDetail("g1"))
        assertEquals("groups/g1/edit", Routes.editGroup("g1"))
        assertEquals("groups/g1/pay", Routes.makePayment("g1"))
    }

    @Test
    fun `setupDebt omits debtId for create`() {
        assertEquals("groups/g1/debts/setup", Routes.setupDebt("g1"))
        assertEquals("groups/g1/debts/setup?debtId=d9", Routes.setupDebt("g1", "d9"))
    }

    @Test
    fun `payment routes carry tx and error args`() {
        assertEquals("payments/success/tx1", Routes.paymentSuccess("tx1"))
        assertEquals("payments/failed", Routes.paymentFailed())
        assertEquals("payments/failed?error=boom", Routes.paymentFailed("boom"))
    }

    @Test
    fun `receipt omits txId when absent`() {
        assertEquals("receipts", Routes.receipt())
        assertEquals("receipts?txId=tx1", Routes.receipt("tx1"))
    }
}
