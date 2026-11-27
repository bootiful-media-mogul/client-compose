package com.joshlong.mogul.client.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.resources.Res
import com.joshlong.mogul.client.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * the studio's icons, the svgs in the Vue client's assets/images/icons, as vector drawables:
 * android can't draw an svg resource (painterResource throws, and the app dies), and every
 * platform draws these. the svgs that knocked a glyph out with a mask have it cut out of the
 * path itself, as a drawable has no masks. they're drawn in a single color, the current ink, as
 * Icon.vue drew them with currentColor
 */
enum class MogulIcon(val resource: DrawableResource) {
    Ai(Res.drawable.ic_ai),
    Attachment(Res.drawable.ic_attachment),
    AudioEditor(Res.drawable.ic_audio_editor),
    AudioFile(Res.drawable.ic_audio_file),
    Ayrshare(Res.drawable.ic_ayrshare),
    Back(Res.drawable.ic_back),
    Bold(Res.drawable.ic_bold),
    Checkbox(Res.drawable.ic_checkbox),
    Close(Res.drawable.ic_close),
    Collapse(Res.drawable.ic_collapse),
    Concise(Res.drawable.ic_concise),
    Delete(Res.drawable.ic_delete),
    Down(Res.drawable.ic_down),
    Edit(Res.drawable.ic_edit),
    Error(Res.drawable.ic_error),
    Eye(Res.drawable.ic_eye),
    EyeOff(Res.drawable.ic_eye_off),
    Folder(Res.drawable.ic_folder),
    Friendly(Res.drawable.ic_friendly),
    H1(Res.drawable.ic_h1),
    H2(Res.drawable.ic_h2),
    H3(Res.drawable.ic_h3),
    Home(Res.drawable.ic_home),
    Image(Res.drawable.ic_image),
    ImageFile(Res.drawable.ic_image_file),
    Italic(Res.drawable.ic_italic),
    Jobs(Res.drawable.ic_jobs),
    Link(Res.drawable.ic_link),
    List(Res.drawable.ic_list),
    Markdown(Res.drawable.ic_markdown),
    Maximize(Res.drawable.ic_maximize),
    MusicFile(Res.drawable.ic_music_file),
    Notes(Res.drawable.ic_notes),
    Plus(Res.drawable.ic_plus),
    Podcast(Res.drawable.ic_podcast),
    Preview(Res.drawable.ic_preview),
    Professional(Res.drawable.ic_professional),
    Proofread(Res.drawable.ic_proofread),
    Rewrite(Res.drawable.ic_rewrite),
    Rss(Res.drawable.ic_rss),
    Search(Res.drawable.ic_search),
    Settings(Res.drawable.ic_settings),
    Transcript(Res.drawable.ic_transcript),
    Up(Res.drawable.ic_up),
}

/** an icon, drawn in tint (the ink, by default); Color.Unspecified keeps a logo's own colors */
@Composable
fun Icon(
    icon: MogulIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = palette.ink,
) {
    Image(
        painter = painterResource(icon.resource),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = if (tint == Color.Unspecified) null else ColorFilter.tint(tint),
    )
}

/**
 * an icon that does something when clicked, as Icon.vue with a click handler. it takes the
 * hover color while the pointer or the keyboard focus is on it; a disabled one is muted and
 * does nothing. a sticky (toggle) icon shows hoverTint while it's on.
 */
@Composable
fun IconButton(
    icon: MogulIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 24.dp,
    tint: Color = palette.ink,
    hoverTint: Color = palette.accent,
    toggled: Boolean? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val color = when {
        // as the .disabled class: the icon's own ink, faded, so it reads as there but unavailable
        !enabled -> tint.copy(alpha = 0.3f)
        toggled == true || hovered || focused -> hoverTint
        else -> tint
    }
    val clickModifier = if (toggled != null) {
        Modifier.toggleable(toggled, interactionSource = interaction, indication = null, enabled = enabled, role = Role.Switch) { onClick() }
    } else {
        Modifier.clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
    }
    Box(
        modifier = modifier
            .hoverable(interaction)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .then(clickModifier)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, size = size, tint = color)
    }
}
