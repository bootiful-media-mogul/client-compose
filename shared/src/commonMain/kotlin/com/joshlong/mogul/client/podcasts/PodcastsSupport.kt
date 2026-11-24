package com.joshlong.mogul.client.podcasts

import com.joshlong.mogul.client.ui.FormSectionTitle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// the bits the podcast pages share

/** a page's heading, as the views' h1 */
@Composable
internal fun PageTitle(text: String) = com.joshlong.mogul.client.ui.PageTitle(text)

/** a heading inside a fieldset, as .form-section-title */
@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) = FormSectionTitle(text, modifier)

/** a coroutine whose failure is reported rather than taking the app down with it */
internal fun CoroutineScope.launchSafely(block: suspend CoroutineScope.() -> Unit) {
    launch {
        try {
            block()
        } catch (e: Exception) {
            println("podcasts: ${e.message}")
        }
    }
}

/** an id from a notification's context (a string, maybe written as a double) matches */
internal fun sameId(value: String?, id: Long): Boolean = value?.toDoubleOrNull()?.toLong() == id
