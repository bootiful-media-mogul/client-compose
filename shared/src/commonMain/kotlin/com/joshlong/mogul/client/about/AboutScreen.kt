package com.joshlong.mogul.client.about

import com.joshlong.mogul.client.ui.PageTitle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.joshlong.mogul.client.blogs.Link
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.platform.appVersion
import com.joshlong.mogul.client.platform.browser
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.palette

private const val SOURCE = "https://github.com/bootiful-media-mogul/studio-compose"

private const val SERVICES =
    "Auth0, AWS, Ably, Ayrshare, Bluesky, Cleanvoice, Elastic, Facebook, GitHub, " +
            "Google Business Profile, Instagram, LinkedIn, OpenAI, Pinterest, Podbean, Reddit, Snapchat, " +
            "Telegram, Threads, TikTok, WordPress, X, YouTube"

@Composable
fun AboutScreen() {
    val build =
        listOfNotNull(appVersion?.let { t("about.version", "version" to it) }, browser.origin)
    Column(verticalArrangement = Arrangement.spacedBy(Gutter.full)) {
        PageTitle(t("about.title"))
        Text(
            t("app.connected-to", "server" to build.joinToString(" · ")),
            color = palette.ink
        )
        Text(t("app.made-with-love", "josh" to "Joshua Long"), color = palette.ink)
        Text(t("about.services", "services" to SERVICES), color = palette.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(Gutter.full)) {
            Link(t("about.source")) { browser.open(SOURCE) }
            Link(t("about.license")) { browser.open("$SOURCE/blob/main/LICENSE") }
        }
    }
}
