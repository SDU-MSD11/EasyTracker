package com.sdumsd11.easytracker.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks the MVVM reliability contract: ops never throw, failures surface as Failed. */
class RunRequestTest {

    private val vm = object : BaseViewModel() {
        suspend fun <T> run(block: suspend () -> T): RequestStatus<T> = runRequest(block)
    }

    @Test
    fun `success passes data through`() = runBlocking {
        assertEquals(RequestStatus.Success(42), vm.run { 42 })
    }

    @Test
    fun `exception maps to Failed with message`() = runBlocking {
        val r = vm.run<String> { throw IllegalStateException("nope") }
        assertTrue(r is RequestStatus.Failed)
        assertEquals("nope", (r as RequestStatus.Failed).message)
    }

    @Test
    fun `require failures surface as Failed not crash`() = runBlocking {
        val r = vm.run { require(false) { "bad arg" } }
        assertTrue(r is RequestStatus.Failed)
        assertEquals("bad arg", (r as RequestStatus.Failed).message)
    }
}
