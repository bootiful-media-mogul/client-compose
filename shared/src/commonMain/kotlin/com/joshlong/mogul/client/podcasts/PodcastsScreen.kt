package com.joshlong.mogul.client.podcasts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.api.Podcast
import com.joshlong.mogul.client.events.loadNotesForNotable
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.nav.Route
import com.joshlong.mogul.client.nav.Router
import com.joshlong.mogul.client.platform.browser
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.ui.ButtonRow
import com.joshlong.mogul.client.ui.FormSection
import com.joshlong.mogul.client.ui.IconButton
import com.joshlong.mogul.client.ui.MogulButton
import com.joshlong.mogul.client.ui.MogulIcon
import com.joshlong.mogul.client.ui.MogulSelect
import com.joshlong.mogul.client.ui.MogulTextField
import com.joshlong.mogul.client.ui.Rule
import com.joshlong.mogul.client.ui.confirmDeletion
import com.joshlong.mogul.client.ui.dateTimeToString

/** every podcast the mogul has, with links to each one's episodes, its feed, and its editor */
@Composable
fun PodcastsScreen() {
    val scope = rememberCoroutineScope()
    var all by remember { mutableStateOf<List<Podcast>>(emptyList()) }
    var draftPodcast by remember { mutableStateOf<Podcast?>(null) }
    var mogulId by remember { mutableStateOf(0L) }
    var mogulName by remember { mutableStateOf("") }

    suspend fun refresh() {
        all = Mogul.podcasts.podcasts()
    }

    suspend fun editPodcast(podcast: Podcast) {
        draftPodcast = podcast
        loadNotesForNotable("podcast", podcast.id, podcast.title)
    }

    LaunchedEffect(Unit) {
        runCatching {
            val user = Mogul.mogul.user()
            mogulName = "${user.givenName} ${user.familyName}"
            mogulId = user.id
            refresh()
        }.onFailure { println("podcasts: ${it.message}") }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Gutter.full)) {
        PageTitle(t("podcasts.title"))
        val editing = draftPodcast
        if (editing != null) {
            PodcastEditor(editing, onDone = {
                draftPodcast = null
                scope.launchSafely { refresh() }
            })
        } else {
            FormSection(
                t("podcasts.all", "user" to mogulName),
                actions = {
                    IconButton(
                        MogulIcon.Plus,
                        t("icons.new-podcast"),
                        onClick = { scope.launchSafely { editPodcast(Mogul.podcasts.create("")) } },
                    )
                },
            ) {
                all.forEach { podcast ->
                    PodcastRow(
                        podcast,
                        deletable = all.size != 1,
                        onEdit = { scope.launchSafely { editPodcast(podcast) } },
                        onDelete = {
                            scope.launchSafely {
                                if (confirmDeletion(t("confirm.deletion", "title" to podcast.title))) {
                                    Mogul.podcasts.deletePodcast(podcast.id)
                                    refresh()
                                }
                            }
                        },
                        onRss = {
                            val url = browser.apiUrl() + "/public/feeds/moguls/$mogulId/podcasts/${podcast.id}/episodes.atom"
                            browser.open(url, "rssWindowForPodcastNo${podcast.id}")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PodcastRow(podcast: Podcast, deletable: Boolean, onEdit: () -> Unit, onDelete: () -> Unit, onRss: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Rule(padding = androidx.compose.foundation.layout.PaddingValues(bottom = Gutter.half))
        Text(podcast.title, color = palette.ink)
        Row(
            Modifier.fillMaxWidth().padding(top = Gutter.third, bottom = Gutter.half),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Gutter.full),
        ) {
            Row(Modifier.width(60.dp)) {
                Text("#", color = palette.ink)
                Text("${podcast.id}", color = palette.ink, fontWeight = FontWeight.Bold)
            }
            IconButton(MogulIcon.Edit, t("icons.edit", "name" to podcast.title), onClick = onEdit)
            IconButton(MogulIcon.Delete, t("icons.delete", "name" to podcast.title), onClick = onDelete, enabled = deletable)
            IconButton(MogulIcon.Rss, t("icons.rss", "name" to podcast.title), onClick = onRss)
            Text(
                t("podcasts.episodes"),
                modifier = Modifier
                    .width(96.dp)
                    .clickable { Router.push(Route.PodcastEpisodes(podcast.id)) }
                    .pointerHoverIcon(PointerIcon.Hand),
                color = palette.ink,
                fontWeight = FontWeight.SemiBold,
            )
            Text(dateTimeToString(podcast.created) ?: "", color = palette.ink)
        }
    }
}

/** a podcast's title, saved when it's changed */
@Composable
private fun PodcastEditor(podcast: Podcast, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var dirtyKey by remember { mutableStateOf("") }

    LaunchedEffect(podcast.id) {
        runCatching {
            title = Mogul.podcasts.podcastById(podcast.id).title
            dirtyKey = title
        }.onFailure { println("podcasts: ${it.message}") }
    }

    val updateDisabled = title == dirtyKey || title.trim().isEmpty()

    FormSection(
        t("podcasts.editing", "podcast" to title),
        actions = { IconButton(MogulIcon.Back, t("icons.back"), onClick = onDone) },
    ) {
        MogulTextField(
            value = title,
            onValueChange = { title = it },
            label = t("podcasts.new-podcast.title"),
            required = true,
            showCount = true,
        )
        ButtonRow(horizontalArrangement = Arrangement.spacedBy(Gutter.half)) {
            MogulButton(t("podcasts.save"), enabled = !updateDisabled, onClick = {
                scope.launchSafely {
                    Mogul.podcasts.update(podcast.id, title)
                    dirtyKey = title
                }
            })
        }
    }
}

/** picks one of the mogul's podcasts, as PodcastsSelect.vue (the jobs page uses it) */
@Composable
fun PodcastsSelect(selectedPodcastId: Long?, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    var podcasts by remember { mutableStateOf<List<Podcast>>(emptyList()) }
    LaunchedEffect(Unit) {
        runCatching { podcasts = Mogul.podcasts.podcasts() }.onFailure { println("podcasts: ${it.message}") }
    }
    MogulSelect(
        options = podcasts,
        selected = podcasts.firstOrNull { it.id == selectedPodcastId },
        onSelect = { onSelect(it.id) },
        optionLabel = { it.title },
        modifier = modifier,
        placeholder = t("selections.podcasts"),
    )
}
