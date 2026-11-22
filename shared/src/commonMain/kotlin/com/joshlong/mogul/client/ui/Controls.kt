package com.joshlong.mogul.client.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.i18n.tc
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.TextSize
import com.joshlong.mogul.client.theme.fonts
import com.joshlong.mogul.client.theme.palette

/**
 * a button. buttons are never blue: every one is the chrome color (navy by day), as the
 * re-transcribe button is, and a disabled one is an opaque grey.
 */
@Composable
fun MogulButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val background = when {
        !enabled -> palette.disabled
        danger -> palette.danger
        hovered || focused -> palette.chromeHover
        else -> palette.chrome
    }
    val ink = if (enabled) palette.chromeInk else palette.disabledInk
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Gutter.radius))
            .background(background)
            .hoverable(interaction)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Gutter.half),
    ) {
        leading?.invoke()
        Text(text, color = ink, fontWeight = FontWeight.SemiBold, fontSize = TextSize.control, fontFamily = fonts.body)
    }
}

/** a row of buttons (or anything), spaced by the gutter, as a .form-row */
@Composable
fun ButtonRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(Gutter.half, Alignment.End),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = Gutter.half),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** how wide the window is, as base.css's container queries split it: the legends and panels round accordingly */
enum class LayoutWidth { Mobile, Desktop, Wide }

val LocalLayoutWidth = compositionLocalOf { LayoutWidth.Desktop }

/** the window's width, for the few things the Vue client sized in vw */
val LocalWindowWidth = compositionLocalOf { 1200.dp }

/** a page's title, as the views' h1: big display type with the browser's h1 margins */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(vertical = 21.dp),
        color = palette.ink,
        fontFamily = fonts.display,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 35.sp,
        letterSpacing = (-0.035 * 32).sp,
    )
}

/**
 * a fieldset with its legend, as base.css draws a .pure-form legend: a chrome bar that
 * reaches one gutter left of the fields, rounded where it ends in open space (its right end
 * on desktop, both ends once the page is centered), and the fields on the page beneath it.
 * actions are the .toolbar the views put under the legend, e.g. a new-episode plus.
 */
@Composable
fun FormSection(
    legend: String,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = when (LocalLayoutWidth.current) {
        LayoutWidth.Mobile -> RoundedCornerShape(0.dp)
        LayoutWidth.Desktop -> RoundedCornerShape(topEnd = Gutter.radius, bottomEnd = Gutter.radius)
        LayoutWidth.Wide -> RoundedCornerShape(Gutter.radius)
    }
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(
            legend,
            modifier = Modifier
                .bleedStart(Gutter.full)
                .clip(shape)
                .background(palette.chrome)
                .padding(start = Gutter.full, end = 2.dp, top = 5.dp, bottom = 5.dp),
            color = palette.chromeInk,
            fontFamily = fonts.display,
            fontWeight = FontWeight.Medium,
            fontSize = TextSize.base,
            lineHeight = 24.sp,
            letterSpacing = (-0.01 * 16).sp,
        )
        if (actions != null) {
            Row(
                Modifier.padding(vertical = Gutter.half),
                horizontalArrangement = Arrangement.spacedBy(Gutter.half),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(Gutter.half),
            content = content,
        )
    }
}

/** widens a full-width element by [amount] and shifts it that far left, as a negative left offset with a wider width */
fun Modifier.bleedStart(amount: Dp): Modifier = this.layout { measurable, constraints ->
    val extra = amount.roundToPx()
    val width = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
}

/** a heading inside a form, as .form-section-title: a rule above, then the title in display type */
@Composable
fun FormSectionTitle(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = Gutter.full)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rule))
        Text(
            text,
            modifier = Modifier.padding(top = 20.dp, bottom = 5.dp),
            color = palette.ink,
            fontFamily = fonts.display,
            fontWeight = FontWeight.Bold,
            fontSize = TextSize.lg,
            letterSpacing = (-0.02 * 20).sp,
        )
    }
}

/**
 * a field's label, as a .pure-form label: the body face, capitalized. the Vue forms mark no
 * field as required, so neither does this; the parameter stays for the callers that say so
 */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier, @Suppress("UNUSED_PARAMETER") required: Boolean = false) {
    Text(
        text.replaceFirstChar { it.uppercase() },
        modifier = modifier.padding(bottom = 4.dp),
        color = palette.ink,
        fontSize = TextSize.base,
    )
}

/** a short, muted explanation under a field, as a .pure-form-message */
@Composable
fun FieldHint(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = palette.muted, fontSize = TextSize.sm)
}

/**
 * a tool that opens under a text field, as the Vue InputWrapper's children did (the writing
 * assistant, the markdown preview). it gets the field's value and can replace it.
 */
class InputTool(
    val icon: MogulIcon,
    val label: String,
    val content: @Composable (value: String, onValueChange: (String) -> Unit) -> Unit,
)

/**
 * a text field in the studio's recessed-well style, with an optional label, a character
 * count, and tools (icons at the field's right; each opens a panel under the field).
 */
@Composable
fun MogulTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    required: Boolean = false,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 20,
    enabled: Boolean = true,
    password: Boolean = false,
    showCount: Boolean = false,
    tools: List<InputTool> = emptyList(),
    keyboardType: KeyboardType = KeyboardType.Text,
    onSubmit: (() -> Unit)? = null,
    textStyle: TextStyle? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val count = if (value.isEmpty()) t("components.inputWrapper.empty") else tc("components.inputWrapper.character-count", value.length)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Gutter.third)) {
        // with tools, the count sits under the field, above the tool carousel, as in InputWrapper.vue
        val countAbove = showCount && tools.isEmpty()
        if (label != null || countAbove) {
            Row(verticalAlignment = Alignment.Bottom) {
                if (label != null) FieldLabel(label, Modifier.weight(1f), required) else Spacer(Modifier.weight(1f))
                if (countAbove) Text(count, color = palette.muted, fontSize = TextSize.xs)
            }
        }
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Gutter.radius))
                    .background(if (enabled) palette.fieldBg else palette.panelBg)
                    .border(1.dp, if (focused) palette.accent else palette.fieldBorder, RoundedCornerShape(Gutter.radius))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    minLines = minLines,
                    maxLines = maxLines,
                    interactionSource = interaction,
                    cursorBrush = SolidColor(palette.ink),
                    visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    keyboardActions = KeyboardActions(onDone = { onSubmit?.invoke() }, onSearch = { onSubmit?.invoke() }, onGo = { onSubmit?.invoke() }),
                    textStyle = (textStyle ?: TextStyle(fontFamily = fonts.body, fontSize = TextSize.control, lineHeight = 24.sp)).copy(color = palette.ink),
                    modifier = Modifier.fillMaxWidth().onEnterSubmit(singleLine, onSubmit),
                    decorationBox = { inner ->
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, color = palette.muted, fontSize = TextSize.control)
                        }
                        inner()
                    },
                )
            }
        }
        if (tools.isNotEmpty()) {
            ToolCarousel(tools, value, onValueChange, count, enabled)
        }
    }
}

/**
 * the tools under a text field, as InputWrapper.vue draws them: the count, then one tool's icon
 * between arrows that pick another. the icon opens that tool's panel, which drops down from the
 * field with a tinted backdrop reaching up behind the count and the arrows; the icon becomes close.
 */
@Composable
private fun ToolCarousel(tools: List<InputTool>, value: String, onValueChange: (String) -> Unit, count: String, enabled: Boolean) {
    var current by rememberSaveable { mutableStateOf(0) }
    var open by rememberSaveable { mutableStateOf(false) }
    val tool = tools[current.coerceIn(tools.indices)]
    // while a panel is open the arrows are off, as they were in the Vue menu
    val canMove = !open && tools.size > 1
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (open) palette.raised.copy(alpha = 0.5f) else Color.Transparent)
            .padding(if (open) Gutter.third else 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(count, color = palette.muted, fontSize = TextSize.xs)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gutter.half)) {
            CarouselArrow(pointsLeft = true, t("components.inputWrapper.previous-tool"), canMove && current > 0) { current-- }
            if (open) {
                IconButton(MogulIcon.Close, t("icons.close"), onClick = { open = false })
            } else {
                IconButton(tool.icon, tool.label, onClick = { open = true }, enabled = enabled)
            }
            CarouselArrow(pointsLeft = false, t("components.inputWrapper.next-tool"), canMove && current < tools.lastIndex) { current++ }
        }
        if (open) {
            Column(Modifier.fillMaxWidth().padding(top = Gutter.half)) { tool.content(value, onValueChange) }
        }
    }
}

@Composable
private fun CarouselArrow(pointsLeft: Boolean, label: String, enabled: Boolean, onClick: () -> Unit) {
    // drawn, not ◀ / ▶: with no text glyph in our fonts, the browser's emoji font answers instead
    val color = palette.ink.copy(alpha = if (enabled) 1f else 0.3f)
    Canvas(
        Modifier
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .semantics { contentDescription = label }
            .padding(4.dp)
            .size(10.dp),
    ) {
        val tip = if (pointsLeft) 0f else size.width
        val base = size.width - tip
        drawPath(
            Path().apply {
                moveTo(base, 0f)
                lineTo(tip, size.height / 2)
                lineTo(base, size.height)
                close()
            },
            color,
        )
    }
}

@Composable
fun MogulCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(
        modifier = modifier.clickable(enabled = enabled, role = Role.Checkbox) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = palette.accent, uncheckedColor = palette.fieldBorder, checkmarkColor = palette.accentInk),
        )
        Text(label, color = palette.ink, fontSize = TextSize.sm)
    }
}

/** a select: the field shows the chosen option, and opens a menu of the rest */
@Composable
fun <T> MogulSelect(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Gutter.third)) {
        if (label != null) FieldLabel(label)
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Gutter.radius))
                    .background(palette.fieldBg)
                    .border(1.dp, palette.fieldBorder, RoundedCornerShape(Gutter.radius))
                    .clickable(enabled = enabled, role = Role.DropdownList) { open = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    selected?.let(optionLabel) ?: placeholder,
                    modifier = Modifier.weight(1f),
                    color = if (selected == null) palette.muted else palette.ink,
                    fontSize = TextSize.control,
                )
                Icon(MogulIcon.Down, null, size = 16.dp, tint = palette.muted)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = palette.raised) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option), color = palette.ink) },
                        onClick = {
                            open = false
                            onSelect(option)
                        },
                    )
                }
            }
        }
    }
}

/** a small rounded label, as the Vue client's status pills */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, background: androidx.compose.ui.graphics.Color = palette.chrome, ink: androidx.compose.ui.graphics.Color = palette.chromeInk) {
    Text(
        text,
        modifier = modifier.clip(RoundedCornerShape(999.dp)).background(background).padding(horizontal = 8.dp, vertical = 2.dp),
        color = ink,
        fontSize = TextSize.xs,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
}

/** a thin horizontal rule in the rule color */
@Composable
fun Rule(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(vertical = Gutter.half)) {
    Box(modifier.padding(padding).fillMaxWidth().heightIn(min = 1.dp, max = 1.dp).background(palette.rule))
}

/** a gap of the given size, in either direction */
@Composable
fun Gap(size: Dp = Gutter.full) = Spacer(Modifier.size(size))

@Composable
fun HorizontalGap(size: Dp = Gutter.full) = Spacer(Modifier.width(size))
