package com.gugachat.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.AppLanguage
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

internal sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class CodeFence(val lang: String, val code: String) : MdBlock
    data class MathBlock(val rawLatex: String) : MdBlock
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
    messageKey: Any? = null,
    durationMs: Long? = null,
) {
    if (reasoning.isBlank() && !isStreaming) return
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    // Never force-collapse when streaming finishes so readers don't lose scroll position
    var expanded by remember(messageKey) { mutableStateOf(isStreaming) }
    var liveElapsedSec by remember(messageKey) { mutableStateOf(0) }
    LaunchedEffect(isStreaming, messageKey) {
        if (isStreaming) {
            val startedAt = System.currentTimeMillis()
            while (true) {
                liveElapsedSec = ((System.currentTimeMillis() - startedAt) / 1000L).toInt().coerceAtLeast(1)
                delay(1000L)
            }
        }
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "reasoning_chevron",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val labelText = when {
            isStreaming && liveElapsedSec > 0 -> "${strings.chat.thinkingRunning} · ${liveElapsedSec}s"
            isStreaming -> strings.chat.thinkingRunning
            durationMs != null && durationMs > 0L ->
                strings.chat.thinkingDone.fmt("seconds" to kotlin.math.max(1, ((durationMs + 500L) / 1000L).toInt()))
            liveElapsedSec > 0 ->
                strings.chat.thinkingDone.fmt("seconds" to liveElapsedSec)
            else -> strings.chat.thinkingTitle
        }
        Row(
            modifier = Modifier
                .heightIn(min = 28.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 2.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isStreaming) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.6.dp,
                    color = c.primary,
                )
            } else {
                Text(
                    text = "✦",
                    color = c.primary.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = labelText,
                color = if (isStreaming) c.ink.copy(alpha = 0.85f) else c.muted,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = c.muted.copy(alpha = 0.78f),
                modifier = Modifier
                    .size(15.dp)
                    .rotate(chevronRotation),
            )
        }

        AnimatedVisibility(visible = expanded && reasoning.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(start = 5.dp, top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(c.primary.copy(alpha = 0.4f)),
                )
                SelectionContainer {
                    Text(
                        text = reasoning.trim(),
                        color = c.muted,
                        fontSize = 13.sp,
                        lineHeight = 21.sp,
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
    indentFirstLine: Boolean = false,
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    is MdBlock.MathBlock -> {
                        val formattedMath = remember(block.rawLatex) {
                            LatexUnicodeFormatter.formatToDisplay(block.rawLatex)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(p.inlineCodeBg.copy(alpha = 0.72f))
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = formattedMath,
                                color = p.text,
                                fontSize = fontSize,
                                lineHeight = lineHeight,
                                fontFamily = FontFamily.Serif,
                                softWrap = false,
                            )
                        }
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
                        val paragraphText = if (indentFirstLine) {
                            block.text.lines().joinToString("\n") { line ->
                                val trimmedLine = line.trimStart()
                                if (trimmedLine.isEmpty() || line.startsWith("　　") || line.startsWith("  ")) {
                                    line
                                } else {
                                    "　　$trimmedLine"
                                }
                            }
                        } else {
                            block.text
                        }
                        InlineRichText(
                            raw = paragraphText,
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

private val PLAIN_TEXT_FENCE_LANGS = setOf(
    "text", "txt", "plaintext", "plain", "md", "markdown",
    "note", "notes", "diary", "story", "quote", "prose", "log", "output",
)

private val CJK_CHAR_RE = Regex("[\\u4E00-\\u9FFF\\u3040-\\u30FF\\uAC00-\\uD7AF]")
private val CODE_SYNTAX_HINT_RE = Regex(
    """(^\s*(?:import|export|package|def|class|func|function|const|let|var|return|if\s*\(|for\s*\(|while\s*\(|public|private|struct|SELECT|INSERT|UPDATE|DELETE|#include|<!DOCTYPE|<html)\b|[{};]\s*$)""",
    setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE),
)

internal fun isPlainTextCodeFence(lang: String, code: String): Boolean {
    val norm = lang.trim().lowercase()
    if (norm in PLAIN_TEXT_FENCE_LANGS) return true
    if (norm.isNotEmpty()) return false
    // Unlabeled fence: treat as prose if it contains CJK text without obvious code syntax.
    return CJK_CHAR_RE.containsMatchIn(code) && !CODE_SYNTAX_HINT_RE.containsMatchIn(code)
}

internal fun shouldDefaultWrapCodeFence(lang: String, code: String): Boolean {
    if (isPlainTextCodeFence(lang, code)) return true
    val norm = lang.trim().lowercase()
    if (norm.isEmpty()) {
        val longestLine = code.lineSequence().maxOfOrNull { it.length } ?: 0
        if (longestLine > 44 && !CODE_SYNTAX_HINT_RE.containsMatchIn(code)) return true
    }
    return false
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
    val strings = LocalAppStrings.current
    val isZh = strings.language == AppLanguage.ZH
    val isPlainText = remember(lang, code) { isPlainTextCodeFence(lang, code) }
    var wrapLines by remember(lang, code) { mutableStateOf(shouldDefaultWrapCodeFence(lang, code)) }
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
            .clip(RoundedCornerShape(12.dp))
            .background(palette.codeBg)
            .border(theme.borderWidth, palette.border.copy(alpha = 0.65f), RoundedCornerShape(12.dp)),
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
                text = lang.ifBlank { if (isPlainText) "text" else "code" }.lowercase(),
                color = palette.codeMeta,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (wrapLines) palette.accent.copy(alpha = 0.14f) else palette.codeBg.copy(alpha = 0.72f),
                        )
                        .clickable { wrapLines = !wrapLines }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.WrapText,
                        contentDescription = if (isZh) "切换自动换行" else "Toggle line wrap",
                        tint = if (wrapLines) palette.accent else palette.codeMeta,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = if (isZh) {
                            if (wrapLines) "已换行" else "换行"
                        } else {
                            if (wrapLines) "Wrapped" else "Wrap"
                        },
                        color = if (wrapLines) palette.accent else palette.codeMeta,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Row(
                    modifier = Modifier
                        .heightIn(min = 28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.codeBg.copy(alpha = 0.72f))
                        .clickable {
                            onCopy()
                            justCopied = true
                        }
                        .padding(horizontal = 9.dp, vertical = 4.dp),
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
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        HorizontalDivider(
            thickness = theme.borderWidth,
            color = palette.border.copy(alpha = 0.75f),
        )
        Text(
            text = code,
            color = palette.codeInk,
            fontSize = if (isPlainText && wrapLines) 13.5.sp else 12.5.sp,
            lineHeight = if (isPlainText && wrapLines) 21.sp else 19.5.sp,
            fontFamily = if (isPlainText && wrapLines) FontFamily.Default else FontFamily.Monospace,
            softWrap = wrapLines,
            modifier = if (wrapLines) {
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 12.dp)
            } else {
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 13.dp, vertical = 12.dp)
            },
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

internal fun parseMarkdownBlocks(raw: String): List<MdBlock> {
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

    fun tryConsumeDelimitedMathBlock(openDelim: String, closeDelim: String): Boolean {
        val line = lines[i]
        val trimmed = line.trim()
        if (!trimmed.startsWith(openDelim)) return false
        val afterOpen = trimmed.indexOf(closeDelim, openDelim.length)
        if (afterOpen >= openDelim.length && afterOpen == trimmed.length - closeDelim.length) {
            flushPara()
            val inner = trimmed.substring(openDelim.length, afterOpen).trim()
            if (inner.isNotEmpty()) out.add(MdBlock.MathBlock(inner))
            i++
            return true
        }
        if (afterOpen < 0) {
            var closeLine = -1
            var closeCol = -1
            for (j in (i + 1) until lines.size) {
                val idx = lines[j].indexOf(closeDelim)
                if (idx >= 0) {
                    closeLine = j
                    closeCol = idx
                    break
                }
            }
            if (closeLine > i) {
                flushPara()
                val mathLines = ArrayList<String>()
                val firstTail = trimmed.removePrefix(openDelim).trim()
                if (firstTail.isNotEmpty()) mathLines.add(firstTail)
                for (k in (i + 1) until closeLine) {
                    mathLines.add(lines[k])
                }
                val lastHead = lines[closeLine].substring(0, closeCol).trimEnd()
                if (lastHead.isNotEmpty()) mathLines.add(lastHead)
                val expr = mathLines.joinToString("\n").trim()
                if (expr.isNotEmpty()) out.add(MdBlock.MathBlock(expr))
                val trailing = lines[closeLine].substring(closeCol + closeDelim.length).trim()
                if (trailing.isNotEmpty()) paraBuf.add(trailing)
                i = closeLine + 1
                return true
            }
        }
        return false
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

        if (tryConsumeDelimitedMathBlock("$$", "$$")) continue
        if (tryConsumeDelimitedMathBlock("\\[", "\\]")) continue

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

object LatexUnicodeFormatter {
    private val GREEK_AND_SYMBOLS: Map<String, String> = mapOf(
        // Lowercase Greek
        "alpha" to "α",
        "beta" to "β",
        "gamma" to "γ",
        "delta" to "δ",
        "epsilon" to "ε",
        "varepsilon" to "ε",
        "zeta" to "ζ",
        "eta" to "η",
        "theta" to "θ",
        "vartheta" to "ϑ",
        "iota" to "ι",
        "kappa" to "κ",
        "lambda" to "λ",
        "mu" to "μ",
        "nu" to "ν",
        "xi" to "ξ",
        "omicron" to "ο",
        "pi" to "π",
        "varpi" to "ϖ",
        "rho" to "ρ",
        "varrho" to "ϱ",
        "sigma" to "σ",
        "varsigma" to "ς",
        "tau" to "τ",
        "upsilon" to "υ",
        "phi" to "φ",
        "varphi" to "φ",
        "chi" to "χ",
        "psi" to "ψ",
        "omega" to "ω",
        // Uppercase Greek
        "Gamma" to "Γ",
        "Delta" to "Δ",
        "Theta" to "Θ",
        "Lambda" to "Λ",
        "Xi" to "Ξ",
        "Pi" to "Π",
        "Sigma" to "Σ",
        "Upsilon" to "Υ",
        "Phi" to "Φ",
        "Psi" to "Ψ",
        "Omega" to "Ω",
        // Operators, relations & arrows
        "times" to "×",
        "cdot" to "·",
        "div" to "÷",
        "pm" to "±",
        "mp" to "∓",
        "leq" to "≤",
        "le" to "≤",
        "geq" to "≥",
        "ge" to "≥",
        "neq" to "≠",
        "ne" to "≠",
        "approx" to "≈",
        "equiv" to "≡",
        "sim" to "∼",
        "simeq" to "≃",
        "propto" to "∝",
        "rightarrow" to "→",
        "to" to "→",
        "leftarrow" to "←",
        "gets" to "←",
        "leftrightarrow" to "↔",
        "Rightarrow" to "⇒",
        "implies" to "⇒",
        "Leftarrow" to "⇐",
        "Leftrightarrow" to "⇔",
        "iff" to "⇔",
        "mapsto" to "↦",
        "infty" to "∞",
        "sum" to "∑",
        "prod" to "∏",
        "int" to "∫",
        "iint" to "∬",
        "oint" to "∮",
        "partial" to "∂",
        "nabla" to "∇",
        "forall" to "∀",
        "exists" to "∃",
        "in" to "∈",
        "notin" to "∉",
        "subset" to "⊂",
        "subseteq" to "⊆",
        "supset" to "⊃",
        "supseteq" to "⊇",
        "cup" to "∪",
        "cap" to "∩",
        "emptyset" to "∅",
        "varnothing" to "∅",
        "land" to "∧",
        "wedge" to "∧",
        "lor" to "∨",
        "vee" to "∨",
        "neg" to "¬",
        "lnot" to "¬",
        "therefore" to "∴",
        "because" to "∵",
        "dots" to "…",
        "ldots" to "…",
        "cdots" to "⋯",
        "circ" to "°",
        "degree" to "°",
        "prime" to "′",
        "quad" to "  ",
        "qquad" to "    ",
    )

    private val BLACKBOARD_BOLD: Map<Char, String> = mapOf(
        'R' to "ℝ",
        'N' to "ℕ",
        'Z' to "ℤ",
        'Q' to "ℚ",
        'C' to "ℂ",
    )

    private val SUPERSCRIPTS: Map<Char, Char> = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'a' to 'ᵃ', 'b' to 'ᵇ', 'c' to 'ᶜ', 'd' to 'ᵈ', 'e' to 'ᵉ',
        'f' to 'ᶠ', 'g' to 'ᵍ', 'h' to 'ʰ', 'i' to 'ⁱ', 'j' to 'ʲ',
        'k' to 'ᵏ', 'l' to 'ˡ', 'm' to 'ᵐ', 'n' to 'ⁿ', 'o' to 'ᵒ',
        'p' to 'ᵖ', 'r' to 'ʳ', 's' to 'ˢ', 't' to 'ᵗ', 'u' to 'ᵘ',
        'v' to 'ᵛ', 'w' to 'ʷ', 'x' to 'ˣ', 'y' to 'ʸ', 'z' to 'ᶻ',
    )

    private val SUBSCRIPTS: Map<Char, Char> = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ',
        'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ',
        'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ',
        'v' to 'ᵥ', 'x' to 'ₓ',
    )

    private val UNWRAP_TEXT_CMDS = setOf("text", "mathrm", "mathbf", "mathit", "mathsf", "operatorname")

    fun formatToDisplay(expr: String): String =
        formatInternal(expr.trim(), depth = 0).trim()

    private fun formatInternal(input: String, depth: Int): String {
        if (input.isEmpty() || depth > 8) return input
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            when {
                ch == '\\' && i + 1 < input.length -> {
                    val next = input[i + 1]
                    if (!next.isLetter()) {
                        when (next) {
                            '\\' -> sb.append('\n')
                            ',', ';', ':' -> sb.append(' ')
                            '!' -> Unit
                            '{', '}', '$', '%', '&', '#', '_' -> sb.append(next)
                            else -> sb.append(next)
                        }
                        i += 2
                        continue
                    }
                    var cmdEnd = i + 1
                    while (cmdEnd < input.length && input[cmdEnd].isLetter()) cmdEnd++
                    val cmd = input.substring(i + 1, cmdEnd)
                    when (cmd) {
                        "frac", "dfrac", "tfrac" -> {
                            val num = readBraceArg(input, cmdEnd)
                            val den = num?.let { readBraceArg(input, it.second) }
                            if (num != null && den != null) {
                                val fNum = formatInternal(num.first, depth + 1).trim()
                                val fDen = formatInternal(den.first, depth + 1).trim()
                                sb.append("($fNum)/($fDen)")
                                i = den.second
                            } else {
                                sb.append('/').also { i = cmdEnd }
                            }
                        }
                        "sqrt" -> {
                            var cursor = skipSpaces(input, cmdEnd)
                            var degree: String? = null
                            if (cursor < input.length && input[cursor] == '[') {
                                val closeBracket = input.indexOf(']', cursor + 1)
                                if (closeBracket > cursor) {
                                    degree = formatInternal(input.substring(cursor + 1, closeBracket), depth + 1).trim()
                                    cursor = closeBracket + 1
                                }
                            }
                            val arg = readGroupOrSingleToken(input, cursor)
                            if (arg != null) {
                                val inner = formatInternal(arg.first, depth + 1).trim()
                                if (degree != null) {
                                    val supDeg = toUnicodeScript(degree, SUPERSCRIPTS)
                                    if (supDeg != null) sb.append(supDeg) else sb.append(degree)
                                }
                                if (inner.length == 1 && inner[0].isLetterOrDigit()) {
                                    sb.append('√').append(inner)
                                } else {
                                    sb.append("√(").append(inner).append(')')
                                }
                                i = arg.second
                            } else {
                                sb.append('√')
                                i = cmdEnd
                            }
                        }
                        "mathbb" -> {
                            val arg = readBraceArg(input, cmdEnd)
                            if (arg != null) {
                                val key = arg.first.trim()
                                val mapped = if (key.length == 1) BLACKBOARD_BOLD[key[0]] else null
                                sb.append(mapped ?: key)
                                i = arg.second
                            } else {
                                i = cmdEnd
                            }
                        }
                        in UNWRAP_TEXT_CMDS -> {
                            val arg = readBraceArg(input, cmdEnd)
                            if (arg != null) {
                                sb.append(arg.first)
                                i = arg.second
                            } else {
                                i = cmdEnd
                            }
                        }
                        "left", "right" -> {
                            i = skipSpaces(input, cmdEnd)
                            if (i < input.length && input[i] == '.') i++
                        }
                        else -> {
                            val sym = GREEK_AND_SYMBOLS[cmd]
                            if (sym != null) {
                                sb.append(sym)
                            } else {
                                sb.append('\\').append(cmd)
                            }
                            i = cmdEnd
                        }
                    }
                }
                ch == '^' || ch == '_' -> {
                    val isSuper = ch == '^'
                    val arg = readGroupOrSingleToken(input, i + 1)
                    if (arg != null) {
                        val rawArg = arg.first.trim()
                        if (isSuper && (rawArg == "\\circ" || rawArg == "\\degree")) {
                            sb.append('°')
                        } else {
                            val formattedArg = formatInternal(rawArg, depth + 1).trim()
                            val table = if (isSuper) SUPERSCRIPTS else SUBSCRIPTS
                            val converted = toUnicodeScript(formattedArg, table)
                            if (converted != null) {
                                sb.append(converted)
                            } else if (formattedArg.length == 1) {
                                sb.append(ch).append(formattedArg)
                            } else {
                                sb.append(ch).append('(').append(formattedArg).append(')')
                            }
                        }
                        i = arg.second
                    } else {
                        sb.append(ch)
                        i++
                    }
                }
                ch == '{' -> {
                    val group = readBraceArg(input, i)
                    if (group != null) {
                        sb.append(formatInternal(group.first, depth + 1))
                        i = group.second
                    } else {
                        sb.append(ch)
                        i++
                    }
                }
                else -> {
                    sb.append(ch)
                    i++
                }
            }
        }
        return sb.toString()
    }

    private fun skipSpaces(s: String, start: Int): Int {
        var idx = start
        while (idx < s.length && s[idx] == ' ') idx++
        return idx
    }

    private fun readBraceArg(s: String, start: Int): Pair<String, Int>? {
        val openIdx = skipSpaces(s, start)
        if (openIdx >= s.length || s[openIdx] != '{') return null
        var depth = 0
        var idx = openIdx
        while (idx < s.length) {
            when (s[idx]) {
                '\\' -> idx += 2
                '{' -> {
                    depth++
                    idx++
                }
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return s.substring(openIdx + 1, idx) to (idx + 1)
                    }
                    idx++
                }
                else -> idx++
            }
        }
        return null
    }

    private fun readGroupOrSingleToken(s: String, start: Int): Pair<String, Int>? {
        readBraceArg(s, start)?.let { return it }
        val idx = skipSpaces(s, start)
        if (idx >= s.length) return null
        if (s[idx] == '\\') {
            var end = idx + 1
            while (end < s.length && s[end].isLetter()) end++
            if (end > idx + 1) return s.substring(idx, end) to end
            if (idx + 1 < s.length) return s.substring(idx, idx + 2) to (idx + 2)
        }
        return s.substring(idx, idx + 1) to (idx + 1)
    }

    private fun toUnicodeScript(text: String, table: Map<Char, Char>): String? {
        if (text.isEmpty()) return null
        val out = StringBuilder(text.length)
        var hasMapped = false
        for (ch in text) {
            if (ch == ' ') {
                out.append(' ')
                continue
            }
            val mapped = table[ch] ?: return null
            out.append(mapped)
            hasMapped = true
        }
        return if (hasMapped) out.toString() else null
    }
}

object MarkdownMathGuard {
    sealed interface MathSegment {
        data class Text(val text: String) : MathSegment
        data class InlineMath(val rawLatex: String, val displayText: String) : MathSegment
        data class DisplayMath(val rawLatex: String, val displayText: String) : MathSegment
    }

    sealed interface InlineToken {
        data class Plain(val text: String) : InlineToken
        data class Bold(val children: List<InlineToken>) : InlineToken
        data class Italic(val children: List<InlineToken>) : InlineToken
        data class BoldItalic(val children: List<InlineToken>) : InlineToken
        data class Strike(val children: List<InlineToken>) : InlineToken
        data class Code(val code: String) : InlineToken
        data class Link(val label: String, val url: String) : InlineToken
        data class InlineMath(val rawLatex: String, val displayText: String) : InlineToken
        data class DisplayMath(val rawLatex: String, val displayText: String) : InlineToken
    }

    private data class MathMatch(
        val isDisplay: Boolean,
        val rawLatex: String,
        val endExclusive: Int,
    )

    private fun isEscaped(s: String, idx: Int): Boolean {
        var backslashes = 0
        var j = idx - 1
        while (j >= 0 && s[j] == '\\') {
            backslashes++
            j--
        }
        return (backslashes % 2) == 1
    }

    private fun hasBlankLineBetween(s: String, startInclusive: Int, endExclusive: Int): Boolean {
        var idx = startInclusive
        while (idx < endExclusive) {
            if (s[idx] == '\n') {
                var k = idx + 1
                while (k < endExclusive && (s[k] == ' ' || s[k] == '\t' || s[k] == '\r')) k++
                if (k < endExclusive && s[k] == '\n') return true
            }
            idx++
        }
        return false
    }

    fun isValidInlineDollarSpan(raw: String, openDollarIdx: Int): Int {
        if (openDollarIdx !in raw.indices || raw[openDollarIdx] != '$') return -1
        if (isEscaped(raw, openDollarIdx)) return -1
        if (openDollarIdx + 1 >= raw.length) return -1
        if (raw[openDollarIdx + 1] == '$') return -1
        if (openDollarIdx > 0 && raw[openDollarIdx - 1] == '$' && !isEscaped(raw, openDollarIdx - 1)) return -1
        // Pandoc rule: opening '$' must NOT be immediately followed by whitespace.
        if (raw[openDollarIdx + 1].isWhitespace()) return -1

        var j = openDollarIdx + 1
        while (j < raw.length) {
            if (raw[j] == '\n') {
                var k = j + 1
                while (k < raw.length && (raw[k] == ' ' || raw[k] == '\t' || raw[k] == '\r')) k++
                if (k < raw.length && raw[k] == '\n') return -1
            }
            if (raw[j] == '$' && !isEscaped(raw, j)) {
                val nextCh = raw.getOrNull(j + 1)
                if (nextCh == '$') return -1
                val validClosing = j >= openDollarIdx + 2 &&
                    !raw[j - 1].isWhitespace() &&
                    (nextCh == null || nextCh !in '0'..'9')
                return if (validClosing) j else -1
            }
            j++
        }
        return -1
    }

    private fun findClosingDoubleDelim(raw: String, contentStart: Int, closeDelim: String, allowBlankLines: Boolean = false): Int {
        var j = contentStart
        while (j <= raw.length - closeDelim.length) {
            if (raw.startsWith(closeDelim, j) && !isEscaped(raw, j)) {
                if (!allowBlankLines && hasBlankLineBetween(raw, contentStart, j)) return -1
                return j
            }
            j++
        }
        return -1
    }

    private fun matchMathAt(raw: String, i: Int): MathMatch? {
        if (raw.startsWith("$$", i) && !isEscaped(raw, i)) {
            val end = findClosingDoubleDelim(raw, i + 2, "$$", allowBlankLines = false)
            if (end > i + 2) {
                val expr = raw.substring(i + 2, end).trim()
                if (expr.isNotEmpty()) return MathMatch(isDisplay = true, rawLatex = expr, endExclusive = end + 2)
            }
        }
        if (raw.startsWith("\\[", i) && !isEscaped(raw, i)) {
            val end = findClosingDoubleDelim(raw, i + 2, "\\]", allowBlankLines = false)
            if (end > i + 2) {
                val expr = raw.substring(i + 2, end).trim()
                if (expr.isNotEmpty()) return MathMatch(isDisplay = true, rawLatex = expr, endExclusive = end + 2)
            }
        }
        if (raw.startsWith("\\(", i) && !isEscaped(raw, i)) {
            val end = findClosingDoubleDelim(raw, i + 2, "\\)", allowBlankLines = false)
            if (end > i + 2) {
                val expr = raw.substring(i + 2, end).trim()
                if (expr.isNotEmpty()) return MathMatch(isDisplay = false, rawLatex = expr, endExclusive = end + 2)
            }
        }
        if (raw[i] == '$') {
            val closeIdx = isValidInlineDollarSpan(raw, i)
            if (closeIdx > i + 1) {
                val expr = raw.substring(i + 1, closeIdx)
                return MathMatch(isDisplay = false, rawLatex = expr, endExclusive = closeIdx + 1)
            }
        }
        return null
    }

    fun splitMathSegments(raw: String): List<MathSegment> {
        if (raw.isEmpty()) return emptyList()
        val out = ArrayList<MathSegment>()
        val textBuf = StringBuilder()

        fun flushText() {
            if (textBuf.isNotEmpty()) {
                out.add(MathSegment.Text(textBuf.toString()))
                textBuf.setLength(0)
            }
        }

        var i = 0
        while (i < raw.length) {
            if (raw[i] == '`') {
                val closeCode = raw.indexOf('`', i + 1)
                if (closeCode > i + 1 && !hasBlankLineBetween(raw, i + 1, closeCode)) {
                    textBuf.append(raw, i, closeCode + 1)
                    i = closeCode + 1
                    continue
                }
            }
            val math = matchMathAt(raw, i)
            if (math != null) {
                flushText()
                val formatted = LatexUnicodeFormatter.formatToDisplay(math.rawLatex)
                if (math.isDisplay) {
                    out.add(MathSegment.DisplayMath(math.rawLatex, formatted))
                } else {
                    out.add(MathSegment.InlineMath(math.rawLatex, formatted))
                }
                i = math.endExclusive
                continue
            }
            textBuf.append(raw[i])
            i++
        }
        flushText()
        return out
    }

    private fun findClosingMarkerSkippingProtected(
        raw: String,
        contentStart: Int,
        marker: String,
        isUnderscore: Boolean = false,
    ): Int {
        var j = contentStart
        while (j <= raw.length - marker.length) {
            if (raw[j] == '`') {
                val codeEnd = raw.indexOf('`', j + 1)
                if (codeEnd > j + 1) {
                    j = codeEnd + 1
                    continue
                }
            }
            val math = matchMathAt(raw, j)
            if (math != null) {
                j = math.endExclusive
                continue
            }
            if (raw.startsWith(marker, j) && !isEscaped(raw, j)) {
                if (isUnderscore) {
                    val prevOk = j > contentStart && !raw[j - 1].isWhitespace()
                    val nextCh = raw.getOrNull(j + 1)
                    val nextOk = nextCh == null || !nextCh.isLetterOrDigit()
                    val notDouble = nextCh != '_' && raw.getOrNull(j - 1) != '_'
                    if (prevOk && nextOk && notDouble) return j
                } else if (marker == "*") {
                    if (raw.startsWith("**", j)) {
                        j += 2
                        continue
                    }
                    if (j > contentStart) return j
                } else if (j > contentStart) {
                    return j
                }
            }
            j++
        }
        return -1
    }

    fun tokenizeInline(raw: String, depth: Int = 0): List<InlineToken> {
        if (raw.isEmpty()) return emptyList()
        val out = ArrayList<InlineToken>()
        val plainBuf = StringBuilder()

        fun flushPlain() {
            if (plainBuf.isNotEmpty()) {
                out.add(InlineToken.Plain(plainBuf.toString()))
                plainBuf.setLength(0)
            }
        }

        fun tokenizeChildren(inner: String): List<InlineToken> =
            if (depth >= 4) listOf(InlineToken.Plain(inner)) else tokenizeInline(inner, depth + 1)

        var i = 0
        while (i < raw.length) {
            if (raw[i] == '`') {
                val end = raw.indexOf('`', i + 1)
                if (end > i + 1) {
                    flushPlain()
                    out.add(InlineToken.Code(raw.substring(i + 1, end)))
                    i = end + 1
                    continue
                }
            }

            val math = matchMathAt(raw, i)
            if (math != null) {
                flushPlain()
                val formatted = LatexUnicodeFormatter.formatToDisplay(math.rawLatex)
                if (math.isDisplay) {
                    out.add(InlineToken.DisplayMath(math.rawLatex, formatted))
                } else {
                    out.add(InlineToken.InlineMath(math.rawLatex, formatted))
                }
                i = math.endExclusive
                continue
            }

            when {
                raw.startsWith("***", i) && !isEscaped(raw, i) -> {
                    val end = findClosingMarkerSkippingProtected(raw, i + 3, "***")
                    if (end > i + 3) {
                        flushPlain()
                        out.add(InlineToken.BoldItalic(tokenizeChildren(raw.substring(i + 3, end))))
                        i = end + 3
                    } else {
                        plainBuf.append("***")
                        i += 3
                    }
                }
                raw.startsWith("**", i) && !isEscaped(raw, i) -> {
                    val end = findClosingMarkerSkippingProtected(raw, i + 2, "**")
                    if (end > i + 2) {
                        flushPlain()
                        out.add(InlineToken.Bold(tokenizeChildren(raw.substring(i + 2, end))))
                        i = end + 2
                    } else {
                        plainBuf.append("**")
                        i += 2
                    }
                }
                raw.startsWith("~~", i) && !isEscaped(raw, i) -> {
                    val end = findClosingMarkerSkippingProtected(raw, i + 2, "~~")
                    if (end > i + 2) {
                        flushPlain()
                        out.add(InlineToken.Strike(tokenizeChildren(raw.substring(i + 2, end))))
                        i = end + 2
                    } else {
                        plainBuf.append("~~")
                        i += 2
                    }
                }
                raw[i] == '*' && !raw.startsWith("**", i) && !isEscaped(raw, i) -> {
                    val end = findClosingMarkerSkippingProtected(raw, i + 1, "*")
                    if (end > i + 1) {
                        flushPlain()
                        out.add(InlineToken.Italic(tokenizeChildren(raw.substring(i + 1, end))))
                        i = end + 1
                    } else {
                        plainBuf.append('*')
                        i++
                    }
                }
                raw[i] == '_' && !raw.startsWith("__", i) && !isEscaped(raw, i) &&
                    (i == 0 || (!raw[i - 1].isLetterOrDigit() && raw[i - 1] != '_')) &&
                    i + 1 < raw.length && !raw[i + 1].isWhitespace() -> {
                    val end = findClosingMarkerSkippingProtected(raw, i + 1, "_", isUnderscore = true)
                    if (end > i + 1) {
                        flushPlain()
                        out.add(InlineToken.Italic(tokenizeChildren(raw.substring(i + 1, end))))
                        i = end + 1
                    } else {
                        plainBuf.append('_')
                        i++
                    }
                }
                raw[i] == '[' -> {
                    val closeBracket = raw.indexOf(']', i + 1)
                    val openParen = if (closeBracket > i && closeBracket + 1 < raw.length && raw[closeBracket + 1] == '(') closeBracket + 1 else -1
                    val closeParen = if (openParen > 0) raw.indexOf(')', openParen + 1) else -1
                    if (closeBracket > i && openParen > 0 && closeParen > openParen) {
                        flushPlain()
                        val label = raw.substring(i + 1, closeBracket)
                        val url = raw.substring(openParen + 1, closeParen).trim()
                        out.add(InlineToken.Link(label, url))
                        i = closeParen + 1
                    } else {
                        plainBuf.append('[')
                        i++
                    }
                }
                else -> {
                    plainBuf.append(raw[i])
                    i++
                }
            }
        }
        flushPlain()
        return out
    }
}

private fun parseInlineAnnotated(
    raw: String,
    linkColor: Color,
    codeBg: Color,
    codeInk: Color,
): AnnotatedString = buildAnnotatedString {
    val tokens = MarkdownMathGuard.tokenizeInline(raw)

    fun appendTokens(list: List<MarkdownMathGuard.InlineToken>) {
        list.forEach { tok ->
            when (tok) {
                is MarkdownMathGuard.InlineToken.Plain -> append(tok.text)
                is MarkdownMathGuard.InlineToken.Bold -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.ExtraBold))
                    appendTokens(tok.children)
                    pop()
                }
                is MarkdownMathGuard.InlineToken.Italic -> {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    appendTokens(tok.children)
                    pop()
                }
                is MarkdownMathGuard.InlineToken.BoldItalic -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic))
                    appendTokens(tok.children)
                    pop()
                }
                is MarkdownMathGuard.InlineToken.Strike -> {
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    appendTokens(tok.children)
                    pop()
                }
                is MarkdownMathGuard.InlineToken.Code -> {
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, color = codeInk))
                    append(" ${tok.code} ")
                    pop()
                }
                is MarkdownMathGuard.InlineToken.InlineMath -> {
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Serif,
                            background = codeBg.copy(alpha = 0.55f),
                            color = codeInk,
                        ),
                    )
                    append(tok.displayText)
                    pop()
                }
                is MarkdownMathGuard.InlineToken.DisplayMath -> {
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Serif,
                            background = codeBg.copy(alpha = 0.65f),
                            color = codeInk,
                        ),
                    )
                    append(tok.displayText)
                    pop()
                }
                is MarkdownMathGuard.InlineToken.Link -> {
                    val url = tok.url
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
                            append(tok.label)
                        }
                    } else {
                        pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold))
                        append(tok.label)
                        pop()
                    }
                }
            }
        }
    }

    appendTokens(tokens)
}
