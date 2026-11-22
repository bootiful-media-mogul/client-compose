package com.joshlong.mogul.client.about

import com.joshlong.mogul.client.ui.PageTitle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.palette

@Composable
fun AboutScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(Gutter.full)) {
        PageTitle(t("about.title"))
        Text(t("app.made-with-love", "josh" to "Josh Long"), color = palette.ink)
    }
}
