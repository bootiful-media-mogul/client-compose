package com.joshlong.mogul.client.publications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.joshlong.mogul.client.api.AyrsharePublicationComposition
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.api.json
import com.joshlong.mogul.client.compositions.CompositionPanel
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.i18n.tc
import com.joshlong.mogul.client.platform.browser
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.TextSize
import com.joshlong.mogul.client.theme.fonts
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.ui.FloatingMenu
import com.joshlong.mogul.client.ui.ListenForNotification
import com.joshlong.mogul.client.ui.MogulButton
import com.joshlong.mogul.client.ui.MogulIcon
import com.joshlong.mogul.client.ui.MogulTextField
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

// the shared text's row. no platform is called this
private const val SHARED = "*"

/**
 * the Ayrshare composer: a list of the picked platforms down the left, and the text for
 * whichever row is open on the right. one shared text goes everywhere, unless a platform has
 * been given its own; each platform has its own media, in its draft composition.
 */
internal class AyrsharePlugin(scope: PublicationScope, name: String, private val readyEventCategory: String? = null) :
    PublicationPlugin(scope, name, PluginIcon.Svg(MogulIcon.Ayrshare)) {

    private var publishing by mutableStateOf(false)

    private var pluginReady by mutableStateOf(false)

    private var compositions by mutableStateOf<List<AyrsharePublicationComposition>>(emptyList())

    private var selected by mutableStateOf<List<String>>(emptyList())

    private var shared by mutableStateOf("")

    private val overrides = mutableStateMapOf<String, String>()

    /** the row whose text is in the editor */
    private var open by mutableStateOf(SHARED)

    val draft: AyrshareDraft
        get() = AyrshareDraft(
            shared = this.shared,
            overrides = this.overrides.toMap(),
            // in the order the platforms are listed, not the order they were ticked
            selected = this.compositions.map { it.platform }.filter { it in this.selected },
        )

    override val enabled: Boolean get() = !this.publishing && this.pluginReady && isPublishable(this.draft)

    private fun key(key: String) = "publications.plugins.${this.name}.composer.$key"

    private fun ct(key: String, vararg args: Pair<String, Any?>) = t(key(key), *args)

    private fun platformLabel(platform: String) = t("publications.plugins.${this.name}.platforms.$platform.label")

    private fun platformShortLabel(platform: String) = t("publications.plugins.${this.name}.platforms.$platform.shortLabel")

    private fun formattedLimit(platform: String) = AYRSHARE_PLATFORM_LIMITS[platform]?.let { formatNumber(it) } ?: ""

    // the ticked platforms are remembered in the browser, per plugin, and ticked again the
    // next time the composer starts
    private val selectedStorageKey = "ayrshare-selected-platforms-$name"

    private fun saveSelectedPlatforms(platforms: List<String>) =
        browser.localSet(this.selectedStorageKey, JsonArray(platforms.map { JsonPrimitive(it) }).toString())

    private fun restoreSelectedPlatforms() {
        val stored = runCatching {
            (json.parseToJsonElement(browser.localGet(this.selectedStorageKey) ?: "[]") as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        }.getOrNull() ?: return
        val known = this.compositions.map { it.platform }
        this.selected = stored.filter { it in known }
    }

    private suspend fun setSelected(platforms: List<String>) {
        this.selected = platforms
        // a platform that's unticked while it's open hands the editor back to the shared text
        if (this.open != SHARED && this.open !in platforms) this.open = SHARED
        saveSelectedPlatforms(platforms)
        this.pluginReady = isPluginReady()
    }

    // the first edit on a platform's row gives that platform text of its own. an "edit" that
    // changes nothing doesn't
    private fun setPlatformText(platform: String, value: String) {
        if (isCustomized(this.draft, platform) || value != this.shared) this.overrides[platform] = value
    }

    private fun clearDraft() {
        this.shared = ""
        this.overrides.clear()
        this.open = SHARED
    }

    private suspend fun isPluginReady(): Boolean = this.scope.isPluginReady(emptyMap(), this.name)

    // publishing to N platforms sends N completion notifications, and every one of them asks
    // us to reload. they'd all ask for the same thing, so ride along on the one in flight
    private var refreshing: CompletableDeferred<Unit>? = null

    // the drafts' compositions (one per platform, holding its media) are replaced once a post
    // goes out, so the ids are read afresh; what's been written stays put
    private suspend fun refresh() {
        this.refreshing?.let { return it.await() }
        val deferred = CompletableDeferred<Unit>()
        this.refreshing = deferred
        try {
            this.compositions = runCatching { Mogul.ayrshare.publicationCompositions() }.getOrDefault(this.compositions)
        } finally {
            this.refreshing = null
            deferred.complete(Unit)
        }
    }

    override suspend fun publish(scheduled: Instant?) {
        this.publishing = true
        try {
            this.scope.publish(buildClientContext(this.draft, this.compositions), this.name, scheduled)
            // a scheduled post is as done as it's going to be for days, so the composer starts
            // over now. left filled in, it looks like the schedule never happened
            if (scheduled != null) clearDraft()
        } catch (e: Exception) {
            this.publishing = false
            throw e
        } finally {
            // a post for right now holds the panel until ayrshare says it's out
            if (scheduled != null) this.publishing = false
        }
    }

    @Composable
    override fun Effects() {
        val coroutines = rememberCoroutineScope()
        LaunchedEffect(this) {
            refresh()
            restoreSelectedPlatforms()
            this@AyrsharePlugin.pluginReady = isPluginReady()
        }
        this.readyEventCategory?.let { category ->
            ListenForNotification(category) { coroutines.launch { this@AyrsharePlugin.pluginReady = isPluginReady() } }
        }
        // a file finished uploading into one of the drafts' attachments
        ListenForNotification("attachment-managed-file-updated-event") { coroutines.launch { refresh() } }
        ListenForNotification("ayrshare-publication-completion-event") {
            // the post that just went out is done with, so the composer starts over. a
            // scheduled one going out days later leaves alone whatever is being written by then
            val wasPublishing = this.publishing
            this.publishing = false
            coroutines.launch {
                refresh()
                if (wasPublishing) clearDraft()
            }
        }
    }

    @Composable
    override fun Panel() {
        val coroutines = rememberCoroutineScope()
        val draft = this.draft
        Column(Modifier.fillMaxWidth().padding(vertical = Gutter.full)) {
            PlatformPicker(draft) { platforms -> coroutines.launch { setSelected(platforms) } }
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = Gutter.full)) {
                // too narrow for two columns: the list goes on top and the editor under it
                if (maxWidth < 640.dp) {
                    Column {
                        PlatformList(draft, Modifier.fillMaxWidth(), wide = false)
                        Box(Modifier.padding(top = Gutter.half)) { Editor(draft, joinsTop = false) }
                    }
                } else {
                    Row {
                        PlatformList(draft, Modifier.width(240.dp), wide = true)
                        Box(Modifier.weight(1f)) { Editor(draft, joinsTop = this@AyrsharePlugin.open == SHARED) }
                    }
                }
            }
        }
    }

    @Composable
    private fun Badge(platform: String?, modifier: Modifier = Modifier) {
        val badge = platform?.let { badgeFor(it) }
        Box(
            modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(badge?.let { Color(0xFF000000 or it.color) } ?: palette.chrome),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                badge?.initials ?: "*",
                color = badge?.let { Color(0xFF000000 or it.ink) } ?: palette.chromeInk,
                fontFamily = fonts.display,
                fontWeight = FontWeight.Bold,
                fontSize = if (badge == null) TextSize.base else TextSize.xs,
            )
        }
    }

    /** choosing the platforms: a dropdown of checkboxes, and the badges of what's picked beside it */
    @Composable
    private fun PlatformPicker(draft: AyrshareDraft, onChange: (List<String>) -> Unit) {
        var menuOpen by remember { mutableStateOf(false) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Gutter.half), verticalArrangement = Arrangement.spacedBy(Gutter.half)) {
            Text(ct("platforms"), Modifier.align(Alignment.CenterVertically), color = palette.ink, fontWeight = FontWeight.Bold)
            Box {
                MogulButton(
                    ct(if (draft.selected.isNotEmpty()) "change" else "choose") + " ▾",
                    onClick = { menuOpen = true },
                    enabled = !this@AyrsharePlugin.publishing,
                )
                FloatingMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    Column(Modifier.widthIn(min = 288.dp).padding(horizontal = Gutter.half)) {
                        this@AyrsharePlugin.compositions.forEach { c ->
                            val checked = c.platform in this@AyrsharePlugin.selected
                            val toggle = {
                                val current = this@AyrsharePlugin.selected
                                onChange(if (checked) current - c.platform else current + c.platform)
                            }
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).clickable { toggle() }.padding(end = Gutter.half),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Gutter.half),
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { toggle() },
                                    colors = CheckboxDefaults.colors(checkedColor = palette.accent, uncheckedColor = palette.fieldBorder, checkmarkColor = palette.accentInk),
                                )
                                Badge(c.platform)
                                Text(platformShortLabel(c.platform), Modifier.weight(1f), color = palette.ink)
                                Text(ct("limitShort", "num" to formattedLimit(c.platform)), color = palette.muted, fontFamily = fonts.mono, fontSize = TextSize.xs)
                            }
                        }
                    }
                }
            }
            draft.selected.forEach { Badge(it, Modifier.align(Alignment.CenterVertically)) }
        }
    }

    private data class RowStatus(val kind: String, val text: String)

    // what a ticked platform's row says about its text. a platform still on the shared text
    // isn't flagged for being empty: the shared text is where the fix goes anyway
    private fun rowStatus(draft: AyrshareDraft, platform: String): RowStatus {
        val own = isCustomized(draft, platform)
        if (own && isBlank(draft, platform)) return RowStatus("problem", ct("rowEmpty"))
        val over = charactersOver(draft, platform)
        if (over > 0) return RowStatus("problem", ct("rowOver", "num" to formatNumber(over)))
        if (own) return RowStatus("own", ct("rowOwn"))
        return RowStatus("shared", ct("rowShared", "shared" to ct("shared")))
    }

    private fun attachmentCount(platform: String): Int =
        this.compositions.firstOrNull { it.platform == platform }?.composition?.attachments?.size ?: 0

    @Composable
    private fun PlatformList(draft: AyrshareDraft, modifier: Modifier, wide: Boolean) {
        val coroutines = rememberCoroutineScope()
        val shape = if (wide) RoundedCornerShape(topStart = Gutter.radius, bottomStart = Gutter.radius) else RoundedCornerShape(Gutter.radius)
        Column(modifier.padding(bottom = Gutter.radius)) {
            @Composable
            fun PlatformRow(row: String, content: @Composable () -> Unit) {
                val isOpen = this@AyrsharePlugin.open == row
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(if (isOpen) palette.raised else Color.Transparent)
                        .clickable {
                            // the drafts are read again on the way out of a platform's row, so
                            // it shows any attachments it was just given
                            if (this@AyrsharePlugin.open != SHARED && this@AyrsharePlugin.open != row) coroutines.launch { refresh() }
                            this@AyrsharePlugin.open = row
                        }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = Gutter.half, vertical = Gutter.third),
                    horizontalArrangement = Arrangement.spacedBy(Gutter.half),
                ) {
                    Badge(if (row == SHARED) null else row)
                    Column(Modifier.weight(1f)) { content() }
                }
            }
            PlatformRow(SHARED) {
                Text(ct("shared"), color = palette.ink, fontWeight = if (this@AyrsharePlugin.open == SHARED) FontWeight.Bold else FontWeight.Medium)
                Text(
                    if (draft.selected.isNotEmpty()) tc(key("sharedStatus"), draft.selected.size) else ct("pickPlatforms"),
                    color = palette.muted,
                    fontSize = TextSize.xs,
                )
            }
            draft.selected.forEach { p ->
                PlatformRow(p) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            platformShortLabel(p),
                            Modifier.weight(1f),
                            color = palette.ink,
                            fontWeight = if (this@AyrsharePlugin.open == p) FontWeight.Bold else FontWeight.Medium,
                        )
                        Text(ct("limitShort", "num" to formattedLimit(p)), color = palette.muted, fontFamily = fonts.mono, fontSize = TextSize.xs)
                    }
                    val status = rowStatus(draft, p)
                    val count = attachmentCount(p)
                    Text(
                        buildAnnotatedString {
                            val style = when (status.kind) {
                                "problem" -> SpanStyle(color = palette.danger, fontWeight = FontWeight.SemiBold)
                                "own" -> SpanStyle(color = palette.accent, fontWeight = FontWeight.SemiBold)
                                else -> SpanStyle(color = palette.muted)
                            }
                            withStyle(style) { append(status.text) }
                            if (count > 0) withStyle(SpanStyle(color = palette.muted)) { append(" · " + tc(key("attachments"), count)) }
                        },
                        fontSize = TextSize.xs,
                    )
                }
            }
        }
    }

    @Composable
    private fun Editor(draft: AyrshareDraft, joinsTop: Boolean) {
        val open = this.open
        val shape = if (joinsTop) RoundedCornerShape(topEnd = Gutter.radius, bottomStart = Gutter.radius, bottomEnd = Gutter.radius) else RoundedCornerShape(Gutter.radius)
        Column(
            Modifier.fillMaxWidth().clip(shape).background(palette.raised).padding(start = Gutter.full, end = Gutter.full, top = Gutter.full),
            verticalArrangement = Arrangement.spacedBy(Gutter.half),
        ) {
            if (open == SHARED) {
                MogulTextField(
                    value = draft.shared,
                    onValueChange = { this@AyrsharePlugin.shared = it },
                    label = ct("sharedLabel"),
                    singleLine = false,
                    minLines = 8,
                    enabled = !this@AyrsharePlugin.publishing,
                    showCount = true,
                )
                Column(Modifier.padding(bottom = Gutter.full), verticalArrangement = Arrangement.spacedBy(Gutter.third)) {
                    val sharedPlatforms = draft.selected.filter { !isCustomized(draft, it) }
                    when {
                        sharedPlatforms.isNotEmpty() -> Text(usedBy(draft, sharedPlatforms), color = palette.muted, fontSize = TextSize.sm)
                        draft.selected.isNotEmpty() -> Note(ct("usedByNone"))
                        else -> Note(ct("pickPlatforms"))
                    }
                    Note(ct("media"))
                }
            } else {
                // one platform. it shows the shared text until it's edited; the first edit gives
                // the platform text of its own
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gutter.full)) {
                    Text(
                        if (isCustomized(draft, open)) ct("customized", "platform" to platformShortLabel(open))
                        else ct("inherited", "platform" to platformShortLabel(open), "shared" to ct("shared")),
                        Modifier.weight(1f),
                        color = palette.ink,
                    )
                    if (isCustomized(draft, open)) {
                        MogulButton(
                            ct("useShared", "shared" to ct("shared")),
                            onClick = { this@AyrsharePlugin.overrides.remove(open) },
                            enabled = !this@AyrsharePlugin.publishing,
                        )
                    }
                }
                MogulTextField(
                    value = effectiveText(draft, open),
                    onValueChange = { setPlatformText(open, it) },
                    label = ct("platformLabel", "platform" to platformLabel(open)),
                    singleLine = false,
                    minLines = 8,
                    enabled = !this@AyrsharePlugin.publishing,
                    showCount = true,
                )
                this@AyrsharePlugin.compositions.firstOrNull { it.platform == open }?.let { c ->
                    CompositionPanel(c.composition.id)
                }
                Column(Modifier.padding(bottom = Gutter.full)) {
                    val over = charactersOver(draft, open)
                    if (over > 0) {
                        Text(
                            ct("over", "num" to formatNumber(over), "platform" to platformShortLabel(open)),
                            color = palette.danger,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = TextSize.sm,
                        )
                    } else if (formattedLimit(open).isNotEmpty()) {
                        Note(ct("limit", "num" to formattedLimit(open)))
                    }
                }
            }
        }
    }

    @Composable
    private fun Note(text: String) = Text(text, color = palette.muted, fontSize = TextSize.sm)

    // "Goes to {platforms}.", with the platforms over their limit called out
    @Composable
    private fun usedBy(draft: AyrshareDraft, platforms: List<String>) = buildAnnotatedString {
        val template = t(key("usedBy"))
        val at = template.indexOf("{platforms}")
        append(if (at >= 0) template.substring(0, at) else template)
        if (at >= 0) {
            platforms.forEachIndexed { i, p ->
                if (i > 0) append(", ")
                val over = charactersOver(draft, p) > 0
                if (over) {
                    withStyle(SpanStyle(color = palette.danger, fontWeight = FontWeight.SemiBold)) {
                        append(platformShortLabel(p))
                        append(" (" + ct("overShort") + ")")
                    }
                } else {
                    append(platformShortLabel(p))
                }
            }
            append(template.substring(at + "{platforms}".length))
        }
    }
}
