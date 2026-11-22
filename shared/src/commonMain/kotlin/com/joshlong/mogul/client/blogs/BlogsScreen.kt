package com.joshlong.mogul.client.blogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joshlong.mogul.client.api.Blog
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.api.User
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.nav.Route
import com.joshlong.mogul.client.nav.Router
import com.joshlong.mogul.client.platform.browser
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.TextSize
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.ui.ButtonRow
import com.joshlong.mogul.client.ui.FormSection
import com.joshlong.mogul.client.ui.IconButton
import com.joshlong.mogul.client.ui.MogulButton
import com.joshlong.mogul.client.ui.MogulIcon
import com.joshlong.mogul.client.ui.MogulSelect
import com.joshlong.mogul.client.ui.MogulTextField
import com.joshlong.mogul.client.ui.confirmDeletion
import com.joshlong.mogul.client.ui.dateTimeToString
import com.joshlong.mogul.client.writing.writingTools
import kotlinx.coroutines.launch

/** the mogul's blogs (BlogsSearchView.vue), or the editor for one of them */
@Composable
fun BlogsScreen() {
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf<User?>(null) }
    var blogs by remember { mutableStateOf<List<Blog>>(emptyList()) }
    var draft by remember { mutableStateOf<Blog?>(null) }

    suspend fun reset() {
        user = Mogul.mogul.user()
        blogs = Mogul.blogs.blogs()
    }

    LaunchedEffect(Unit) { reset() }

    Column(verticalArrangement = Arrangement.spacedBy(Gutter.full)) {
        PageTitle(t("blogs.title"))
        val editing = draft
        if (editing != null) {
            BlogEditor(editing, onDone = {
                draft = null
                scope.launch { reset() }
            })
            return@Column
        }
        val mogulName = user?.let { "${it.givenName ?: ""} ${it.familyName ?: ""}" } ?: ""
        FormSection(
            t("blogs.all", "user" to mogulName),
            actions = {
                IconButton(MogulIcon.Plus, t("icons.new-blog"), onClick = {
                    scope.launch { draft = Mogul.blogs.createBlog("", "") }
                }, tint = palette.chromeInk, hoverTint = palette.brand)
            },
        ) {
            blogs.forEach { blog ->
                BlogRow(
                    blog,
                    deletable = blogs.size > 1,
                    onEdit = { draft = blog },
                    onRss = {
                        val url = "${browser.apiUrl()}/public/feeds/moguls/${user?.id ?: 0}/blogs/${blog.id}/posts.atom"
                        browser.open(url, "rssWindowForBlogNo${blog.id}")
                    },
                    onDelete = {
                        if (confirmDeletion(t("confirm.deletion", "title" to blog.title))) {
                            scope.launch {
                                Mogul.blogs.deleteBlog(blog.id)
                                reset()
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun BlogRow(blog: Blog, deletable: Boolean, onEdit: () -> Unit, onRss: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Gutter.third)) {
        RowRule()
        Text(blog.title, color = palette.ink)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gutter.full)) {
            Row(Modifier.width(64.dp)) {
                Text("#", color = palette.ink)
                Text("${blog.id}", color = palette.ink, fontWeight = FontWeight.Bold)
            }
            IconButton(MogulIcon.Edit, t("icons.edit", "name" to blog.title), onClick = onEdit)
            IconButton(MogulIcon.Delete, t("icons.delete", "name" to blog.title), onClick = onDelete, enabled = deletable)
            IconButton(MogulIcon.Rss, t("icons.rss", "name" to blog.title), onClick = onRss)
            Link(t("blogs.posts"), Modifier.width(96.dp)) { Router.push(Route.BlogPosts(blog.id)) }
            Text(dateTimeToString(blog.created) ?: "", color = palette.ink)
        }
    }
}

/** editing one blog (BlogsEditor.vue) */
@Composable
private fun BlogEditor(blog: Blog, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var rssUrl by remember { mutableStateOf("") }
    // what was last loaded or saved, so Save is only on when there's something to save
    var savedKey by remember { mutableStateOf("") }
    fun key() = "$title|$description|$rssUrl"

    LaunchedEffect(blog.id) {
        val loaded = Mogul.blogs.blogById(blog.id)
        title = loaded.title
        description = loaded.description ?: ""
        rssUrl = loaded.rssUrl ?: ""
        savedKey = key()
    }

    val saveDisabled = key() == savedKey || "$description:$title".trim() == ":"
    FormSection(t("blogs.editing", "title" to title, "blog" to title)) {
        MogulTextField(title, { title = it }, label = t("blogs.new-blog.title"), required = true, showCount = true, tools = writingTools())
        MogulTextField(
            description,
            { description = it },
            label = t("blogs.new-blog.description"),
            singleLine = false,
            minLines = 4,
            showCount = true,
            tools = writingTools(),
        )
        MogulTextField(rssUrl, { rssUrl = it }, label = t("blogs.new-blog.rss-url"))
        ButtonRow(horizontalArrangement = Arrangement.spacedBy(Gutter.half)) {
            MogulButton(t("blogs.save"), enabled = !saveDisabled, onClick = {
                scope.launch {
                    Mogul.blogs.update(blog.id, title, description, rssUrl.trim().ifEmpty { null })
                    savedKey = key()
                }
            })
            MogulButton(t("blogs.done"), onClick = onDone)
        }
    }
}

/** a choice of the mogul's blogs (BlogsSelect.vue) */
@Composable
fun BlogSelect(selectedBlogId: Long?, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    var blogs by remember { mutableStateOf<List<Blog>>(emptyList()) }
    LaunchedEffect(Unit) { blogs = Mogul.blogs.blogs() }
    MogulSelect(
        blogs,
        blogs.firstOrNull { it.id == selectedBlogId },
        onSelect = { onSelect(it.id) },
        optionLabel = { it.title },
        modifier = modifier,
        placeholder = t("selections.blogs"),
    )
}

@Composable
internal fun PageTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, color = palette.ink)
}

@Composable
internal fun RowRule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rule))
}

/** a text link, in the ink's bold, as the Vue client's anchors */
@Composable
internal fun Link(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text,
        modifier = modifier.pointerHoverIcon(PointerIcon.Hand).clickable(role = Role.Button, onClick = onClick).padding(vertical = 2.dp),
        color = palette.ink,
        fontWeight = FontWeight.SemiBold,
        fontSize = TextSize.base,
    )
}
