package com.joshlong.mogul.client.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * the studio's colors, the tokens base.css defines. the day palette is the original sky-blue
 * one; the dark palettes redefine the same tokens, so a component never knows which palette
 * it is drawn in. `chrome*` is the dark bar family (panel headers, legends, buttons, tabs);
 * `raised*` is the light one (popovers, active tool tabs).
 */
@Immutable
data class Palette(
    val name: AppliedTheme,
    val dark: Boolean,
    val bg: Color,
    val panelBg: Color,
    val raised: Color,
    val chrome: Color,
    val chromeHover: Color,
    val chromeInk: Color,
    val chromeMuted: Color,
    val ink: Color,
    val muted: Color,
    val label: Color,
    /** translucent ink, so a line reads the same on the page and on a panel */
    val rule: Color,
    val fieldBg: Color,
    val fieldBorder: Color,
    val accent: Color,
    val accentHover: Color,
    val accentInk: Color,
    val disabled: Color,
    val disabledInk: Color,
    val danger: Color,
    /** the band across the top of the page: darker than the chrome, dark in every palette */
    val topbar: Color,
    /** a top-bar icon's hover and the rule under the section you're in: the palette's signature hue */
    val brand: Color,
) {
    /** a publication panel and its tab: a shade off the panel it sits in */
    val publicationPanelBg: Color get() = lerp(this.panelBg, this.ink, 0.07f)

    /** a faint wash of ink, for a hovered row or a shaded region */
    val hoverTint: Color get() = this.ink.copy(alpha = 0.08f)

    /** the keyboard focus ring */
    val focus: Color get() = this.accent

    /** the page color, lifted toward white */
    val bgLight: Color get() = if (this.dark) lerp(this.bg, Color.White, 0.12f) else lerp(this.bg, Color.White, 0.75f)
}

enum class AppliedTheme { Light, Midnight, Graphite, Ember }

private fun hex(value: Long): Color = Color(0xFF000000 or value)

private fun rgba(r: Int, g: Int, b: Int, a: Float): Color = Color(r, g, b, (a * 255).toInt())

val LightPalette = Palette(
    name = AppliedTheme.Light, dark = false,
    bg = hex(0x9ccbe8), panelBg = hex(0xf3f7fb), raised = hex(0xffffff),
    chrome = hex(0x132a52), chromeHover = hex(0x1f3d70), chromeInk = hex(0xf2f6fc), chromeMuted = hex(0x9fb3d6),
    ink = hex(0x0a0f1a), muted = hex(0x405069), label = hex(0x1b2840), rule = rgba(10, 15, 26, 0.2f),
    fieldBg = hex(0xffffff), fieldBorder = hex(0x74889f),
    accent = hex(0x1f4f9c), accentHover = hex(0x1f3d70), accentInk = hex(0xffffff),
    disabled = hex(0x8fa2bd), disabledInk = hex(0xf2f6fc), danger = hex(0xc0392b),
    topbar = hex(0x0a0f1a), brand = hex(0x9ccbe8),
)

/** the day theme after sunset: the same navy family, deepened, with the sky blue kept */
val MidnightPalette = Palette(
    name = AppliedTheme.Midnight, dark = true,
    bg = hex(0x0b1424), panelBg = hex(0x131f36), raised = hex(0x1b2a47),
    chrome = hex(0x24467c), chromeHover = hex(0x2e5592), chromeInk = hex(0xeef3fb), chromeMuted = hex(0xa9bcdc),
    ink = hex(0xe3ebf7), muted = hex(0x93a5c2), label = hex(0xc6d4ea), rule = rgba(227, 235, 247, 0.16f),
    fieldBg = hex(0x0d1729), fieldBorder = hex(0x5a77a3),
    accent = hex(0x74aef5), accentHover = hex(0x93c1f8), accentInk = hex(0x0a0f1a),
    disabled = hex(0x1e2a40), disabledInk = hex(0x5f7192), danger = hex(0xff7a6b),
    topbar = hex(0x050a14), brand = hex(0x9ccbe8),
)

/** a neutral console grey with almost no hue, so the media carries the color */
val GraphitePalette = Palette(
    name = AppliedTheme.Graphite, dark = true,
    bg = hex(0x111316), panelBg = hex(0x1a1d21), raised = hex(0x24282e),
    chrome = hex(0x333a44), chromeHover = hex(0x3f4854), chromeInk = hex(0xeceff3), chromeMuted = hex(0xa6aebb),
    ink = hex(0xe5e7ea), muted = hex(0x99a0aa), label = hex(0xc8ccd3), rule = rgba(229, 231, 234, 0.14f),
    fieldBg = hex(0x141619), fieldBorder = hex(0x666f7c),
    accent = hex(0x7cc4ee), accentHover = hex(0x9dd3f3), accentInk = hex(0x0a0f1a),
    disabled = hex(0x24282e), disabledInk = hex(0x646b76), danger = hex(0xff7a6b),
    topbar = hex(0x08090b), brand = hex(0x7cc4ee),
)

/** warm and low in blue, for late nights in the booth; the accent is an on-air lamp's amber */
val EmberPalette = Palette(
    name = AppliedTheme.Ember, dark = true,
    bg = hex(0x16110e), panelBg = hex(0x201915), raised = hex(0x2b221d),
    chrome = hex(0x4a3426), chromeHover = hex(0x5a3f2e), chromeInk = hex(0xf7ede4), chromeMuted = hex(0xd0b39d),
    ink = hex(0xefe4da), muted = hex(0xb09e90), label = hex(0xddcbbc), rule = rgba(239, 228, 218, 0.14f),
    fieldBg = hex(0x18120f), fieldBorder = hex(0x806754),
    accent = hex(0xf0a04b), accentHover = hex(0xf5b46c), accentInk = hex(0x1a1008),
    disabled = hex(0x2c231d), disabledInk = hex(0x76655a), danger = hex(0xff7a6b),
    topbar = hex(0x0e0a08), brand = hex(0xf0a04b),
)

fun paletteFor(theme: AppliedTheme): Palette = when (theme) {
    AppliedTheme.Light -> LightPalette
    AppliedTheme.Midnight -> MidnightPalette
    AppliedTheme.Graphite -> GraphitePalette
    AppliedTheme.Ember -> EmberPalette
}
