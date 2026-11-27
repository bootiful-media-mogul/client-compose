package com.joshlong.mogul.client.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.joshlong.mogul.client.platform.browser

/**
 * the language the studio is shown in. the choice belongs to the account: the api keeps it,
 * and has the model write in it. it is kept on this device as well (localStorage, or the
 * app's settings), so the first frame and the sign-in screen are in it before the api has
 * answered. with nothing chosen anywhere, the studio follows the device's own language
 * where it has it, and is in English where it doesn't
 */
object LanguageController {

    private const val STORAGE_KEY = "mogul.language"

    /** what the device itself is set to, if the studio has that language */
    val system: Language = Language.of(browser.language()) ?: Language.English

    /** the language last chosen, here or on the account; null follows the device */
    var preference: Language? by mutableStateOf(Language.of(browser.localGet(STORAGE_KEY)))
        private set

    val current: Language get() = this.preference ?: this.system

    private var started = false

    fun update(language: Language) {
        this.preference = language
        browser.localSet(STORAGE_KEY, language.code)
        apply()
    }

    /**
     * takes on the account's language at sign-in, when the studio has it: the account wins
     * over whatever this device had.
     * @return the language to tell the api, when the account has none yet; otherwise null
     */
    fun adopt(accountLanguage: String?): Language? {
        val known = Language.of(accountLanguage)
        if (known != null) {
            if (known != this.preference) update(known)
            return null
        }
        // a language the studio has no translation for was put there by something else: leave it
        return if (accountLanguage.isNullOrBlank()) this.current else null
    }

    private fun apply() {
        currentLanguage = this.current
        browser.setDocumentLanguage(this.current.code)
    }

    /** applies the language, and keeps it current when another tab changes the choice */
    fun start() {
        apply()
        if (this.started) return
        this.started = true
        browser.onStorage { key, value ->
            if (key == STORAGE_KEY) {
                this.preference = Language.of(value)
                apply()
            }
        }
    }
}
