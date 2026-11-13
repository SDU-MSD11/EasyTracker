package com.sdumsd11.easytracker.data.fake

import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.domain.model.NotificationPrefs
import com.sdumsd11.easytracker.domain.model.User
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

/** Host tests for [FakeUserRepository]: profile + prefs + member cache. Pure JVM. */
class FakeUserRepositoryTest {

    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun repo(initial: List<User> = emptyList(), current: String? = null) = FakeUserRepository(
        scope = scope,
        notifications = FakeNotificationService(),
        queue = RetryQueue(scope, {}, baseDelayMs = 0L),
        sync = SyncSimulator(latencyMs = 0L),
        initial = initial,
        currentUserId = current,
    )

    @Test
    fun `upsert rejects blank name`() = runBlocking {
        assertTrue(repo().upsertUser(User("u", "")) is RequestStatus.Failed)
    }

    @Test
    fun `saveCurrentProfile sets current and is observable`() = runBlocking {
        val r = repo()
        val me = User("me", "Me", "me@x.y")
        assertTrue(r.saveCurrentProfile(me) is RequestStatus.Success)
        val cur = r.observeCurrentProfile().first { it is RequestStatus.Success } as RequestStatus.Success
        assertEquals(me, cur.data)
    }

    @Test
    fun `observeUsers sorts by name`() = runBlocking {
        val r = repo(listOf(User("b", "Zed"), User("a", "Ann")))
        val all = r.observeUsers().first { it is RequestStatus.Success } as RequestStatus.Success
        assertEquals(listOf("Ann", "Zed"), all.data.map { it.name })
    }

    @Test
    fun `updatePrefs persists and unknown user fails`() = runBlocking {
        val r = repo(listOf(User("u", "U")))
        val prefs = NotificationPrefs(newExpense = false, reminders = true, settlementUpdates = false)
        assertTrue(r.updateNotificationPrefs("u", prefs) is RequestStatus.Success)
        val after = r.observeUser("u").first { it is RequestStatus.Success } as RequestStatus.Success
        assertEquals(prefs, after.data!!.notificationPrefs)
        assertTrue(r.updateNotificationPrefs("ghost", prefs) is RequestStatus.Failed)
    }

    @Test
    fun `null current profile when signed out`() = runBlocking {
        val cur = repo().observeCurrentProfile().first { it is RequestStatus.Success } as RequestStatus.Success
        assertEquals(null, cur.data)
    }
}
