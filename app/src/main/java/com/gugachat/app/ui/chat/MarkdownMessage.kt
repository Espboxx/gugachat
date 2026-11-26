package com.gugachat.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.LocalAppTheme
import kotlinx.coroutines.delay

@Immutable
data class MarkdownPalette(
    val text: Color,
    val muted: Color,
    val accent: Color,
    val border: Color,
    val quoteBg: Color,
    val inlineCodeBg: Color,
    val inlineCodeInk: Color,
    val codeBg: Color,
    val codeHeaderBg: Color,
    val codeInk: Color,
    val codeMeta: Color,
    val tableHeaderBg: Color,
)

private sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class CodeFence(val lang: String, val code: String) : MdBlock
    data class Blockquote(val text: String) : MdBlock
    data class BulletItem(val bullet: String, val text: String) : MdBlock
    data class TableBlock(val headers: List<String>, val rows: List<List<String>>) : MdBlock
    data object Divider : MdBlock
}

@Composable
fun ReasoningCard(
    reasoning: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
) {
    if (reasoning.isBlank() && !isStreaming) return
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    var expanded by remember(isStreaming) { mutableStateOf(isStreaming) }
    val collapsedPreview = remember(reasoning) {
        reasoning.trim()
            .replace(Regex("\\s+"), " ")
            .take(120)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 36.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(c.surfaceSoft)
                .border(theme.borderWidth, c.border, RoundedCornerShape(999.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isStreaming) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 1.8.dp,
                    color = c.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = c.primary,
                )
            }
            Text(
                text = if (isStreaming) {
                    strings.chat.thinkingRunning
                } else {
                    strings.chat.thinkingDone.fmt("seconds" to kotlin.math.max(1, reasoning.length / 45))
                },
                color = c.ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = c.muted,
                modifier = Modifier.size(16.dp),
            )
        }

        if (!expanded && !isStreaming && collapsedPreview.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(start = 6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { expanded = true }
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(c.primary.copy(alpha = 0.45f)),
                )
                Text(
                    text = collapsedPreview,
                    color = c.muted.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        AnimatedVisibility(visible = expanded && reasoning.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(start = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(c.primary.copy(alpha = 0.45f)),
                )
                SelectionContainer {
                    Text(
                        text = reasoning.trim(),
                        color = c.muted,
                        fontSize = 13.sp,
                        lineHeight = 20.5.sp,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun MarkdownMessageText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = LocalAppTheme.current.colors.ink,
    fontSize: TextUnit = 14.sp,
    lineHeight: TextUnit = 21.sp,
    useSerif: Boolean = false,
    palette: MarkdownPalette? = null,
    onCopyToast: ((String) -> Unit)? = null,
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val ctx = LocalContext.current
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val p = palette ?: remember(theme.isDark, c, textColor) {
        if (theme.isDark) {
            MarkdownPalette(
                text = textColor,
                muted = c.muted,
                accent = c.primary,
                border = c.border,
                quoteBg = c.surfaceSoft.copy(alpha = 0.65f),
                inlineCodeBg = c.surfaceSoft,
                inlineCodeInk = c.primarySoftInk,
                codeBg = c.surfaceSoft,
                codeHeaderBg = c.surfaceElevated,
                codeInk = Color(0xFFE6EDF3),
                codeMeta = c.muted,
                tableHeaderBg = c.surfaceSoft,
            )
        } else {
            MarkdownPalette(
                text = textColor,
                muted = c.muted,
                accent = c.primary,
                border = c.border,
                quoteBg = c.surfaceSoft.copy(alpha = 0.8f),
                inlineCodeBg = c.surfaceSoft,
                inlineCodeInk = c.primarySoftInk,
                codeBg = c.surfaceSoft,
                codeHeaderBg = c.border.copy(alpha = 0.55f),
                codeInk = c.ink,
                codeMeta = c.muted,
                tableHeaderBg = c.surfaceSoft,
            )
        }
    }
    val family = if (useSerif) FontFamily.Serif else FontFamily.Default

    SelectionContainer {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            blocks.forEachIndexed { index, block ->
                when (block) {
                    is MdBlock.Divider -> HorizontalDivider(
                        color = p.border,
                        thickness = theme.borderWidth,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                    is MdBlock.Heading -> {
                        val hSize = when (block.level) {
                            1 -> (fontSize.value + 5f).sp
                            2 -> (fontSize.value + 3f).sp
                            else -> (fontSize.value + 1.5f).sp
                        }
                        InlineRichText(
                            raw = block.text,
                            color = p.text,
                            linkColor = p.accent,
                            codeBg = p.inlineCodeBg,
                            codeInk = p.inlineCodeInk,
                            fontSize = hSize,
                            lineHeight = (hSize.value * 1.42f).sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = family,
                            modifier = Modifier.padding(top = if (index > 0) 4.dp else 0.dp),
                        )
                    }
                    is MdBlock.Blockquote -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .clip(RoundedCornerShape(10.dp))
                                .background(p.quoteBg),
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .background(p.accent),
                            )
                            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                InlineRichText(
                                    raw = block.text,
                                    color = p.muted,
                                    linkColor = p.accent,
                                    codeBg = p.inlineCodeBg,
                                    codeInk = p.inlineCodeInk,
                                    fontSize = fontSize,
                                    lineHeight = lineHeight,
                                    fontWeight = FontWeight.Normal,
                                    fontFamily = family,
                                )
                            }
                        }
                    }
                    is MdBlock.BulletItem -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = block.bullet,
                                color = p.accent,
                                fontSize = fontSize,
                                lineHeight = lineHeight,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.widthIn(min = 14.dp),
                            )
                            InlineRichText(
                                raw = block.text,
                                color = p.text,
                                linkColor = p.accent,
                                codeBg = p.inlineCodeBg,
                                codeInk = p.inlineCodeInk,
                                fontSize = fontSize,
                                lineHeight = lineHeight,
                                fontWeight = FontWeight.Normal,
                                fontFamily = family,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    is MdBlock.CodeFence -> {
                        CodeFenceBlock(
                            lang = block.lang,
                            code = block.code,
                            palette = p,
                            copyLabel = strings.chat.copyCode,
                            copiedLabel = strings.common.copied,
                            onCopy = {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("code", block.code))
                                onCopyToast?.invoke(strings.common.copied)
                            },
                        )
                    }
                    is MdBlock.TableBlock -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        ) {
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(theme.borderWidth, p.border, RoundedCornerShape(12.dp)),
                            ) {
                                Row(modifier = Modifier.background(p.tableHeaderBg)) {
                                    block.headers.forEach { h ->
                                        Box(
                                            modifier = Modifier
                                                .widthIn(min = 88.dp, max = 180.dp)
                                                .padding(horizontal = 11.dp, vertical = 9.dp),
                                        ) {
                                            InlineRichText(
                                                raw = h,
                                                color = p.text,
                                                linkColor = p.accent,
                                                codeBg = p.inlineCodeBg,
                                                codeInk = p.inlineCodeInk,
                                                fontSize = 12.sp,
                                                lineHeight = 17.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = family,
                                            )
                                        }
                                    }
                                }
                                block.rows.forEachIndexed { rowIdx, row ->
                                    HorizontalDivider(color = p.border, thickness = theme.borderWidth)
                                    val rowTint = if (rowIdx % 2 == 1) {
                                        p.tableHeaderBg.copy(alpha = 0.35f)
                                    } else {
                                        Color.Transparent
                                    }
                                    Row(modifier = Modifier.background(rowTint)) {
                                        block.headers.indices.forEach { idx ->
                                            Box(
                                                modifier = Modifier
                                                    .widthIn(min = 88.dp, max = 180.dp)
                                                    .padding(horizontal = 11.dp, vertical = 8.dp),
                                            ) {
                                                InlineRichText(
                                                    raw = row.getOrNull(idx).orEmpty(),
                                                    color = p.text,
                                                    linkColor = p.accent,
                                                    codeBg = p.inlineCodeBg,
                                                    codeInk = p.inlineCodeInk,
                                                    fontSize = 12.sp,
                                                    lineHeight = 17.sp,
                                                    fontWeight = FontWeight.Normal,
                                                    fontFamily = family,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is MdBlock.Paragraph -> {
                        InlineRichText(
                            raw = block.text,
                            color = p.text,
                            linkColor = p.accent,
                            codeBg = p.inlineCodeBg,
                            codeInk = p.inlineCodeInk,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            fontWeight = FontWeight.Normal,
                            fontFamily = family,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeFenceBlock(
    lang: String,
    code: String,
    palette: MarkdownPalette,
    copyLabel: String,
    copiedLabel: String,
    onCopy: () -> Unit,
) {
    val theme = LocalAppTheme.current
    var justCopied by remember(code) { mutableStateOf(false) }
    LaunchedEffect(justCopied) {
        if (justCopied) {
            delay(1600L)
            justCopied = false
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.codeBg)
            .border(theme.borderWidth, palette.border, RoundedCornerShape(14.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.codeHeaderBg)
                .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = lang.ifBlank { "code" }.lowercase(),
                color = palette.codeMeta,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp,
            )
            Row(
                modifier = Modifier
                    .heightIn(min = 32.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(palette.codeBg.copy(alpha = 0.72f))
                    .border(theme.borderWidth, palette.border.copy(alpha = 0.8f), RoundedCornerShape(999.dp))
                    .clickable {
                        onCopy()
                        justCopied = true
                    }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = if (justCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = copyLabel,
                    tint = palette.accent,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = if (justCopied) copiedLabel else copyLabel,
                    color = palette.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        HorizontalDivider(
            thickness = theme.borderWidth,
            color = palette.border.copy(alpha = 0.75f),
        )
        Text(
            text = code,
            color = palette.codeInk,
            fontSize = 12.5.sp,
            lineHeight = 19.5.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 13.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun InlineRichText(
    raw: String,
    color: Color,
    linkColor: Color,
    codeBg: Color,
    codeInk: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier,
) {
    val annotated = remember(raw, linkColor, codeBg, codeInk) {
        parseInlineAnnotated(raw, linkColor = linkColor, codeBg = codeBg, codeInk = codeInk)
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
        ),
    )
}

private fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    if (raw.isEmpty()) return emptyList()
    val lines = raw.replace("\r\n", "\n").split('\n')
    val out = ArrayList<MdBlock>()
    var i = 0
    val paraBuf = ArrayList<String>()

    fun flushPara() {
        if (paraBuf.isNotEmpty()) {
            val txt = paraBuf.joinToString("\n").trim()
            if (txt.isNotEmpty()) out.add(MdBlock.Paragraph(txt))
            paraBuf.clear()
        }
    }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            flushPara()
            val lang = trimmed.removePrefix("```").trim()
            val codeLines = ArrayList<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            out.add(MdBlock.CodeFence(lang, codeLines.joinToString("\n")))
            i++
            continue
        }

        if (trimmed.isEmpty()) {
            flushPara()
            i++
            continue
        }

        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            flushPara()
            out.add(MdBlock.Divider)
            i++
            continue
        }

        val headingMatch = Regex("^(#{1,4})\\s+(.+)$").matchEntire(trimmed)
        if (headingMatch != null) {
            flushPara()
            out.add(MdBlock.Heading(headingMatch.groupValues[1].length, headingMatch.groupValues[2]))
            i++
            continue
        }

        if (trimmed.startsWith(">")) {
            flushPara()
            val quoteLines = ArrayList<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quoteLines.add(lines[i].trim().removePrefix(">").trimStart())
                i++
            }
            out.add(MdBlock.Blockquote(quoteLines.joinToString("\n")))
            continue
        }

        val bulletMatch = Regex("^([-*•]|\\d+\\.)\\s+(.+)$").matchEntire(trimmed)
        if (bulletMatch != null) {
            flushPara()
            val sym = if (bulletMatch.groupValues[1] in listOf("-", "*", "•")) "•" else bulletMatch.groupValues[1]
            out.add(MdBlock.BulletItem(sym, bulletMatch.groupValues[2]))
            i++
            continue
        }

        if (trimmed.contains("|") && i + 1 < lines.size && lines[i + 1].trim().matches(Regex("^\\|?\\s*[-:]+\\s*(\\|\\s*[-:]+\\s*)+\\|?$"))) {
            flushPara()
            val headers = splitTableRow(trimmed)
            i += 2
            val rows = ArrayList<List<String>>()
            while (i < lines.size && lines[i].trim().contains("|")) {
                rows.add(splitTableRow(lines[i].trim()))
                i++
            }
            out.add(MdBlock.TableBlock(headers, rows))
            continue
        }

        paraBuf.add(line)
        i++
    }
    flushPara()
    return out
}

private fun splitTableRow(line: String): List<String> =
    line.trim().trim('|').split('|').map { it.trim() }

private fun parseInlineAnnotated(
    raw: String,
    linkColor: Color,
    codeBg: Color,
    codeInk: Color,
): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < raw.length) {
        when {
            raw.startsWith("***", i) -> {
                val end = raw.indexOf("***", i + 3)
                if (end > i + 3) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic))
                    append(raw.substring(i + 3, end))
                    pop()
                    i = end + 3
                } else {
                    append("***"); i += 3
                }
            }
            raw.startsWith("**", i) -> {
                val end = raw.indexOf("**", i + 2)
                if (end > i + 2) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.ExtraBold))
                    append(raw.substring(i + 2, end))
                    pop()
                    i = end + 2
                } else {
                    append("**"); i += 2
                }
            }
            raw.startsWith("~~", i) -> {
                val end = raw.indexOf("~~", i + 2)
                if (end > i + 2) {
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    append(raw.substring(i + 2, end))
                    pop()
                    i = end + 2
                } else {
                    append("~~"); i += 2
                }
            }
            raw[i] == '`' -> {
                val end = raw.indexOf('`', i + 1)
                if (end > i + 1) {
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, color = codeInk))
                    append(" ${raw.substring(i + 1, end)} ")
                    pop()
                    i = end + 1
                } else {
                    append('`'); i++
                }
            }
            raw[i] == '*' && !raw.startsWith("**", i) -> {
                val end = raw.indexOf('*', i + 1)
                if (end > i + 1) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(raw.substring(i + 1, end))
                    pop()
                    i = end + 1
                } else {
                    append('*'); i++
                }
            }
            raw[i] == '[' -> {
                val closeBracket = raw.indexOf(']', i + 1)
                val openParen = if (closeBracket > i && closeBracket + 1 < raw.length && raw[closeBracket + 1] == '(') closeBracket + 1 else -1
                val closeParen = if (openParen > 0) raw.indexOf(')', openParen + 1) else -1
                if (closeBracket > i && openParen > 0 && closeParen > openParen) {
                    val label = raw.substring(i + 1, closeBracket)
                    val url = raw.substring(openParen + 1, closeParen).trim()
                    if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                        withLink(
                            LinkAnnotation.Url(
                                url = url,
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        color = linkColor,
                                        textDecoration = TextDecoration.Underline,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                ),
                            ),
                        ) {
                            append(label)
                        }
                    } else {
                        pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold))
                        append(label)
                        pop()
                    }
                    i = closeParen + 1
                } else {
                    append('['); i++
                }
            }
            else -> {
                append(raw[i])
                i++
            }
        }
    }
}
