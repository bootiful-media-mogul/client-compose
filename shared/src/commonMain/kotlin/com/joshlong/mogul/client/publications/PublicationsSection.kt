package com.joshlong.mogul.client.publications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.api.Publication
import com.joshlong.mogul.client.api.toJson
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.ui.ListenForNotification
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlin.time.Instant

private val tileSize = 50.dp

/**
 * the publications of a publishable, and the panels that publish it: a row of plugin tiles,
 * each opening its panel beneath, then the publications so far. which plugins are offered
 * depends on the publishable's type (post, episode, mogulStatus), as the Vue editors chose.
 * showExistingPublications is false where something else on the page already lists them
 * (the home page); scheduled is when the publish buttons send a publication, null for now.
 */
@Composable
fun PublicationsSection(
    publishableType: String,
    publishableId: Long,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showExistingPublications: Boolean = true,
    scheduled: Instant? = null,
) {
    val coroutines = rememberCoroutineScope()
    var existing by remember(publishableType, publishableId) { mutableStateOf<List<Publication>>(emptyList()) }
    var startedIds by remember(publishableType, publishableId) { mutableStateOf<Set<Long>>(emptySet()) }

    /**
     * the callers of publishAndAwait, keyed by the publication they're waiting on. the publish
     * mutation only queues the work; the completed-notification is what says it actually
     * happened, and its key is the publication id -- the only way to tell our own publication
     * apart from one another tab, or another plugin, just finished.
     */
    val pending = remember(publishableType, publishableId) { mutableMapOf<Long, CompletableDeferred<Publication?>>() }

    suspend fun refresh() {
        existing = runCatching { Mogul.publications.publications(publishableId, publishableType) }.getOrDefault(existing)
    }

    suspend fun publish(context: Map<String, Any?>, plugin: String, whenever: Instant?): Long {
        val id = Mogul.publications.publish(publishableType, publishableId, toJson(context).toString(), plugin, whenever)
        refresh()
        return id
    }

    val scope = remember(publishableType, publishableId) {
        PublicationScope(
            type = publishableType,
            publishableId = publishableId,
            publishFunction = { context, plugin, whenever -> publish(context, plugin, whenever) },
            publishAndAwaitFunction = { context, plugin, whenever ->
                val id = publish(context, plugin, whenever)
                // nobody should be left waiting days for a scheduled one; it's pending, and that's
                // as finished as it's going to get for now
                if (whenever != null) {
                    existing.firstOrNull { it.id == id }
                } else {
                    // a plugin with nothing to render finishes in milliseconds, so the
                    // notification can have been and gone before the mutation even returned
                    existing.firstOrNull { it.id == id && it.state == "published" }
                        ?: CompletableDeferred<Publication?>().also { pending[id] = it }.await()
                }
            },
        )
    }
    val plugins = remember(scope) { pluginsFor(scope) }
    var selected by remember(scope) { mutableStateOf<String?>(null) }

    LaunchedEffect(scope) { refresh() }
    DisposableEffect(scope) {
        onDispose {
            // whoever was waiting has nobody left to hand a result to. settle them anyway, so
            // an awaiting caller isn't left holding something that can never complete
            pending.values.forEach { it.complete(null) }
            pending.clear()
        }
    }

    ListenForNotification("publication-started-event") { notification ->
        coroutines.launch {
            refresh()
            notification.key.toLongOrNull()?.let { startedIds = startedIds + it }
        }
    }
    ListenForNotification("publication-completed-event") { notification ->
        coroutines.launch {
            refresh()
            val id = notification.key.toLongOrNull() ?: return@launch
            startedIds = startedIds - id
            pending.remove(id)?.complete(existing.firstOrNull { it.id == id })
        }
    }

    plugins.forEach { it.Effects() }

    Column(modifier.fillMaxWidth().padding(top = Gutter.full)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Gutter.half)) {
            plugins.forEach { plugin ->
                PluginTile(plugin, isSelected = selected == plugin.name, anySelected = selected != null, enabled = enabled) {
                    selected = if (selected == plugin.name) null else plugin.name
                }
            }
        }
        plugins.firstOrNull { it.name == selected }?.let { plugin ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = if (plugins.first() == plugin) 0.dp else Gutter.radius, topEnd = Gutter.radius, bottomStart = Gutter.radius, bottomEnd = Gutter.radius))
                    .background(palette.publicationPanelBg)
                    .padding(Gutter.half),
                verticalArrangement = Arrangement.spacedBy(Gutter.half),
            ) {
                Text(t("publications.plugins.${plugin.name}.description"), color = palette.ink)
                plugin.Panel()
                if (plugin.hasAction) {
                    PublicationActions(
                        label = plugin.label,
                        enabled = plugin.enabled && enabled,
                        schedulable = plugin.schedulable,
                        scheduledFor = scheduled,
                        publish = { plugin.publish(it) },
                        // once something's been published or scheduled the panel has done its
                        // job, and closing it is the sign that it worked
                        onPublished = { selected = null },
                    )
                }
            }
        }
        if (showExistingPublications) {
            PublicationsList(
                publications = existing,
                unpublishable = true,
                onUnpublish = { publication ->
                    coroutines.launch {
                        runCatching { Mogul.publications.unpublish(publication.id) }
                        refresh()
                    }
                },
                publishingIds = startedIds,
            )
        }
    }
}

/**
 * a plugin's tile: its logo on the near-black of the top bar (the logos are drawn light).
 * hovering shows what a click does: the tile turns the panel's color, as a selected one is
 */
@Composable
private fun PluginTile(plugin: PublicationPlugin, isSelected: Boolean, anySelected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val flatBottom = isSelected || anySelected
    val shape = if (flatBottom) RoundedCornerShape(topStart = Gutter.radius, topEnd = Gutter.radius) else RoundedCornerShape(Gutter.radius)
    val background = if (isSelected || hovered) palette.publicationPanelBg else palette.topbar
    Box(
        Modifier
            .size(tileSize)
            .clip(shape)
            .background(background)
            .hoverable(interaction)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = pluginTitle(plugin.name) },
    ) {
        PluginIconImage(plugin.icon, null, tint = if (isSelected || hovered) palette.ink else palette.chromeInk)
    }
}
