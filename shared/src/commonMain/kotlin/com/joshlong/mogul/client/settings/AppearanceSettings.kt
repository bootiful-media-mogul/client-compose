package com.joshlong.mogul.client.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.theme.DarkPalette
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.TextSize
import com.joshlong.mogul.client.theme.ThemeController
import com.joshlong.mogul.client.theme.ThemeMode
import com.joshlong.mogul.client.theme.fonts
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.theme.paletteFor
import com.joshlong.mogul.client.ui.BrandMark
import com.joshlong.mogul.client.ui.FieldHint
import com.joshlong.mogul.client.ui.FieldRow
import com.joshlong.mogul.client.ui.FormSection
import com.joshlong.mogul.client.ui.MogulCheckbox
import com.joshlong.mogul.client.ui.MogulSelect

/** an hour as a clock shows it, e.g. `7 PM` */
private fun hourLabel(hour: Int): String = "${if (hour % 12 == 0) 12 else hour % 12} ${if (hour < 12) "AM" else "PM"}"

/**
 * the studio's appearance (AppearanceSettings.vue). the choice is per-browser, so it is
 * applied as soon as it's made: there is nothing to save
 */
@Composable
internal fun AppearanceSettings() {
    val preferences = ThemeController.preferences
    val showing = t("settings.appearance.palettes.${ThemeController.applied.name.lowercase()}")
    FormSection(t("settings.appearance.title")) {
        FieldRow(t("settings.appearance.mode")) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Gutter.full)) {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        Modifier.selectable(preferences.mode == mode, role = Role.RadioButton) {
                            ThemeController.update { it.copy(mode = mode) }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = preferences.mode == mode,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = palette.accent, unselectedColor = palette.fieldBorder),
                        )
                        Text(t("settings.appearance.modes.${mode.key}"), color = palette.ink, fontSize = TextSize.sm)
                    }
                }
            }
            FieldHint(t("settings.appearance.showing", "theme" to showing))
        }

        if (preferences.mode == ThemeMode.Auto) {
            MogulCheckbox(
                preferences.followSystem,
                { checked -> ThemeController.update { it.copy(followSystem = checked) } },
                t("settings.appearance.follow-system"),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Gutter.half),
                verticalArrangement = Arrangement.spacedBy(Gutter.half),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                val hours = (0 until 24).toList()
                Text(t("settings.appearance.evening-from"), color = palette.ink)
                MogulSelect(
                    hours, preferences.evening, { hour -> ThemeController.update { it.copy(evening = hour) } }, ::hourLabel,
                    Modifier.width(120.dp),
                )
                Text(t("settings.appearance.evening-until"), color = palette.ink)
                MogulSelect(
                    hours, preferences.morning, { hour -> ThemeController.update { it.copy(morning = hour) } }, ::hourLabel,
                    Modifier.width(120.dp),
                )
            }
        }

        if (preferences.mode != ThemeMode.Light) {
            FieldRow(t("settings.appearance.dark-palette")) {
                Column(Modifier.widthIn(max = 448.dp), verticalArrangement = Arrangement.spacedBy(Gutter.full)) {
                    DarkPalette.entries.forEach { dark ->
                        PaletteCard(dark, preferences.dark == dark) { ThemeController.update { it.copy(dark = dark) } }
                    }
                }
            }
        }
    }
}

/** a miniature studio drawn in the palette's own colors: the cards are previews, not pictures */
@Composable
private fun PaletteCard(dark: DarkPalette, selected: Boolean, onClick: () -> Unit) {
    val p = paletteFor(dark.applied)
    val outline = if (selected) palette.accent else androidx.compose.ui.graphics.Color.Transparent
    Box(Modifier.border(2.dp, outline, RoundedCornerShape(Gutter.radius + 4.dp)).padding(4.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Gutter.radius))
                .background(p.bg)
                .selectable(selected, role = Role.Button, onClick = onClick),
        ) {
            Row(Modifier.fillMaxWidth().background(p.topbar).padding(horizontal = 10.dp, vertical = 6.dp)) {
                BrandMark(16, onClick = null)
            }
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Gutter.radius)).background(p.panelBg).padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        t("settings.appearance.palettes.${dark.key}"),
                        Modifier.fillMaxWidth().background(p.chrome).padding(horizontal = 8.dp, vertical = 3.dp),
                        color = p.chromeInk,
                        fontFamily = fonts.display,
                        fontSize = TextSize.sm,
                    )
                    Box(Modifier.padding(horizontal = 8.dp).fillMaxWidth().height(6.dp).alpha(0.75f).clip(RoundedCornerShape(16.dp)).background(p.ink))
                    Box(Modifier.padding(horizontal = 8.dp).fillMaxWidth(0.55f).height(6.dp).clip(RoundedCornerShape(16.dp)).background(p.muted))
                    Box(
                        Modifier.padding(horizontal = 8.dp).fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp))
                            .background(p.fieldBg).border(1.dp, p.accent, RoundedCornerShape(4.dp)),
                    )
                    Box(Modifier.padding(horizontal = 8.dp).width(48.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(p.chrome))
                }
            }
            Text(
                t("settings.appearance.blurbs.${dark.key}"),
                Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                color = p.muted,
                fontSize = TextSize.sm,
                lineHeight = 20.sp,
            )
        }
    }
}
