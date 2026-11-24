package com.joshlong.mogul.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.platform.signIn
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.ui.BrandMark
import com.joshlong.mogul.client.ui.MogulButton
import kotlinx.coroutines.launch

/**
 * the apps' front door: they're oauth clients of their own, so they sign in to auth0 in the
 * system browser and come back here. the web build never shows it
 */
@Composable
internal fun SignInScreen(onSignedIn: () -> Unit) {
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(palette.topbar), contentAlignment = Alignment.Center) {
        Column(
            Modifier.safeDrawingPadding().widthIn(max = 420.dp).padding(Gutter.full),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Gutter.full),
        ) {
            BrandMark(48)
            Text(t("app.sign-in.prompt"), color = palette.chromeInk, textAlign = TextAlign.Center)
            MogulButton(t("app.sign-in"), enabled = !working, onClick = {
                working = true
                failure = null
                scope.launch {
                    try {
                        signIn.signIn()
                        onSignedIn()
                    } catch (e: Exception) {
                        failure = t("app.sign-in.failed", "message" to (e.message ?: e::class.simpleName))
                    } finally {
                        working = false
                    }
                }
            })
            failure?.let { Text(it, color = palette.chromeMuted, textAlign = TextAlign.Center) }
        }
    }
}
