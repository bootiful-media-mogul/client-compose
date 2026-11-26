package com.joshlong.mogul.client.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
        // A date dialog's content is a Box: siblings overlap unless we provide a Column.
        // Own the shell so the calendar, time and actions have a predictable layout.
        Dialog(onDismissRequest = { open = false }) {
            MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
                primaryContainer = palette.accent,
                onPrimaryContainer = palette.accentInk,
                secondaryContainer = palette.fieldBg,
                onSecondaryContainer = palette.ink,
            )) {
                Surface(
                    modifier = Modifier.widthIn(max = 360.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = palette.raised,
                    border = BorderStroke(1.dp, palette.rule),
                ) {
                    Column {
                        Text(
                            label,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = Gutter.full),
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.ink,
                        )
                        HorizontalDivider(color = palette.rule)
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                            DatePicker(
                                state = dateState,
                                title = null,
                                headline = null,
                                showModeToggle = false,
                            )
                            HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = palette.rule)
                            Column(
                                Modifier.fillMaxWidth().padding(top = Gutter.full, bottom = Gutter.half),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(Gutter.half),
                            ) {
                                Text(zone.id, style = MaterialTheme.typography.bodySmall, color = palette.muted)
                                TimeInput(timeState)
                            }
                        }
                        HorizontalDivider(color = palette.rule)
                        Row(
                            Modifier.fillMaxWidth().padding(Gutter.full),
                            horizontalArrangement = Arrangement.spacedBy(Gutter.half, Alignment.End),
                        ) {
                            TextButton(onClick = { open = false }) { Text(t("buttons.cancel"), color = palette.muted) }
                            MogulButton(t("buttons.accept"), enabled = dateState.selectedDateMillis != null, onClick = {
                                val millis = dateState.selectedDateMillis ?: return@MogulButton
                                val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
                                // Preserve seconds when editing an existing timestamp.
                                onValueChange(LocalDateTime(date, LocalTime(timeState.hour, timeState.minute, local?.second ?: 0)).toInstant(zone))
                                open = false
                            })
                        }
                    }
                }
            }
        }
    }
}
