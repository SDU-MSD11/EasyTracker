package com.sdumsd11.easytracker.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [displayGroupLabel] (ship-blocker 1): resolved names win; a raw UUID never
 * leaks as the primary line — unknown groups fall back to a short ref, and
 * unknown-everything yields null so callers omit the line.
 */
class GroupLabelTest {

    @Test
    fun `resolved name wins over id`() {
        assertEquals("Flatmates", displayGroupLabel("550e8400-e29b-41d4-a716-446655440000", "Flatmates"))
    }

    @Test
    fun `uuid falls back to short ref`() {
        assertEquals(
            "Group ••••0000",
            displayGroupLabel("550e8400-e29b-41d4-a716-446655440000", null),
        )
    }

    @Test
    fun `short ids pass through`() {
        assertEquals("g1", displayGroupLabel("g1", null))
    }

    @Test
    fun `blank id and name yields null`() {
        assertNull(displayGroupLabel("", null))
        assertNull(displayGroupLabel("", "  "))
    }

    @Test
    fun `blank name falls back to id`() {
        assertEquals("Group ••••zzzz", displayGroupLabel("xxxxxxxx-yyyy-zzzz", "  "))
    }
}
