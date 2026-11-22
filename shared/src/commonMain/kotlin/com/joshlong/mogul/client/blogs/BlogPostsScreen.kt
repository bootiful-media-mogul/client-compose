package com.joshlong.mogul.client.blogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.joshlong.mogul.client.api.Blog
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.api.Post
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
import kotlinx.coroutines.launch

/** a blog's posts (BlogsPostsSearchView.vue) */
@Composable
fun BlogPostsScreen(blogId: Long) {
    val scope = rememberCoroutineScope()
    var blog by remember { mutableStateOf<Blog?>(null) }
    var posts by remember { mutableStateOf<List<Post>>(emptyList()) }

    suspend fun loadBlog() {
        blog = Mogul.blogs.blogById(blogId)
        posts = Mogul.blogs.blogPostPreviewsByBlog(blogId)
    }

    LaunchedEffect(blogId) { loadBlog() }

    Column(verticalArrangement = Arrangement.spacedBy(Gutter.full)) {
        PageTitle(t("blogs.posts.title"))
        FormSection(
            t("blogs.posts.posts-for", "title" to (blog?.title ?: "")),
            actions = {
                IconButton(MogulIcon.Plus, t("icons.new-post"), onClick = {
                    scope.launch {
                        val post = Mogul.blogs.createPost(blogId, "", "", "")
                        Router.push(Route.BlogPost(blogId, post.id))
                    }
                }, tint = palette.chromeInk, hoverTint = palette.brand)
            },
        ) {
            posts.forEach { post ->
                PostResult(post, onEdit = { Router.push(Route.BlogPost(blogId, post.id)) }, onDelete = {
                    if (confirmDeletion(t("confirm.deletion", "title" to post.title))) {
                        scope.launch {
                            Mogul.blogs.deletePost(post.id)
                            loadBlog()
                        }
                    }
                })
            }
        }
    }
}

/** a post as a search result (Result.vue): its kind's mark, title, then edit, date, id and delete */
@Composable
private fun PostResult(post: Post, onEdit: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        RowRule()
        Row(Modifier.padding(vertical = Gutter.half), horizontalArrangement = Arrangement.spacedBy(Gutter.half)) {
            Icon(MogulIcon.Rss, null, size = 36.dp, tint = palette.muted)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Gutter.full)) {
                Text(post.title, color = palette.ink)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(MogulIcon.Edit, t("icons.edit", "name" to post.title), onClick = onEdit)
                    Text(
                        dateTimeToString(post.created) ?: "",
                        Modifier.weight(1f).padding(end = Gutter.half),
                        color = palette.ink,
                        textAlign = TextAlign.End,
                    )
                    Text("#${post.id}", Modifier.weight(1f).padding(start = Gutter.half), color = palette.ink)
                    IconButton(MogulIcon.Delete, t("icons.delete", "name" to post.title), onClick = onDelete)
                }
            }
        }
    }
}
