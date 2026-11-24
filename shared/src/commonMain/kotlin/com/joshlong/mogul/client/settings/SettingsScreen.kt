package com.joshlong.mogul.client.settings

import com.joshlong.mogul.client.ui.PageTitle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.api.Setting
import com.joshlong.mogul.client.api.SettingsPage
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.i18n.te
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.ui.FieldHint
import com.joshlong.mogul.client.ui.FieldRow
import com.joshlong.mogul.client.ui.FormSection
import com.joshlong.mogul.client.ui.MogulButton
import com.joshlong.mogul.client.ui.MogulCheckbox
import com.joshlong.mogul.client.ui.MogulTextField
import kotlinx.coroutines.launch

// sections that aren't publisher plugins, like cleanvoice, keep their labels under
// settings.categories; the plugins' have always been under publications.plugins
private fun label(category: String, name: String): String {
    val own = "settings.categories.$category.$name"
    return if (te(own)) t(own) else t("publications.plugins.$category.$name")
}

@Composable
fun SettingsScreen() {
    val scope = rememberCoroutineScope()
    var pages by remember { mutableStateOf<List<SettingsPage>>(emptyList()) }
    // what's in the fields, by category and name, and what the server said, to save only changes
    val edited = remember { mutableStateMapOf<String, String>() }
    var loaded by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    fun keyOf(category: String, name: String) = "$category/$name"

    suspend fun reload() {
        val fresh = runCatching { Mogul.settings.settings() }.getOrElse {
            println("could not load the settings: ${it.message}")
            return
        }
        // an unset checkbox shows what unset means, so saving without touching it changes nothing
        val values = fresh.flatMap { page ->
            page.settings.map { s ->
                val value = if (s.type == "BOOLEAN" && s.value.isNullOrEmpty()) s.defaultValue ?: "false" else s.value ?: ""
                keyOf(page.category, s.name) to value
            }
        }.toMap()
        pages = fresh
        loaded = values
        edited.clear()
        edited.putAll(values)
    }

    suspend fun save(page: SettingsPage) {
        for (setting in page.settings) {
            val key = keyOf(page.category, setting.name)
            val value = edited[key] ?: ""
            if (loaded[key] != value) {
                runCatching { Mogul.settings.updateSetting(page.category, setting.name, value) }
                reload()
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Gutter.full)) {
        PageTitle(t("settings.title"))
        AppearanceSettings()
        pages.forEach { page ->
            val plugin = label(page.category, "title")
            FormSection(plugin) {
                page.settings.forEach { setting ->
                    val key = keyOf(page.category, setting.name)
                    SettingField(page.category, setting, edited[key] ?: "") { edited[key] = it }
                }
                if (page.settings.isNotEmpty()) {
                    Row {
                        MogulButton(t("settings.save-button", "plugin" to plugin), onClick = { scope.launch { save(page) } })
                    }
                } else {
                    Text(t("settings.no-configuration-for-plugin", "plugin" to plugin), color = palette.ink)
                }
            }
        }
    }
}

@Composable
private fun SettingField(category: String, setting: Setting, value: String, onValueChange: (String) -> Unit) {
    val name = label(category, setting.name)
    if (setting.type == "BOOLEAN") {
        MogulCheckbox(value == "true", { onValueChange(if (it) "true" else "false") }, name)
        return
    }
    FieldRow(name) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gutter.half)) {
            val secret = setting.type != "TEXT"
            var revealed by remember { mutableStateOf(false) }
            MogulTextField(value, onValueChange, Modifier.weight(1f), password = secret && !revealed, withWritingTools = !secret)
            if (secret) PasswordReveal(revealed) { revealed = it }
        }
        if (setting.required && !setting.valid) FieldHint(t("labels.required-value"))
        if (!setting.required) FieldHint(t("labels.optional-value"))
    }
}

/** the eye at a secret field's end, showing and hiding what's in it (PrivatePasswordInputComponent.vue) */
@Composable
private fun PasswordReveal(revealed: Boolean, onToggle: (Boolean) -> Unit) {
    com.joshlong.mogul.client.ui.IconButton(
        if (revealed) com.joshlong.mogul.client.ui.MogulIcon.EyeOff else com.joshlong.mogul.client.ui.MogulIcon.Eye,
        t("icons.show-password"),
        onClick = { onToggle(!revealed) },
        size = 24.dp,
        toggled = revealed,
    )
}
