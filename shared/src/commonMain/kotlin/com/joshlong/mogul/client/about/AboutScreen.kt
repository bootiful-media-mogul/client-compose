package com.joshlong.mogul.client.about

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
        Text(t("about.title"), style = MaterialTheme.typography.headlineMedium, color = palette.ink)
        Text(t("app.made-with-love", "josh" to "Josh Long"), color = palette.ink)
    }
}
