package com.joshlong.mogul.client.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.TextSize
import com.joshlong.mogul.client.theme.palette
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * a date and time, in the system's zone, in a form row like any other field. clicking the value
 * (never hovering or focusing it) opens the picker: the day, with the time in 24-hour form under it
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeField(label: String, value: Instant?, onValueChange: (Instant) -> Unit, modifier: Modifier = Modifier) {
    val zone = TimeZone.currentSystemDefault()
    var open by remember { mutableStateOf(false) }
    val local = value?.toLocalDateTime(zone)

    FieldRow(label, modifier) {
        FieldWell(Modifier.clickable(role = Role.Button) { open = true }, border = palette.fieldBorder, background = palette.fieldBg) {
            Text(dateTimeToString(value) ?: "", color = palette.ink, fontSize = TextSize.control)
        }
    }

    if (open) {
        // the picker works in UTC midnights; the day is read back in UTC so it doesn't shift
        val dateState = rememberDatePickerState(initialSelectedDateMillis = local?.date?.atTime(0, 0)?.toInstant(TimeZone.UTC)?.toEpochMilliseconds())
        val timeState = rememberTimePickerState(initialHour = local?.hour ?: 12, initialMinute = local?.minute ?: 0, is24Hour = true)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    open = false
                    val millis = dateState.selectedDateMillis ?: return@TextButton
                    val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
                    // the seconds are kept, so a save doesn't truncate a date nobody changed the time of
                    onValueChange(LocalDateTime(date, LocalTime(timeState.hour, timeState.minute, local?.second ?: 0)).toInstant(zone))
                }) { Text(t("buttons.accept"), color = palette.ink) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(t("buttons.cancel"), color = palette.ink) } },
        ) {
            DatePicker(dateState)
            Row(Modifier.fillMaxWidth().padding(Gutter.full), horizontalArrangement = Arrangement.Center) {
                TimeInput(timeState)
            }
        }
    }
}
