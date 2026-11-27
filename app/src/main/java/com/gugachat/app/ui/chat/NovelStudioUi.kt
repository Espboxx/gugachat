package com.gugachat.app.ui.chat

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.storage.LocalMediaPathResolver
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuCompactSearchBar
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuSegmentedControl
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuSwitchRow
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.core.util.FuzzyMatcher
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.NovelChapter
import com.gugachat.app.domain.chat.NovelConfig
import com.gugachat.app.domain.chat.NovelDraftOption
import com.gugachat.app.domain.chat.NovelEngine
import com.gugachat.app.domain.chat.NovelExport
import com.gugachat.app.domain.chat.NovelLoreEntry
import com.gugachat.app.domain.chat.NovelPresets
import com.gugachat.app.domain.chat.TimelineReconciler
import java.util.Locale

private data class NovelGenreVisual(
    val startColor: Color,
    val endColor: Color,
    val icon: ImageVector,
)

private sealed interface NovelStudioSubPage {
    data class ChapterDetail(val chapterId: String) : NovelStudioSubPage
    data object FinishChapter : NovelStudioSubPage
    data class LoreEditor(val loreId: String?) : NovelStudioSubPage
}

private val STUDIO_TARGET_WORD_STEPS = listOf(1500, 2500, 4000, 6000)
private val ROMAN_BRANCH_NUMERALS = listOf("Ⅰ", "Ⅱ", "Ⅲ", "Ⅳ")

private fun resolveNovelGenreVisual(presetId: String): NovelGenreVisual = when (presetId.lowercase()) {
    "xuanhuan" -> NovelGenreVisual(Color(0xFF1F3B5C), Color(0xFF112038), Icons.Default.AutoAwesome)
    "urban_fantasy" -> NovelGenreVisual(Color(0xFF312E81), Color(0xFF1B194D), Icons.Default.Psychology)
    "scifi" -> NovelGenreVisual(Color(0xFF0E4A66), Color(0xFF0A2438), Icons.Default.Public)
    "western_fantasy" -> NovelGenreVisual(Color(0xFF5A371A), Color(0xFF2E1B0C), Icons.Default.Security)
    "mystery" -> NovelGenreVisual(Color(0xFF334155), Color(0xFF18202C), Icons.Default.Search)
    "historical" -> NovelGenreVisual(Color(0xFF6B2435), Color(0xFF33101B), Icons.Default.AutoStories)
    "apocalypse" -> NovelGenreVisual(Color(0xFF5E2B20), Color(0xFF2B1310), Icons.Default.WarningAmber)
    "custom" -> NovelGenreVisual(Color(0xFF2E5A4C), Color(0xFF163028), Icons.Default.Edit)
    else -> NovelGenreVisual(Color(0xFF4C3075), Color(0xFF261742), Icons.Default.Landscape)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NovelWizardDialog(
    hasModelSelected: Boolean = true,
    isCreatingWithAi: Boolean = false,
    onDismiss: () -> Unit,
    onCreate: (
        presetId: String,
        title: String,
        idea: String,
        pov: String,
        targetWords: Int,
        playStyle: String,
        antiAiRules: Boolean,
    ) -> Unit,
    onCreateWithAi: (
        presetId: String,
        title: String,
        idea: String,
        pov: String,
        targetWords: Int,
        playStyle: String,
        antiAiRules: Boolean,
        onDone: () -> Unit,
    ) -> Unit = { presetId, title, idea, pov, targetWords, playStyle, antiAiRules, onDone ->
        onCreate(presetId, title, idea, pov, targetWords, playStyle, antiAiRules)
        onDone()
    },
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH
    var selectedPreset by remember { mutableStateOf(NovelPresets.ALL.first().id) }
    var bookTitle by remember { mutableStateOf("") }
    var idea by remember { mutableStateOf("") }
    var pov by remember { mutableStateOf(NovelPresets.ALL.first().pov) }
    var targetWords by remember { mutableIntStateOf(NovelPresets.ALL.first().targetWordsPerChapter) }
    var playStyle by remember { mutableStateOf("hybrid") }
    var antiAiRules by remember { mutableStateOf(true) }

    val activePreset = NovelPresets.findById(selectedPreset)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { if (!isCreatingWithAi) onDismiss() },
        sheetState = sheetState,
        containerColor = c.page,
        modifier = Modifier.statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = strings.novel.wizardTitle,
                subtitle = if (isZh) "挑选题材模板或自由原创，可由 AI 一键生成大纲、世界书与第一章开篇" else "Choose a genre template or go freeform — optionally let AI craft your outline & Chapter 1",
                onClose = { if (!isCreatingWithAi) onDismiss() },
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

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(NovelPresets.ALL, key = { it.id }) { preset ->
                        val isSelected = selectedPreset == preset.id
                        val visual = remember(preset.id) { resolveNovelGenreVisual(preset.id) }
                        val label = strings.novel.genres[preset.id] ?: preset.name

                        Surface(
                            onClick = {
                                selectedPreset = preset.id
                                pov = preset.pov
                                targetWords = preset.targetWordsPerChapter
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = c.surface,
                            border = if (isSelected) BorderStroke(1.5.dp, c.primary) else null,
                            shadowElevation = if (isSelected && !theme.isDark) 3.dp else 0.5.dp,
                            modifier = Modifier
                                .width(146.dp)
                                .height(104.dp),
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                        .background(Brush.horizontalGradient(listOf(visual.startColor, visual.endColor)))
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
                                            size = 24.dp,
                                            iconSize = 14.dp,
                                            cornerRadius = 7.dp,
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
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) c.primarySoftInk else c.ink,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    GuMetaText(
                                        text = if (isZh) "约 ${preset.targetWordsPerChapter} 字/章" else "~${preset.targetWordsPerChapter}w / ch",
                                        color = c.muted,
                                    )
                                }
                            }
                        }
                    }
                }

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
                        verticalArrangement = Arrangement.spacedBy(7.dp),
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
                                fontWeight = FontWeight.Bold,
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

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GuSectionHeader(title = if (isZh) "书名与核心构思" else "Book Details & Premise")
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
                                placeholder = strings.novel.genres[activePreset.id] ?: activePreset.name,
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

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GuSectionHeader(title = if (isZh) "视角、节奏与玩法" else "POV, Pacing & Play Style")
                    GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(strings.novel.povLabel, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                val povKeys = listOf("third_limited", "first", "third_omniscient")
                                GuSegmentedControl(
                                    items = listOf(
                                        strings.novel.povThirdLimited,
                                        strings.novel.povFirst,
                                        strings.novel.povThirdOmniscient,
                                    ),
                                    selectedIndex = povKeys.indexOf(pov).coerceAtLeast(0),
                                    onSelect = { idx -> pov = povKeys[idx] },
                                    compact = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(strings.novel.playStyleLabel, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                val styleKeys = listOf("hybrid", "author", "interactive")
                                GuSegmentedControl(
                                    items = listOf(
                                        strings.novel.playHybrid,
                                        strings.novel.playAuthor,
                                        strings.novel.playInteractive,
                                    ),
                                    selectedIndex = styleKeys.indexOf(playStyle).coerceAtLeast(0),
                                    onSelect = { idx -> playStyle = styleKeys[idx] },
                                    compact = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(strings.novel.chapterTargetWords, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                val wordSteps = remember(targetWords) {
                                    if (targetWords in STUDIO_TARGET_WORD_STEPS) STUDIO_TARGET_WORD_STEPS
                                    else (STUDIO_TARGET_WORD_STEPS + targetWords).sorted()
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    wordSteps.forEach { w ->
                                        GuPill(
                                            label = if (isZh) "${w}字" else "${w}w",
                                            selected = targetWords == w,
                                            onClick = { targetWords = w },
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(thickness = 0.5.dp, color = c.border.copy(alpha = 0.5f))
                            GuSwitchRow(
                                label = strings.novel.antiAiToggle,
                                checked = antiAiRules,
                                onCheckedChange = { antiAiRules = it },
                            )
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = c.border.copy(alpha = 0.6f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (isCreatingWithAi) {
                    Text(
                        text = "✨ ${strings.novel.wizardGenerating}",
                        color = c.primarySoftInk,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GuButton(
                        onClick = {
                            onCreate(selectedPreset, bookTitle, idea, pov, targetWords, playStyle, antiAiRules)
                            onDismiss()
                        },
                        enabled = !isCreatingWithAi,
                        variant = GuButtonVariant.SUBTLE,
                        modifier = Modifier.weight(0.9f),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(strings.novel.wizardEmptyBtn, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    GuButton(
                        onClick = {
                            onCreateWithAi(
                                selectedPreset,
                                bookTitle,
                                idea,
                                pov,
                                targetWords,
                                playStyle,
                                antiAiRules,
                                onDismiss,
                            )
                        },
                        enabled = hasModelSelected && !isCreatingWithAi,
                        loading = isCreatingWithAi,
                        variant = GuButtonVariant.HONEY,
                        modifier = Modifier.weight(1.3f),
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (isZh) "AI 一键开书" else "AI Generate Book",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NovelStudioSheet(
    novel: NovelConfig,
    chapters: List<NovelChapter>,
    lore: List<NovelLoreEntry>,
    messages: List<ChatMessage>,
    triggeredLoreIds: Set<String> = emptySet(),
    initialTab: Int = 0,
    draftOptions: List<NovelDraftOption>,
    outlineReply: String,
    polishResult: String,
    isBusy: Boolean,
    isExtractingLore: Boolean = false,
    isGeneratingRecap: Boolean = false,
    isSyncingChapters: Boolean = false,
    onSelectChapter: (String) -> Unit,
    onAddChapter: (String, String) -> Unit,
    onUpdateChapter: (NovelChapter) -> Unit = {},
    onDeleteChapter: (String) -> Unit = {},
    onFinishChapterAndAdvance: (nextTitle: String, nextOutline: String, autoRecap: Boolean) -> Unit = { _, _, _ -> },
    onGenerateChapterRecap: (String) -> Unit = {},
    onAddLore: (String, String, String, String, Boolean) -> Unit,
    onUpdateLore: (NovelLoreEntry) -> Unit = {},
    onToggleLoreEnabled: (String) -> Unit = {},
    onToggleLoreAlwaysActive: (String) -> Unit = {},
    onDeleteLore: (String) -> Unit,
    onImportLorebook: (Uri) -> Unit = {},
    onExtractLoreNow: () -> Unit = {},
    onSaveForeshadowToLore: (String) -> Unit = {},
    onUpdateConfig: ((NovelConfig) -> NovelConfig) -> Unit,
    onGenerate3Paths: (String) -> Unit,
    onAdoptDraft: (NovelDraftOption, Boolean) -> Unit,
    onOutlineChat: (String) -> Unit,
    onApplyOutlineToBook: (String) -> Unit = {},
    onSyncChaptersFromOutline: (String) -> Unit = {},
    onPolishLast: (instruction: String, presetMode: String?) -> Unit,
    onApplyPolishAsVariant: (String) -> Unit = {},
    onFillComposerDraft: (String) -> Unit = {},
    onExportTxt: (android.net.Uri, Boolean) -> Unit,
    onExportMd: (android.net.Uri, Boolean) -> Unit,
    onToast: (String) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val ctx = LocalContext.current
    val isZh = strings.language == AppLanguage.ZH

    var tab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 3)) }
    var subPage by remember { mutableStateOf<NovelStudioSubPage?>(null) }

    // Tab 0 inline states (zero AlertDialogs!)
    var showAddChapterForm by remember { mutableStateOf(false) }
    var newChapTitle by remember { mutableStateOf("") }
    var newChapOutline by remember { mutableStateOf("") }
    var expandedChapterId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteChapterId by remember { mutableStateOf<String?>(null) }

    // Tab 1 states
    var loreSearch by remember { mutableStateOf("") }
    var loreFilterCat by remember { mutableStateOf("all") }
    var expandedLoreId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteLoreId by remember { mutableStateOf<String?>(null) }

    // Tab 2 states
    var outlinePrompt by remember { mutableStateOf("") }
    var polishInstruction by remember { mutableStateOf("") }
    var draftHint by remember { mutableStateOf("") }
    var showFullPolishSource by remember { mutableStateOf(false) }

    // Tab 3 states (Book Cover + Accordions)
    var bookTitleDraft by remember(novel.title) { mutableStateOf(novel.title) }
    var bookGenreDraft by remember(novel.genre) { mutableStateOf(novel.genre) }
    var bookSynopsisDraft by remember(novel.synopsis) { mutableStateOf(novel.synopsis) }
    var bookWorldviewDraft by remember(novel.worldview) { mutableStateOf(novel.worldview) }
    var bookOutlineDraft by remember(novel.mainOutline) { mutableStateOf(novel.mainOutline) }
    var bookAuthorNoteDraft by remember(novel.authorNote) { mutableStateOf(novel.authorNote) }
    var bookStyleDraft by remember(novel.style) { mutableStateOf(novel.style) }
    var bookStyleSampleDraft by remember(novel.styleSample) { mutableStateOf(novel.styleSample) }
    var exportIncludeLore by remember { mutableStateOf(true) }
    var expandedArchiveKey by remember { mutableStateOf<String?>(null) }

    val profileDirty = bookTitleDraft != novel.title ||
        bookGenreDraft != novel.genre ||
        bookSynopsisDraft != novel.synopsis ||
        bookWorldviewDraft != novel.worldview ||
        bookOutlineDraft != novel.mainOutline ||
        bookAuthorNoteDraft != novel.authorNote ||
        bookStyleDraft != novel.style ||
        bookStyleSampleDraft != novel.styleSample

    fun saveBookArchiveProfile() {
        onUpdateConfig { cur ->
            cur.copy(
                title = bookTitleDraft.trim().ifBlank { cur.title },
                genre = bookGenreDraft.trim(),
                synopsis = bookSynopsisDraft.trim(),
                worldview = bookWorldviewDraft.trim(),
                mainOutline = bookOutlineDraft.trim(),
                authorNote = bookAuthorNoteDraft.trim(),
                style = bookStyleDraft.trim(),
                styleSample = bookStyleSampleDraft.trim(),
            )
        }
        onToast(if (isZh) "已保存本书档案设定" else "Book profile saved")
    }

    val latestAssistantProse = remember(messages) {
        TimelineReconciler.activeMessages(messages)
            .lastOrNull { it.role == "assistant" }
            ?.let { NovelExport.stripInternalTags(it.content) }
            .orEmpty()
    }
    val hasAssistantProse = latestAssistantProse.isNotBlank()
    val activeCh = chapters.firstOrNull { it.id == novel.activeChapterId } ?: chapters.lastOrNull()
    val totalBookWords = remember(chapters) { chapters.sumOf { it.wordCount } }
    val chatVm = LocalChatViewModel.current
    val chatState = chatVm?.ui?.collectAsStateWithLifecycle()?.value
    val isGeneratingCover = chatState?.isGeneratingRpArt == true

    val exportTxtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) onExportTxt(uri, exportIncludeLore)
    }
    val exportMdLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) onExportMd(uri, exportIncludeLore)
    }
    val exportEpubLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/epub+zip")) { uri ->
        if (uri != null) chatVm?.exportNovelEpubToUri(uri, ctx.contentResolver, includeLore = exportIncludeLore)
    }
    val pickCoverLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) chatVm?.bindNovelCoverImage(uri, ctx.contentResolver)
    }
    val importLoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportLorebook(uri)
    }

    val tabLabels = remember(isZh) {
        if (isZh) {
            listOf("目录细纲", "世界设定", "AI 笔法", "书卷档案")
        } else {
            listOf("Chapters", "Worldbook", "Craft", "Archive")
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.page,
        modifier = Modifier.statusBarsPadding(),
    ) {
        BackHandler(enabled = subPage != null) {
            subPage = null
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            AnimatedContent(
                targetState = subPage,
                transitionSpec = {
                    if (targetState != null && initialState == null) {
                        (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 5 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { it / 5 } + fadeOut())
                    }
                },
                label = "novel_studio_push_pop",
                modifier = Modifier.fillMaxSize(),
            ) { activeSub ->
                when (activeSub) {
                    is NovelStudioSubPage.ChapterDetail -> {
                        val targetChapter = chapters.firstOrNull { it.id == activeSub.chapterId }
                        if (targetChapter == null) {
                            LaunchedEffect(Unit) { subPage = null }
                        } else {
                            NovelChapterDetailSubPage(
                                chapter = targetChapter,
                                canDelete = chapters.size > 1,
                                isGeneratingRecap = isGeneratingRecap,
                                onBack = { subPage = null },
                                onGenerateRecap = { onGenerateChapterRecap(targetChapter.id) },
                                onSave = { updated ->
                                    onUpdateChapter(updated)
                                    subPage = null
                                },
                                onDelete = {
                                    onDeleteChapter(targetChapter.id)
                                    subPage = null
                                },
                            )
                        }
                    }

                    is NovelStudioSubPage.FinishChapter -> {
                        val currentActive = activeCh
                        if (currentActive == null) {
                            LaunchedEffect(Unit) { subPage = null }
                        } else {
                            val nextPlanned = remember(chapters, currentActive.id) {
                                chapters.sortedBy { it.order }
                                    .firstOrNull { it.order > currentActive.order && it.status == "draft" && it.wordCount == 0 }
                            }
                            NovelFinishChapterSubPage(
                                activeChapter = currentActive,
                                nextOrder = (chapters.maxOfOrNull { it.order } ?: 0) + 1,
                                nextPlannedChapter = nextPlanned,
                                onBack = { subPage = null },
                                onConfirm = { nextTitle, nextOutline, autoRecap ->
                                    onFinishChapterAndAdvance(nextTitle, nextOutline, autoRecap)
                                    subPage = null
                                },
                            )
                        }
                    }

                    is NovelStudioSubPage.LoreEditor -> {
                        val existingEntry = activeSub.loreId?.let { id -> lore.firstOrNull { it.id == id } }
                        NovelLoreEditorSubPage(
                            existing = existingEntry,
                            onBack = { subPage = null },
                            onSaveNew = { cat, title, keywords, content, always ->
                                onAddLore(cat, title, keywords, content, always)
                                subPage = null
                            },
                            onSaveExisting = { updated ->
                                onUpdateLore(updated)
                                subPage = null
                            },
                            onDelete = { id ->
                                onDeleteLore(id)
                                subPage = null
                            },
                        )
                    }

                    null -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            GuSheetHeader(
                                title = if (isZh) "书卷案头" else strings.novel.studioTitle,
                                subtitle = "《${novel.title}》 · ${if (isZh) "共 ${chapters.size} 章 · ${formatStudioCount(totalBookWords)} 字" else "${chapters.size} ch · ${formatStudioCount(totalBookWords)}w"}",
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
                                // ==================== TAB 0: CHAPTERS & BEATS ====================
                                0 -> {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .padding(horizontal = 16.dp),
                                        contentPadding = PaddingValues(bottom = 28.dp),
                                        verticalArrangement = Arrangement.spacedBy(9.dp),
                                    ) {
                                        item(key = "__active_chapter_card__") {
                                            val activeWords = activeCh?.wordCount ?: 0
                                            val targetWords = novel.targetWordsPerChapter.coerceAtLeast(500)
                                            val pct = (activeWords.toFloat() / targetWords.toFloat()).coerceIn(0f, 1f)
                                            GuCard(modifier = Modifier.fillMaxWidth()) {
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.weight(1f),
                                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                                        ) {
                                                            Text(
                                                                text = activeCh?.title ?: novel.title,
                                                                color = c.ink,
                                                                fontSize = 14.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                fontFamily = FontFamily.Serif,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                            )
                                                            GuMetaText(
                                                                text = if (isZh) {
                                                                    "本章 ${formatStudioCount(activeWords)} / ${formatStudioCount(targetWords)} 字 · 全书 ${formatStudioCount(totalBookWords)} 字"
                                                                } else {
                                                                    "Ch. ${formatStudioCount(activeWords)} / ${formatStudioCount(targetWords)}w · Book ${formatStudioCount(totalBookWords)}w"
                                                                },
                                                                color = c.muted,
                                                            )
                                                        }
                                                        GuButton(
                                                            onClick = { subPage = NovelStudioSubPage.FinishChapter },
                                                            enabled = activeCh != null,
                                                            compact = true,
                                                            variant = GuButtonVariant.HONEY,
                                                        ) {
                                                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                                                            Spacer(Modifier.width(4.dp))
                                                            Text(
                                                                text = if (isZh) "完结并开启下一章" else "Finish & Next",
                                                                fontSize = 11.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 1,
                                                            )
                                                        }
                                                    }
                                                    LinearProgressIndicator(
                                                        progress = { pct },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(5.dp)
                                                            .clip(RoundedCornerShape(999.dp)),
                                                        color = if (pct >= 1f) c.success else c.primary,
                                                        trackColor = c.border.copy(alpha = 0.4f),
                                                    )
                                                }
                                            }
                                        }

                                        item(key = "__chapter_list_header__") {
                                            GuSectionHeader(
                                                title = strings.novel.tabChapters,
                                                trailing = {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    ) {
                                                        GuBadge("${chapters.size}", variant = GuBadgeVariant.BLUE)
                                                        Surface(
                                                            onClick = { showAddChapterForm = !showAddChapterForm },
                                                            shape = RoundedCornerShape(999.dp),
                                                            color = c.primarySoft,
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                            ) {
                                                                Icon(Icons.Default.Add, contentDescription = null, tint = c.primarySoftInk, modifier = Modifier.size(13.dp))
                                                                Text(
                                                                    text = strings.novel.newChapterBtn,
                                                                    color = c.primarySoftInk,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                )
                                                            }
                                                        }
                                                    }
                                                },
                                            )
                                        }

                                        if (showAddChapterForm) {
                                            item(key = "__chapter_form__") {
                                                GuCard(modifier = Modifier.fillMaxWidth(), backgroundColor = c.surfaceSoft) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        GuTextField(
                                                            value = newChapTitle,
                                                            onValueChange = { newChapTitle = it },
                                                            placeholder = if (isZh) "新章节标题（留空默认第${chapters.size + 1}章）" else "New chapter title",
                                                        )
                                                        GuTextField(
                                                            value = newChapOutline,
                                                            onValueChange = { newChapOutline = it },
                                                            placeholder = strings.novel.chapterOutlinePlaceholder,
                                                            singleLine = false,
                                                            minLines = 2,
                                                            maxLines = 4,
                                                        )
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.End,
                                                        ) {
                                                            TextButton(onClick = { showAddChapterForm = false }) {
                                                                Text(strings.common.cancel, color = c.muted, fontSize = 12.sp)
                                                            }
                                                            GuButton(
                                                                onClick = {
                                                                    onAddChapter(newChapTitle, newChapOutline)
                                                                    newChapTitle = ""
                                                                    newChapOutline = ""
                                                                    showAddChapterForm = false
                                                                },
                                                                compact = true,
                                                            ) {
                                                                Text(strings.novel.newChapterBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        items(chapters.sortedBy { it.order }, key = { it.id }) { ch ->
                                            val isActive = ch.id == novel.activeChapterId
                                            val isExpanded = expandedChapterId == ch.id
                                            val isConfirmingDelete = pendingDeleteChapterId == ch.id
                                            val statusText = localizeChapterStatus(ch.status, isZh)
                                            val chTarget = novel.targetWordsPerChapter.coerceAtLeast(500)
                                            val chProgress = (ch.wordCount.toFloat() / chTarget.toFloat()).coerceIn(0f, 1f)

                                            GuCard(
                                                modifier = Modifier.fillMaxWidth(),
                                                backgroundColor = if (isActive) {
                                                    c.primarySoft.copy(alpha = if (theme.isDark) 0.55f else 0.72f)
                                                } else {
                                                    c.surface
                                                },
                                                borderColor = if (isActive) c.primary.copy(alpha = 0.45f) else null,
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                                onClick = {
                                                    expandedChapterId = if (isExpanded) null else ch.id
                                                    pendingDeleteChapterId = null
                                                },
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(26.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(
                                                                    when {
                                                                        isActive -> c.primary
                                                                        ch.status == "done" -> c.successBg
                                                                        else -> c.surfaceSoft
                                                                    },
                                                                ),
                                                            contentAlignment = Alignment.Center,
                                                        ) {
                                                            Text(
                                                                text = ch.order.toString().padStart(2, '0'),
                                                                color = when {
                                                                    isActive -> Color.White
                                                                    ch.status == "done" -> c.success
                                                                    else -> c.muted
                                                                },
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                fontFamily = FontFamily.Monospace,
                                                            )
                                                        }
                                                        Column(
                                                            modifier = Modifier.weight(1f),
                                                            verticalArrangement = Arrangement.spacedBy(1.dp),
                                                        ) {
                                                            Text(
                                                                text = ch.title,
                                                                color = if (isActive) c.primarySoftInk else c.ink,
                                                                fontWeight = FontWeight.Bold,
                                                                fontFamily = FontFamily.Serif,
                                                                fontSize = 14.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                            )
                                                            GuMetaText(
                                                                text = if (isZh) "${formatStudioCount(ch.wordCount)} 字 · $statusText" else "${formatStudioCount(ch.wordCount)}w · $statusText",
                                                                color = c.muted,
                                                            )
                                                        }
                                                        if (isActive) {
                                                            GuBadge(if (isZh) "当前" else "Active", variant = GuBadgeVariant.HONEY)
                                                        }
                                                        IconButton(
                                                            onClick = { subPage = NovelStudioSubPage.ChapterDetail(ch.id) },
                                                            modifier = Modifier.size(30.dp),
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Edit,
                                                                contentDescription = strings.common.edit,
                                                                tint = c.primary,
                                                                modifier = Modifier.size(16.dp),
                                                            )
                                                        }
                                                    }

                                                    if (ch.wordCount > 0) {
                                                        LinearProgressIndicator(
                                                            progress = { chProgress },
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(2.5.dp)
                                                                .clip(RoundedCornerShape(999.dp)),
                                                            color = if (ch.status == "done" || chProgress >= 1f) c.success else c.primary,
                                                            trackColor = c.border.copy(alpha = 0.35f),
                                                        )
                                                    }

                                                    if (!isExpanded && ch.outline.isNotBlank()) {
                                                        Text(
                                                            text = ch.outline,
                                                            color = c.muted,
                                                            fontSize = 12.sp,
                                                            lineHeight = 17.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }

                                                    AnimatedVisibility(visible = isExpanded) {
                                                        Column(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(top = 4.dp),
                                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                                        ) {
                                                            if (ch.outline.isNotBlank()) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .clip(RoundedCornerShape(10.dp))
                                                                        .background(c.surfaceSoft)
                                                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                                                ) {
                                                                    Text(
                                                                        text = "${strings.novel.chapterOutline}：${ch.outline}",
                                                                        color = c.ink,
                                                                        fontSize = 12.sp,
                                                                        lineHeight = 17.5.sp,
                                                                    )
                                                                }
                                                            }
                                                            if (ch.summary.isNotBlank()) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .clip(RoundedCornerShape(10.dp))
                                                                        .background(c.surfaceSoft)
                                                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                                                ) {
                                                                    Text(
                                                                        text = "${strings.novel.chapterSummaryLabel}：${ch.summary}",
                                                                        color = c.muted,
                                                                        fontSize = 12.sp,
                                                                        lineHeight = 17.5.sp,
                                                                    )
                                                                }
                                                            }

                                                            if (isConfirmingDelete) {
                                                                Row(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .clip(RoundedCornerShape(10.dp))
                                                                        .background(c.dangerBg)
                                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                ) {
                                                                    Text(
                                                                        text = if (isZh) "确定删除？正文将保留在相邻章节" else "Delete chapter? Prose stays intact",
                                                                        color = c.danger,
                                                                        fontSize = 11.5.sp,
                                                                        fontWeight = FontWeight.SemiBold,
                                                                        modifier = Modifier.weight(1f),
                                                                    )
                                                                    TextButton(onClick = { pendingDeleteChapterId = null }) {
                                                                        Text(strings.common.cancel, fontSize = 11.5.sp, color = c.muted)
                                                                    }
                                                                    GuButton(
                                                                        onClick = {
                                                                            pendingDeleteChapterId = null
                                                                            onDeleteChapter(ch.id)
                                                                        },
                                                                        compact = true,
                                                                        variant = GuButtonVariant.DANGER,
                                                                    ) {
                                                                        Text(strings.common.delete, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                                    }
                                                                }
                                                            } else {
                                                                FlowRow(
                                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                                                ) {
                                                                    if (!isActive) {
                                                                        GuPill(
                                                                            label = if (isZh) "设为当前写作" else "Set Active",
                                                                            selected = true,
                                                                            onClick = { onSelectChapter(ch.id) },
                                                                        )
                                                                    }
                                                                    GuPill(
                                                                        label = if (isZh) "编辑章名与细纲" else "Edit Beat",
                                                                        selected = false,
                                                                        onClick = { subPage = NovelStudioSubPage.ChapterDetail(ch.id) },
                                                                    )
                                                                    if (ch.wordCount > 0) {
                                                                        GuPill(
                                                                            label = if (isGeneratingRecap) "..." else strings.novel.recapBtn,
                                                                            selected = false,
                                                                            enabled = !isGeneratingRecap,
                                                                            onClick = { onGenerateChapterRecap(ch.id) },
                                                                        )
                                                                    }
                                                                    GuPill(
                                                                        label = strings.common.copy,
                                                                        selected = false,
                                                                        onClick = {
                                                                            val txt = NovelExport.formatSingleChapter(novel, ch, chapters, messages, markdown = false)
                                                                            copyTextToClipboard(ctx, ch.title, txt)
                                                                            onToast(strings.common.copied)
                                                                        },
                                                                    )
                                                                    if (chapters.size > 1) {
                                                                        GuPill(
                                                                            label = strings.common.delete,
                                                                            selected = false,
                                                                            onClick = { pendingDeleteChapterId = ch.id },
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // ==================== TAB 1: WORLDBOOK / LORE (2-LINE COMPACT CARDS) ====================
                                1 -> {
                                    val filteredLore = remember(lore, loreSearch, loreFilterCat) {
                                        val byCat = if (loreFilterCat == "all") {
                                            lore
                                        } else {
                                            lore.filter { it.category.equals(loreFilterCat, ignoreCase = true) }
                                        }
                                        FuzzyMatcher.filterAndRank(byCat, loreSearch) { entry ->
                                            listOf(entry.title, entry.content, entry.keywords.joinToString(" "), entry.aliases.joinToString(" "))
                                        }
                                    }

                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .padding(horizontal = 16.dp),
                                        contentPadding = PaddingValues(bottom = 28.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        item(key = "__lore_toolbar__") {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                GuButton(
                                                    onClick = { subPage = NovelStudioSubPage.LoreEditor(null) },
                                                    compact = true,
                                                    variant = GuButtonVariant.PRIMARY,
                                                    modifier = Modifier.weight(1f),
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(strings.novel.loreAddBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                                }
                                                GuButton(
                                                    onClick = { importLoreLauncher.launch(arrayOf("*/*")) },
                                                    compact = true,
                                                    variant = GuButtonVariant.SUBTLE,
                                                ) {
                                                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(15.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(if (isZh) "导入" else "Import", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                                }
                                                GuButton(
                                                    onClick = onExtractLoreNow,
                                                    enabled = hasAssistantProse && !isExtractingLore,
                                                    loading = isExtractingLore,
                                                    compact = true,
                                                    variant = GuButtonVariant.HONEY,
                                                    modifier = Modifier.weight(1.05f),
                                                ) {
                                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        text = if (isExtractingLore) strings.novel.loreExtracting else strings.novel.loreExtractBtn,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                        }

                                        item(key = "__lore_search_filter__") {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                GuCompactSearchBar(
                                                    value = loreSearch,
                                                    onValueChange = { loreSearch = it },
                                                    placeholder = if (isZh) "搜索世界书标题、关键词或设定内容…" else "Search worldbook entries…",
                                                    clearContentDescription = strings.common.clear,
                                                )
                                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    items(listOf("all", "character", "location", "item", "faction", "rule", "foreshadow", "plot", "world"), key = { it }) { cat ->
                                                        GuPill(
                                                            label = strings.novel.loreCats[cat] ?: cat,
                                                            selected = loreFilterCat == cat,
                                                            onClick = { loreFilterCat = cat },
                                                        )
                                                    }
                                                }
                                                GuSectionHeader(
                                                    title = strings.novel.loreHeader,
                                                    trailing = {
                                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            if (triggeredLoreIds.isNotEmpty()) {
                                                                GuBadge(
                                                                    text = if (isZh) "命中 ${triggeredLoreIds.size}" else "${triggeredLoreIds.size} active",
                                                                    variant = GuBadgeVariant.EMERALD,
                                                                )
                                                            }
                                                            GuBadge(strings.novel.loreCount.fmt("count" to lore.size), variant = GuBadgeVariant.HONEY)
                                                        }
                                                    },
                                                )
                                            }
                                        }

                                        if (filteredLore.isEmpty()) {
                                            item(key = "__lore_empty__") {
                                                GuEmptyState(
                                                    title = strings.novel.loreEmpty,
                                                    icon = Icons.AutoMirrored.Filled.MenuBook,
                                                )
                                            }
                                        } else {
                                            items(filteredLore, key = { it.id }) { entry ->
                                                val isTriggered = entry.id in triggeredLoreIds
                                                val isExpanded = expandedLoreId == entry.id
                                                val isConfirmingDelete = pendingDeleteLoreId == entry.id
                                                val estTokens = remember(entry.id, entry.estimatedTokens, entry.title, entry.content) {
                                                    NovelEngine.estimateLoreTokens(entry)
                                                }

                                                GuCard(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    borderColor = if (isTriggered) c.success.copy(alpha = 0.45f) else null,
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                                    onClick = {
                                                        expandedLoreId = if (isExpanded) null else entry.id
                                                        pendingDeleteLoreId = null
                                                    },
                                                ) {
                                                    // High-density 2-line layout (fits 5-6 cards per viewport)
                                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        // Line 1: Category + Title + [常驻●] + Compact Switch
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
                                                                color = if (entry.enabled) c.ink else c.muted,
                                                                fontWeight = FontWeight.SemiBold,
                                                                fontSize = 13.5.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.weight(1f),
                                                            )
                                                            if (isTriggered) {
                                                                GuBadge(if (isZh) "命中" else "Hit", variant = GuBadgeVariant.EMERALD)
                                                            }
                                                            Surface(
                                                                onClick = { onToggleLoreAlwaysActive(entry.id) },
                                                                shape = RoundedCornerShape(999.dp),
                                                                color = if (entry.alwaysActive) c.primarySoft else c.surfaceSoft,
                                                            ) {
                                                                Text(
                                                                    text = if (entry.alwaysActive) {
                                                                        if (isZh) "常驻●" else "Pin●"
                                                                    } else {
                                                                        if (isZh) "按词○" else "Key○"
                                                                    },
                                                                    color = if (entry.alwaysActive) c.primarySoftInk else c.muted,
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
                                                                )
                                                            }
                                                            CompactTogglePill(
                                                                checked = entry.enabled,
                                                                onToggle = { onToggleLoreEnabled(entry.id) },
                                                            )
                                                        }

                                                        // Line 2: 1-line preview + ~tok readout
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        ) {
                                                            val previewLine = if (entry.keywords.isNotEmpty()) {
                                                                "${entry.keywords.joinToString("/")} · ${entry.content}"
                                                            } else {
                                                                entry.content
                                                            }
                                                            Text(
                                                                text = previewLine.replace('\n', ' '),
                                                                color = if (entry.enabled) c.muted else c.muted.copy(alpha = 0.55f),
                                                                fontSize = 11.5.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.weight(1f),
                                                            )
                                                            GuMetaText(
                                                                text = "~${estTokens}tok",
                                                                color = c.muted,
                                                            )
                                                        }

                                                        // Expanded detail & actions
                                                        AnimatedVisibility(visible = isExpanded) {
                                                            Column(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(top = 6.dp),
                                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .clip(RoundedCornerShape(10.dp))
                                                                        .background(c.surfaceSoft)
                                                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                                                ) {
                                                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                        Text(
                                                                            text = entry.content,
                                                                            color = c.ink,
                                                                            fontSize = 12.sp,
                                                                            lineHeight = 18.sp,
                                                                        )
                                                                        if (entry.keywords.isNotEmpty() || entry.aliases.isNotEmpty()) {
                                                                            val kw = (entry.keywords + entry.aliases).distinct().joinToString(" · ")
                                                                            Text(
                                                                                text = "🔑 $kw",
                                                                                color = c.muted,
                                                                                fontSize = 11.sp,
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                                if (isConfirmingDelete) {
                                                                    Row(
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .clip(RoundedCornerShape(10.dp))
                                                                            .background(c.dangerBg)
                                                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                    ) {
                                                                        Text(
                                                                            text = if (isZh) "确定删除此词条？" else "Delete entry?",
                                                                            color = c.danger,
                                                                            fontSize = 11.5.sp,
                                                                            fontWeight = FontWeight.SemiBold,
                                                                            modifier = Modifier.weight(1f),
                                                                        )
                                                                        TextButton(onClick = { pendingDeleteLoreId = null }) {
                                                                            Text(strings.common.cancel, fontSize = 11.5.sp, color = c.muted)
                                                                        }
                                                                        GuButton(
                                                                            onClick = {
                                                                                pendingDeleteLoreId = null
                                                                                onDeleteLore(entry.id)
                                                                            },
                                                                            compact = true,
                                                                            variant = GuButtonVariant.DANGER,
                                                                        ) {
                                                                            Text(strings.common.delete, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                                        }
                                                                    }
                                                                } else {
                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                                                    ) {
                                                                        GuPill(
                                                                            label = strings.common.delete,
                                                                            selected = false,
                                                                            onClick = { pendingDeleteLoreId = entry.id },
                                                                        )
                                                                        GuPill(
                                                                            label = strings.common.edit,
                                                                            selected = true,
                                                                            onClick = { subPage = NovelStudioSubPage.LoreEditor(entry.id) },
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // ==================== TAB 2: AI CRAFT DESK (BORDERLESS SOFT TROUGHS) ====================
                                2 -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .verticalScroll(rememberScrollState())
                                            .padding(horizontal = 16.dp)
                                            .padding(bottom = 28.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        // 1. Three-Path Plot Drafts
                                        GuCard(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
                                                GuIconTile(icon = Icons.Default.AutoAwesome, tint = c.tagInk, backgroundColor = c.tag)
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(strings.novel.threePathsBtn, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                    Text(
                                                        text = if (isZh) "推演三路走向草案，可直接按此推进或填入输入框" else "Generate 3 divergent plot paths to adopt or send",
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
                                                    placeholder = if (isZh) "分歧方向提示（可选，如：突然遇伏 / 内部反水）" else "Branch hint (optional)",
                                                    modifier = Modifier.weight(1f),
                                                )
                                                GuButton(
                                                    onClick = { onGenerate3Paths(draftHint) },
                                                    loading = isBusy,
                                                    compact = true,
                                                    variant = GuButtonVariant.HONEY,
                                                    modifier = Modifier.heightIn(min = 42.dp),
                                                ) {
                                                    Icon(Icons.Default.AutoAwesome, contentDescription = strings.novel.threePathsBtn, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                            if (draftOptions.isNotEmpty()) {
                                                Spacer(Modifier.height(10.dp))
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    draftOptions.forEachIndexed { idx, opt ->
                                                        val roman = ROMAN_BRANCH_NUMERALS.getOrElse(idx) { "${idx + 1}" }
                                                        Column(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .background(c.surfaceSoft)
                                                                .padding(12.dp),
                                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                                        ) {
                                                            Row(
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.fillMaxWidth(),
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(22.dp)
                                                                        .clip(CircleShape)
                                                                        .background(c.tag),
                                                                    contentAlignment = Alignment.Center,
                                                                ) {
                                                                    Text(
                                                                        text = roman,
                                                                        color = c.tagInk,
                                                                        fontSize = 11.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        fontFamily = FontFamily.Serif,
                                                                    )
                                                                }
                                                                Text(
                                                                    text = opt.title,
                                                                    color = c.ink,
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 13.5.sp,
                                                                    modifier = Modifier.weight(1f),
                                                                )
                                                                if (opt.tone.isNotBlank()) {
                                                                    GuBadge(opt.tone, variant = GuBadgeVariant.BLUE)
                                                                }
                                                            }
                                                            Text(opt.summary, color = c.muted, fontSize = 12.sp, lineHeight = 17.5.sp)
                                                            if (opt.opening.isNotBlank()) {
                                                                Text(
                                                                    text = opt.opening,
                                                                    color = c.ink,
                                                                    fontSize = 12.sp,
                                                                    lineHeight = 18.sp,
                                                                    fontFamily = FontFamily.Serif,
                                                                    maxLines = 4,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                )
                                                            }
                                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                GuButton(
                                                                    onClick = {
                                                                        onAdoptDraft(opt, false)
                                                                        onDismiss()
                                                                    },
                                                                    compact = true,
                                                                    variant = GuButtonVariant.SUBTLE,
                                                                ) {
                                                                    Text(if (isZh) "填入草稿框" else "To Draft", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                                }
                                                                GuButton(
                                                                    onClick = {
                                                                        onAdoptDraft(opt, true)
                                                                        onDismiss()
                                                                    },
                                                                    compact = true,
                                                                ) {
                                                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(13.dp))
                                                                    Spacer(Modifier.width(4.dp))
                                                                    Text(if (isZh) "采纳推进" else "Adopt & Write", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // 2. Anti-AI Prose Polish + Side-by-Side/Stacked Paper Trough Comparison Preview
                                        GuCard(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
                                                GuIconTile(icon = Icons.Default.AutoFixHigh, tint = c.success, backgroundColor = c.successBg)
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = if (isZh) "反 AI 味润色与对照预览" else "Anti-AI Polish & Comparison",
                                                        color = c.ink,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                    )
                                                    Text(
                                                        text = if (isZh) "精修上一轮正文，对比原版后一键采纳或填入草稿" else "Refine latest prose, compare original vs polished & adopt",
                                                        color = c.muted,
                                                        fontSize = 11.5.sp,
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(10.dp))
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                listOf(
                                                    "deai" to strings.novel.polishDeai,
                                                    "describe" to strings.novel.polishDescribe,
                                                    "tighten" to strings.novel.polishTighten,
                                                    "dialogue" to (if (isZh) "对白增色" else "Dialogue"),
                                                ).forEach { (presetKey, label) ->
                                                    GuPill(
                                                        label = "✨ $label",
                                                        selected = false,
                                                        enabled = !isBusy && hasAssistantProse,
                                                        onClick = { onPolishLast(polishInstruction, presetKey) },
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                GuTextField(
                                                    value = polishInstruction,
                                                    onValueChange = { polishInstruction = it },
                                                    placeholder = if (isZh) "自定义润色方向（如：删去工整排比、口语化些）" else "Custom polish direction (optional)",
                                                    modifier = Modifier.weight(1f),
                                                )
                                                GuButton(
                                                    onClick = { onPolishLast(polishInstruction, null) },
                                                    enabled = !isBusy && hasAssistantProse,
                                                    loading = isBusy,
                                                    compact = true,
                                                    variant = GuButtonVariant.SUBTLE,
                                                    modifier = Modifier.heightIn(min = 42.dp),
                                                ) {
                                                    Text(if (isZh) "润色" else "Polish", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            if (polishResult.isNotBlank()) {
                                                Spacer(Modifier.height(10.dp))
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    // Original Paper Trough (borderless surfaceSoft)
                                                    if (latestAssistantProse.isNotBlank()) {
                                                        Column(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .background(c.surfaceSoft.copy(alpha = 0.65f))
                                                                .clickable { showFullPolishSource = !showFullPolishSource }
                                                                .padding(11.dp),
                                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically,
                                                            ) {
                                                                GuMetaText(
                                                                    text = if (isZh) "原文段落" else "Original Passage",
                                                                    color = c.muted,
                                                                )
                                                                Text(
                                                                    text = if (showFullPolishSource) {
                                                                        if (isZh) "收起" else "Collapse"
                                                                    } else {
                                                                        if (isZh) "展开对比" else "Expand"
                                                                    },
                                                                    color = c.primary,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                )
                                                            }
                                                            Text(
                                                                text = latestAssistantProse,
                                                                color = c.muted,
                                                                fontSize = 12.sp,
                                                                lineHeight = 18.sp,
                                                                maxLines = if (showFullPolishSource) 18 else 3,
                                                                overflow = TextOverflow.Ellipsis,
                                                            )
                                                        }
                                                    }

                                                    // Polished Paper Trough (borderless surfaceSoft with subtle left accent)
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(IntrinsicSize.Min)
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .background(c.surfaceSoft),
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .width(3.dp)
                                                                .fillMaxHeight()
                                                                .background(c.success),
                                                        )
                                                        Column(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .padding(11.dp),
                                                            verticalArrangement = Arrangement.spacedBy(5.dp),
                                                        ) {
                                                            GuMetaText(
                                                                text = if (isZh) "✨ 润色对照版" else "✨ Polished Version",
                                                                color = c.success,
                                                            )
                                                            Text(
                                                                text = polishResult,
                                                                color = c.ink,
                                                                fontSize = 12.5.sp,
                                                                lineHeight = 19.sp,
                                                                fontFamily = FontFamily.Serif,
                                                            )
                                                        }
                                                    }

                                                    FlowRow(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                                    ) {
                                                        GuButton(
                                                            onClick = {
                                                                copyTextToClipboard(ctx, "polish", polishResult)
                                                                onToast(strings.common.copied)
                                                            },
                                                            compact = true,
                                                            variant = GuButtonVariant.SUBTLE,
                                                        ) {
                                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                                                            Spacer(Modifier.width(4.dp))
                                                            Text(strings.common.copy, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                        GuButton(
                                                            onClick = {
                                                                onApplyPolishAsVariant(polishResult)
                                                                onDismiss()
                                                            },
                                                            compact = true,
                                                        ) {
                                                            Text(if (isZh) "一键采纳为新版本" else "Adopt Variant", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                        GuButton(
                                                            onClick = {
                                                                onFillComposerDraft(polishResult)
                                                                onDismiss()
                                                            },
                                                            compact = true,
                                                            variant = GuButtonVariant.SUBTLE,
                                                        ) {
                                                            Text(if (isZh) "填入草稿框" else "To Draft", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // 3. Outline Co-Creation
                                        GuCard(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
                                                GuIconTile(icon = Icons.Default.Psychology, tint = c.primarySoftInk, backgroundColor = c.primarySoft)
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(strings.novel.outlineChatTitle, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                    Text(strings.novel.outlineChatSub, color = c.muted, fontSize = 11.5.sp)
                                                }
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                val quickPrompts = if (isZh) {
                                                    listOf("规划后续4章细纲", "设计一个意料之外的反转", "梳理未回收伏笔清单")
                                                } else {
                                                    listOf("Plan next 4 chapter beats", "Design an unexpected twist", "Audit unresolved clues")
                                                }
                                                quickPrompts.forEach { qp ->
                                                    GuPill(label = qp, selected = false, onClick = { outlinePrompt = qp })
                                                }
                                            }
                                            Spacer(Modifier.height(8.dp))
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
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(c.surfaceSoft)
                                                        .padding(12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(9.dp),
                                                ) {
                                                    Text(outlineReply, color = c.ink, fontSize = 12.5.sp, lineHeight = 19.sp)
                                                    FlowRow(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                                    ) {
                                                        GuButton(
                                                            onClick = {
                                                                copyTextToClipboard(ctx, "outline", outlineReply)
                                                                onToast(strings.common.copied)
                                                            },
                                                            compact = true,
                                                            variant = GuButtonVariant.SUBTLE,
                                                        ) {
                                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                                                            Spacer(Modifier.width(4.dp))
                                                            Text(strings.common.copy, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                        GuButton(
                                                            onClick = { onApplyOutlineToBook(outlineReply) },
                                                            compact = true,
                                                            variant = GuButtonVariant.PRIMARY,
                                                        ) {
                                                            Text(strings.novel.outlineChatApply, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                        GuButton(
                                                            onClick = { onSyncChaptersFromOutline(outlineReply) },
                                                            enabled = !isSyncingChapters,
                                                            loading = isSyncingChapters,
                                                            compact = true,
                                                            variant = GuButtonVariant.HONEY,
                                                        ) {
                                                            Text(if (isZh) "提取并导入分章" else "Import Chapters", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // 4. Foreshadow Tracker
                                        val hudForeshadows = novel.currentStatus?.foreshadows.orEmpty()
                                        val loreForeshadows = lore.filter { it.category.equals("foreshadow", ignoreCase = true) && !it.stale }
                                        if (hudForeshadows.isNotEmpty() || loreForeshadows.isNotEmpty()) {
                                            GuCard(modifier = Modifier.fillMaxWidth()) {
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    ) {
                                                        GuIconTile(icon = Icons.Default.BookmarkAdd, tint = c.accent, backgroundColor = c.accentSoft)
                                                        Text(
                                                            text = if (isZh) "伏笔线索追踪" else "Foreshadow Tracker",
                                                            color = c.ink,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp,
                                                        )
                                                    }
                                                    hudForeshadows.forEach { fs ->
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(10.dp))
                                                                .background(c.surfaceSoft)
                                                                .padding(horizontal = 10.dp, vertical = 7.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        ) {
                                                            Text("🧵 $fs", color = c.ink, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                                            TextButton(
                                                                onClick = { onSaveForeshadowToLore(fs) },
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                            ) {
                                                                Text(if (isZh) "收录世界书" else "Save Lore", fontSize = 11.sp, color = c.primary, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }
                                                    loreForeshadows.forEach { entry ->
                                                        Text(
                                                            text = "• ${entry.title}：${entry.content}",
                                                            color = c.muted,
                                                            fontSize = 12.sp,
                                                            lineHeight = 17.sp,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // ==================== TAB 3: BOOK ARCHIVE (COVER PARAMS CARD + ACCORDIONS + EXPORT) ====================
                                else -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .verticalScroll(rememberScrollState())
                                            .padding(horizontal = 16.dp)
                                            .padding(bottom = 28.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        GuSectionHeader(
                                            title = if (isZh) "书封参数卡" else "Book Cover & Parameters",
                                            trailing = {
                                                if (profileDirty) {
                                                    GuButton(
                                                        onClick = ::saveBookArchiveProfile,
                                                        compact = true,
                                                    ) {
                                                        Text(strings.common.save, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            },
                                        )

                                        // Compact Book Cover Parameter Card
                                        GuCard(modifier = Modifier.fillMaxWidth()) {
                                            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                                                val coverFile = remember(novel.coverImagePath) {
                                                    LocalMediaPathResolver.resolveFile(
                                                        ctx.applicationContext.filesDir.absolutePath,
                                                        novel.coverImagePath,
                                                    )?.takeIf { it.isFile }
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(width = 72.dp, height = 96.dp)
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(c.surfaceSoft)
                                                            .clickable {
                                                                pickCoverLauncher.launch(
                                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                                                )
                                                            },
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        if (coverFile != null) {
                                                            Image(
                                                                painter = rememberAsyncImagePainter(coverFile),
                                                                contentDescription = novel.title,
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.fillMaxSize(),
                                                            )
                                                        } else {
                                                            Column(
                                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.AutoStories,
                                                                    contentDescription = null,
                                                                    tint = c.muted,
                                                                    modifier = Modifier.size(22.dp),
                                                                )
                                                                GuMetaText(
                                                                    text = if (isZh) "书封" else "Cover",
                                                                    color = c.muted,
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Column(
                                                        modifier = Modifier.weight(1f),
                                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                                    ) {
                                                        Text(
                                                            text = if (isZh) "精装书封与插图" else "Book Cover Artwork",
                                                            color = c.ink,
                                                            fontSize = 13.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                        )
                                                        FlowRow(
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                                        ) {
                                                            GuButton(
                                                                onClick = { chatVm?.generateNovelCoverArt() },
                                                                enabled = chatVm != null && !isGeneratingCover,
                                                                loading = isGeneratingCover,
                                                                compact = true,
                                                                variant = GuButtonVariant.HONEY,
                                                            ) {
                                                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(13.dp))
                                                                Spacer(Modifier.width(4.dp))
                                                                Text(if (isZh) "✨ AI 绘书封" else "✨ AI Cover", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                            GuButton(
                                                                onClick = {
                                                                    pickCoverLauncher.launch(
                                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                                                    )
                                                                },
                                                                enabled = chatVm != null,
                                                                compact = true,
                                                                variant = GuButtonVariant.SUBTLE,
                                                            ) {
                                                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(13.dp))
                                                                Spacer(Modifier.width(4.dp))
                                                                Text(if (isZh) "相册选图" else "Pick", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                            if (coverFile != null) {
                                                                GuButton(
                                                                    onClick = { chatVm?.clearNovelCoverImage() },
                                                                    compact = true,
                                                                    variant = GuButtonVariant.SECONDARY,
                                                                ) {
                                                                    Text(if (isZh) "清除" else "Clear", color = c.danger, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                HorizontalDivider(thickness = 0.5.dp, color = c.border.copy(alpha = 0.45f))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    GuTextField(
                                                        value = bookTitleDraft,
                                                        onValueChange = { bookTitleDraft = it },
                                                        label = if (isZh) "书名" else "Title",
                                                        modifier = Modifier.weight(1.3f),
                                                    )
                                                    GuTextField(
                                                        value = bookGenreDraft,
                                                        onValueChange = { bookGenreDraft = it },
                                                        label = if (isZh) "题材标签" else "Genre",
                                                        modifier = Modifier.weight(1f),
                                                    )
                                                }

                                                // Narrative POV via GuSegmentedControl
                                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                                    Text(strings.novel.povLabel, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    val povKeys = listOf("third_limited", "first", "third_omniscient")
                                                    GuSegmentedControl(
                                                        items = listOf(
                                                            strings.novel.povThirdLimited,
                                                            strings.novel.povFirst,
                                                            strings.novel.povThirdOmniscient,
                                                        ),
                                                        selectedIndex = povKeys.indexOf(novel.pov).coerceAtLeast(0),
                                                        onSelect = { idx -> onUpdateConfig { it.copy(pov = povKeys[idx]) } },
                                                        compact = true,
                                                        modifier = Modifier.fillMaxWidth(),
                                                    )
                                                }

                                                // Play Style via GuSegmentedControl
                                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                                    Text(strings.novel.playStyleLabel, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    val styleKeys = listOf("hybrid", "author", "interactive")
                                                    GuSegmentedControl(
                                                        items = listOf(
                                                            strings.novel.playHybrid,
                                                            strings.novel.playAuthor,
                                                            strings.novel.playInteractive,
                                                        ),
                                                        selectedIndex = styleKeys.indexOf(novel.playStyle).coerceAtLeast(0),
                                                        onSelect = { idx ->
                                                            val k = styleKeys[idx]
                                                            onUpdateConfig {
                                                                it.copy(
                                                                    playStyle = k,
                                                                    enableChoices = if (k == "author") false else it.enableChoices,
                                                                )
                                                            }
                                                        },
                                                        compact = true,
                                                        modifier = Modifier.fillMaxWidth(),
                                                    )
                                                }

                                                // Chapter Target Words Capsules (1500 / 2500 / 4000 / 6000)
                                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                                    Text(strings.novel.chapterTargetWords, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    val wordOptions = remember(novel.targetWordsPerChapter) {
                                                        if (novel.targetWordsPerChapter in STUDIO_TARGET_WORD_STEPS) STUDIO_TARGET_WORD_STEPS
                                                        else (STUDIO_TARGET_WORD_STEPS + novel.targetWordsPerChapter).sorted()
                                                    }
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .horizontalScroll(rememberScrollState()),
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    ) {
                                                        wordOptions.forEach { w ->
                                                            GuPill(
                                                                label = if (isZh) "${w}字" else "${w}w",
                                                                selected = novel.targetWordsPerChapter == w,
                                                                onClick = { onUpdateConfig { it.copy(targetWordsPerChapter = w) } },
                                                            )
                                                        }
                                                    }
                                                }

                                                // Worldbook Budget Capsules
                                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                                    Text(if (isZh) "世界书注入预算 (Tokens)" else "Worldbook Token Budget", color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .horizontalScroll(rememberScrollState()),
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    ) {
                                                        listOf(800, 1600, 2400, 3600).forEach { b ->
                                                            GuPill(
                                                                label = "$b",
                                                                selected = novel.loreTokenBudget == b,
                                                                onClick = { onUpdateConfig { it.copy(loreTokenBudget = b) } },
                                                            )
                                                        }
                                                    }
                                                }

                                                HorizontalDivider(thickness = 0.5.dp, color = c.border.copy(alpha = 0.5f))

                                                Column {
                                                    GuSwitchRow(
                                                        label = strings.novel.antiAiToggle,
                                                        checked = novel.antiAiRules,
                                                        onCheckedChange = { v -> onUpdateConfig { it.copy(antiAiRules = v) } },
                                                    )
                                                    HorizontalDivider(thickness = 0.5.dp, color = c.border.copy(alpha = 0.4f))
                                                    GuSwitchRow(
                                                        label = strings.novel.hudToggle,
                                                        checked = novel.enableStatusHud,
                                                        onCheckedChange = { v -> onUpdateConfig { it.copy(enableStatusHud = v) } },
                                                    )
                                                    HorizontalDivider(thickness = 0.5.dp, color = c.border.copy(alpha = 0.4f))
                                                    GuSwitchRow(
                                                        label = strings.novel.autoSyncLoreToggle,
                                                        checked = novel.autoSyncLore,
                                                        onCheckedChange = { v -> onUpdateConfig { it.copy(autoSyncLore = v) } },
                                                    )
                                                    HorizontalDivider(thickness = 0.5.dp, color = c.border.copy(alpha = 0.4f))
                                                    GuSwitchRow(
                                                        label = strings.novel.autoAdvanceChapterToggle,
                                                        checked = novel.autoAdvanceChapter,
                                                        onCheckedChange = { v -> onUpdateConfig { it.copy(autoAdvanceChapter = v) } },
                                                    )
                                                    HorizontalDivider(thickness = 0.5.dp, color = c.border.copy(alpha = 0.4f))
                                                    GuSwitchRow(
                                                        label = strings.novel.choicesToggle,
                                                        checked = novel.enableChoices,
                                                        onCheckedChange = { v -> onUpdateConfig { it.copy(enableChoices = v) } },
                                                    )
                                                }
                                            }
                                        }

                                        // Accordion Archive Cards (breaks the 8-input wall!)
                                        GuSectionHeader(title = if (isZh) "书卷文本档案（按需展开）" else "Manuscript Notes & Lore (Tap to Expand)")

                                        ArchiveAccordionCard(
                                            title = strings.novel.blurbLabel,
                                            preview = bookSynopsisDraft,
                                            emptyText = if (isZh) "点击补充故事简介…" else "Tap to add synopsis…",
                                            expanded = expandedArchiveKey == "synopsis",
                                            onToggle = {
                                                expandedArchiveKey = if (expandedArchiveKey == "synopsis") null else "synopsis"
                                            },
                                        ) {
                                            GuTextField(
                                                value = bookSynopsisDraft,
                                                onValueChange = { bookSynopsisDraft = it },
                                                placeholder = strings.novel.blurbPlaceholder,
                                                singleLine = false,
                                                minLines = 3,
                                                maxLines = 6,
                                            )
                                        }

                                        ArchiveAccordionCard(
                                            title = strings.novel.masterOutline,
                                            preview = bookOutlineDraft,
                                            emptyText = if (isZh) "点击编写全书主线总纲…" else "Tap to write master outline…",
                                            expanded = expandedArchiveKey == "outline",
                                            onToggle = {
                                                expandedArchiveKey = if (expandedArchiveKey == "outline") null else "outline"
                                            },
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                GuTextField(
                                                    value = bookOutlineDraft,
                                                    onValueChange = { bookOutlineDraft = it },
                                                    placeholder = strings.novel.masterOutlinePlaceholder,
                                                    singleLine = false,
                                                    minLines = 3,
                                                    maxLines = 8,
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.End,
                                                ) {
                                                    TextButton(onClick = { tab = 2 }) {
                                                        Text(strings.novel.outlineChatBtn, fontSize = 12.sp, color = c.primary)
                                                    }
                                                }
                                            }
                                        }

                                        ArchiveAccordionCard(
                                            title = if (isZh) "文风范本与世界观" else "Style Sample & Worldview",
                                            preview = listOf(bookStyleDraft, bookStyleSampleDraft, bookWorldviewDraft)
                                                .firstOrNull { it.isNotBlank() }
                                                .orEmpty(),
                                            emptyText = if (isZh) "点击设置文风指令、语感样段或世界观基石…" else "Tap to configure prose style, sample & worldview…",
                                            expanded = expandedArchiveKey == "style",
                                            onToggle = {
                                                expandedArchiveKey = if (expandedArchiveKey == "style") null else "style"
                                            },
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                GuTextField(
                                                    value = bookStyleDraft,
                                                    onValueChange = { bookStyleDraft = it },
                                                    label = strings.novel.stylePromptLabel,
                                                    singleLine = false,
                                                    minLines = 1,
                                                    maxLines = 3,
                                                )
                                                GuTextField(
                                                    value = bookStyleSampleDraft,
                                                    onValueChange = { bookStyleSampleDraft = it },
                                                    label = strings.novel.styleSampleLabel,
                                                    placeholder = if (isZh) "粘贴 100-400 字你偏爱的文风样段，AI 将贴近其句式语感…" else "Paste a short prose excerpt to match cadence & voice…",
                                                    singleLine = false,
                                                    minLines = 2,
                                                    maxLines = 6,
                                                )
                                                GuTextField(
                                                    value = bookWorldviewDraft,
                                                    onValueChange = { bookWorldviewDraft = it },
                                                    label = if (isZh) "世界观基石" else "Worldview",
                                                    singleLine = false,
                                                    minLines = 2,
                                                    maxLines = 6,
                                                )
                                            }
                                        }

                                        ArchiveAccordionCard(
                                            title = if (isZh) "全局作者备注" else strings.novel.noteTitle,
                                            preview = bookAuthorNoteDraft,
                                            emptyText = if (isZh) "点击填写全局导演备注（每轮自动注入）…" else "Tap to add global author note…",
                                            expanded = expandedArchiveKey == "note",
                                            onToggle = {
                                                expandedArchiveKey = if (expandedArchiveKey == "note") null else "note"
                                            },
                                        ) {
                                            GuTextField(
                                                value = bookAuthorNoteDraft,
                                                onValueChange = { bookAuthorNoteDraft = it },
                                                placeholder = strings.novel.notePlaceholder,
                                                singleLine = false,
                                                minLines = 2,
                                                maxLines = 5,
                                            )
                                        }

                                        if (profileDirty) {
                                            GuButton(
                                                onClick = ::saveBookArchiveProfile,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Text(if (isZh) "保存作品档案" else "Save Book Profile", fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        // Export Section
                                        GuSectionHeader(title = if (isZh) "导出成书" else "Export Manuscript")
                                        GuCard(modifier = Modifier.fillMaxWidth()) {
                                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                GuSwitchRow(
                                                    label = strings.novel.exportIncludeLore,
                                                    checked = exportIncludeLore,
                                                    onCheckedChange = { exportIncludeLore = it },
                                                )
                                                GuButton(
                                                    onClick = {
                                                        exportEpubLauncher.launch(NovelExport.sanitizeFileName(novel.title, "epub"))
                                                    },
                                                    enabled = chatVm != null,
                                                    variant = GuButtonVariant.HONEY,
                                                    modifier = Modifier.fillMaxWidth(),
                                                ) {
                                                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = if (isZh) "📕 导出精装 EPUB" else "📕 Export EPUB 3",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    GuButton(
                                                        onClick = {
                                                            exportTxtLauncher.launch(NovelExport.sanitizeFileName(novel.title, "txt"))
                                                        },
                                                        compact = true,
                                                        variant = GuButtonVariant.SUBTLE,
                                                        modifier = Modifier.weight(1f),
                                                    ) {
                                                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(15.dp))
                                                        Spacer(Modifier.width(5.dp))
                                                        Text(if (isZh) "导出 TXT" else "Export TXT", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                                    }
                                                    GuButton(
                                                        onClick = {
                                                            exportMdLauncher.launch(NovelExport.sanitizeFileName(novel.title, "md"))
                                                        },
                                                        compact = true,
                                                        variant = GuButtonVariant.SUBTLE,
                                                        modifier = Modifier.weight(1f),
                                                    ) {
                                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                                                        Spacer(Modifier.width(5.dp))
                                                        Text(if (isZh) "导出 Markdown" else "Export Markdown", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                                    }
                                                }
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(c.surfaceSoft)
                                                        .clickable {
                                                            val fullText = NovelExport.formatAsTxt(
                                                                novel = novel,
                                                                chapters = chapters,
                                                                messages = messages,
                                                                lore = lore,
                                                                includeLoreAppendix = exportIncludeLore,
                                                            )
                                                            copyTextToClipboard(ctx, novel.title, fullText)
                                                            onToast(strings.common.copied)
                                                        }
                                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = c.primary, modifier = Modifier.size(15.dp))
                                                    Text(
                                                        text = if (isZh) "复制全书正文到剪贴板" else "Copy Full Manuscript",
                                                        color = c.ink,
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.weight(1f),
                                                    )
                                                    GuMetaText(
                                                        text = if (isZh) "${formatStudioCount(totalBookWords)} 字" else "${formatStudioCount(totalBookWords)}w",
                                                        color = c.muted,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelSubPageHeader(
    title: String,
    backLabel: String,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = LocalAppTheme.current.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = backLabel,
                    tint = c.primary,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    text = backLabel,
                    color = c.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = title,
                color = c.ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                trailing()
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = c.border.copy(alpha = 0.45f))
    }
}

@Composable
private fun NovelChapterDetailSubPage(
    chapter: NovelChapter,
    canDelete: Boolean,
    isGeneratingRecap: Boolean,
    onBack: () -> Unit,
    onGenerateRecap: () -> Unit,
    onSave: (NovelChapter) -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH

    var title by remember(chapter.id) { mutableStateOf(chapter.title) }
    var status by remember(chapter.id) { mutableStateOf(chapter.status) }
    var outline by remember(chapter.id) { mutableStateOf(chapter.outline) }
    var summary by remember(chapter.id, chapter.summary) { mutableStateOf(chapter.summary) }
    var confirmDelete by remember(chapter.id) { mutableStateOf(false) }

    LaunchedEffect(chapter.summary) {
        if (chapter.summary.isNotBlank() && summary.isBlank()) {
            summary = chapter.summary
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NovelSubPageHeader(
            title = if (isZh) "第 ${chapter.order} 章 · 细纲与梗概" else "Chapter ${chapter.order}",
            backLabel = if (isZh) "目录" else "Back",
            onBack = onBack,
            trailing = {
                GuButton(
                    onClick = {
                        onSave(
                            chapter.copy(
                                title = title.trim().ifBlank { chapter.title },
                                status = status,
                                outline = outline.trim(),
                                summary = summary.trim(),
                            ),
                        )
                    },
                    compact = true,
                ) {
                    Text(strings.common.save, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GuCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GuTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = if (isZh) "章节标题" else "Chapter Title",
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(if (isZh) "章节状态" else "Status", color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        val statusKeys = listOf("draft", "writing", "done")
                        GuSegmentedControl(
                            items = statusKeys.map { localizeChapterStatus(it, isZh) },
                            selectedIndex = statusKeys.indexOf(status).coerceAtLeast(0),
                            onSelect = { idx -> status = statusKeys[idx] },
                            compact = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            GuCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GuTextField(
                        value = outline,
                        onValueChange = { outline = it },
                        label = strings.novel.chapterOutline,
                        placeholder = strings.novel.chapterOutlinePlaceholder,
                        singleLine = false,
                        minLines = 3,
                        maxLines = 7,
                    )
                    GuTextField(
                        value = summary,
                        onValueChange = { summary = it },
                        label = strings.novel.chapterSummaryLabel,
                        placeholder = if (isZh) "本章完结后的前情梗概（将注入后续章节提示词）…" else "Chapter recap injected into future chapters…",
                        singleLine = false,
                        minLines = 3,
                        maxLines = 6,
                    )
                    GuButton(
                        onClick = onGenerateRecap,
                        enabled = !isGeneratingRecap && chapter.wordCount > 0,
                        loading = isGeneratingRecap,
                        compact = true,
                        variant = GuButtonVariant.SUBTLE,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(strings.novel.recapBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (canDelete) {
                if (confirmDelete) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.dangerBg)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isZh) "删除《${chapter.title}》？正文将保留在相邻章节。" else "Delete chapter? Prose remains intact.",
                            color = c.danger,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { confirmDelete = false }) {
                            Text(strings.common.cancel, color = c.muted, fontSize = 12.sp)
                        }
                        GuButton(
                            onClick = onDelete,
                            compact = true,
                            variant = GuButtonVariant.DANGER,
                        ) {
                            Text(strings.common.delete, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    TextButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = c.danger, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isZh) "删除本章" else "Delete Chapter", color = c.danger, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelFinishChapterSubPage(
    activeChapter: NovelChapter,
    nextOrder: Int,
    nextPlannedChapter: NovelChapter?,
    onBack: () -> Unit,
    onConfirm: (nextTitle: String, nextOutline: String, autoRecap: Boolean) -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH

    var nextTitle by remember(nextPlannedChapter?.id) { mutableStateOf(nextPlannedChapter?.title.orEmpty()) }
    var nextOutline by remember(nextPlannedChapter?.id) { mutableStateOf(nextPlannedChapter?.outline.orEmpty()) }
    var autoRecap by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        NovelSubPageHeader(
            title = if (isZh) "完结并开启下一章" else "Finish & Start Next",
            backLabel = if (isZh) "目录" else "Back",
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GuCard(modifier = Modifier.fillMaxWidth(), backgroundColor = c.surfaceSoft) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GuMetaText(
                        text = if (isZh) "即将完结当前章节" else "Finishing Active Chapter",
                        color = c.muted,
                    )
                    Text(
                        text = "《${activeChapter.title}》 · ${formatStudioCount(activeChapter.wordCount)} ${if (isZh) "字" else "w"}",
                        color = c.ink,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                    )
                }
            }

            GuCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (nextPlannedChapter != null) {
                        GuBadge(
                            text = if (isZh) "已检测到预规划草稿：${nextPlannedChapter.title}" else "Next planned draft: ${nextPlannedChapter.title}",
                            variant = GuBadgeVariant.EMERALD,
                        )
                        if (nextPlannedChapter.outline.isNotBlank()) {
                            Text(
                                text = nextPlannedChapter.outline,
                                color = c.muted,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                            )
                        }
                    } else {
                        GuTextField(
                            value = nextTitle,
                            onValueChange = { nextTitle = it },
                            label = if (isZh) "下一章标题（可选）" else "Next Chapter Title",
                            placeholder = if (isZh) "第${nextOrder}章" else "Chapter $nextOrder",
                        )
                        GuTextField(
                            value = nextOutline,
                            onValueChange = { nextOutline = it },
                            label = strings.novel.chapterOutline,
                            placeholder = strings.novel.chapterOutlinePlaceholder,
                            singleLine = false,
                            minLines = 3,
                            maxLines = 6,
                        )
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = c.border.copy(alpha = 0.5f))
                    GuSwitchRow(
                        label = if (isZh) "AI 自动总结本章前情梗概" else "Auto-generate AI recap for finished chapter",
                        checked = autoRecap,
                        onCheckedChange = { autoRecap = it },
                    )
                }
            }

            GuButton(
                onClick = {
                    val usePlanned = nextPlannedChapter != null
                    onConfirm(
                        if (usePlanned) "" else nextTitle,
                        if (usePlanned) "" else nextOutline,
                        autoRecap,
                    )
                },
                variant = GuButtonVariant.HONEY,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isZh) "确认完结并开启下一章" else "Finish & Start Next", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NovelLoreEditorSubPage(
    existing: NovelLoreEntry?,
    onBack: () -> Unit,
    onSaveNew: (cat: String, title: String, keywords: String, content: String, always: Boolean) -> Unit,
    onSaveExisting: (NovelLoreEntry) -> Unit,
    onDelete: (String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH

    var cat by remember(existing?.id) { mutableStateOf(existing?.category ?: "character") }
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var keywords by remember(existing?.id) { mutableStateOf(existing?.keywords?.joinToString(", ").orEmpty()) }
    var aliases by remember(existing?.id) { mutableStateOf(existing?.aliases?.joinToString(", ").orEmpty()) }
    var priority by remember(existing?.id) { mutableIntStateOf(existing?.priority ?: 50) }
    var content by remember(existing?.id) { mutableStateOf(existing?.content.orEmpty()) }
    var always by remember(existing?.id) { mutableStateOf(existing?.alwaysActive ?: false) }
    var confirmDelete by remember(existing?.id) { mutableStateOf(false) }

    val canSave = title.isNotBlank() && content.isNotBlank()

    fun commitSave() {
        if (!canSave) return
        if (existing != null) {
            val kws = keywords.split(',', '，', '、').map { it.trim() }.filter { it.isNotEmpty() }
            val als = aliases.split(',', '，', '、').map { it.trim() }.filter { it.isNotEmpty() }
            onSaveExisting(
                existing.copy(
                    category = cat,
                    title = title.trim(),
                    keywords = kws.ifEmpty { listOf(title.trim()) },
                    aliases = als,
                    priority = priority,
                    content = content.trim(),
                    alwaysActive = always,
                ),
            )
        } else {
            onSaveNew(cat, title, keywords, content, always)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NovelSubPageHeader(
            title = if (existing != null) {
                if (isZh) "编辑世界书条目" else "Edit Worldbook Entry"
            } else {
                strings.novel.loreAddBtn
            },
            backLabel = if (isZh) "世界设定" else "Back",
            onBack = onBack,
            trailing = {
                GuButton(
                    onClick = ::commitSave,
                    enabled = canSave,
                    compact = true,
                ) {
                    Text(strings.common.save, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("character", "location", "item", "faction", "rule", "foreshadow", "plot", "world"), key = { it }) { catKey ->
                    GuPill(
                        label = strings.novel.loreCats[catKey] ?: catKey,
                        selected = cat == catKey,
                        onClick = { cat = catKey },
                    )
                }
            }

            GuCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GuTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = if (isZh) "词条名称" else "Entry Title",
                        placeholder = strings.novel.loreTitlePlaceholder,
                    )
                    GuTextField(
                        value = keywords,
                        onValueChange = { keywords = it },
                        label = if (isZh) "触发关键词" else "Trigger Keywords",
                        placeholder = strings.novel.loreKeywordsPlaceholder,
                    )
                    GuTextField(
                        value = aliases,
                        onValueChange = { aliases = it },
                        label = if (isZh) "别名 / 简称（选填）" else "Aliases (Optional)",
                        placeholder = if (isZh) "别名/简称（逗号分隔，选填）" else "Aliases (comma-separated)",
                    )
                    GuTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = if (isZh) "设定内容" else "Lore Content",
                        placeholder = strings.novel.loreContentPlaceholder,
                        singleLine = false,
                        minLines = 3,
                        maxLines = 8,
                    )
                }
            }

            GuCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(if (isZh) "注入优先级" else "Priority", color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        val pValues = listOf(30, 50, 80, 100)
                        val pLabels = if (isZh) listOf("低", "中", "高", "核心") else listOf("Low", "Mid", "High", "Core")
                        GuSegmentedControl(
                            items = pLabels,
                            selectedIndex = pValues.indexOfFirst { it >= priority }.takeIf { it >= 0 } ?: 1,
                            onSelect = { idx -> priority = pValues[idx] },
                            compact = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = c.border.copy(alpha = 0.5f))
                    GuSwitchRow(
                        label = strings.novel.loreAlways,
                        checked = always,
                        onCheckedChange = { always = it },
                    )
                }
            }

            if (existing != null) {
                if (confirmDelete) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.dangerBg)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isZh) "确认永久删除「${existing.title}」？" else "Delete \"${existing.title}\"?",
                            color = c.danger,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { confirmDelete = false }) {
                            Text(strings.common.cancel, color = c.muted, fontSize = 12.sp)
                        }
                        GuButton(
                            onClick = { onDelete(existing.id) },
                            compact = true,
                            variant = GuButtonVariant.DANGER,
                        ) {
                            Text(strings.common.delete, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    TextButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = c.danger, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isZh) "删除此词条" else "Delete Entry", color = c.danger, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveAccordionCard(
    title: String,
    preview: String,
    emptyText: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val c = LocalAppTheme.current.colors
    GuCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = title,
                        color = c.ink,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (!expanded) {
                        Text(
                            text = preview.trim().replace('\n', ' ').ifBlank { emptyText },
                            color = c.muted,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = c.muted,
                    modifier = Modifier.size(18.dp),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun CompactTogglePill(
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    val trackColor = if (checked) c.primary else c.surfaceSoft
    val thumbOffset = if (checked) 14.dp else 2.dp
    Box(
        modifier = Modifier
            .width(34.dp)
            .height(20.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(trackColor)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(16.dp)
                .clip(CircleShape)
                .background(if (checked) Color.White else if (theme.isDark) Color(0xFFB4C0D6) else Color(0xFF94A3B8)),
        )
    }
}

private fun localizeChapterStatus(status: String, isZh: Boolean): String = when (status.lowercase()) {
    "writing", "in_progress" -> if (isZh) "连载中" else "Writing"
    "done", "completed", "finished" -> if (isZh) "已完成" else "Done"
    "draft" -> if (isZh) "草稿" else "Draft"
    else -> status
}

private fun formatStudioCount(value: Int): String =
    com.gugachat.app.core.util.FormatUtils.formatCommaCount(value)

private fun copyTextToClipboard(ctx: Context, label: String, text: String) {
    com.gugachat.app.core.util.ClipboardUtils.copyText(ctx, label, text)
}
