package com.joshlong.mogul.client.podcasts

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.api.Podcast
import com.joshlong.mogul.client.api.PodcastEpisode
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.nav.Route
import com.joshlong.mogul.client.nav.Router
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.ui.FormSection
import com.joshlong.mogul.client.ui.Icon
import com.joshlong.mogul.client.ui.IconButton
import com.joshlong.mogul.client.ui.MogulIcon
import com.joshlong.mogul.client.ui.confirmDeletion
import com.joshlong.mogul.client.ui.dateTimeToString

/** a podcast's episodes, each a link to its editor */
@Composable
fun PodcastEpisodesScreen(podcastId: Long) {
    val scope = rememberCoroutineScope()
    var podcast by remember { mutableStateOf<Podcast?>(null) }
    var episodes by remember { mutableStateOf<List<PodcastEpisode>>(emptyList()) }

    suspend fun loadPodcast() {
        podcast = Mogul.podcasts.podcastById(podcastId)
        episodes = Mogul.podcasts.podcastEpisodesPreviews(podcastId)
    }

    LaunchedEffect(podcastId) {
        runCatching { loadPodcast() }.onFailure { println("podcasts: ${it.message}") }
    }

    Column {
        PageTitle(t("podcasts.episodes"))
        FormSection(
            t("podcasts.episodes.title", "title" to (podcast?.title ?: "")),
            actions = {
                IconButton(
                    MogulIcon.Plus,
                    t("icons.new-episode"),
                    onClick = {
                        scope.launchSafely {
                            val episode = Mogul.podcasts.createPodcastEpisodeDraft(podcastId, "", "")
                            Router.push(Route.PodcastEpisode(podcastId, episode.id))
                        }
                    },
                )
            },
        ) {
            episodes.forEach { episode ->
                EpisodeResult(
                    episode,
                    onEdit = { Router.push(Route.Entity("episode", episode.id)) },
                    onDelete = {
                        scope.launchSafely {
                            if (confirmDeletion(t("confirm.deletion", "title" to episode.title))) {
                                Mogul.podcasts.deletePodcastEpisode(episode.id)
                                loadPodcast()
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * an episode, laid out as a search result (Result.vue): the type's icon faded in a column of
 * its own, then the title, and under it edit, the date, the id and delete
 */
@Composable
private fun EpisodeResult(episode: PodcastEpisode, onEdit: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rule))
        Row(Modifier.fillMaxWidth().padding(vertical = Gutter.half), horizontalArrangement = Arrangement.spacedBy(Gutter.half)) {
            Icon(MogulIcon.Podcast, null, size = 48.dp, tint = palette.ink.copy(alpha = 0.3f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Gutter.full)) {
                if (episode.title.isNotBlank()) Text(episode.title, color = palette.ink)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(MogulIcon.Edit, t("icons.edit", "name" to episode.title), onClick = onEdit)
                    Text(
                        dateTimeToString(episode.created) ?: "",
                        modifier = Modifier.weight(1f).padding(end = Gutter.half),
                        color = palette.ink,
                        textAlign = TextAlign.End,
                    )
                    Box(Modifier.size(1.dp, 20.dp).background(palette.rule))
                    Text("#${episode.id}", modifier = Modifier.weight(1f).padding(start = Gutter.half), color = palette.ink)
                    IconButton(MogulIcon.Delete, t("icons.delete", "name" to episode.title), onClick = onDelete)
                }
            }
        }
    }
}
