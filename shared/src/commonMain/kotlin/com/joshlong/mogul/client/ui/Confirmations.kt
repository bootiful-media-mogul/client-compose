package com.joshlong.mogul.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.palette
import kotlinx.coroutines.CompletableDeferred

/**
 * yes-or-no questions, for the apps, where nothing can block to wait for an answer the way
 * the browser's confirm() does: [ask] suspends until [ConfirmationDialog] is answered
 */
object Confirmations {

    internal class Pending(val message: String, val answer: CompletableDeferred<Boolean>)

    internal var pending by mutableStateOf<Pending?>(null)
        private set

    suspend fun ask(message: String): Boolean {
        val answer = CompletableDeferred<Boolean>()
        this.pending = Pending(message, answer)
        return try {
            answer.await()
        } finally {
            this.pending = null
        }
    }
}

/** the question [Confirmations.ask] is waiting on, if any, with OK and cancel */
@Composable
fun ConfirmationDialog() {
    val pending = Confirmations.pending ?: return
    Dialog(onDismissRequest = { pending.answer.complete(false) }) {
        Column(
            Modifier
                .widthIn(max = 480.dp)
                .shadow(24.dp, RoundedCornerShape(Gutter.radius))
                .clip(RoundedCornerShape(Gutter.radius))
                .background(palette.raised)
                .padding(Gutter.full),
            verticalArrangement = Arrangement.spacedBy(Gutter.full),
        ) {
            Text(pending.message, color = palette.ink)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gutter.half, Alignment.End)) {
                MogulButton(t("buttons.cancel"), onClick = { pending.answer.complete(false) })
                MogulButton(t("buttons.ok"), onClick = { pending.answer.complete(true) }, danger = true)
            }
        }
    }
}
