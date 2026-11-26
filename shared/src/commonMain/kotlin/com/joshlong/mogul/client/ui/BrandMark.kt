package com.joshlong.mogul.client.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.nav.Route
import com.joshlong.mogul.client.nav.Router
import com.joshlong.mogul.client.resources.Res
import com.joshlong.mogul.client.resources.mogul_studio_logo
import org.jetbrains.compose.resources.painterResource

/**
 * the logo: the mark, "mogul" and "STUDIO", all one picture (brand/mogul-studio-logo.svg, which bin/brand.sh makes the drawable) with the
 * words as outlines, so it's the same picture at every size, in every layout and every palette,
 * with no line heights to shift one word against the other. it's drawn for the dark bars it sits
 * on. size is the "mogul"'s, in dp; the logo is 1.25 times that tall. it goes home when clicked,
 * unless onClick says otherwise, or is null for a picture of the logo that does nothing
 */
@Composable
internal fun BrandMark(size: Int, modifier: Modifier = Modifier, onClick: (() -> Unit)? = { Router.push(Route.Home) }) {
    // the drawable loads in the background, with no shape until it's there, so the box is sized from
    // the shape it will have rather than jumping when it arrives
    Image(
        painterResource(Res.drawable.mogul_studio_logo),
        contentDescription = t("brand.name") + " " + t("brand.product"),
        modifier = modifier
            .height((size * 1.25f).dp)
            .aspectRatio(LOGO_ASPECT)
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand)),
    )
}


/** brand/mogul-studio-logo.svg's width over its height, as brand/wordmark.py prints it */
private const val LOGO_ASPECT = 640.28f / 125f
