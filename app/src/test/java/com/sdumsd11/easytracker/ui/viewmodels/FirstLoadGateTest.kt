package com.sdumsd11.easytracker.ui.viewmodels

import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.domain.model.Balance
import com.sdumsd11.easytracker.domain.model.DebtSummary
import com.sdumsd11.easytracker.domain.model.Group
import com.sdumsd11.easytracker.domain.model.Settlement
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host tests for the first-paint loading gates (blocker 3): a cold empty cache must
 * count as loading until the first explicit refresh lands, while warm caches paint
 * instantly and failures surface instead of spinning. Pure JVM: the gates are
 * top-level functions over [RequestStatus], no Android/VM needed.
 */
class FirstLoadGateTest {

    private val group = Group(id = "g1", name = "Flatmates", ownerId = "me", memberIds = listOf("me"))
    private val settled = DebtSummary(
        balances = listOf(Balance("me", 0L)),
        settlements = listOf(Settlement("a", "b", 100L)),
        totalCents = 100L,
    )
    private val emptySummary = DebtSummary(emptyList(), emptyList(), 0L)

    // Groups gate.

    @Test
    fun groupsPendingIsLoading() {
        assertTrue(groupsFirstLoad(RequestStatus.Pending, refreshedOnce = false))
        assertTrue(groupsFirstLoad(RequestStatus.Pending, refreshedOnce = true))
    }

    @Test
    fun groupsEmptyCacheIsLoadingUntilRefreshed() {
        val empty = RequestStatus.Success(emptyList<Group>())
        assertTrue(groupsFirstLoad(empty, refreshedOnce = false))
        assertFalse(groupsFirstLoad(empty, refreshedOnce = true))
    }

    @Test
    fun groupsWarmCachePaintsInstantly() {
        val warm = RequestStatus.Success(listOf(group))
        assertFalse(groupsFirstLoad(warm, refreshedOnce = false))
        assertFalse(groupsFirstLoad(warm, refreshedOnce = true))
    }

    @Test
    fun groupsFailureSurfacesInsteadOfSpinning() {
        val failed = RequestStatus.Failed("offline")
        assertFalse(groupsFirstLoad(failed, refreshedOnce = false))
        assertFalse(groupsFirstLoad(failed, refreshedOnce = true))
    }

    // Detail gate.

    @Test
    fun detailPendingIsLoading() {
        assertTrue(detailFirstLoad(RequestStatus.Pending, RequestStatus.Success(emptySummary), false))
        assertTrue(detailFirstLoad(RequestStatus.Success(group), RequestStatus.Pending, false))
    }

    @Test
    fun detailColdEmptyStaysLoadingUntilRefreshed() {
        // Missing group + empty summary: the "all settled up" flash before the network answers.
        assertTrue(
            detailFirstLoad(
                RequestStatus.Success(null),
                RequestStatus.Success(emptySummary),
                refreshedOnce = false,
            ),
        )
        assertFalse(
            detailFirstLoad(
                RequestStatus.Success(null),
                RequestStatus.Success(emptySummary),
                refreshedOnce = true,
            ),
        )
    }

    @Test
    fun detailWarmCachePaintsInstantly() {
        assertFalse(
            detailFirstLoad(
                RequestStatus.Success(group),
                RequestStatus.Success(settled),
                refreshedOnce = false,
            ),
        )
    }

    @Test
    fun detailFailureSurfacesInsteadOfSpinning() {
        assertFalse(
            detailFirstLoad(
                RequestStatus.Success(group),
                RequestStatus.Failed("offline"),
                refreshedOnce = false,
            ),
        )
    }
}
