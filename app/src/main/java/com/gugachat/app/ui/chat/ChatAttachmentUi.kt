package com.gugachat.app.ui.chat

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.chat.ChatAttachmentMeta
import com.gugachat.app.domain.chat.FileContextBudgeter
import com.gugachat.app.domain.chat.FileInjectMode
import com.gugachat.app.domain.chat.FileTextExtractor
import java.io.File
import java.util.Locale

private data class FileBadgeStyle(
    val icon: ImageVector,
    val tint: Color,
    val bg: Color,
    val extLabel: String,
)

private fun resolveFileStyle(fileName: String, isDark: Boolean): FileBadgeStyle {
    val ext = FileTextExtractor.extensionOf(fileName)
    val label = ext.uppercase(Locale.US).ifBlank { "FILE" }.take(5)
    return when (ext) {
        "pdf" -> FileBadgeStyle(
            icon = Icons.Default.PictureAsPdf,
            tint = if (isDark) Color(0xFFFCA5A5) else Color(0xFFDC2626),
            bg = if (isDark) Color(0x33DC2626) else Color(0xFFFEE2E2),
            extLabel = label,
        )
        "doc", "docx", "rtf", "odt" -> FileBadgeStyle(
            icon = Icons.Default.Description,
            tint = if (isDark) Color(0xFF93C5FD) else Color(0xFF2563EB),
            bg = if (isDark) Color(0x332563EB) else Color(0xFFDBEAFE),
            extLabel = label,
        )
        "xls", "xlsx", "csv", "tsv" -> FileBadgeStyle(
            icon = Icons.Default.TableChart,
            tint = if (isDark) Color(0xFF86EFAC) else Color(0xFF15803D),
            bg = if (isDark) Color(0x3316A34A) else Color(0xFFDCFCE7),
            extLabel = label,
        )
        "ppt", "pptx" -> FileBadgeStyle(
            icon = Icons.Default.Slideshow,
            tint = if (isDark) Color(0xFFFDBA74) else Color(0xFFEA580C),
            bg = if (isDark) Color(0x33EA580C) else Color(0xFFFFEDD5),
            extLabel = label,
        )
        "epub" -> FileBadgeStyle(
            icon = Icons.Default.AutoStories,
            tint = if (isDark) Color(0xFFD8B4FE) else Color(0xFF7C3AED),
            bg = if (isDark) Color(0x337C3AED) else Color(0xFFF3E8FF),
            extLabel = label,
        )
        "kt", "kts", "java", "py", "js", "jsx", "ts", "tsx", "go", "rs", "c", "cpp", "h",
        "hpp", "cs", "swift", "rb", "php", "sh", "sql", "json", "jsonl", "yaml", "yml", "xml", "html", "css",
        -> FileBadgeStyle(
            icon = Icons.Default.Code,
            tint = if (isDark) Color(0xFFA5B4FC) else Color(0xFF4F46E5),
            bg = if (isDark) Color(0x334F46E5) else Color(0xFFE0E7FF),
            extLabel = label,
        )
        else -> FileBadgeStyle(
            icon = Icons.Default.InsertDriveFile,
            tint = if (isDark) Color(0xFF5EEAD4) else Color(0xFF0F766E),
            bg = if (isDark) Color(0x330F766E) else Color(0xFFCCFBF1),
            extLabel = label,
        )
    }
}

fun formatAttachmentBytes(bytes: Long): String =
    com.gugachat.app.core.util.FormatUtils.formatAttachmentBytes(bytes)

@Composable
fun FileAttachmentCard(
    meta: ChatAttachmentMeta,
    isSmartGuarded: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val style = remember(meta.fileName, theme.isDark) { resolveFileStyle(meta.fileName, theme.isDark) }

    val sizeLabel = remember(meta.sizeBytes) { formatAttachmentBytes(meta.sizeBytes) }
    val tokLabel = remember(meta.estimatedTokens) {
        if (meta.estimatedTokens > 0) FileContextBudgeter.formatShortTokens(meta.estimatedTokens) else ""
    }
    val subLine = remember(sizeLabel, tokLabel, meta.pageCount) {
        buildList {
            if (sizeLabel.isNotBlank()) add(sizeLabel)
            if (tokLabel.isNotBlank()) add(tokLabel)
            if (meta.pageCount > 0) add("${meta.pageCount}P")
        }.joinToString(" · ")
    }

    Box(modifier = modifier) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(13.dp),
            color = if (theme.isDark) c.surfaceSoft else c.surface,
            border = BorderStroke(theme.borderWidth, c.border),
            modifier = Modifier
                .padding(top = if (onRemove != null) 4.dp else 0.dp, end = if (onRemove != null) 4.dp else 0.dp)
                .widthIn(min = if (compact) 156.dp else 174.dp, max = if (compact) 232.dp else 258.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 34.dp else 36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(style.bg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = style.icon,
                        contentDescription = null,
                        tint = style.tint,
                        modifier = Modifier.size(if (compact) 18.dp else 19.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = meta.fileName.ifBlank { "document.txt" },
                        color = c.ink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        if (subLine.isNotBlank()) {
                            Text(
                                text = subLine,
                                color = c.muted,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        val badgeText = if (isSmartGuarded) {
                            strings.attachments.fileSmartGuardBadge
                        } else {
                            strings.attachments.fileFullBadge
                        }
                        val badgeBg = if (isSmartGuarded) c.warningBg else c.successBg
                        val badgeFg = if (isSmartGuarded) c.warning else c.success
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(badgeBg)
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = badgeText,
                                color = badgeFg,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        if (onRemove != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(c.danger),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = strings.common.delete,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilePreviewAndStrategyDialog(
    meta: ChatAttachmentMeta,
    isSmartGuarded: Boolean,
    onReadText: suspend (String) -> String?,
    onSelectInjectMode: ((FileInjectMode) -> Unit)? = null,
    onDismiss: () -> Unit,
    onCopiedToast: (() -> Unit)? = null,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val ctx = LocalContext.current
    val style = remember(meta.fileName, theme.isDark) { resolveFileStyle(meta.fileName, theme.isDark) }

    var loadedText by remember(meta.id) { mutableStateOf<String?>(null) }
    var isLoading by remember(meta.id) { mutableStateOf(true) }
    var selectedMode by remember(meta.id, meta.injectMode) { mutableStateOf(meta.injectMode) }

    LaunchedEffect(meta.id) {
        isLoading = true
        loadedText = onReadText(meta.id)
        isLoading = false
    }

    val modeOptions = listOf(
        Triple(FileInjectMode.auto, strings.attachments.fileModeAuto, strings.attachments.fileModeAutoDesc),
        Triple(FileInjectMode.retrieve, strings.attachments.fileModeRetrieve, strings.attachments.fileModeRetrieveDesc),
        Triple(FileInjectMode.head_tail, strings.attachments.fileModeHeadTail, strings.attachments.fileModeHeadTailDesc),
        Triple(FileInjectMode.full, strings.attachments.fileModeFull, strings.attachments.fileModeFullDesc),
    )
    val selectedDesc = modeOptions.firstOrNull { it.first == selectedMode }?.third.orEmpty()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = c.surfaceElevated,
            border = BorderStroke(theme.borderWidth, c.border),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(style.bg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = style.icon,
                            contentDescription = null,
                            tint = style.tint,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = meta.fileName.ifBlank { "document.txt" },
                            color = c.ink,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val metaLine = buildList {
                            formatAttachmentBytes(meta.sizeBytes).takeIf { it.isNotBlank() }?.let { add(it) }
                            if (meta.estimatedTokens > 0) add(FileContextBudgeter.formatShortTokens(meta.estimatedTokens))
                            if (meta.charCount > 0) add("${meta.charCount} chars")
                            if (meta.pageCount > 0) add("${meta.pageCount}P")
                        }.joinToString(" · ")
                        if (metaLine.isNotBlank()) {
                            Text(
                                text = metaLine,
                                color = c.muted,
                                fontSize = 11.5.sp,
                            )
                        }
                    }
                    Surface(
                        onClick = onDismiss,
                        shape = CircleShape,
                        color = c.surfaceSoft,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = strings.common.close,
                                tint = c.muted,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                // Budget guard banner
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = if (isSmartGuarded) c.warningBg else c.successBg,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (isSmartGuarded) c.warning else c.success,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = strings.attachments.fileBudgetSummary.fmt(
                                "total" to FileContextBudgeter.formatShortTokens(meta.estimatedTokens.coerceAtLeast(1)),
                            ),
                            color = if (isSmartGuarded) c.warning else c.success,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 16.sp,
                        )
                    }
                }

                if (meta.extractionNote.isNotBlank()) {
                    Text(
                        text = meta.extractionNote,
                        color = c.muted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }

                // Injection strategy selector (interactive in composer draft)
                if (onSelectInjectMode != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = strings.attachments.fileInjectModeLabel,
                            color = c.ink,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            modeOptions.forEach { (mode, label, _) ->
                                val active = selectedMode == mode
                                Surface(
                                    onClick = {
                                        selectedMode = mode
                                        onSelectInjectMode(mode)
                                    },
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (active) c.primary else c.surfaceSoft,
                                    border = BorderStroke(
                                        width = theme.borderWidth,
                                        color = if (active) c.primary else c.border,
                                    ),
                                ) {
                                    Text(
                                        text = label,
                                        color = if (active) Color.White else c.ink,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    )
                                }
                            }
                        }
                        if (selectedDesc.isNotBlank()) {
                            Text(
                                text = selectedDesc,
                                color = c.muted,
                                fontSize = 11.sp,
                                lineHeight = 15.5.sp,
                            )
                        }
                    }
                }

                HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.7f))

                // Extracted text preview box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = c.surfaceSoft,
                    border = BorderStroke(theme.borderWidth, c.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 300.dp),
                ) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.4.dp,
                                color = c.primary,
                            )
                        }
                    } else {
                        val previewBody = remember(loadedText, meta.previewSnippet) {
                            val raw = loadedText?.takeIf { it.isNotBlank() } ?: meta.previewSnippet
                            if (raw.length > 20_000) {
                                raw.take(20_000) + "\n\n… (预览已截断前 20,000 字符，原文共 ${raw.length} 字符)"
                            } else {
                                raw
                            }
                        }
                        if (previewBody.isBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = strings.attachments.filePreviewEmpty,
                                    color = c.muted,
                                    fontSize = 12.sp,
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(11.dp),
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = previewBody,
                                        color = c.ink,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                    }
                }

                // Footer buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!loadedText.isNullOrBlank()) {
                        TextButton(
                            onClick = {
                                com.gugachat.app.core.util.ClipboardUtils.copyText(ctx, meta.fileName, loadedText!!.take(100_000))
                                onCopiedToast?.invoke()
                            },
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = c.primary,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(strings.common.copy, color = c.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text(strings.common.close, color = c.ink, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun LocalImageLightboxDialog(
    file: File,
    title: String = "",
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { viewportSize = it }
                .pointerInput(file.absolutePath) {
                    detectTapGestures(
                        onTap = { if (scale <= 1.05f) onDismiss() },
                        onDoubleTap = {
                            if (scale > 1.05f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.4f
                            }
                        },
                    )
                }
                .pointerInput(file.absolutePath) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val nextScale = (scale * zoom).coerceIn(1f, 5f)
                        scale = nextScale
                        if (nextScale <= 1.01f) {
                            offset = Offset.Zero
                        } else {
                            val maxX = ((viewportSize.width * (nextScale - 1f)) / 2f).coerceAtLeast(0f)
                            val maxY = ((viewportSize.height * (nextScale - 1f)) / 2f).coerceAtLeast(0f)
                            offset = Offset(
                                x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                y = (offset.y + pan.y).coerceIn(-maxY, maxY),
                            )
                        }
                    }
                },
        ) {
            Image(
                painter = rememberAsyncImagePainter(file),
                contentDescription = title.ifBlank { null },
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (title.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.Black.copy(alpha = 0.62f),
                        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.22f)),
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                } else {
                    Spacer(Modifier.size(1.dp))
                }
                Surface(
                    onClick = onDismiss,
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.62f),
                    border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.22f)),
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = strings.common.close,
                            tint = Color.White,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }
        }
    }
}
