package com.joshlong.mogul.client.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joshlong.mogul.client.resources.Res
import com.joshlong.mogul.client.resources.ibmplexmono_medium
import com.joshlong.mogul.client.resources.ibmplexmono_regular
import com.joshlong.mogul.client.resources.ibmplexsans_italic
import com.joshlong.mogul.client.resources.ibmplexsans_medium
import com.joshlong.mogul.client.resources.ibmplexsans_regular
import com.joshlong.mogul.client.resources.ibmplexsans_semibold
import com.joshlong.mogul.client.resources.spacegrotesk_bold
import com.joshlong.mogul.client.resources.spacegrotesk_medium
import org.jetbrains.compose.resources.Font

/** the base.css spacing: everything is a multiple of the 12pt gutter */
object Gutter {
    val full: Dp = 16.dp
    val half: Dp = 8.dp
    val third: Dp = 5.dp
    val small: Dp = 8.dp
    val radius: Dp = 8.dp
}

/** the broadcast theme's type: Space Grotesk for display, IBM Plex for body and code */
data class MogulFonts(val display: FontFamily, val body: FontFamily, val mono: FontFamily)

/** the app text scale, from base.css */
object TextSize {
    val xs = 12.sp
    val sm = 15.sp
    val base = 16.sp
    val control = 17.sp
    val lg = 20.sp
}

val LocalPalette = staticCompositionLocalOf { LightPalette }

val LocalFonts = staticCompositionLocalOf { MogulFonts(FontFamily.SansSerif, FontFamily.SansSerif, FontFamily.Monospace) }

/** the palette in effect, e.g. `palette.ink` */
val palette: Palette @Composable get() = LocalPalette.current

val fonts: MogulFonts @Composable get() = LocalFonts.current

@Composable
fun MogulTheme(palette: Palette = paletteFor(ThemeController.applied), content: @Composable () -> Unit) {
    val fonts = MogulFonts(
        display = FontFamily(
            Font(Res.font.spacegrotesk_medium, FontWeight.Medium),
            Font(Res.font.spacegrotesk_bold, FontWeight.Bold),
        ),
        body = FontFamily(
            Font(Res.font.ibmplexsans_regular, FontWeight.Normal),
            Font(Res.font.ibmplexsans_italic, FontWeight.Normal, FontStyle.Italic),
            Font(Res.font.ibmplexsans_medium, FontWeight.Medium),
            Font(Res.font.ibmplexsans_semibold, FontWeight.SemiBold),
            Font(Res.font.ibmplexsans_semibold, FontWeight.Bold),
        ),
        mono = FontFamily(
            Font(Res.font.ibmplexmono_regular, FontWeight.Normal),
            Font(Res.font.ibmplexmono_medium, FontWeight.Medium),
        ),
    )
    // material components (date pickers, menus, dialogs) take their colors from these
    val scheme = if (palette.dark) {
        darkColorScheme(
            primary = palette.accent, onPrimary = palette.accentInk,
            secondary = palette.chrome, onSecondary = palette.chromeInk,
            background = palette.bg, onBackground = palette.ink,
            surface = palette.raised, onSurface = palette.ink,
            surfaceVariant = palette.panelBg, onSurfaceVariant = palette.muted,
            surfaceContainer = palette.raised, surfaceContainerHigh = palette.raised,
            surfaceContainerHighest = palette.raised, surfaceContainerLow = palette.panelBg,
            outline = palette.fieldBorder, outlineVariant = palette.rule, error = palette.danger,
        )
    } else {
        lightColorScheme(
            primary = palette.accent, onPrimary = palette.accentInk,
            secondary = palette.chrome, onSecondary = palette.chromeInk,
            background = palette.bg, onBackground = palette.ink,
            surface = palette.raised, onSurface = palette.ink,
            surfaceVariant = palette.panelBg, onSurfaceVariant = palette.muted,
            surfaceContainer = palette.raised, surfaceContainerHigh = palette.raised,
            surfaceContainerHighest = palette.raised, surfaceContainerLow = palette.panelBg,
            outline = palette.fieldBorder, outlineVariant = palette.rule, error = palette.danger,
        )
    }
    val body = TextStyle(fontFamily = fonts.body, fontSize = TextSize.base, lineHeight = 24.sp, color = palette.ink)
    val typography = MaterialTheme.typography.let { m ->
        m.copy(
            bodyLarge = body,
            bodyMedium = body.copy(fontSize = TextSize.sm, lineHeight = 22.sp),
            bodySmall = body.copy(fontSize = TextSize.xs, lineHeight = 16.sp),
            labelLarge = body.copy(fontSize = TextSize.sm, fontWeight = FontWeight.Medium),
            labelMedium = body.copy(fontSize = TextSize.xs, fontWeight = FontWeight.Medium),
            labelSmall = body.copy(fontSize = TextSize.xs),
            titleLarge = body.copy(fontFamily = fonts.display, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
            titleMedium = body.copy(fontFamily = fonts.display, fontWeight = FontWeight.Bold, fontSize = TextSize.lg, lineHeight = 26.sp),
            titleSmall = body.copy(fontFamily = fonts.display, fontWeight = FontWeight.Medium, fontSize = TextSize.base),
            headlineSmall = body.copy(fontFamily = fonts.display, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
            headlineMedium = body.copy(fontFamily = fonts.display, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp),
        )
    }
    MaterialTheme(colorScheme = scheme, typography = typography) {
        CompositionLocalProvider(
            LocalPalette provides palette,
            LocalFonts provides fonts,
            LocalTextSelectionColors provides TextSelectionColors(palette.accent, palette.accent.copy(alpha = 0.3f)),
            content = content,
        )
    }
}
