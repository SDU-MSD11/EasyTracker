package com.sdumsd11.easytracker.data.fake

import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.domain.model.Group
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Host tests for [FakeGroupRepository.updateGroup] (pure JVM, no Android framework). */
class FakeGroupRepositoryTest {

    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun repo() = FakeGroupRepository(
        scope = scope,
        notifications = FakeNotificationService(),
        queue = RetryQueue(scope, {}, baseDelayMs = 0L),
        sync = SyncSimulator(latencyMs = 0L),
    )

    @Test
    fun `updateGroup renames and keeps untouched fields`() = runBlocking {
        val r = repo()
        val created = (r.createGroup("Old", "desc", "owner", emptyList())
            as RequestStatus.Success<Group>).data
        val updated = r.updateGroup(created.id, "New", null)
        assertTrue(updated is RequestStatus.Success)
        assertEquals("New", (updated as RequestStatus.Success<Group>).data.name)
        assertEquals("desc", updated.data.description)
        assertTrue(r.updateGroup("missing", "x", null) is RequestStatus.Failed)
    }

    @Test
    fun `removeMember evicts member and fails gracefully on unknown group`() = runBlocking {
        val r = repo()
        val created = (r.createGroup("G", "", "owner", listOf("m1", "m2"))
            as RequestStatus.Success<Group>).data
        assertTrue(r.removeMember(created.id, "m1") is RequestStatus.Success)
        val after = r.observeGroup(created.id).first { it is RequestStatus.Success }
            as RequestStatus.Success
        assertFalse(after.data!!.memberIds.contains("m1"))
        assertTrue(after.data.memberIds.contains("m2"))
        // Unknown group id fails gracefully (no crash); unknown member is idempotent success.
        assertTrue(r.removeMember("missing", "m2") is RequestStatus.Failed)
        assertTrue(r.removeMember(created.id, "ghost") is RequestStatus.Success)
    }
}
