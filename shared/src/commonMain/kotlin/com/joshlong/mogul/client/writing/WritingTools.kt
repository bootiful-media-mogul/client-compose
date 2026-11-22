package com.joshlong.mogul.client.writing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.TextSize
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.ui.Icon
import com.joshlong.mogul.client.ui.IconButton
import com.joshlong.mogul.client.ui.InputTool
import com.joshlong.mogul.client.ui.MogulButton
import com.joshlong.mogul.client.ui.MogulIcon
import com.joshlong.mogul.client.ui.MogulTextField
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import kotlinx.coroutines.launch

/** the writing assistant (WritingAssistant.vue), as a tool under a text field */
fun writingAssistantTool(): InputTool =
    InputTool(MogulIcon.Rewrite, t("icons.writing-assistant")) { value, onValueChange -> WritingAssistant(value, onValueChange) }

/** the markdown editing toolbar and preview (Markdown.vue), as a tool under a text field */
fun markdownPreviewTool(): InputTool =
    InputTool(MogulIcon.Markdown, t("icons.markdown")) { value, onValueChange -> MarkdownTool(value, onValueChange) }

/** both, as InputTools.vue put them under a long text field */
fun writingTools(): List<InputTool> = listOf(writingAssistantTool(), markdownPreviewTool())

private enum class ActiveTool { None, Rewrite, Ai }

private fun aiPrompt(instruction: String, text: String) = """$instruction Return only the new text, and nothing else.
        ==========================================
        $text
      """

/**
 * a single prompt for the ai. when the text box already has content we ask the model to
 * transform it using the user's instruction; when it's empty, to write something from scratch.
 */
private fun buildPrompt(instruction: String, existing: String): String =
    if (existing.isEmpty()) "$instruction\n\nReturn only the requested text, and nothing else."
    else """Follow the instruction below to rewrite or transform the text that appears after the line made of "="'s. Return only the resulting text, and nothing else.

Instruction: $instruction
==========================================
$existing"""

@Composable
private fun WritingAssistant(value: String, onValueChange: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var activeTool by remember { mutableStateOf(ActiveTool.None) }
    var previous by remember { mutableStateOf("") }
    var proposalApprovalRequired by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }

    // every ai answer is a proposal: the text changes, and the user accepts or reverts it
    fun propose(updated: String) {
        previous = value
        onValueChange(updated)
        proposalApprovalRequired = true
    }

    fun reset() {
        proposalApprovalRequired = false
        activeTool = ActiveTool.None
        prompt = ""
        working = false
    }

    fun ask(question: String) {
        if (working) return
        working = true
        scope.launch {
            try {
                propose(Mogul.ai.chat(question))
            } finally {
                working = false
            }
        }
    }

    fun rewrite(style: String) {
        if (value.trim().isEmpty()) return
        ask(aiPrompt("""Please rewrite the text following the line made of "="'s $style.""", value))
    }

    if (proposalApprovalRequired) {
        Row(horizontalArrangement = Arrangement.spacedBy(Gutter.half), verticalAlignment = Alignment.CenterVertically) {
            Text(t("buttons.accept"), Modifier.clickable(role = Role.Button) { reset() }, color = palette.ink, fontWeight = FontWeight.SemiBold, fontSize = TextSize.sm)
            Text("|", color = palette.muted, fontSize = TextSize.sm)
            Text(
                t("buttons.revert"),
                Modifier.clickable(role = Role.Button) {
                    onValueChange(previous)
                    reset()
                },
                color = palette.ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = TextSize.sm,
            )
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(Gutter.third)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Gutter.third)) {
            ToolButton(MogulIcon.Proofread, t("writingassistant.buttons.proofread"), active = false, enabled = !working) {
                val contents = value.trim()
                if (contents.isNotEmpty()) {
                    activeTool = ActiveTool.None
                    ask(
                        """Please proof read the text following the line made of "="'s. Return only the proofread text, and nothing else.
        ==========================================
        $contents
      """,
                    )
                }
            }
            ToolButton(MogulIcon.Rewrite, t("writingassistant.buttons.rewrite"), active = activeTool == ActiveTool.Rewrite, enabled = !working) {
                activeTool = if (activeTool == ActiveTool.Rewrite) ActiveTool.None else ActiveTool.Rewrite
            }
            ToolButton(MogulIcon.Ai, t("writingassistant.buttons.generate-with-ai"), active = activeTool == ActiveTool.Ai, enabled = !working) {
                activeTool = if (activeTool == ActiveTool.Ai) ActiveTool.None else ActiveTool.Ai
            }
        }
        when (activeTool) {
            ActiveTool.Rewrite -> Row(horizontalArrangement = Arrangement.spacedBy(Gutter.third)) {
                ToolButton(MogulIcon.Friendly, t("writingassistant.buttons.friendly"), active = true, enabled = !working) { rewrite("to be more friendly in tone") }
                ToolButton(MogulIcon.Concise, t("writingassistant.buttons.concise"), active = true, enabled = !working) { rewrite("to be more concise") }
                ToolButton(MogulIcon.Professional, t("writingassistant.buttons.professional"), active = true, enabled = !working) { rewrite("to sound more professional") }
            }
            ActiveTool.Ai -> Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Gutter.radius)).background(palette.panelBg).padding(Gutter.half),
                verticalArrangement = Arrangement.spacedBy(Gutter.third),
            ) {
                MogulTextField(
                    prompt,
                    { prompt = it },
                    placeholder = t("writingassistant.placeholder"),
                    singleLine = false,
                    minLines = 3,
                    enabled = !working,
                )
                Row {
                    MogulButton(
                        if (working) t("writingassistant.buttons.generating") else t("writingassistant.buttons.generate"),
                        onClick = {
                            val instruction = prompt.trim()
                            if (instruction.isNotEmpty()) ask(buildPrompt(instruction, value.trim()))
                        },
                        enabled = !working,
                    )
                }
            }
            ActiveTool.None -> Unit
        }
    }
}

/** a writing-tools button: an icon over its label, on the panel color (raised when active) */
@Composable
private fun ToolButton(icon: MogulIcon, label: String, active: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(Gutter.radius))
            .background(if (active) palette.panelBg else palette.raised)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Gutter.half, vertical = Gutter.third),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, size = 20.dp, tint = if (enabled) palette.ink else palette.muted)
        Text(label, color = if (enabled) palette.ink else palette.muted, fontSize = TextSize.sm)
    }
}

/** what a markdown formatting button asks for before it can insert anything */
private enum class PendingPrompt { None, Link, Image }

/**
 * the formatting toolbar and the rendered preview. the tool sees only the field's text, not
 * its selection, so the buttons work on the last line: headings and bullets prefix it, bold
 * and italic wrap it, and links and images are appended
 */
@Composable
private fun MarkdownTool(value: String, onValueChange: (String) -> Unit) {
    var pending by remember { mutableStateOf(PendingPrompt.None) }
    var answer by remember { mutableStateOf("") }

    fun lastLine(transform: (String) -> String) {
        val at = value.lastIndexOf('\n') + 1
        onValueChange(value.substring(0, at) + transform(value.substring(at)))
    }

    fun append(text: String) = onValueChange(if (value.isEmpty() || value.endsWith("\n")) value + text else "$value\n$text")

    Column(verticalArrangement = Arrangement.spacedBy(Gutter.half)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Gutter.half)) {
            IconButton(MogulIcon.Bold, t("icons.bold"), onClick = { lastLine { "**$it**" } }, size = 20.dp)
            IconButton(MogulIcon.Italic, t("icons.italic"), onClick = { lastLine { "*$it*" } }, size = 20.dp)
            IconButton(MogulIcon.Link, t("icons.link"), onClick = { pending = PendingPrompt.Link; answer = "" }, size = 20.dp)
            IconButton(MogulIcon.List, t("icons.list"), onClick = { lastLine { "* $it" } }, size = 20.dp)
            IconButton(MogulIcon.H1, t("icons.h1"), onClick = { lastLine { "# $it" } }, size = 20.dp)
            IconButton(MogulIcon.H2, t("icons.h2"), onClick = { lastLine { "## $it" } }, size = 20.dp)
            IconButton(MogulIcon.H3, t("icons.h3"), onClick = { lastLine { "### $it" } }, size = 20.dp)
            IconButton(MogulIcon.Image, t("icons.image"), onClick = { pending = PendingPrompt.Image; answer = "" }, size = 20.dp)
        }
        if (pending != PendingPrompt.None) {
            // the Vue client asked with window.prompt; here the question is asked inline
            val submit = {
                val text = answer.trim()
                if (text.isNotEmpty()) {
                    if (pending == PendingPrompt.Link) append("[$text]($text)") else append("![$text]()")
                }
                pending = PendingPrompt.None
            }
            MogulTextField(
                answer,
                { answer = it },
                label = t(if (pending == PendingPrompt.Link) "markdown.add-link-prompt" else "markdown.add-image-prompt"),
                onSubmit = submit,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Gutter.half)) {
                MogulButton(t("buttons.accept"), onClick = submit)
                MogulButton(t("buttons.cancel"), onClick = { pending = PendingPrompt.None })
            }
        }
        Column(Modifier.fillMaxWidth().padding(Gutter.half)) {
            if (value.trim().isEmpty()) {
                Text(t("markdown.nothing-to-preview"), color = palette.muted, fontSize = TextSize.sm)
            } else {
                MarkdownView(value)
            }
        }
    }
}

/** markdown, rendered in the palette's colors */
@Composable
fun MarkdownView(markdown: String, modifier: Modifier = Modifier) {
    Markdown(
        content = markdown,
        modifier = modifier,
        colors = markdownColor(text = palette.ink),
    )
}
