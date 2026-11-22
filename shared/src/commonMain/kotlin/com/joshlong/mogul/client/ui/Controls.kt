package com.joshlong.mogul.client.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
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

/**
 * a fieldset with its legend, as the Vue forms are drawn: a chrome band carrying the title,
 * and the fields on the panel color below it. actions sit at the right of the legend band.
 */
@Composable
fun FormSection(
    legend: String,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Gutter.radius))
            .background(palette.panelBg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(palette.chrome).padding(horizontal = Gutter.full, vertical = Gutter.third),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                legend,
                modifier = Modifier.weight(1f),
                color = palette.chromeInk,
                fontFamily = fonts.display,
                fontWeight = FontWeight.Medium,
                fontSize = TextSize.lg,
            )
            if (actions != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(Gutter.half), verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(Gutter.full),
            verticalArrangement = Arrangement.spacedBy(Gutter.half),
            content = content,
        )
    }
}

/** a field's label; a required one carries a red asterisk */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier, required: Boolean = false) {
    Row(modifier = modifier) {
        Text(text, color = palette.label, fontWeight = FontWeight.SemiBold, fontSize = TextSize.sm)
        if (required) Text(" *", color = palette.danger, fontWeight = FontWeight.SemiBold, fontSize = TextSize.sm)
    }
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
    var openTool by rememberSaveable { mutableStateOf<String?>(null) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Gutter.third)) {
        if (label != null || showCount) {
            Row(verticalAlignment = Alignment.Bottom) {
                if (label != null) FieldLabel(label, Modifier.weight(1f), required) else Spacer(Modifier.weight(1f))
                if (showCount) {
                    Text(
                        if (value.isEmpty()) t("components.inputWrapper.empty") else tc("components.inputWrapper.character-count", value.length),
                        color = palette.muted,
                        fontSize = TextSize.xs,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Gutter.half)) {
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
            if (tools.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(Gutter.third)) {
                    tools.forEach { tool ->
                        IconButton(
                            tool.icon,
                            tool.label,
                            onClick = { openTool = if (openTool == tool.label) null else tool.label },
                            toggled = openTool == tool.label,
                            enabled = enabled,
                        )
                    }
                }
            }
        }
        tools.firstOrNull { it.label == openTool }?.let { tool ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Gutter.radius))
                    .background(palette.raised)
                    .border(1.dp, palette.rule, RoundedCornerShape(Gutter.radius))
                    .padding(Gutter.half),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tool.label, modifier = Modifier.weight(1f), color = palette.label, fontWeight = FontWeight.SemiBold, fontSize = TextSize.sm)
                    IconButton(MogulIcon.Close, t("icons.close"), onClick = { openTool = null }, size = 18.dp)
                }
                tool.content(value, onValueChange)
            }
        }
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
