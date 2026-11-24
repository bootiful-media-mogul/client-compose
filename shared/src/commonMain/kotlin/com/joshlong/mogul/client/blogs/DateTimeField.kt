package com.joshlong.mogul.client.blogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
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
import com.joshlong.mogul.client.theme.TextSize
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.ui.FieldRow
import com.joshlong.mogul.client.ui.FieldWell
import com.joshlong.mogul.client.ui.dateTimeToString
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * a date and time, picked on click (never on hover or focus): first the day, then the time,
 * in 24-hour form, as the Vue client's v-calendar dateTime picker
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateTimeField(label: String, value: Instant?, onValueChange: (Instant) -> Unit, modifier: Modifier = Modifier) {
    val zone = TimeZone.currentSystemDefault()
    var pickingDate by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<kotlinx.datetime.LocalDate?>(null) }
    val local = value?.toLocalDateTime(zone)

    FieldRow(label, modifier) {
        FieldWell(Modifier.clickable(role = Role.Button) { pickingDate = true }, border = palette.fieldBorder, background = palette.fieldBg) {
            Text(dateTimeToString(value) ?: "", color = palette.ink, fontSize = TextSize.control)
        }
    }

    if (pickingDate) {
        // the picker works in UTC midnights; the day is read back in UTC so it doesn't shift
        val initial = local?.date?.atTime(0, 0)?.toInstant(TimeZone.UTC)?.toEpochMilliseconds()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickingDate = false
                    state.selectedDateMillis?.let {
                        pickedDate = Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date
                    }
                }) { Text(t("buttons.accept"), color = palette.ink) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(t("buttons.cancel"), color = palette.ink) } },
        ) {
            DatePicker(state)
        }
    }

    pickedDate?.let { date ->
        val state = rememberTimePickerState(initialHour = local?.hour ?: 12, initialMinute = local?.minute ?: 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickedDate = null },
            confirmButton = {
                TextButton(onClick = {
                    pickedDate = null
                    // the seconds are kept, so a save doesn't truncate a date nobody changed the time of
                    val time = LocalTime(state.hour, state.minute, local?.second ?: 0)
                    onValueChange(LocalDateTime(date, time).toInstant(zone))
                }) { Text(t("buttons.accept"), color = palette.ink) }
            },
            dismissButton = { TextButton(onClick = { pickedDate = null }) { Text(t("buttons.cancel"), color = palette.ink) } },
            text = { TimePicker(state) },
            containerColor = palette.raised,
        )
    }
}
