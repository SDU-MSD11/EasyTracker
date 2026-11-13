package com.sdumsd11.easytracker.data.local

import com.sdumsd11.easytracker.domain.model.Expense
import com.sdumsd11.easytracker.domain.model.Group
import com.sdumsd11.easytracker.domain.model.InviteStatus
import com.sdumsd11.easytracker.domain.model.NotificationPrefs
import com.sdumsd11.easytracker.domain.model.User
import org.junit.Assert.assertEquals
import org.junit.Test

/** Round-trip guards for the U+001F / invite encodings (no TypeConverters on purpose). */
class LocalMappersTest {

    @Test
    fun `user round-trips prefs`() {
        val u = User("u1", "Ann", "a@x.y", NotificationPrefs(false, true, false))
        assertEquals(u, u.toEntity().toDomain())
    }

    @Test
    fun `group member ids survive commas in values`() {
        val g = Group("g", "G", "", "o", listOf("a,b", "c"), mapOf("a,b" to InviteStatus.PENDING))
        assertEquals(g, g.toEntity().toDomain())
    }

    @Test
    fun `group invites decode skips unknown statuses`() {
        val e = GroupEntity("g", "G", "", "o", "m1", "m1=ACCEPTED;bad=BOGUS")
        assertEquals(mapOf("m1" to InviteStatus.ACCEPTED), e.toDomain().invites)
    }

    @Test
    fun `expense splitAmong round-trips and legacy empty falls back to payer`() {
        val x = Expense("e", "g", "a", 100L, "d", listOf("a", "b"), createdAt = 7L)
        assertEquals(x, x.toEntity().toDomain())
        val legacy = ExpenseEntity("e", "g", "a", 100L, "d", "", null, 7L)
        assertEquals(listOf("a"), legacy.toDomain().splitAmong)
    }

    @Test
    fun `expense settlement flag round-trips`() {
        val x = Expense("e", "g", "a", 100L, "Settlement", listOf("b"), isSettlement = true)
        assertEquals(x, x.toEntity().toDomain())
    }

    @Test
    fun `empty member list encodes to empty`() {
        val g = Group("g", "G", "", "o")
        assertEquals(emptyList<String>(), g.toEntity().toDomain().memberIds)
    }
}
