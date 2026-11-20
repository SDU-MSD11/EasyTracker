package com.sdumsd11.easytracker.navigation

import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.domain.model.User
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Splash-gate regression test (startup stall): routing is session-driven with a
 * bounded fallback — a settled session routes by user (user -> Groups, null -> Login)
 * and a session that never settles still routes to Login via timeout instead of
 * spinning on Splash forever. Pure JVM: the gate is a suspend helper over flows,
 * so runBlocking suffices (no coroutines-test/Main-dispatcher harness in deps).
 */
class SplashGateTest {

    private val user = User(id = "u1", name = "Ada", contact = "ada@x.test")

    @Test
    fun `settled user routes to Groups`() = runBlocking {
        val dest = awaitSplashDestination(
            sessions = flowOf<RequestStatus<User?>>(RequestStatus.Success(user)),
            startedMs = 0L,
            timeoutMs = 1_000L,
            minDwellMs = 0L,
        )
        assertEquals(Routes.groups(), dest)
    }

    @Test
    fun `settled null routes to Login`() = runBlocking {
        val dest = awaitSplashDestination(
            sessions = flowOf<RequestStatus<User?>>(RequestStatus.Success(null)),
            startedMs = 0L,
            timeoutMs = 1_000L,
            minDwellMs = 0L,
        )
        assertEquals(Routes.Login, dest)
    }

    @Test
    fun `late settle wins before the timeout`() = runBlocking {
        val sessions = MutableStateFlow<RequestStatus<User?>>(RequestStatus.Pending)
        async {
            delay(50)
            sessions.value = RequestStatus.Success(user)
        }
        val dest = awaitSplashDestination(
            sessions = sessions,
            startedMs = 0L,
            timeoutMs = 5_000L,
            minDwellMs = 0L,
        )
        assertEquals(Routes.groups(), dest)
    }

    @Test
    fun `never settles falls back to Login via timeout`() = runBlocking {
        val start = System.currentTimeMillis()
        val dest = awaitSplashDestination(
            sessions = MutableStateFlow<RequestStatus<User?>>(RequestStatus.Pending),
            startedMs = 0L,
            timeoutMs = 100L,
            minDwellMs = 0L,
        )
        assertEquals(Routes.Login, dest)
        assertTrue(System.currentTimeMillis() - start < 5_000L)
    }

    @Test
    fun `branding dwell is enforced even when session is already settled`() = runBlocking {
        val start = System.currentTimeMillis()
        val dest = awaitSplashDestination(
            sessions = flowOf<RequestStatus<User?>>(RequestStatus.Success(null)),
            startedMs = start,
            timeoutMs = 1_000L,
            minDwellMs = 300L,
        )
        assertEquals(Routes.Login, dest)
        assertTrue(System.currentTimeMillis() - start >= 300L)
    }

    @Test
    fun `splash on top still routes despite stray duplicate entry`() {
        // Regression pin for the cold-start stall: the old `size > 1 -> SKIP` wedged
        // whenever the stack held 2 entries with Splash on top. The guard keys on
        // the current destination, not the stack size, so this must route.
        assertTrue(shouldRouteFromSplash(Routes.Splash, alreadyRouted = false))
    }

    @Test
    fun `handled deep link already elsewhere never reroutes`() {
        assertFalse(shouldRouteFromSplash(Routes.groups(), alreadyRouted = false))
        assertFalse(shouldRouteFromSplash(null, alreadyRouted = false))
    }

    @Test
    fun `exactly-once guard blocks double navigation`() {
        assertFalse(shouldRouteFromSplash(Routes.Splash, alreadyRouted = true))
    }
}
