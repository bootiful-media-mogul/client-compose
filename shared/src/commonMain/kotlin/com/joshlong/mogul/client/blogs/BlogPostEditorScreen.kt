package com.joshlong.mogul.client.blogs

import com.joshlong.mogul.client.ui.DateTimeField
import com.joshlong.mogul.client.ui.FormSectionTitle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.api.Post
import com.joshlong.mogul.client.compositions.compositionTool
import com.joshlong.mogul.client.events.loadNotesForNotable
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.publications.PublicationsSection
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.TextSize
import com.joshlong.mogul.client.theme.fonts
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.ui.ButtonRow
import com.joshlong.mogul.client.ui.FieldRow
import com.joshlong.mogul.client.ui.FormSection
import com.joshlong.mogul.client.ui.IconButton
import com.joshlong.mogul.client.ui.InputTool
import com.joshlong.mogul.client.ui.MogulButton
import com.joshlong.mogul.client.ui.MogulIcon
import com.joshlong.mogul.client.ui.MogulTextField
import com.joshlong.mogul.client.ui.dateTimeToString
import kotlinx.coroutines.launch
import kotlin.time.Instant

/** editing one blog post (BlogsPostEditor.vue) */
@Composable
fun BlogPostEditorScreen(blogId: Long, postId: Long) {
    val scope = rememberCoroutineScope()
    var post by remember { mutableStateOf<Post?>(null) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var rssSlug by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var created by remember { mutableStateOf<Instant?>(null) }
    var summarizing by remember { mutableStateOf(false) }
    var savedKey by remember { mutableStateOf("") }

    // the creation date is part of what Save persists, so editing it marks the form dirty
    fun key() = "${post?.id ?: ""}$title:$description:$summary:$rssSlug:${created?.toEpochMilliseconds() ?: ""}"

    suspend fun load() {
        val loaded = Mogul.blogs.postById(postId)
        post = loaded
        title = loaded.title
        description = loaded.content
        summary = loaded.summary
        rssSlug = loaded.rssSlug ?: ""
        visible = loaded.visible
        created = loaded.created
        savedKey = key()
        loadNotesForNotable("post", postId, title)
    }

    LaunchedEffect(blogId, postId) { load() }

    val current = post
    val buttonsDisabled = current == null || savedKey == key()

    // the summary's own tool: asks the ai to summarize the post's content
    val summarizeTool = InputTool(MogulIcon.Transcript, t("icons.transcript")) { _, onValueChange ->
        Row {
            MogulButton(
                t(if (summarizing) "blogs.posts.buttons.summarizing" else "blogs.posts.buttons.summarize"),
                enabled = !summarizing,
                onClick = {
                    summarizing = true
                    scope.launch {
                        try {
                            onValueChange(
                                Mogul.ai.chat(
                                    """
    derive a summary of the following blog post.
    do not include or refer to any embedded HTML markup like <IFRAME> elements when generating the summary.

    ===================================
    ${current?.content ?: ""}""",
                                ),
                            )
                        } finally {
                            summarizing = false
                        }
                    }
                },
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Gutter.full)) {
        PageTitle(t("blogs.posts.post"))
        val legend = buildString {
            append(if (title.isNotEmpty()) t("blogs.posts.editing", "id" to (current?.id ?: ""), "title" to title) else t("blogs.posts.new-post"))
            if (current != null) append(" (${dateTimeToString(current.created) ?: ""})")
        }
        FormSection(legend) {
            SectionTitle(t("blogs.posts.basics"))
            MogulTextField(title, { title = it }, label = t("blogs.posts.post.title"), required = true, showCount = true)
            MogulTextField(
                description,
                { description = it },
                label = t("blogs.posts.post.description"),
                required = true,
                singleLine = false,
                minLines = 10,
                showCount = true,
                tools = listOfNotNull(current?.descriptionComposition?.let { compositionTool(it.id) }),
            )
            MogulTextField(
                summary,
                { summary = it },
                label = t("blogs.posts.post.summary"),
                required = true,
                singleLine = false,
                minLines = 5,
                showCount = true,
                tools = listOf(summarizeTool),
            )
            MogulTextField(rssSlug, { rssSlug = it }, label = t("blogs.posts.post.rss-slug"))
            if (current != null) {
                DateTimeField(t("blogs.posts.post.created"), created, { created = it })
            }
            FieldRow(t("blogs.posts.post.visible")) {
                IconButton(
                    if (visible) MogulIcon.Eye else MogulIcon.EyeOff,
                    t(if (visible) "blogs.posts.post.visible" else "blogs.posts.post.not-visible"),
                    onClick = {
                        val previous = visible
                        visible = !previous
                        if (current != null) {
                            scope.launch {
                                // put the toggle back if the server didn't take it
                                runCatching { Mogul.blogs.setBlogPostVisibility(current.id, !previous) }
                                    .onFailure { visible = previous }
                            }
                        }
                    },
                    toggled = visible,
                )
            }
            ButtonRow(horizontalArrangement = Arrangement.spacedBy(Gutter.half)) {
                MogulButton(t("blogs.posts.buttons.save"), enabled = !buttonsDisabled, onClick = {
                    val id = current?.id ?: return@MogulButton
                    scope.launch {
                        Mogul.blogs.updatePost(id, title, description, summary, rssSlug.trim().ifEmpty { null }, created)
                        load()
                    }
                })
                MogulButton(t("blogs.posts.buttons.cancel"), enabled = !buttonsDisabled, onClick = {
                    scope.launch { load() }
                })
            }
            SectionTitle(t("blogs.posts.publications"))
            PublicationsSection("post", postId, enabled = true)
        }
    }
}

/** a .form-section-title: a small heading inside a fieldset */
@Composable
private fun SectionTitle(text: String) = FormSectionTitle(text)
