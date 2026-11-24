package com.joshlong.mogul.client.podcasts

import com.joshlong.mogul.client.compositions.compositionTool
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.api.PodcastEpisode
import com.joshlong.mogul.client.api.PodcastEpisodeSegment
import com.joshlong.mogul.client.api.parseStringContext
import com.joshlong.mogul.client.events.loadNotesForNotable
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.publications.PublicationsSection
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.TextSize
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.ui.DateTimeField
import com.joshlong.mogul.client.ui.ButtonRow
import com.joshlong.mogul.client.ui.FormSection
import com.joshlong.mogul.client.ui.IconButton
import com.joshlong.mogul.client.ui.ListenForNotification
import com.joshlong.mogul.client.ui.MogulButton
import com.joshlong.mogul.client.ui.MogulIcon
import com.joshlong.mogul.client.ui.MogulTextField
import com.joshlong.mogul.client.ui.confirmDeletion
import com.joshlong.mogul.client.ui.dateTimeToString
import com.joshlong.mogul.client.ui.durationToString
import kotlin.time.Instant

/** an episode: its title and description, its graphic and segments, and its publications */
@Composable
fun PodcastEpisodeEditorScreen(podcastId: Long, episodeId: Long) {
    val scope = rememberCoroutineScope()
    var draftEpisode by remember { mutableStateOf<PodcastEpisode?>(null) }
    var segments by remember { mutableStateOf<List<PodcastEpisodeSegment>>(emptyList()) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    // bound to the date picker; null until an episode is loaded
    var createdDate by remember { mutableStateOf<Instant?>(null) }
    var dirtyKey by remember { mutableStateOf("") }
    var publicationsDisabled by remember { mutableStateOf(false) }
    // the render runs in the processors module, so publishing an episode whose segments have
    // changed returns at once and the publication waits until the render comes back
    var productionFailed by remember { mutableStateOf(false) }

    val draftId = draftEpisode?.id ?: 0L

    // the creation date is part of what Save persists, so editing it marks the form dirty
    fun computeDirtyKey() = "${if (draftId != 0L) draftId else ""}$description:$title:${createdDate?.toEpochMilliseconds() ?: ""}"

    fun loadEpisodeIntoEditor(episode: PodcastEpisode) {
        draftEpisode = episode
        publicationsDisabled = !episode.complete
        description = episode.description
        title = episode.title
        createdDate = episode.created
        segments = episode.segments
        dirtyKey = "${if (episode.id != 0L) episode.id else ""}${episode.description}:${episode.title}:${episode.created?.toEpochMilliseconds() ?: ""}"
        loadNotesForNotable("episode", episodeId, episode.title)
    }

    suspend fun loadEpisodeFromDbIntoEditor(id: Long) = loadEpisodeIntoEditor(Mogul.podcasts.podcastEpisodeById(id))

    suspend fun loadEpisodeSegments(id: Long) {
        val episode = Mogul.podcasts.podcastEpisodeById(id)
        if (episode.segments.isNotEmpty()) segments = episode.segments
    }

    LaunchedEffect(podcastId, episodeId) {
        runCatching {
            Mogul.podcasts.podcastById(podcastId)
            if (episodeId != 0L) loadEpisodeFromDbIntoEditor(episodeId)
        }.onFailure { println("podcasts: ${it.message}") }
    }

    ListenForNotification("podcast-episode-completed-event") { n ->
        val ctx = parseStringContext(n.context)
        if (!sameId(ctx["episodeId"], draftId)) return@ListenForNotification
        publicationsDisabled = ctx["complete"] == "false"
    }

    // a segment's audio is normalized long after the upload returns, and that's when its
    // duration becomes known. the notification carries the duration itself, since a re-read
    // could arrive before the server's write
    ListenForNotification("media-normalized-event") { n ->
        val ctx = parseStringContext(n.context)
        if (!sameId(ctx["episodeId"], draftId)) return@ListenForNotification
        val duration = ctx["durationInMilliseconds"]?.toDoubleOrNull() ?: return@ListenForNotification
        if (segments.none { sameId(ctx["segmentId"], it.id) }) return@ListenForNotification
        segments = segments.map { if (sameId(ctx["segmentId"], it.id)) it.copy(duration = duration) else it }
        // the episode's duration is the sum of its segments', server-side too
        draftEpisode = draftEpisode?.copy(duration = segments.sumOf { it.duration })
    }

    // a segment's cleaning state decides what its editor button does
    fun cleaningChanged(context: String?, cleaning: Boolean, cleaned: Boolean) {
        val ctx = parseStringContext(context)
        if (!sameId(ctx["episodeId"], draftId)) return
        segments = segments.map {
            if (sameId(ctx["segmentId"], it.id)) {
                it.copy(cleaning = cleaning, cleaned = cleaned, cleanable = !cleaning, cleaningError = ctx["reason"])
            } else {
                it
            }
        }
    }
    ListenForNotification("podcast-episode-segment-cleaning-started-event") { cleaningChanged(it.context, true, false) }
    ListenForNotification("podcast-episode-segment-cleaned-event") { cleaningChanged(it.context, false, true) }
    ListenForNotification("podcast-episode-segment-cleaning-failed-event") { cleaningChanged(it.context, false, false) }

    // both of these are keyed by episode id, and arrive for every episode this mogul has open
    // anywhere, so check before believing them
    ListenForNotification("podcast-episode-render-started-event") { n ->
        if (!sameId(n.key, draftId)) return@ListenForNotification
        productionFailed = false
    }
    ListenForNotification("podcast-episode-render-finished-event") { n ->
        if (!sameId(n.key, draftId)) return@ListenForNotification
        productionFailed = parseStringContext(n.context)["success"] == "false"
        // the produced audio is a different file now; reload rather than patch
        scope.launchSafely { loadEpisodeFromDbIntoEditor(draftId) }
    }
    ListenForNotification("publication-completed-event") { if (draftId != 0L) scope.launchSafely { loadEpisodeFromDbIntoEditor(draftId) } }
    ListenForNotification("publication-started-event") { if (draftId != 0L) scope.launchSafely { loadEpisodeFromDbIntoEditor(draftId) } }

    val buttonsDisabled = if (draftId == 0L) {
        !(description.trim().isNotEmpty() && title.trim().isNotEmpty())
    } else {
        dirtyKey == computeDirtyKey()
    }

    val minOrder = segments.minOfOrNull { it.order }
    val maxOrder = segments.maxOfOrNull { it.order }

    Column(verticalArrangement = Arrangement.spacedBy(Gutter.full)) {
        PageTitle(t("podcasts.episodes.episode"))
        val legend = buildString {
            append(if (title.isNotEmpty()) t("podcasts.episodes.episode.editing", "id" to draftId, "title" to title) else t("podcasts.episodes.new-episode"))
            if (draftId != 0L) {
                append(" (${dateTimeToString(draftEpisode?.created) ?: ""})")
                durationToString(draftEpisode?.duration)?.let { append("  $it") }
            }
        }
        FormSection(legend) {
            SectionTitle(t("podcasts.episodes.basics"))
            MogulTextField(
                value = title,
                onValueChange = { title = it },
                label = t("podcasts.episodes.episode.title"),
                required = true,
                showCount = true,
            )
            MogulTextField(
                value = description,
                onValueChange = { description = it },
                label = t("podcasts.episodes.episode.description"),
                required = true,
                singleLine = false,
                minLines = 10,
                showCount = true,
                tools = listOfNotNull(draftEpisode?.descriptionComposition?.let { compositionTool(it.id) }),
            )
            if (draftId != 0L) {
                DateTimeField(t("podcasts.episodes.episode.created"), createdDate, onValueChange = { createdDate = it })
            }
            ButtonRow(horizontalArrangement = Arrangement.spacedBy(Gutter.half)) {
                MogulButton(t("podcasts.episodes.buttons.save"), enabled = !buttonsDisabled, onClick = {
                    scope.launchSafely {
                        if (draftId != 0L) {
                            Mogul.podcasts.updatePodcastEpisode(draftId, title, description, createdDate)
                            loadEpisodeFromDbIntoEditor(draftId)
                        } else {
                            val episode = Mogul.podcasts.createPodcastEpisodeDraft(podcastId, title, description)
                            loadEpisodeFromDbIntoEditor(episode.id)
                        }
                    }
                })
                MogulButton(t("podcasts.episodes.buttons.cancel"), enabled = !(description.isEmpty() && title.isEmpty()), onClick = {
                    title = ""
                    description = ""
                    segments = emptyList()
                    publicationsDisabled = true
                })
            }

            SectionTitle(t("podcasts.episodes.segments"))
            // the new-segment plus is a toolbar under the title, as in the Vue editor
            Row(Modifier.fillMaxWidth().padding(vertical = Gutter.half)) {
                IconButton(MogulIcon.Plus, t("icons.new-segment"), enabled = draftId != 0L, onClick = {
                    publicationsDisabled = true
                    scope.launchSafely {
                        Mogul.podcasts.createPodcastEpisodeSegment(draftId)
                        loadEpisodeSegments(draftId)
                    }
                })
            }

            draftEpisode?.graphic?.let { graphic ->
                PodcastEpisodeSegmentRow(
                    label = t("podcasts.episodes.episode.graphic"),
                    managedFileId = graphic.id,
                    accept = ".jpg,.jpeg,.png,image/jpeg,image/jpg,image/png",
                    type = "graphic",
                )
            }
            segments.forEach { segment ->
                androidx.compose.runtime.key(segment.id) {
                    PodcastEpisodeSegmentRow(
                        label = t("podcasts.episodes.episode.segments.number", "order" to segment.order),
                        managedFileId = segment.audio?.id ?: 0L,
                        accept = ".mp3,audio/mpeg",
                        type = "audio",
                        segmentId = segment.id,
                        showReorder = true,
                        canMoveUp = segment.order != minOrder,
                        canMoveDown = segment.order != maxOrder,
                        showDelete = true,
                        showTranscript = true,
                        showAudioEditor = true,
                        cleaning = segment.cleaning,
                        cleaned = segment.cleaned,
                        cleanable = segment.cleanable,
                        cleaningError = segment.cleaningError,
                        duration = segment.duration,
                        onMoveUp = {
                            scope.launchSafely {
                                Mogul.podcasts.movePodcastEpisodeSegmentUp(draftId, segment.id)
                                loadEpisodeSegments(draftId)
                            }
                        },
                        onMoveDown = {
                            scope.launchSafely {
                                Mogul.podcasts.movePodcastEpisodeSegmentDown(draftId, segment.id)
                                loadEpisodeSegments(draftId)
                            }
                        },
                        onDelete = {
                            val details = t("podcasts.episodes.segments.number", "id" to segment.order)
                            scope.launchSafely {
                                if (confirmDeletion(t("confirm.deletion", "title" to details))) {
                                    publicationsDisabled = true
                                    Mogul.podcasts.deletePodcastEpisodeSegment(segment.id)
                                    loadEpisodeSegments(draftId)
                                }
                            }
                        },
                        onEditTranscript = {
                            scope.launchSafely {
                                val episode = Mogul.podcasts.podcastEpisodeById(draftId)
                                val match = episode.segments.firstOrNull { it.id == segment.id } ?: return@launchSafely
                                val transcript = match.transcript ?: return@launchSafely
                                Mogul.transcripts.editTranscript(
                                    transcript.id,
                                    transcript.transcript ?: "",
                                    t("podcasts.episodes.episode.segments.number", "order" to segment.order),
                                )
                            }
                        },
                    )
                }
            }

            SectionTitle(t("podcasts.episodes.publications"))
            if (productionFailed) {
                Text(
                    t("podcasts.episodes.publications.production-failed"),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Gutter.radius))
                        .background(palette.panelBg)
                        .padding(Gutter.half),
                    color = palette.ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = TextSize.sm,
                )
            }
            if (draftId != 0L) {
                PublicationsSection("episode", draftId, enabled = !publicationsDisabled)
            }
        }
    }
}
