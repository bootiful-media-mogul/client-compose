package com.joshlong.mogul.client.jobs

import com.joshlong.mogul.client.ui.PageTitle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.joshlong.mogul.client.api.Blog
import com.joshlong.mogul.client.api.Job
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.api.Podcast
import com.joshlong.mogul.client.api.json
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.managedfiles.ManagedFileField
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.ui.FieldRow
import com.joshlong.mogul.client.ui.FormSection
import com.joshlong.mogul.client.ui.ListenForNotification
import com.joshlong.mogul.client.ui.MogulButton
import com.joshlong.mogul.client.ui.MogulSelect
import com.joshlong.mogul.client.ui.MogulTextField
import kotlinx.coroutines.launch
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val MANAGED_FILE_ID = "managedFileId"

/** a job, the values picked for its attributes, and how its last launch went */
private class JobRequest(val job: Job) {
    // every attribute starts empty, except a file to upload into, which is made up front
    val values = mutableStateMapOf<String, Any?>()
    var busy by mutableStateOf(false)
    var success by mutableStateOf(false)
    var done by mutableStateOf(false)

    val ready: Boolean get() = this.job.requiredContextAttributes.all { this.values[it] != null }
}

@Composable
fun JobsScreen() {
    val scope = rememberCoroutineScope()
    val all = remember { mutableStateListOf<JobRequest>() }

    ListenForNotification("job-stopped-event") { notification ->
        all.firstOrNull { it.job.name == notification.key }?.let { job ->
            job.busy = false
            job.success = runCatching {
                json.parseToJsonElement(notification.context ?: "{}").jsonObject["success"]?.jsonPrimitive?.booleanOrNull ?: false
            }.getOrDefault(false)
            job.done = true
        }
    }

    LaunchedEffect(Unit) {
        val jobs = runCatching { Mogul.jobs.jobs() }.getOrElse { emptyList() }
        all.clear()
        all.addAll(
            jobs.map { job ->
                JobRequest(job).also { request ->
                    // a job that takes a file needs one to upload into *before* it is launched,
                    // and the file field loads it by id the moment it shows
                    if (MANAGED_FILE_ID in job.requiredContextAttributes) {
                        request.values[MANAGED_FILE_ID] = runCatching { Mogul.jobs.createManagedFile(job.name) }.getOrNull()
                    }
                }
            },
        )
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Gutter.full)) {
        PageTitle(t("jobs.title"))
        all.forEach { request ->
            val name = t("jobs.name.${request.job.name}")
            FormSection(name) {
                request.job.requiredContextAttributes.forEach { attribute ->
                    FieldRow(t("selections.params.${request.job.name}.$attribute"), required = request.values[attribute] == null) {
                        JobParameter(request, attribute)
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Gutter.full),
                ) {
                    MogulButton(
                        t("jobs.launch", "name" to name),
                        enabled = request.ready && !request.busy,
                        onClick = {
                            request.busy = true
                            request.done = false
                            request.success = false
                            scope.launch {
                                runCatching { Mogul.jobs.launch(request.job.name, request.values.toMap()) }
                                    .onFailure {
                                        request.busy = false
                                        request.done = true
                                    }
                            }
                        },
                    )
                    when {
                        request.busy -> Text(t("jobs.busy", "name" to name), color = palette.ink)
                        request.done && request.success -> Text(t("jobs.done", "name" to name), color = palette.ink)
                        request.done -> Text(t("jobs.failed", "name" to name), color = palette.danger)
                    }
                }
            }
        }
    }
}

/** the control for one attribute: a podcast, a blog, a file, or plain text */
@Composable
private fun JobParameter(request: JobRequest, attribute: String) {
    when (attribute) {
        "podcastId" -> {
            var podcasts by remember { mutableStateOf<List<Podcast>>(emptyList()) }
            LaunchedEffect(Unit) { podcasts = runCatching { Mogul.podcasts.podcasts() }.getOrElse { emptyList() } }
            MogulSelect(
                podcasts,
                podcasts.firstOrNull { it.id == request.values[attribute] },
                { request.values[attribute] = it.id },
                { it.title },
                placeholder = t("selections.podcasts"),
            )
        }
        "blogId" -> {
            var blogs by remember { mutableStateOf<List<Blog>>(emptyList()) }
            LaunchedEffect(Unit) { blogs = runCatching { Mogul.blogs.blogs() }.getOrElse { emptyList() } }
            MogulSelect(
                blogs,
                blogs.firstOrNull { it.id == request.values[attribute] },
                { request.values[attribute] = it.id },
                { it.title },
                placeholder = t("selections.blogs"),
            )
        }
        MANAGED_FILE_ID -> {
            (request.values[attribute] as? Long)?.let { id ->
                ManagedFileField(id, "tgz,zip", onHasData = { request.values[attribute] = it })
            }
        }
        else -> {
            var text by remember { mutableStateOf("") }
            MogulTextField(
                text,
                {
                    text = it
                    request.values[attribute] = it.trimEnd().ifEmpty { null }
                },
                placeholder = t("components.input.enter-a-value"),
            )
        }
    }
}
