package com.joshlong.mogul.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key as keyed
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.joshlong.mogul.client.api.Pager
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.palette
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** how near the page's bottom the next page is sent for */
private val loadAhead = 600.dp

/**
 * a [Pager]'s rows, and under them whatever it's up to: loading, or why it couldn't. the
 * next page is sent for whenever the page's scroll ([LocalPageScroll]) nears its bottom, so a
 * first page too short to fill the window brings the next with it.
 */
@Composable
fun <T> PagedList(pager: Pager<T>, key: (T) -> Any, row: @Composable (T) -> Unit) {
    val scroll = LocalPageScroll.current
    val scope = rememberCoroutineScope()
    val ahead = with(LocalDensity.current) { loadAhead.roundToPx() }

    LaunchedEffect(pager, scroll, ahead) {
        if (scroll == null) return@LaunchedEffect
        while (true) {
            // a failed page waits for its retry rather than being asked for over and over
            snapshotFlow { !pager.loading && pager.hasMore && pager.error == null && scroll.maxValue - scroll.value < ahead }
                .first { it }
            pager.loadMore()
            // the scroll doesn't know how much longer the new rows made the page until they're laid out
            repeat(2) { withFrameNanos { } }
        }
    }

    pager.items.forEach { item -> keyed(key(item)) { row(item) } }

    val error = pager.error
    if (error != null) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = Gutter.half),
            horizontalArrangement = Arrangement.spacedBy(Gutter.half),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(t("paging.failed", "message" to error), Modifier.weight(1f), color = palette.danger)
            MogulButton(t("paging.retry"), onClick = { scope.launch { pager.loadMore() } })
        }
    } else if (pager.loading) {
        Text(t("paging.loading"), Modifier.padding(vertical = Gutter.half), color = palette.muted)
    }
}
