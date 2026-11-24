package com.joshlong.mogul.client.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joshlong.mogul.client.theme.Palette
import com.joshlong.mogul.client.theme.palette

/**
 * how anything floating over the page (the mobile toolbar, the menus that drop down) stands
 * off what's under it, so they all do it alike. a shadow alone barely reads on the dark
 * palettes, whose page is near black already, and not at all where a bar floats over a bar
 * of its own color, so a floating thing has a hairline edge too: the palette's rule, which is
 * translucent ink, so it shows lighter than any fill in the dark and darker in the day
 */
object Floating {

    /** deep enough that the shadow shows past the edge on the dark palettes */
    val elevation: Dp = 16.dp

    val edgeWidth: Dp = 1.dp

    fun edge(palette: Palette): BorderStroke = BorderStroke(this.edgeWidth, palette.rule)
}

/** [Floating]'s shadow and edge around a [fill] of this [shape], clipped to it */
@Composable
fun Modifier.floating(shape: Shape, fill: Color): Modifier {
    val edge = Floating.edge(palette)
    return this
        .shadow(Floating.elevation, shape)
        .background(fill, shape)
        .border(edge, shape)
}

/** a menu that drops down from what opened it, standing off the page as [Floating] has it */
@Composable
fun FloatingMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = MenuDefaults.shape,
        containerColor = palette.raised,
        // no tint toward the color scheme's primary: the fill is the palette's raised, exactly
        tonalElevation = 0.dp,
        shadowElevation = Floating.elevation,
        border = Floating.edge(palette),
        content = content,
    )
}
