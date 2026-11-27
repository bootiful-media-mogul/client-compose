package com.joshlong.mogul.client.settings

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.i18n.Language
import com.joshlong.mogul.client.i18n.LanguageController
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.theme.TextSize
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.ui.FieldHint
import com.joshlong.mogul.client.ui.FieldRow
import com.joshlong.mogul.client.ui.FormSection
import com.joshlong.mogul.client.ui.MogulSelect
import kotlinx.coroutines.launch

/**
 * the language of the mogul's system: one of the languages the studio is written in. it is
 * applied here as soon as it's chosen, and saved to the account, where the api reads it for
 * everything it has the model write
 */
@Composable
internal fun LanguageSettings() {
    val scope = rememberCoroutineScope()
    var problem by remember { mutableStateOf<String?>(null) }
    FormSection(t("settings.language.title")) {
        FieldRow(t("settings.language.label")) {
            MogulSelect(
                Language.entries,
                LanguageController.current,
                { language ->
                    LanguageController.update(language)
                    scope.launch {
                        problem = runCatching { Mogul.mogul.setLanguage(language.code) }
                            .exceptionOrNull()
                            ?.let { it.message ?: it.toString() }
                    }
                },
                // each language under its own name, so it can be found by someone who can't read the current one
                { it.label },
                Modifier.widthIn(max = 448.dp),
            )
            FieldHint(t("settings.language.hint"))
            problem?.let {
                Text(t("settings.language.save-failed", "message" to it), color = palette.danger, fontSize = TextSize.sm)
            }
        }
    }
}
