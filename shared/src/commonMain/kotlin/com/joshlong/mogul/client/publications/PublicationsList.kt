package com.joshlong.mogul.client.publications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joshlong.mogul.client.api.Publication
import com.joshlong.mogul.client.api.PublicationOutcome
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.platform.browser
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.TextSize
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.ui.Icon
import com.joshlong.mogul.client.ui.IconButton
import com.joshlong.mogul.client.ui.MogulIcon
import com.joshlong.mogul.client.ui.Rule
import com.joshlong.mogul.client.ui.dateTimeToString

private val iconColumn = 40.dp
private val dateColumn = 150.dp
// fits the longest outcome label, "X (formerly known as Twitter)"
private val keyColumn = 220.dp

/** a publication with nothing (yet, or any more) to link to */
fun withdrawn(publication: Publication): Boolean =
    publication.url == "" || publication.state in setOf("pending", "draft", "unpublished")

/**
 * renders publications that already happened. deliberately dumb: it owns no fetching and no
 * publishing, so the same rows render next to the publish controls for one publishable and,
 * on the home page, in the mogul's day-by-day history. `publishingIds` are publications
 * known to be underway (a started-event arrived), which show the clock even before their
 * state says pending.
 */
@Composable
fun PublicationsList(
    publications: List<Publication>,
    modifier: Modifier = Modifier,
    unpublishable: Boolean = false,
    onUnpublish: (Publication) -> Unit = {},
    publishingIds: Set<Long> = emptySet(),
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }
    BoxWithConstraints(modifier.fillMaxWidth().padding(top = Gutter.full)) {
        // lays out by its own width, so it fits wherever it's put: too narrow for three dates
        // side by side, each date gets a line of its own, named by its label
        val narrow = maxWidth < 560.dp
        Column {
            if (publications.isNotEmpty() && !narrow) {
                Row(Modifier.padding(bottom = Gutter.third)) {
                    Spacer(Modifier.width(iconColumn * 3 + Gutter.full + Gutter.half * 2))
                    listOf("created", "scheduled", "published").forEach {
                        Text(t("publications.dates.$it"), Modifier.width(dateColumn), color = palette.muted, fontSize = TextSize.xs)
                    }
                }
            }
            publications.forEach { publication ->
                Rule(padding = androidx.compose.foundation.layout.PaddingValues(0.dp))
                PublicationRow(publication, narrow, unpublishable, onUnpublish, publication.publishing || publication.id in publishingIds)
                Column(Modifier.padding(start = if (narrow) 0.dp else iconColumn, bottom = Gutter.half)) {
                    publication.outcomes.forEach { outcome ->
                        OutcomeRow(publication, outcome, narrow) { errorMessage = it }
                    }
                }
            }
        }
    }
    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            confirmButton = { TextButton(onClick = { errorMessage = null }) { Text(t("icons.close"), color = palette.ink) } },
            text = { Text(message, color = palette.ink) },
            containerColor = palette.raised,
        )
    }
}

@Composable
private fun PublicationRow(publication: Publication, narrow: Boolean, unpublishable: Boolean, onUnpublish: (Publication) -> Unit, publishing: Boolean) {
    val icons = @Composable {
        Box(Modifier.size(iconColumn).clip(RoundedCornerShape(Gutter.radius)).background(palette.chrome), contentAlignment = Alignment.Center) {
            PLUGIN_ICONS[publication.plugin]?.let { PluginIconImage(it, pluginTitle(publication.plugin)) }
        }
        Spacer(Modifier.width(if (narrow) Gutter.half else Gutter.full))
        Box(Modifier.size(iconColumn), contentAlignment = Alignment.Center) {
            if (unpublishable) {
                IconButton(MogulIcon.Delete, t("icons.withdraw"), onClick = { onUnpublish(publication) }, enabled = !withdrawn(publication))
            }
        }
        Spacer(Modifier.width(Gutter.half))
        Box(Modifier.size(iconColumn), contentAlignment = Alignment.Center) {
            if (publishing) Text("🕒")
        }
    }
    val created = dateTimeToString(publication.created) ?: ""
    val scheduled = dateTimeToString(publication.scheduled) ?: t("publications.dates.now")
    val published = dateTimeToString(publication.published) ?: ""
    if (narrow) {
        Column(Modifier.padding(vertical = Gutter.half)) {
            Row(verticalAlignment = Alignment.CenterVertically) { icons() }
            Column(Modifier.padding(top = Gutter.half), verticalArrangement = Arrangement.spacedBy(Gutter.third)) {
                LabeledDate("created", created)
                LabeledDate("scheduled", scheduled)
                LabeledDate("published", published)
            }
        }
    } else {
        Row(Modifier.padding(vertical = Gutter.half), verticalAlignment = Alignment.CenterVertically) {
            icons()
            Spacer(Modifier.width(Gutter.half))
            DateCell(created, dateColumn)
            DateCell(scheduled, dateColumn)
            DateCell(published, dateColumn)
        }
    }
}

@Composable
private fun DateCell(text: String, width: Dp) = Text(text, Modifier.width(width), color = palette.ink, fontSize = TextSize.sm)

@Composable
private fun LabeledDate(label: String, value: String) {
    Row {
        Text(t("publications.dates.$label"), Modifier.width(104.dp), color = palette.muted, fontSize = TextSize.xs)
        Text(value, color = palette.ink, fontSize = TextSize.sm)
    }
}

@Composable
private fun OutcomeRow(publication: Publication, outcome: PublicationOutcome, narrow: Boolean, showError: (String) -> Unit) {
    val gone = withdrawn(publication)
    Row(
        Modifier.fillMaxWidth().padding(start = if (narrow) 0.dp else Gutter.full, top = Gutter.third),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Gutter.half),
    ) {
        Box(Modifier.width(iconColumn), contentAlignment = Alignment.Center) {
            IconButton(
                MogulIcon.Preview,
                t("icons.open-link"),
                onClick = { outcome.url?.let { browser.open(it, "_blank") } },
                enabled = !gone && !outcome.url.isNullOrBlank(),
            )
        }
        Box(Modifier.width(iconColumn), contentAlignment = Alignment.Center) {
            if (outcome.success) Icon(MogulIcon.Checkbox, t("icons.succeeded"))
            // svg icons take the text color, so a failure is tinted rather than drawn in red
            else Icon(MogulIcon.Error, t("icons.failed"), tint = palette.danger)
        }
        Text(
            t("publications.outcomes.keys.${outcome.key}"),
            Modifier.width(if (narrow) 144.dp else keyColumn),
            color = palette.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!outcome.success) {
            val message = outcome.serverErrorMessage
            if (!message.isNullOrBlank()) {
                Text(
                    t("publications.outcomes.error-message"),
                    Modifier.clickable { showError(message) }.pointerHoverIcon(PointerIcon.Hand),
                    color = palette.ink,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            } else {
                Text(t("publications.outcomes.no-error-message"), color = palette.ink, maxLines = 1)
            }
        } else if (!outcome.preview.isNullOrBlank()) {
            Text(
                outcome.preview,
                Modifier.weight(1f),
                color = palette.ink.copy(alpha = 0.75f),
                fontStyle = FontStyle.Italic,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
