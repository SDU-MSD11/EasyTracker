package com.sdumsd11.easytracker.data.fake

import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.domain.model.Group
import com.sdumsd11.easytracker.domain.model.InviteStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Invite lifecycle + create/update/delete contracts for [FakeGroupRepository]. Pure JVM. */
class FakeGroupFlowsTest {

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

    private suspend fun group(r: FakeGroupRepository, id: String): Group =
        (r.observeGroup(id).first { it is RequestStatus.Success } as RequestStatus.Success).data!!

    @Test
    fun `createGroup rejects blank and includes owner`() = runBlocking {
        val r = repo()
        assertTrue(r.createGroup("  ", "", "o", emptyList()) is RequestStatus.Failed)
        val g = (r.createGroup("  Trip  ", "d", "o", listOf("m")) as RequestStatus.Success<Group>).data
        assertEquals("Trip", g.name)
        assertTrue(g.memberIds.containsAll(listOf("o", "m")))
    }

    @Test
    fun `addMember marks invite accepted`() = runBlocking {
        val r = repo()
        val g = (r.createGroup("G", "", "o", emptyList()) as RequestStatus.Success<Group>).data
        assertTrue(r.addMember(g.id, "n@x.y") is RequestStatus.Success)
        val after = group(r, g.id)
        assertTrue(after.memberIds.contains("n@x.y"))
        assertEquals(InviteStatus.ACCEPTED, after.invites["n@x.y"])
        assertTrue(r.addMember("missing", "x") is RequestStatus.Failed)
    }

    @Test
    fun `respondToInvite accept and decline`() = runBlocking {
        val r = repo()
        val g = (r.createGroup("G", "", "o", emptyList()) as RequestStatus.Success<Group>).data
        assertTrue(r.respondToInvite(g.id, "i", true) is RequestStatus.Success)
        assertTrue(group(r, g.id).memberIds.contains("i"))
        assertTrue(r.respondToInvite(g.id, "i", false) is RequestStatus.Success)
        val after = group(r, g.id)
        assertTrue(!after.memberIds.contains("i"))
        assertEquals(InviteStatus.DECLINED, after.invites["i"])
    }

    @Test
    fun `updateGroup blank name keeps current`() = runBlocking {
        val r = repo()
        val g = (r.createGroup("Old", "d", "o", emptyList()) as RequestStatus.Success<Group>).data
        val kept = (r.updateGroup(g.id, "  ", "new-d") as RequestStatus.Success<Group>).data
        assertEquals("Old", kept.name)
        assertEquals("new-d", kept.description)
    }

    @Test
    fun `deleteGroup removes and second delete fails`() = runBlocking {
        val r = repo()
        val g = (r.createGroup("G", "", "o", emptyList()) as RequestStatus.Success<Group>).data
        assertTrue(r.deleteGroup(g.id) is RequestStatus.Success)
        assertTrue(r.deleteGroup(g.id) is RequestStatus.Failed)
    }
}
