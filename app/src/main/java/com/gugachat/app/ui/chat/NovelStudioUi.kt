package com.gugachat.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuSegmentedControl
import com.gugachat.app.core.theme.GuSettingsRow
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuSwitchRow
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.NovelChapter
import com.gugachat.app.domain.chat.NovelConfig
import com.gugachat.app.domain.chat.NovelDraftOption
import com.gugachat.app.domain.chat.NovelExport
import com.gugachat.app.domain.chat.NovelLoreEntry
import com.gugachat.app.domain.chat.NovelPresets
import com.gugachat.app.domain.chat.TimelineReconciler

private data class NovelGenreVisual(
    val startColor: Color,
    val endColor: Color,
    val icon: ImageVector,
)

private fun resolveNovelGenreVisual(presetId: String): NovelGenreVisual = when (presetId.lowercase()) {
    "xuanhuan" -> NovelGenreVisual(Color(0xFF1F3B5C), Color(0xFF112038), Icons.Default.AutoAwesome)
    "urban_fantasy" -> NovelGenreVisual(Color(0xFF312E81), Color(0xFF1B194D), Icons.Default.Psychology)
    "scifi" -> NovelGenreVisual(Color(0xFF0E4A66), Color(0xFF0A2438), Icons.Default.Public)
    "western_fantasy" -> NovelGenreVisual(Color(0xFF5A371A), Color(0xFF2E1B0C), Icons.Default.Security)
    "mystery" -> NovelGenreVisual(Color(0xFF334155), Color(0xFF18202C), Icons.Default.Search)
    "historical" -> NovelGenreVisual(Color(0xFF6B2435), Color(0xFF33101B), Icons.Default.AutoStories)
    "apocalypse" -> NovelGenreVisual(Color(0xFF5E2B20), Color(0xFF2B1310), Icons.Default.WarningAmber)
    else -> NovelGenreVisual(Color(0xFF4C3075), Color(0xFF261742), Icons.Default.Landscape)
}

@Composable
fun NovelStatusHudBar(
    novel: NovelConfig,
    chapters: List<NovelChapter>,
    onOpenStudio: () -> Unit,
    onEnterPureReading: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH
    val activeCh = chapters.firstOrNull { it.id == novel.activeChapterId } ?: chapters.lastOrNull()
    val hud = novel.currentStatus
    var expanded by remember(novel.title, novel.activeChapterId) { mutableStateOf(false) }

    val wordCount = activeCh?.wordCount ?: 0
    val wordLabel = if (isZh) "$wordCount 字" else "$wordCount words"
    val summarySub = hud?.location?.takeIf { it.isNotBlank() }?.let { "📍 $it · $wordLabel" } ?: wordLabel
    val detailLine = buildString {
        if (hud != null) {
            if (hud.location.isNotBlank()) append("📍 ${hud.location}  ")
            if (hud.time.isNotBlank()) append("🕒 ${hud.time}  ")
            if (hud.conflict.isNotBlank()) append("⚡ ${hud.conflict}")
        }
        if (isEmpty()) {
            append(wordLabel)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GuIconTile(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                tint = c.tagInk,
                backgroundColor = c.tag,
                size = 28.dp,
                iconSize = 15.dp,
                cornerRadius = 8.dp,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = activeCh?.title ?: novel.title,
                    color = c.ink,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!expanded) {
                    Text(
                        text = summarySub,
                        color = c.muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (!expanded) {
                Surface(
                    onClick = onEnterPureReading,
                    shape = RoundedCornerShape(999.dp),
                    color = c.surfaceSoft,
                    border = BorderStroke(theme.borderWidth, c.border),
                    modifier = Modifier.heightIn(min = 34.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoStories,
                            contentDescription = strings.novel.pureReading,
                            tint = c.primarySoftInk,
                            modifier = Modifier.size(13.5.dp),
                        )
                        Text(
                            text = if (isZh) "阅读" else "Read",
                            color = c.ink,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
                Surface(
                    onClick = onOpenStudio,
                    shape = RoundedCornerShape(999.dp),
                    color = c.tag,
                    border = BorderStroke(theme.borderWidth, c.accent.copy(alpha = 0.45f)),
                    modifier = Modifier.heightIn(min = 34.dp),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = strings.novel.studioBtn,
                            color = c.tagInk,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }

            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = c.muted,
                modifier = Modifier.size(18.dp),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = c.surfaceSoft,
                    border = BorderStroke(theme.borderWidth, c.border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = novel.title,
                                color = c.ink,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (novel.genre.isNotBlank()) {
                                GuBadge(novel.genre, variant = GuBadgeVariant.HONEY)
                            }
                            GuBadge(wordLabel, variant = GuBadgeVariant.BLUE)
                        }
                        Text(
                            text = detailLine,
                            color = c.muted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GuButton(
                        onClick = onEnterPureReading,
                        compact = true,
                        variant = GuButtonVariant.SUBTLE,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(strings.novel.pureReading, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    GuButton(
                        onClick = onOpenStudio,
                        compact = true,
                        variant = GuButtonVariant.HONEY,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(strings.novel.studioTitle, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        HorizontalDivider(
            thickness = theme.borderWidth,
            color = c.border.copy(alpha = 0.85f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelWizardDialog(
    onDismiss: () -> Unit,
    onCreate: (presetId: String, title: String, idea: String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH
    var selectedPreset by remember { mutableStateOf(NovelPresets.ALL.first().id) }
    var bookTitle by remember { mutableStateOf("") }
    var idea by remember { mutableStateOf("") }

    val activePreset = NovelPresets.findById(selectedPreset)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.page,
        modifier = Modifier.statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = strings.novel.wizardTitle,
                subtitle = if (isZh) "挑选书本题材模板，或自定义你的世界与开篇" else "Choose an editorial genre template or shape your own book",
                onClose = onDismiss,
                closeContentDescription = strings.common.close,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                GuSectionHeader(
                    title = strings.novel.wizardGenreStep,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                // Editorial Book-Cover Genre Gallery
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(NovelPresets.ALL, key = { it.id }) { preset ->
                        val isSelected = selectedPreset == preset.id
                        val visual = remember(preset.id) { resolveNovelGenreVisual(preset.id) }
                        val label = strings.novel.genres[preset.id] ?: preset.name

                        Surface(
                            onClick = { selectedPreset = preset.id },
                            shape = RoundedCornerShape(14.dp),
                            color = c.surface,
                            border = BorderStroke(
                                width = if (isSelected) 1.6.dp else theme.borderWidth,
                                color = if (isSelected) c.primary else c.border,
                            ),
                            shadowElevation = if (isSelected && !theme.isDark) 3.dp else 0.dp,
                            modifier = Modifier
                                .width(148.dp)
                                .height(106.dp),
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(visual.startColor, visual.endColor),
                                            ),
                                        )
                                        .padding(horizontal = 10.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        GuIconTile(
                                            icon = visual.icon,
                                            tint = Color(0xFFFFF5E1),
                                            backgroundColor = Color.White.copy(alpha = 0.16f),
                                            size = 26.dp,
                                            iconSize = 15.dp,
                                            cornerRadius = 8.dp,
                                        )
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .background(c.primary),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(13.dp),
                                                )
                                            }
                                        }
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) c.primarySoftInk else c.ink,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = if (isZh) {
                                            "约 ${preset.targetWordsPerChapter} 字/章"
                                        } else {
                                            "~${preset.targetWordsPerChapter}w / ch"
                                        },
                                        color = c.muted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }

                // Selected Preset Editorial Synopsis Card
                GuGroupedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    backgroundColor = c.surfaceSoft.copy(alpha = if (theme.isDark) 0.78f else 0.85f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = activePreset.genre,
                                color = c.ink,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            GuBadge(activePreset.firstChapterTitle, variant = GuBadgeVariant.HONEY)
                        }
                        Text(
                            text = activePreset.synopsis,
                            color = c.muted,
                            fontSize = 12.5.sp,
                            lineHeight = 18.5.sp,
                        )
                        Text(
                            text = if (isZh) "文风：${activePreset.style}" else "Style: ${activePreset.style}",
                            color = c.primarySoftInk,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Grouped Book Info Inputs
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GuSectionHeader(
                        title = if (isZh) "书名与核心构思" else "Book Details & Premise",
                    )
                    GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            GuTextField(
                                value = bookTitle,
                                onValueChange = { bookTitle = it },
                                label = strings.novel.wizardBookTitle,
                                placeholder = activePreset.name,
                            )
                            GuTextField(
                                value = idea,
                                onValueChange = { idea = it },
                                label = strings.novel.wizardIdea,
                                placeholder = strings.novel.wizardIdeaPlaceholder,
                                singleLine = false,
                                minLines = 2,
                                maxLines = 4,
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                thickness = theme.borderWidth,
                color = c.border.copy(alpha = 0.85f),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 42.dp),
                ) {
                    Text(strings.common.cancel, color = c.muted, fontWeight = FontWeight.SemiBold)
                }
                GuButton(
                    onClick = {
                        onCreate(selectedPreset, bookTitle, idea)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.novel.wizardEmptyBtn, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelStudioSheet(
    novel: NovelConfig,
    chapters: List<NovelChapter>,
    lore: List<NovelLoreEntry>,
    messages: List<ChatMessage>,
    draftOptions: List<NovelDraftOption>,
    outlineReply: String,
    polishResult: String,
    isBusy: Boolean,
    onSelectChapter: (String) -> Unit,
    onAddChapter: (String, String) -> Unit,
    onAddLore: (String, String, String, String, Boolean) -> Unit,
    onDeleteLore: (String) -> Unit,
    onUpdateConfig: ((NovelConfig) -> NovelConfig) -> Unit,
    onGenerate3Paths: (String) -> Unit,
    onAdoptDraft: (String) -> Unit,
    onOutlineChat: (String) -> Unit,
    onPolishLast: (String) -> Unit,
    onExportTxt: (android.net.Uri) -> Unit,
    onExportMd: (android.net.Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val ctx = LocalContext.current
    val isZh = strings.language == AppLanguage.ZH
    var tab by remember { mutableIntStateOf(0) }

    var newChapTitle by remember { mutableStateOf("") }
    var newChapOutline by remember { mutableStateOf("") }

    var loreCat by remember { mutableStateOf("character") }
    var loreTitle by remember { mutableStateOf("") }
    var loreKeywords by remember { mutableStateOf("") }
    var loreContent by remember { mutableStateOf("") }
    var loreAlways by remember { mutableStateOf(false) }

    var outlinePrompt by remember { mutableStateOf("") }
    var polishInstruction by remember { mutableStateOf("") }
    var draftHint by remember { mutableStateOf("") }

    val hasAssistantProse = remember(messages) {
        TimelineReconciler.activeMessages(messages).any { it.role == "assistant" && it.content.isNotBlank() }
    }

    val exportTxtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) onExportTxt(uri)
    }
    val exportMdLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) onExportMd(uri)
    }

    val tabLabels = remember(strings, isZh) {
        listOf(
            strings.novel.tabChapters,
            strings.novel.tabLore,
            if (isZh) "工坊工具" else "Tools",
            if (isZh) "设置导出" else "Export",
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.page,
        modifier = Modifier.statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = strings.novel.studioTitle,
                subtitle = novel.title,
                onClose = onDismiss,
                closeContentDescription = strings.common.close,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                GuSegmentedControl(
                    items = tabLabels,
                    selectedIndex = tab,
                    onSelect = { tab = it },
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            when (tab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "__chapter_form__") {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                GuSectionHeader(
                                    title = strings.novel.newChapterBtn,
                                )
                                GuCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            GuTextField(
                                                value = newChapTitle,
                                                onValueChange = { newChapTitle = it },
                                                placeholder = strings.novel.newChapterBtn,
                                                modifier = Modifier.weight(1f),
                                            )
                                            GuButton(
                                                onClick = {
                                                    onAddChapter(newChapTitle, newChapOutline)
                                                    newChapTitle = ""
                                                    newChapOutline = ""
                                                },
                                                compact = true,
                                                modifier = Modifier.heightIn(min = 42.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = strings.novel.newChapterBtn,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        }
                                        GuTextField(
                                            value = newChapOutline,
                                            onValueChange = { newChapOutline = it },
                                            placeholder = strings.novel.chapterOutlinePlaceholder,
                                        )
                                    }
                                }
                            }
                        }

                        item(key = "__chapter_list_header__") {
                            GuSectionHeader(
                                title = strings.novel.tabChapters,
                                trailing = {
                                    GuBadge("${chapters.size}", variant = GuBadgeVariant.BLUE)
                                },
                            )
                        }

                        items(chapters, key = { it.id }) { ch ->
                            val isActive = ch.id == novel.activeChapterId
                            val statusText = localizeChapterStatus(ch.status, isZh)
                            val badgeText = if (isZh) "${ch.wordCount} 字 · $statusText" else "${ch.wordCount}w · $statusText"
                            val accentBarColor = c.primary

                            Surface(
                                onClick = { onSelectChapter(ch.id) },
                                shape = RoundedCornerShape(theme.cardRadius),
                                color = if (isActive) c.primarySoft.copy(alpha = if (theme.isDark) 0.72f else 0.78f) else c.surface,
                                border = BorderStroke(
                                    width = theme.borderWidth,
                                    color = if (isActive) c.primary.copy(alpha = 0.55f) else c.border,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .drawBehind {
                                            if (isActive) {
                                                drawRect(
                                                    color = accentBarColor,
                                                    topLeft = Offset.Zero,
                                                    size = Size(3.5.dp.toPx(), size.height),
                                                )
                                            }
                                        }
                                        .padding(start = 15.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = ch.title,
                                            color = if (isActive) c.primarySoftInk else c.ink,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        GuBadge(
                                            text = badgeText,
                                            variant = if (isActive) GuBadgeVariant.BLUE else GuBadgeVariant.NEUTRAL,
                                        )
                                    }
                                    if (ch.outline.isNotBlank()) {
                                        Text(
                                            text = ch.outline,
                                            color = c.muted,
                                            fontSize = 12.sp,
                                            lineHeight = 17.5.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "__lore_form__") {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                GuSectionHeader(title = strings.novel.loreAddBtn)
                                GuCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(listOf("character", "location", "item", "faction", "rule"), key = { it }) { cat ->
                                                GuPill(
                                                    label = strings.novel.loreCats[cat] ?: cat,
                                                    selected = loreCat == cat,
                                                    onClick = { loreCat = cat },
                                                )
                                            }
                                        }
                                        GuTextField(
                                            value = loreTitle,
                                            onValueChange = { loreTitle = it },
                                            placeholder = strings.novel.loreTitlePlaceholder,
                                        )
                                        GuTextField(
                                            value = loreKeywords,
                                            onValueChange = { loreKeywords = it },
                                            placeholder = strings.novel.loreKeywordsPlaceholder,
                                        )
                                        GuTextField(
                                            value = loreContent,
                                            onValueChange = { loreContent = it },
                                            placeholder = strings.novel.loreContentPlaceholder,
                                            singleLine = false,
                                            minLines = 2,
                                            maxLines = 4,
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                GuSwitchRow(
                                                    label = strings.novel.loreAlways,
                                                    checked = loreAlways,
                                                    onCheckedChange = { loreAlways = it },
                                                )
                                            }
                                            val canAddLore = loreTitle.isNotBlank() && loreContent.isNotBlank()
                                            GuButton(
                                                onClick = {
                                                    if (canAddLore) {
                                                        onAddLore(loreCat, loreTitle, loreKeywords, loreContent, loreAlways)
                                                        loreTitle = ""
                                                        loreKeywords = ""
                                                        loreContent = ""
                                                    }
                                                },
                                                enabled = canAddLore,
                                                compact = true,
                                            ) {
                                                Text(strings.novel.loreAddBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "__lore_list_header__") {
                            GuSectionHeader(
                                title = strings.novel.tabLore,
                                trailing = {
                                    GuBadge("${lore.size}", variant = GuBadgeVariant.HONEY)
                                },
                            )
                        }

                        if (lore.isEmpty()) {
                            item(key = "__lore_empty__") {
                                GuEmptyState(
                                    title = strings.novel.loreEmpty,
                                    icon = Icons.AutoMirrored.Filled.MenuBook,
                                )
                            }
                        } else {
                            items(lore, key = { it.id }) { entry ->
                                GuCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        GuBadge(
                                            text = strings.novel.loreCats[entry.category] ?: entry.category,
                                            variant = GuBadgeVariant.HONEY,
                                        )
                                        Text(
                                            text = entry.title,
                                            color = c.ink,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (entry.alwaysActive) {
                                            GuBadge(strings.novel.loreAlways, variant = GuBadgeVariant.BLUE)
                                        }
                                        IconButton(
                                            onClick = { onDeleteLore(entry.id) },
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = strings.common.delete,
                                                tint = c.danger,
                                                modifier = Modifier.size(17.dp),
                                            )
                                        }
                                    }
                                    if (entry.keywords.isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "🔑 ${entry.keywords.joinToString(", ")}",
                                            color = c.muted,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = entry.content,
                                        color = c.muted,
                                        fontSize = 12.sp,
                                        lineHeight = 17.5.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                2 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        GuSectionHeader(
                            title = if (isZh) "AI 创作工坊工具" else "Editorial Craft Tools",
                        )

                        // Tool 1: Three-Path Plot Branches
                        GuCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                GuIconTile(
                                    icon = Icons.Default.AutoAwesome,
                                    tint = c.tagInk,
                                    backgroundColor = c.tag,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = strings.novel.threePathsBtn,
                                        color = c.ink,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                    )
                                    Text(
                                        text = if (isZh) "生成三种不同走向的剧情草案供择优采纳" else "Generate 3 divergent plot paths to choose from",
                                        color = c.muted,
                                        fontSize = 11.5.sp,
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                GuTextField(
                                    value = draftHint,
                                    onValueChange = { draftHint = it },
                                    placeholder = if (isZh) "分歧提示词（可选）" else "Branch hint (optional)",
                                    modifier = Modifier.weight(1f),
                                )
                                GuButton(
                                    onClick = { onGenerate3Paths(draftHint) },
                                    loading = isBusy,
                                    compact = true,
                                    variant = GuButtonVariant.HONEY,
                                    modifier = Modifier.heightIn(min = 42.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = strings.novel.threePathsBtn,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            if (draftOptions.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                GuGroupedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    backgroundColor = c.surfaceSoft,
                                    cornerRadius = 12.dp,
                                ) {
                                    draftOptions.forEachIndexed { idx, opt ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onAdoptDraft(opt.opening.ifBlank { opt.summary })
                                                    onDismiss()
                                                }
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Text(
                                                    text = opt.title,
                                                    color = c.ink,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                if (opt.tone.isNotBlank()) {
                                                    GuBadge(opt.tone, variant = GuBadgeVariant.BLUE)
                                                }
                                            }
                                            Text(
                                                text = opt.summary,
                                                color = c.muted,
                                                fontSize = 12.sp,
                                                lineHeight = 17.sp,
                                            )
                                            if (opt.opening.isNotBlank()) {
                                                Text(
                                                    text = opt.opening,
                                                    color = c.ink,
                                                    fontSize = 12.sp,
                                                    lineHeight = 17.5.sp,
                                                    maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                        if (idx < draftOptions.lastIndex) {
                                            HorizontalDivider(
                                                thickness = theme.borderWidth,
                                                color = c.border.copy(alpha = 0.8f),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Tool 2: Outline Brainstorming
                        GuCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                GuIconTile(
                                    icon = Icons.Default.Psychology,
                                    tint = c.primarySoftInk,
                                    backgroundColor = c.primarySoft,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = strings.novel.outlineChatTitle,
                                        color = c.ink,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                    )
                                    Text(
                                        text = if (isZh) "推演伏笔、节奏与章纲结构" else "Brainstorm arcs, twists, and chapter beats",
                                        color = c.muted,
                                        fontSize = 11.5.sp,
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                GuTextField(
                                    value = outlinePrompt,
                                    onValueChange = { outlinePrompt = it },
                                    placeholder = strings.novel.outlineChatPlaceholder,
                                    modifier = Modifier.weight(1f),
                                )
                                GuButton(
                                    onClick = { onOutlineChat(outlinePrompt) },
                                    enabled = !isBusy && outlinePrompt.isNotBlank(),
                                    loading = isBusy,
                                    compact = true,
                                    modifier = Modifier.heightIn(min = 42.dp),
                                ) {
                                    Text(strings.chat.send, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (outlineReply.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = c.surfaceSoft,
                                    border = BorderStroke(theme.borderWidth, c.border),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = outlineReply,
                                            color = c.ink,
                                            fontSize = 12.5.sp,
                                            lineHeight = 19.sp,
                                        )
                                        GuButton(
                                            onClick = { copyTextToClipboard(ctx, "outline", outlineReply) },
                                            compact = true,
                                            variant = GuButtonVariant.SUBTLE,
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(strings.common.copy, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Tool 3: Prose Polish & De-AI
                        GuCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                GuIconTile(
                                    icon = Icons.Default.AutoFixHigh,
                                    tint = c.success,
                                    backgroundColor = c.successBg,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = strings.novel.polishDescribe,
                                        color = c.ink,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                    )
                                    Text(
                                        text = if (isZh) "精修上一轮正文，去除机械感与套话" else "Refine the latest prose and enrich sensory detail",
                                        color = c.muted,
                                        fontSize = 11.5.sp,
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                GuTextField(
                                    value = polishInstruction,
                                    onValueChange = { polishInstruction = it },
                                    placeholder = if (isZh) "增强画面细节 / 去AI味" else "Add sensory detail / tighten prose",
                                    modifier = Modifier.weight(1f),
                                )
                                GuButton(
                                    onClick = { onPolishLast(polishInstruction) },
                                    enabled = !isBusy && hasAssistantProse,
                                    loading = isBusy,
                                    compact = true,
                                    variant = GuButtonVariant.SUBTLE,
                                    modifier = Modifier.heightIn(min = 42.dp),
                                ) {
                                    Text(strings.novel.polishDeai, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (polishResult.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = c.surfaceSoft,
                                    border = BorderStroke(theme.borderWidth, c.border),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = polishResult,
                                            color = c.ink,
                                            fontSize = 12.5.sp,
                                            lineHeight = 19.sp,
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            GuButton(
                                                onClick = { copyTextToClipboard(ctx, "polish", polishResult) },
                                                compact = true,
                                                variant = GuButtonVariant.SUBTLE,
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(strings.common.copy, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                            GuButton(
                                                onClick = {
                                                    onAdoptDraft(polishResult)
                                                    onDismiss()
                                                },
                                                compact = true,
                                            ) {
                                                Text(
                                                    text = if (isZh) "填入输入框" else "Use as Draft",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        GuSectionHeader(
                            title = if (isZh) "创作辅助开关" else "Writing Preferences",
                        )
                        GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                                GuSwitchRow(
                                    label = strings.novel.hudToggle,
                                    checked = novel.enableStatusHud,
                                    onCheckedChange = { v -> onUpdateConfig { it.copy(enableStatusHud = v) } },
                                )
                                HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.75f))
                                GuSwitchRow(
                                    label = strings.novel.autoSyncLoreToggle,
                                    checked = novel.autoSyncLore,
                                    onCheckedChange = { v -> onUpdateConfig { it.copy(autoSyncLore = v) } },
                                )
                                HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.75f))
                                GuSwitchRow(
                                    label = strings.novel.autoAdvanceChapterToggle,
                                    checked = novel.autoAdvanceChapter,
                                    onCheckedChange = { v -> onUpdateConfig { it.copy(autoAdvanceChapter = v) } },
                                )
                                HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.75f))
                                GuSwitchRow(
                                    label = strings.novel.choicesToggle,
                                    checked = novel.enableChoices,
                                    onCheckedChange = { v -> onUpdateConfig { it.copy(enableChoices = v) } },
                                )
                            }
                        }

                        GuSectionHeader(
                            title = if (isZh) "导出成书" else "Export Manuscript",
                        )
                        GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                            GuSettingsRow(
                                title = strings.novel.exportTxt,
                                subtitle = if (isZh) "适合通用阅读器与手机本地备份 (.txt)" else "Plain text manuscript for any e-reader (.txt)",
                                icon = Icons.Default.Description,
                                iconTint = c.primarySoftInk,
                                iconBg = c.primarySoft,
                                showChevron = true,
                                showDivider = true,
                                onClick = {
                                    exportTxtLauncher.launch(NovelExport.sanitizeFileName(novel.title, "txt"))
                                },
                            )
                            GuSettingsRow(
                                title = strings.novel.exportMd,
                                subtitle = if (isZh) "保留章节标题与排版格式 (.md)" else "Structured chapters with Markdown formatting (.md)",
                                icon = Icons.Default.Download,
                                iconTint = c.tagInk,
                                iconBg = c.tag,
                                showChevron = true,
                                onClick = {
                                    exportMdLauncher.launch(NovelExport.sanitizeFileName(novel.title, "md"))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PureReadingOverlay(
    novel: NovelConfig,
    chapters: List<NovelChapter>,
    messages: List<ChatMessage>,
    paper: String,
    fontSizeSp: Int,
    useSerif: Boolean,
    onPaperChange: (String) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onSerifToggle: (Boolean) -> Unit,
    onExit: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val isZh = strings.language == AppLanguage.ZH
    val isNight = paper == "night"
    var showChrome by remember { mutableStateOf(true) }

    val readerColors = remember(paper) {
        when (paper) {
            "parchment" -> ReaderPaperTheme(
                bg = Color(0xFFF3E6C8),
                surface = Color(0xFFEAD9B4),
                chromeBg = Color(0xF0EFE1C0),
                ink = Color(0xFF3A2818),
                muted = Color(0xFF6F563E),
                accent = Color(0xFFB45309),
                border = Color(0xFFD2B888),
                codeBg = Color(0xFFE6D4AC),
                codeHeaderBg = Color(0xFFDDC797),
            )
            "night" -> ReaderPaperTheme(
                bg = Color(0xFF0E121B),
                surface = Color(0xFF171E2E),
                chromeBg = Color(0xF0151B29),
                ink = Color(0xFFE4E9F4),
                muted = Color(0xFF8F9DB6),
                accent = Color(0xFF5B7BF6),
                border = Color(0xFF253047),
                codeBg = Color(0xFF0A0D14),
                codeHeaderBg = Color(0xFF141B29),
            )
            else -> ReaderPaperTheme(
                bg = Color(0xFFFDFBF6),
                surface = Color(0xFFF3EFE6),
                chromeBg = Color(0xF2FAF7F0),
                ink = Color(0xFF1F1C1B),
                muted = Color(0xFF68625C),
                accent = Color(0xFF4361EE),
                border = Color(0xFFE3DDD1),
                codeBg = Color(0xFFF2EEE6),
                codeHeaderBg = Color(0xFFE7E1D5),
            )
        }
    }

    val mdPalette = remember(readerColors) {
        MarkdownPalette(
            text = readerColors.ink,
            muted = readerColors.muted,
            accent = readerColors.accent,
            border = readerColors.border,
            quoteBg = readerColors.surface,
            inlineCodeBg = readerColors.surface,
            inlineCodeInk = readerColors.accent,
            codeBg = readerColors.codeBg,
            codeHeaderBg = readerColors.codeHeaderBg,
            codeInk = readerColors.ink,
            codeMeta = readerColors.muted,
            tableHeaderBg = readerColors.surface,
        )
    }

    val assistantProse = remember(messages) {
        TimelineReconciler.activeMessages(messages)
            .filter { it.role == "assistant" }
            .mapNotNull { NovelExport.stripInternalTags(it.content).takeIf { txt -> txt.isNotBlank() } }
    }
    val activeCh = chapters.firstOrNull { it.id == novel.activeChapterId } ?: chapters.lastOrNull()
    val totalWords = remember(assistantProse) {
        assistantProse.sumOf { it.count { ch -> !ch.isWhitespace() } }
    }

    Dialog(
        onDismissRequest = onExit,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val dialogView = LocalView.current
        SideEffect {
            val window = (dialogView.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, dialogView)
            controller.isAppearanceLightStatusBars = !isNight
            controller.isAppearanceLightNavigationBars = !isNight
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(readerColors.bg),
        ) {
            // Literary Prose Canvas (Tap to toggle floating top/bottom toolbars)
            if (assistantProse.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(readerColors.surface)
                                .border(0.8.dp, readerColors.border, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = readerColors.accent,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Text(
                            text = if (isZh) "本书尚无正文内容" else "No prose written yet",
                            color = readerColors.ink,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = if (isZh) {
                                "返回对话窗口发送剧情指令，AI 续写的正文将自动排版在这里。"
                            } else {
                                "Return to the chat workspace to write your first chapter."
                            },
                            color = readerColors.muted,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(4.dp))
                        ReaderChip(
                            label = strings.novel.exitPureReading,
                            selected = true,
                            palette = readerColors,
                            onClick = onExit,
                        )
                    }
                }
            } else {
                val tapSource = remember { MutableInteractionSource() }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = tapSource,
                            indication = null,
                            onClick = { showChrome = !showChrome },
                        )
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(
                        start = 22.dp,
                        end = 22.dp,
                        top = 74.dp,
                        bottom = 148.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    item(key = "__reader_title_block__") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = activeCh?.title ?: novel.title,
                                color = readerColors.ink,
                                fontSize = (fontSizeSp + 4).coerceIn(18, 28).sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = if (useSerif) FontFamily.Serif else FontFamily.Default,
                                lineHeight = ((fontSizeSp + 4).coerceIn(18, 28) * 1.35f).sp,
                            )
                            Text(
                                text = buildString {
                                    append(novel.title)
                                    if (totalWords > 0) {
                                        append(" · ")
                                        append(if (isZh) "$totalWords 字" else "$totalWords chars")
                                    }
                                },
                                color = readerColors.muted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(6.dp))
                            HorizontalDivider(
                                thickness = 0.8.dp,
                                color = readerColors.border.copy(alpha = 0.8f),
                            )
                        }
                    }

                    itemsIndexed(assistantProse) { idx, chunk ->
                        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            MarkdownMessageText(
                                markdown = chunk,
                                textColor = readerColors.ink,
                                fontSize = fontSizeSp.sp,
                                lineHeight = (fontSizeSp * 1.74f).sp,
                                useSerif = useSerif,
                                palette = mdPalette,
                            )
                            if (idx < assistantProse.lastIndex) {
                                Text(
                                    text = "— ✦ —",
                                    color = readerColors.muted.copy(alpha = 0.55f),
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }

            // Floating Translucent Top Bar
            AnimatedVisibility(
                visible = showChrome || assistantProse.isEmpty(),
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut() + slideOutVertically { -it / 2 },
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(readerColors.chromeBg)
                        .statusBarsPadding(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .padding(start = 18.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            Text(
                                text = novel.title,
                                color = readerColors.ink,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (activeCh != null) {
                                Text(
                                    text = activeCh.title,
                                    color = readerColors.muted,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        IconButton(
                            onClick = onExit,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(readerColors.surface)
                                    .border(0.8.dp, readerColors.border, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = strings.novel.exitPureReading,
                                    tint = readerColors.ink,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    HorizontalDivider(
                        thickness = 0.8.dp,
                        color = readerColors.border.copy(alpha = 0.8f),
                    )
                }
            }

            // Floating Translucent Bottom Typography & Paper Console
            AnimatedVisibility(
                visible = showChrome || assistantProse.isEmpty(),
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = readerColors.chromeBg,
                    border = BorderStroke(0.8.dp, readerColors.border),
                    shadowElevation = if (isNight) 8.dp else 4.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Paper Theme Swatches
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ReaderPaperSwatchPill(
                                label = strings.novel.paperCream,
                                swatchColor = Color(0xFFFDFBF6),
                                selected = paper == "cream",
                                palette = readerColors,
                                onClick = { onPaperChange("cream") },
                                modifier = Modifier.weight(1f),
                            )
                            ReaderPaperSwatchPill(
                                label = strings.novel.paperParchment,
                                swatchColor = Color(0xFFF3E6C8),
                                selected = paper == "parchment",
                                palette = readerColors,
                                onClick = { onPaperChange("parchment") },
                                modifier = Modifier.weight(1f),
                            )
                            ReaderPaperSwatchPill(
                                label = strings.novel.paperNight,
                                swatchColor = Color(0xFF141824),
                                selected = paper == "night",
                                palette = readerColors,
                                onClick = { onPaperChange("night") },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        // Font Size Stepper + Serif Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = readerColors.surface,
                                border = BorderStroke(0.8.dp, readerColors.border),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                                ) {
                                    ReaderFontStepBtn(
                                        label = "A-",
                                        palette = readerColors,
                                        onClick = { onFontSizeChange((fontSizeSp - 1).coerceAtLeast(13)) },
                                    )
                                    Text(
                                        text = "${fontSizeSp}sp",
                                        color = readerColors.ink,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 6.dp),
                                    )
                                    ReaderFontStepBtn(
                                        label = "A+",
                                        palette = readerColors,
                                        onClick = { onFontSizeChange((fontSizeSp + 1).coerceAtMost(24)) },
                                    )
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            ReaderChip(
                                label = strings.novel.serifToggle,
                                selected = useSerif,
                                palette = readerColors,
                                onClick = { onSerifToggle(!useSerif) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class ReaderPaperTheme(
    val bg: Color,
    val surface: Color,
    val chromeBg: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val border: Color,
    val codeBg: Color,
    val codeHeaderBg: Color,
)

@Composable
private fun ReaderPaperSwatchPill(
    label: String,
    swatchColor: Color,
    selected: Boolean,
    palette: ReaderPaperTheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) palette.accent.copy(alpha = 0.14f) else palette.surface,
        border = BorderStroke(
            width = if (selected) 1.2.dp else 0.8.dp,
            color = if (selected) palette.accent else palette.border,
        ),
        modifier = modifier.heightIn(min = 36.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(swatchColor)
                    .border(
                        width = if (selected) 1.5.dp else 0.8.dp,
                        color = if (selected) palette.accent else palette.border,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = if (swatchColor == Color(0xFF141824)) Color.White else palette.accent,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
            Text(
                text = label,
                color = if (selected) palette.accent else palette.ink,
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReaderChip(
    label: String,
    selected: Boolean,
    palette: ReaderPaperTheme,
    onClick: () -> Unit,
) {
    val bg = if (selected) palette.accent else palette.surface
    val fg = if (selected) Color.White else palette.ink
    val border = if (selected) palette.accent else palette.border
    Box(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(0.8.dp, border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun ReaderFontStepBtn(
    label: String,
    palette: ReaderPaperTheme,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .heightIn(min = 32.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = palette.ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

private fun localizeChapterStatus(status: String, isZh: Boolean): String = when (status.lowercase()) {
    "writing", "in_progress" -> if (isZh) "连载中" else "Writing"
    "done", "completed", "finished" -> if (isZh) "已完成" else "Done"
    "draft" -> if (isZh) "草稿" else "Draft"
    else -> status
}

private fun copyTextToClipboard(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}
