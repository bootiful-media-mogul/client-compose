package com.joshlong.mogul.client.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.api.Notification
import com.joshlong.mogul.client.api.parseStringContext
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.managedfiles.PreviewStore
import com.joshlong.mogul.client.notes.NotesStore
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.transcripts.TranscriptsStore
import com.joshlong.mogul.client.ui.ButtonRow
import com.joshlong.mogul.client.ui.MogulButton
import kotlinx.coroutines.delay

/** how long a toast stays up */
private const val DISPLAY_MILLIS = 5000L

/** a notification as a sentence: its category's message, with its context's fields by name */
internal fun notificationMessage(notification: Notification): String = t(
    "notifications." + notification.category,
    // a json context's fields are there by name too, for messages that want, say, a reason
    parseStringContext(notification.context) + mapOf(
        "key" to notification.key,
        "mogulId" to notification.mogulId,
        "when" to notification.`when`,
        "context" to notification.context,
    ),
)

/** the toast and modal notifications (NotificationBoxComponent.vue), over everything */
@Composable
fun NotificationBox() {
    // the sidebar panels' content is only drawn while a panel is expanded, but they have to
    // hear what pages ask of them from the start. this is always on screen, so it starts them
    NotesStore.start()
    PreviewStore.start()
    TranscriptsStore.start()

    var message by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf(false) }
    var modal by remember { mutableStateOf(false) }
    // each notification restarts the toast's clock
    var shown by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        val unregister = Mogul.notifications.listen { notification ->
            message = notificationMessage(notification)
            modal = notification.modal
            toast = notification.visible
            if (notification.visible) shown++
        }
        onDispose { unregister() }
    }

    LaunchedEffect(shown) {
        if (shown == 0) return@LaunchedEffect
        delay(DISPLAY_MILLIS)
        toast = false
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = toast,
            enter = slideInVertically(tween(600)) { -it },
            exit = slideOutVertically(tween(600)) { -it },
        ) {
            Text(
                message,
                Modifier
                    .fillMaxWidth(0.4f)
                    .widthIn(min = 280.dp)
                    .shadow(8.dp, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(palette.raised)
                    .padding(Gutter.full),
                color = palette.ink,
                textAlign = TextAlign.Center,
            )
        }
    }

    if (modal) {
        Dialog(onDismissRequest = {
            modal = false
            toast = false
        }) {
            Column(
                Modifier.clip(RoundedCornerShape(16.dp)).background(palette.chrome).padding(Gutter.full),
                verticalArrangement = Arrangement.spacedBy(Gutter.full),
            ) {
                Text(message, color = palette.chromeInk)
                ButtonRow {
                    // the dialog is the chrome color, as the buttons are, so the button gets an edge
                    MogulButton(t("ok"), modifier = Modifier.border(1.dp, palette.chromeInk.copy(alpha = 0.4f), RoundedCornerShape(Gutter.radius)), onClick = {
                        modal = false
                        toast = false
                    })
                }
            }
        }
    }
}
