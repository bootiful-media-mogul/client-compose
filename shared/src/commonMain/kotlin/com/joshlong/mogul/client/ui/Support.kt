package com.joshlong.mogul.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.api.Notification
import com.joshlong.mogul.client.platform.browser
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** enter submits a single-line field */
fun Modifier.onEnterSubmit(singleLine: Boolean, onSubmit: (() -> Unit)?): Modifier =
    if (!singleLine || onSubmit == null) this
    else this.onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter) && !event.isShiftPressed) {
            onSubmit()
            true
        } else {
            false
        }
    }

/**
 * listens for a notification category while the calling composable is on screen, as the
 * Vue components did with listenForCategory and unregistered on unmount
 */
@Composable
fun ListenForNotification(category: String, onNotification: (Notification) -> Unit) {
    val current = rememberUpdatedState(onNotification)
    DisposableEffect(category) {
        val unregister = Mogul.notifications.listenForCategory(category) { current.value(it) }
        onDispose { unregister() }
    }
}

/** asks before deleting anything: the browser's own confirmation on the web, as the Vue client did, and a dialog in the apps */
suspend fun confirmDeletion(message: String): Boolean = browser.confirm(message)

// dates, as dates.ts formatted them (en-US, two-digit fields)

private fun two(n: Int) = n.toString().padStart(2, '0')

/** `MM/dd/yy, hh:mm AM`, in the browser's zone, or null for no date */
fun dateTimeToString(instant: Instant?): String? {
    instant ?: return null
    val t = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val hour12 = if (t.hour % 12 == 0) 12 else t.hour % 12
    val ampm = if (t.hour < 12) "AM" else "PM"
    return "${two(t.month.ordinal + 1)}/${two(t.day)}/${two(t.year % 100)}, ${two(hour12)}:${two(t.minute)} $ampm"
}

/** `MM/dd/yy` */
fun dateToString(instant: Instant?): String? {
    instant ?: return null
    val t = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${two(t.month.ordinal + 1)}/${two(t.day)}/${two(t.year % 100)}"
}

fun dateToString(date: LocalDate): String = "${two(date.month.ordinal + 1)}/${two(date.day)}/${two(date.year % 100)}"

/** a duration in milliseconds, as hh:mm:ss */
fun durationToString(milliseconds: Double?): String? {
    if (milliseconds == null || milliseconds.isNaN() || milliseconds < 0) return null
    val totalSeconds = (milliseconds / 1000).toLong()
    return "${two((totalSeconds / 3600).toInt())}:${two(((totalSeconds % 3600) / 60).toInt())}:${two((totalSeconds % 60).toInt())}"
}

/** a size in bytes, e.g. `1.50 MB` */
fun prettyPrintInBytes(bytes: Double): String {
    val units = listOf("bytes", "KB", "MB", "GB", "TB", "PB")
    var value = bytes
    var unit = 0
    while (value >= 1024 && unit < units.size - 1) {
        value /= 1024
        unit++
    }
    val hundredths = kotlin.math.round(value * 100).toLong()
    return "${hundredths / 100}.${two((hundredths % 100).toInt())} ${units[unit]}"
}
