package com.joshlong.mogul.client.compositions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import com.joshlong.mogul.client.api.Attachment
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.managedfiles.ManagedFileField
import com.joshlong.mogul.client.platform.browser
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.TextSize
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.ui.IconButton
import com.joshlong.mogul.client.ui.InputTool
import com.joshlong.mogul.client.ui.ListenForNotification
import com.joshlong.mogul.client.ui.MogulButton
import com.joshlong.mogul.client.ui.MogulIcon
import kotlinx.coroutines.launch

/**
 * a composition as a tool under its text field, as CompositionComponent.vue sat in an
 * InputWrapper: inserting an attachment puts its markdown into the field's text
 */
fun compositionTool(compositionId: Long): InputTool =
    InputTool(MogulIcon.Attachment, t("icons.attachments")) { value, onValueChange ->
        CompositionPanel(compositionId, onInsert = { markdown ->
            onValueChange(if (value.isEmpty() || value.endsWith("\n")) value + markdown else "$value\n$markdown")
        })
    }

/**
 * a composition: a field's attachments (CompositionComponent.vue). each attachment is a managed
 * file; its insert button hands the attachment's markdown to onInsert (the Vue client dropped
 * it at the caret; with no caret to go by, a caller appends it), or, with no onInsert, copies it
 */
@Composable
fun CompositionPanel(compositionId: Long, modifier: Modifier = Modifier, onInsert: ((String) -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    var attachments by remember { mutableStateOf<List<Attachment>>(emptyList()) }

    suspend fun reload() {
        attachments = Mogul.compositions.getCompositionById(compositionId).attachments
    }

    LaunchedEffect(compositionId) { reload() }

    // an upload changes an attachment's file, and with it the markdown that embeds it
    ListenForNotification("attachment-managed-file-updated-event") { notification ->
        val managedFileId = notification.key.toLongOrNull() ?: return@ListenForNotification
        if (attachments.any { it.managedFile.id == managedFileId }) {
            scope.launch { reload() }
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Gutter.half)) {
        if (attachments.isNotEmpty()) {
            Text(t("compositions.attachments.drag-and-drop-attachments"), color = palette.muted, fontSize = TextSize.sm)
        }
        attachments.forEach { attachment ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rule))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gutter.half)) {
                IconButton(
                    MogulIcon.Plus,
                    t("compositions.attachments.insert", "id" to attachment.id),
                    onClick = {
                        val markdown = attachment.markdown ?: ""
                        if (onInsert != null) onInsert(markdown) else browser.copyToClipboard(markdown)
                    },
                    size = 20.dp,
                )
                IconButton(
                    MogulIcon.Delete,
                    t("icons.delete-attachment"),
                    onClick = {
                        scope.launch {
                            Mogul.compositions.deleteCompositionAttachment(attachment.id)
                            reload()
                        }
                    },
                    size = 20.dp,
                )
                Box(Modifier.weight(1f).padding(vertical = Gutter.third)) {
                    ManagedFileField(attachment.managedFile.id, ".jpg,.png")
                }
            }
        }
        Row {
            MogulButton(t("compositions.buttons.add-attachment"), onClick = {
                scope.launch {
                    Mogul.compositions.createCompositionAttachment(compositionId)
                    reload()
                }
            })
        }
    }
}
