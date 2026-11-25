package com.gugachat.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.LocalAppTheme

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
    val c = LocalAppTheme.current.colors
    var expanded by remember(isStreaming) { mutableStateOf(isStreaming) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(c.surfaceSoft)
            .border(1.dp, c.border, RoundedCornerShape(10.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isStreaming) {
                CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 1.8.dp, color = c.primary)
            } else {
                Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(15.dp), tint = c.primary)
            }
            Text(
                text = if (isStreaming) strings.chat.thinkingRunning else strings.chat.thinkingDone,
                color = c.muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = c.muted,
                modifier = Modifier.size(16.dp),
            )
        }
        AnimatedVisibility(visible = expanded && reasoning.isNotBlank()) {
            Text(
                text = reasoning.trim(),
                color = c.muted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
            )
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
    onCopyToast: ((String) -> Unit)? = null,
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val ctx = LocalContext.current
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val family = if (useSerif) FontFamily.Serif else FontFamily.Default

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Divider -> HorizontalDivider(color = c.border, thickness = 1.dp)
                is MdBlock.Heading -> {
                    val hSize = when (block.level) {
                        1 -> (fontSize.value + 5f).sp
                        2 -> (fontSize.value + 3f).sp
                        else -> (fontSize.value + 1.5f).sp
                    }
                    InlineRichText(
                        raw = block.text,
                        color = textColor,
                        fontSize = hSize,
                        lineHeight = (hSize.value * 1.4f).sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = family,
                        context = ctx,
                    )
                }
                is MdBlock.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .clip(RoundedCornerShape(6.dp))
                            .background(c.surfaceSoft.copy(alpha = 0.7f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(c.primary),
                        )
                        Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            InlineRichText(
                                raw = block.text,
                                color = c.muted,
                                fontSize = fontSize,
                                lineHeight = lineHeight,
                                fontWeight = FontWeight.Normal,
                                fontFamily = family,
                                context = ctx,
                            )
                        }
                    }
                }
                is MdBlock.BulletItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = block.bullet,
                            color = c.primary,
                            fontSize = fontSize,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                        InlineRichText(
                            raw = block.text,
                            color = textColor,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            fontWeight = FontWeight.Normal,
                            fontFamily = family,
                            context = ctx,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                is MdBlock.CodeFence -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF151C28))
                            .border(1.dp, c.outline.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E2838))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = block.lang.ifBlank { "code" },
                                color = Color(0xFFA6B4CA),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            )
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("code", block.code))
                                        onCopyToast?.invoke(strings.common.copied)
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = strings.chat.copyCode,
                                    tint = Color(0xFFFDE8BA),
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = strings.chat.copyCode,
                                    color = Color(0xFFFDE8BA),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Text(
                            text = block.code,
                            color = Color(0xFFE6EDF3),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(10.dp),
                        )
                    }
                }
                is MdBlock.TableBlock -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, c.border, RoundedCornerShape(8.dp)),
                    ) {
                        Row(modifier = Modifier.background(c.surfaceSoft)) {
                            block.headers.forEach { h ->
                                Text(
                                    text = h,
                                    color = c.ink,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(120.dp).padding(8.dp),
                                )
                            }
                        }
                        block.rows.forEach { row ->
                            HorizontalDivider(color = c.border, thickness = 1.dp)
                            Row {
                                block.headers.indices.forEach { idx ->
                                    Text(
                                        text = row.getOrNull(idx).orEmpty(),
                                        color = textColor,
                                        fontSize = 12.sp,
                                        modifier = Modifier.width(120.dp).padding(8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                is MdBlock.Paragraph -> {
                    InlineRichText(
                        raw = block.text,
                        color = textColor,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        fontWeight = FontWeight.Normal,
                        fontFamily = family,
                        context = ctx,
                    )
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun InlineRichText(
    raw: String,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight,
    fontFamily: FontFamily,
    context: Context,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppTheme.current.colors
    val annotated = remember(raw, c.primary, c.surfaceSoft) {
        parseInlineAnnotated(raw, linkColor = c.primary, codeBg = c.surfaceSoft)
    }
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
        ),
        onClick = { offset ->
            annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()
                ?.let { ann ->
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ann.item)))
                    }
                }
        },
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
): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < raw.length) {
        when {
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
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg))
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
                    pushStringAnnotation(tag = "URL", annotation = url)
                    pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold))
                    append(label)
                    pop()
                    pop()
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
