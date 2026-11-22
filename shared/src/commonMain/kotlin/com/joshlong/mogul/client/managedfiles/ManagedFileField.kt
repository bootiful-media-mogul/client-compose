package com.joshlong.mogul.client.managedfiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.events.previewManagedFile
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.platform.browser
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.TextSize
import com.joshlong.mogul.client.theme.fonts
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.ui.Icon
import com.joshlong.mogul.client.ui.IconButton
import com.joshlong.mogul.client.ui.MogulIcon
import kotlinx.coroutines.launch

/**
 * a managed file: what is uploaded, its visibility, preview and download, and an upload
 * button (ManagedFileComponent.vue). onHasData is told when the file has bytes. controls,
 * if given, lead the row, as the Vue component's slot did.
 */
@Composable
fun ManagedFileField(
    managedFileId: Long,
    accept: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onHasData: (Long) -> Unit = {},
    controls: (@Composable RowScope.() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val hasData by rememberUpdatedState(onHasData)
    var filename by remember(managedFileId) { mutableStateOf("") }
    var contentType by remember(managedFileId) { mutableStateOf("") }
    var size by remember(managedFileId) { mutableStateOf(0.0) }
    var written by remember(managedFileId) { mutableStateOf(false) }
    var visible by remember(managedFileId) { mutableStateOf(false) }
    var uploading by remember(managedFileId) { mutableStateOf(false) }
    var progress by remember(managedFileId) { mutableStateOf(0.0) }

    suspend fun load() {
        val file = runCatching { Mogul.managedFiles.managedFileById(managedFileId) }.getOrNull() ?: return
        filename = file.filename ?: ""
        written = file.written
        file.contentType?.takeIf { '/' in it }?.let { contentType = it.substringAfter('/') }
        // the file going from empty to having bytes is what tells the owner it has data
        if (size == 0.0 && file.size > 0) hasData(managedFileId)
        size = file.size
        visible = file.visible
    }

    LaunchedEffect(managedFileId) { load() }

    fun upload() {
        browser.pickFile(accept.ifBlank { "*/*" }) { picked ->
            picked ?: return@pickFile
            uploading = true
            progress = 0.0
            browser.upload(
                Mogul.managedFiles.uploadPath(managedFileId),
                picked,
                onProgress = { progress = it },
                onDone = { status ->
                    if (status !in 200..300) println("the http post to upload the file did not succeed: $status")
                    written = true
                    uploading = false
                    hasData(managedFileId)
                    scope.launch { load() }
                },
            )
        }
    }

    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Gutter.half),
    ) {
        controls?.invoke(this)
        IconButton(MogulIcon.Folder, t("managedfiles.upload"), onClick = { upload() }, enabled = enabled)
        // one icon whose picture and name follow the state
        IconButton(
            if (visible) MogulIcon.Eye else MogulIcon.EyeOff,
            if (visible) t("icons.hide-file") else t("icons.show-file"),
            onClick = {
                visible = !visible
                val now = visible
                scope.launch { Mogul.managedFiles.setManagedFileVisibility(managedFileId, now) }
            },
            enabled = enabled,
        )
        Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
            when {
                uploading -> Text(if (progress > 0) "${(progress * 100).toInt()}%" else "🕒", color = palette.muted, fontSize = TextSize.xs)
                written -> Icon(MogulIcon.Checkbox, t("icons.uploaded"))
            }
        }
        IconButton(
            MogulIcon.Preview,
            t("managedfiles.preview"),
            onClick = { previewManagedFile(managedFileId) },
            enabled = written,
        )
        Text(
            contentType,
            Modifier.width(80.dp),
            color = palette.ink,
            fontFamily = fonts.mono,
            fontSize = TextSize.sm,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            filename.ifEmpty { t("managedfiles.please-upload-a-file") },
            Modifier.weight(1f).padding(vertical = Gutter.half),
            color = palette.ink.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
