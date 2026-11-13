package com.sdumsd11.easytracker.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the backend JSON fold: email->contact, split fallback, invite filtering. */
class RemoteMappersTest {

    @Test
    fun `user contact falls back to email`() {
        assertEquals("e@x.y", UserDto("u", "N", email = "e@x.y").toDomain().contact)
        assertEquals("c", UserDto("u", "N", email = "e@x.y", contact = "c").toDomain().contact)
    }

    @Test
    fun `group invites skip unknown statuses`() {
        val dto = GroupDto("g", "G", ownerId = "o", invites = listOf(InviteDto("m1", "ACCEPTED"), InviteDto("m2", "BOGUS")))
        assertEquals(mapOf("m1" to com.sdumsd11.easytracker.domain.model.InviteStatus.ACCEPTED), dto.toDomain().invites)
    }

    @Test
    fun `expense empty split falls back to payer`() {
        val dto = ExpenseDto("e", "g", "a", 100L, "d", emptyList(), createdAt = "2026-01-01T00:00:00Z")
        val d = dto.toDomain()
        assertEquals(listOf("a"), d.splitAmong)
        assertTrue(d.createdAt > 0L)
    }

    @Test
    fun `toEpochMillis parses Z and garbage`() {
        assertTrue("2026-01-01T00:00:00Z".toEpochMillis() > 0L)
        assertEquals(0L, "not-a-date".toEpochMillis())
        assertEquals(0L, null.toEpochMillis())
    }

    @Test
    fun `expense settlement kind maps from wire and defaults to debt`() {
        val pay = ExpenseDto("p", "g", "a", 500L, "Settlement", listOf("b"), kind = "settlement")
        assertTrue(pay.toDomain().isSettlement)
        // Old servers / cached rows without kind stay debts.
        val debt = ExpenseDto("e", "g", "a", 100L, "d", listOf("a", "b"), kind = "expense")
        assertTrue(!debt.toDomain().isSettlement)
        val legacy = ExpenseDto("l", "g", "a", 100L, "d", listOf("a", "b"))
        assertTrue(!legacy.toDomain().isSettlement)
    }

    @Test
    fun `summary sorts balances descending`() {
        val s = SummaryDto(mapOf("a" to -5L, "b" to 10L), totalCents = 5L).toDomain()
        assertEquals(listOf("b", "a"), s.balances.map { it.userId })
        assertEquals(5L, s.totalCents)
    }
}
