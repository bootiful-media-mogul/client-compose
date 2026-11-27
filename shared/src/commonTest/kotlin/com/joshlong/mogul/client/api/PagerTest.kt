package com.joshlong.mogul.client.api

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PagerTest {

    /** the server: rows newest first, and a record of what was asked of it */
    private class Rows(var rows: List<Int>) {
        val requests = mutableListOf<Pair<Int, Int>>()
        var failing = false

        fun page(offset: Int, limit: Int): List<Int> {
            this.requests += offset to limit
            if (this.failing) error("no network")
            return this.rows.drop(offset).take(limit)
        }
    }

    private fun pager(rows: Rows, pageSize: Int = 3) = Pager<Int>(pageSize, { it }) { offset, limit -> rows.page(offset, limit) }

    /** runs a suspending block that never really suspends, which none of these fetches do */
    private fun <T> now(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(Continuation(EmptyCoroutineContext) { result = it })
        return (result ?: error("the block suspended")).getOrThrow()
    }

    @Test
    fun aPageIsAppendedAndOneMoreRowIsAskedFor() {
        val rows = Rows((1..10).toList())
        val pager = pager(rows)
        now { pager.loadMore() }
        assertEquals(listOf(1, 2, 3), pager.items)
        assertEquals(listOf(0 to 4), rows.requests)
        assertTrue(pager.hasMore)
        now { pager.loadMore() }
        assertEquals(listOf(1, 2, 3, 4, 5, 6), pager.items)
        assertEquals(3 to 4, rows.requests.last())
        assertFalse(pager.loading)
    }

    @Test
    fun aShortPageIsTheEnd() {
        val rows = Rows((1..5).toList())
        val pager = pager(rows)
        now { pager.loadMore() }
        now { pager.loadMore() }
        assertEquals((1..5).toList(), pager.items)
        assertFalse(pager.hasMore)
        now { pager.loadMore() }
        assertEquals(2, rows.requests.size)
    }

    @Test
    fun anExactMultipleEndsWithoutAnEmptyRequest() {
        val rows = Rows((1..6).toList())
        val pager = pager(rows)
        now { pager.loadMore() }
        assertTrue(pager.hasMore)
        now { pager.loadMore() }
        assertEquals((1..6).toList(), pager.items)
        assertFalse(pager.hasMore)
        now { pager.loadMore() }
        assertEquals(2, rows.requests.size)
    }

    @Test
    fun anEmptyListIsTheEnd() {
        val rows = Rows(emptyList())
        val pager = pager(rows)
        now { pager.loadMore() }
        assertEquals(emptyList(), pager.items)
        assertFalse(pager.hasMore)
        assertNull(pager.error)
    }

    @Test
    fun aFailureKeepsTheRowsAndIsRetriedFromTheSameOffset() {
        val rows = Rows((1..10).toList())
        val pager = pager(rows)
        now { pager.loadMore() }
        rows.failing = true
        now { pager.loadMore() }
        assertEquals("no network", pager.error)
        assertEquals(listOf(1, 2, 3), pager.items)
        assertTrue(pager.hasMore)
        assertFalse(pager.loading)
        rows.failing = false
        now { pager.loadMore() }
        assertNull(pager.error)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), pager.items)
        assertEquals(listOf(0 to 4, 3 to 4, 3 to 4), rows.requests)
    }

    @Test
    fun aSecondLoadWhileOneIsInFlightDoesNothing() {
        var requests = 0
        var waiting: Continuation<List<Int>>? = null
        val pager = Pager<Int>(3, { it }) { _, _ ->
            requests++
            suspendCoroutine { waiting = it }
        }
        suspend { pager.loadMore() }.startCoroutine(Continuation(EmptyCoroutineContext) {})
        assertTrue(pager.loading)
        now { pager.loadMore() }
        assertEquals(1, requests)
        waiting!!.resume(listOf(1, 2))
        assertFalse(pager.loading)
        assertEquals(listOf(1, 2), pager.items)
        assertFalse(pager.hasMore)
    }

    @Test
    fun aRemovedRowLowersTheNextOffset() {
        val rows = Rows((1..10).toList())
        val pager = pager(rows)
        now { pager.loadMore() }
        // deleted on the server, and dropped here
        rows.rows = rows.rows - 2
        pager.remove(2)
        assertEquals(listOf(1, 3), pager.items)
        now { pager.loadMore() }
        assertEquals(2 to 4, rows.requests.last())
        assertEquals(listOf(1, 3, 4, 5, 6), pager.items)
    }

    @Test
    fun aRowSeenBeforeIsNotAddedTwice() {
        val rows = Rows((1..10).toList())
        val pager = pager(rows)
        now { pager.loadMore() }
        // a new row at the top pushes 3 onto the next page
        rows.rows = listOf(0) + rows.rows
        now { pager.loadMore() }
        assertEquals(listOf(1, 2, 3, 4, 5), pager.items)
        assertTrue(pager.hasMore)
    }

    @Test
    fun refreshStartsAgainFromTheFirstPage() {
        val rows = Rows((1..5).toList())
        val pager = pager(rows)
        now { pager.loadMore() }
        now { pager.loadMore() }
        rows.rows = (0..5).toList()
        now { pager.refresh() }
        assertEquals(listOf(0, 1, 2), pager.items)
        assertEquals(0 to 4, rows.requests.last())
        assertTrue(pager.hasMore)
    }
}
