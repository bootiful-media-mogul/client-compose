package com.joshlong.mogul.client.api

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.coroutines.cancellation.CancellationException

/**
 * a list that arrives a page at a time. [fetch] answers the rows from an offset, newest
 * first; it's asked for one more than a page, and that extra row, never shown, is how the
 * pager knows there's more to come without the server having to count.
 */
class Pager<T>(
    private val pageSize: Int = 50,
    private val key: (T) -> Any,
    private val fetch: suspend (offset: Int, limit: Int) -> List<T>,
) {
    var items: List<T> by mutableStateOf(emptyList())
        private set
    var loading: Boolean by mutableStateOf(false)
        private set
    var hasMore: Boolean by mutableStateOf(true)
        private set
    var error: String? by mutableStateOf(null)
        private set

    // a refresh abandons whatever page was in flight
    private var generation = 0

    /** loads the next page; does nothing while one is loading or when there are no more */
    suspend fun loadMore() {
        if (this.loading || !this.hasMore) return
        val generation = this.generation
        this.loading = true
        this.error = null
        try {
            val page = this.fetch(this.items.size, this.pageSize + 1)
            if (generation != this.generation) return
            // a row created elsewhere pushes the rest down one, so a page can repeat a row
            val known = this.items.mapTo(HashSet()) { this.key(it) }
            val fresh = page.take(this.pageSize).filter { known.add(this.key(it)) }
            this.items = this.items + fresh
            // a page of nothing new would be asked for again and again
            this.hasMore = page.size > this.pageSize && fresh.isNotEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (generation == this.generation) this.error = e.message ?: e.toString()
        } finally {
            if (generation == this.generation) this.loading = false
        }
    }

    /** forgets everything and loads the first page again */
    suspend fun refresh() {
        this.generation++
        this.items = emptyList()
        this.hasMore = true
        this.error = null
        this.loading = false
        this.loadMore()
    }

    /** drops a row that's gone from the server too, so the next offset still lines up */
    fun remove(key: Any) {
        this.items = this.items.filterNot { this.key(it) == key }
    }
}
