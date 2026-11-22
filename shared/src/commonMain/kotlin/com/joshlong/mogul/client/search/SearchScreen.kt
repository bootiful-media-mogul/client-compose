package com.joshlong.mogul.client.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.api.SearchableResult
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.nav.Router
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.TextSize
import com.joshlong.mogul.client.theme.fonts
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.ui.FormSection
import com.joshlong.mogul.client.ui.Icon
import com.joshlong.mogul.client.ui.IconButton
import com.joshlong.mogul.client.ui.MogulIcon
import com.joshlong.mogul.client.ui.dateTimeToString
import kotlinx.coroutines.delay

/** the search results page (SearchView.vue) */
@Composable
fun SearchScreen(query: String?) {
    var results by remember { mutableStateOf<List<SearchableResult>?>(null) }
    val term = query ?: ""
    LaunchedEffect(term) {
        results = runCatching { Mogul.search.search(term) }.getOrElse { emptyList() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Gutter.full)) {
        Text(t("search.results.title"), style = MaterialTheme.typography.headlineMedium, color = palette.ink)
        val found = results
        FormSection(
            if (found != null) t("search.results.prompt", "count" to found.size, "term" to term) else t("search.no-results.prompt"),
        ) {
            found?.forEach { SearchResultRow(it) }
        }
    }
}

/** one result (Result.vue): its kind as a watermark, the title, and a link to edit it */
@Composable
private fun SearchResultRow(result: SearchableResult) {
    val title = result.title ?: ""
    Row(
        Modifier.fillMaxWidth().padding(vertical = Gutter.half),
        horizontalArrangement = Arrangement.spacedBy(Gutter.half),
    ) {
        Icon(iconForResultType(result.type), null, size = 36.dp, tint = palette.muted.copy(alpha = 0.5f))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Gutter.half)) {
            Text(title, color = palette.ink)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gutter.half)) {
                IconButton(MogulIcon.Edit, t("icons.edit", "name" to title), onClick = { Router.push(destinationFor(result)) })
                Text(
                    dateTimeToString(result.created) ?: "",
                    Modifier.weight(1f),
                    color = palette.ink,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
                Box(Modifier.heightIn(min = 18.dp).padding(horizontal = 2.dp).background(palette.rule).widthIn(min = 1.dp, max = 1.dp))
                Text("#${result.aggregateId}", Modifier.weight(1f), color = palette.ink)
            }
        }
    }
    Box(Modifier.fillMaxWidth().heightIn(min = 1.dp, max = 1.dp).background(palette.rule))
}

/**
 * search, from anywhere (SearchPalette.vue): a dialog over everything. the list is a
 * combobox's: focus stays in the field, the arrows move the active option, enter chooses it,
 * escape (or a click outside) closes it, and it opens fresh each time.
 */
@Composable
fun SearchPalette(open: Boolean, onDismiss: () -> Unit) {
    if (!open) return
    var term by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(0) }
    var results by remember { mutableStateOf<List<SearchableResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // a new term restarts the wait; only the latest search's answer counts, since a newer
    // term cancels the effect that would have delivered the older answer
    LaunchedEffect(term) {
        active = 0
        val q = term.trim()
        if (q.length < PALETTE_MINIMUM_TERM_LENGTH) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(PALETTE_DEBOUNCE_MILLIS)
        val found = runCatching { Mogul.search.search(q) }.getOrElse {
            println("search failed: ${it.message}")
            emptyList()
        }
        results = found
        searching = false
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val groups = paletteGroups(term, results)
    val options = groups.flatMap { it.options }

    fun choose(option: PaletteOption) {
        val destination = option.destination()
        onDismiss()
        Router.push(destination)
    }

    // keep the active option in view as the arrows walk past the edge of the list
    LaunchedEffect(active, options.size) {
        var row = 0
        for (group in groups) {
            row++ // the group's label
            val at = group.options.indexOfFirst { it === options.getOrNull(active) }
            if (at >= 0) {
                listState.animateScrollToItem(maxOf(0, row + at - 1))
                break
            }
            row += group.options.size
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .padding(top = 96.dp, start = Gutter.full, end = Gutter.full)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.raised)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionDown -> {
                                active = movePaletteSelection(active, 1, options.size)
                                true
                            }
                            Key.DirectionUp -> {
                                active = movePaletteSelection(active, -1, options.size)
                                true
                            }
                            Key.Enter, Key.NumPadEnter -> {
                                options.getOrNull(active)?.let { choose(it) }
                                true
                            }
                            Key.Escape -> {
                                onDismiss()
                                true
                            }
                            else -> false
                        }
                    },
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Gutter.full).heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Gutter.full),
                ) {
                    Icon(MogulIcon.Search, null, size = 20.dp, tint = palette.muted)
                    Box(Modifier.weight(1f)) {
                        BasicTextField(
                            value = term,
                            onValueChange = { term = it },
                            singleLine = true,
                            cursorBrush = SolidColor(palette.ink),
                            textStyle = TextStyle(fontFamily = fonts.body, fontSize = TextSize.lg, color = palette.ink),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                        if (term.isEmpty()) Text(t("search.palette.placeholder"), color = palette.muted, fontSize = TextSize.lg)
                    }
                    KeyCap("esc")
                }
                Box(Modifier.fillMaxWidth().heightIn(min = 1.dp, max = 1.dp).background(palette.rule))
                if (options.isNotEmpty()) {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 384.dp).padding(Gutter.half), state = listState) {
                        groups.forEach { group ->
                            item(key = "group-${group.key}") {
                                Text(
                                    group.label.uppercase(),
                                    Modifier.padding(start = Gutter.half, top = Gutter.half, bottom = Gutter.third),
                                    color = palette.muted,
                                    fontSize = TextSize.xs,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.sp,
                                )
                            }
                            itemsIndexed(group.options, key = { _, o -> o.key }) { _, option ->
                                val index = options.indexOf(option)
                                PaletteRow(option, selected = index == active, onHover = { active = index }, onClick = { choose(option) })
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().heightIn(min = 1.dp, max = 1.dp).background(palette.rule))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Gutter.full, vertical = Gutter.half),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Gutter.full),
                ) {
                    Text(paletteStatus(term, searching, results), Modifier.weight(1f), color = palette.muted, fontSize = TextSize.xs)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        KeyCap("↑")
                        KeyCap("↓")
                        Text(t("search.palette.move"), color = palette.muted, fontSize = TextSize.xs)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        KeyCap("↵")
                        Text(t("search.palette.choose"), color = palette.muted, fontSize = TextSize.xs)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        KeyCap("esc")
                        Text(t("search.palette.close"), color = palette.muted, fontSize = TextSize.xs)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PaletteRow(option: PaletteOption, selected: Boolean, onHover: () -> Unit, onClick: () -> Unit) {
    val ink = if (selected) palette.accentInk else palette.ink
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Gutter.radius))
            .background(if (selected) palette.accent else palette.raised)
            .onPointerEvent(PointerEventType.Move) { onHover() }
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(Gutter.half),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Gutter.full),
    ) {
        Icon(option.icon, null, size = 18.dp, tint = if (selected) ink else palette.muted)
        Text(option.label, Modifier.weight(1f), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        option.hint?.let { Text(it, color = if (selected) ink else palette.muted, fontSize = TextSize.xs) }
    }
}

@Composable
private fun KeyCap(text: String) {
    Text(
        text,
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, palette.rule, RoundedCornerShape(4.dp))
            .background(palette.hoverTint)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        color = palette.muted,
        fontFamily = fonts.mono,
        fontSize = TextSize.xs,
    )
}
