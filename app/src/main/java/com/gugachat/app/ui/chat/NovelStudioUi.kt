package com.gugachat.app.ui.chat

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import coil.compose.rememberAsyncImagePainter
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.storage.LocalMediaPathResolver
import com.gugachat.app.core.theme.GuAccordionCard
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuCompactSwitch
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuLinearProgressBar
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuModalBottomSheet
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuSegmentedControl
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuSwitchRow
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.ChatParams
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.NovelChapter
import com.gugachat.app.domain.chat.NovelConfig
import com.gugachat.app.domain.chat.NovelDraftOption
import com.gugachat.app.domain.chat.NovelExport
import com.gugachat.app.domain.chat.NovelLoreEntry
import com.gugachat.app.domain.chat.NovelPresets
import com.gugachat.app.domain.chat.TimelineReconciler

internal data class NovelGenreVisual(
    val startColor: Color,
    val endColor: Color,
    val icon: ImageVector,
)

internal sealed interface NovelStudioSubPage {
    data class ChapterDetail(val chapterId: String) : NovelStudioSubPage
    data object FinishChapter : NovelStudioSubPage
    data class LoreEditor(val loreId: String?) : NovelStudioSubPage
}

internal val STUDIO_TARGET_WORD_STEPS = listOf(1500, 2500, 4000, 6000)
internal val ROMAN_BRANCH_NUMERALS = listOf("Ⅰ", "Ⅱ", "Ⅲ", "Ⅳ")

internal fun resolveNovelGenreVisual(presetId: String): NovelGenreVisual = when (presetId.lowercase()) {
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
    GuModalBottomSheet(
        onDismissRequest = { if (!isCreatingWithAi) onDismiss() },
        sheetState = sheetState,
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
    isGeneratingCover: Boolean = false,
    extraActions: NovelStudioExtraActions? = null,
    params: ChatParams = ChatParams(),
    onUpdateMinOutput: ((minChars: Int?, autoContinue: Boolean) -> Unit)? = null,
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

    val exportTxtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) onExportTxt(uri, exportIncludeLore)
    }
    val exportMdLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) onExportMd(uri, exportIncludeLore)
    }
    val exportEpubLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/epub+zip")) { uri ->
        if (uri != null) extraActions?.onExportEpub?.invoke(uri, exportIncludeLore)
    }
    val pickCoverLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) extraActions?.onBindCoverImage?.invoke(uri)
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
    GuModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
                                    val sortedChapters = remember(chapters) { chapters.sortedBy { it.order } }
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
                                                    GuLinearProgressBar(
                                                        progress = pct,
                                                        height = 5.dp,
                                                        color = if (pct >= 1f) c.success else c.primary,
                                                        trackColor = c.border.copy(alpha = 0.4f),
                                                        modifier = Modifier.fillMaxWidth(),
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

                                        items(sortedChapters, key = { it.id }) { ch ->
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
                                                        GuLinearProgressBar(
                                                            progress = chProgress,
                                                            height = 2.5.dp,
                                                            color = if (ch.status == "done" || chProgress >= 1f) c.success else c.primary,
                                                            trackColor = c.border.copy(alpha = 0.35f),
                                                            modifier = Modifier.fillMaxWidth(),
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
                                    NovelWorldbookTab(
                                        novel = novel,
                                        lore = lore,
                                        triggeredLoreIds = triggeredLoreIds,
                                        hasAssistantProse = hasAssistantProse,
                                        isExtractingLore = isExtractingLore,
                                        onOpenLoreEditor = { id -> subPage = NovelStudioSubPage.LoreEditor(id) },
                                        onLaunchImportLorebook = { importLoreLauncher.launch(arrayOf("application/json", "*/*")) },
                                        onExtractLoreNow = onExtractLoreNow,
                                        onToggleLoreEnabled = onToggleLoreEnabled,
                                        onToggleLoreAlwaysActive = onToggleLoreAlwaysActive,
                                        onDeleteLore = onDeleteLore,
                                    )
                                }

                                // ==================== TAB 2: AI CRAFT DESK ====================
                                2 -> {
                                    NovelCraftDeskTab(
                                        novel = novel,
                                        lore = lore,
                                        draftOptions = draftOptions,
                                        outlineReply = outlineReply,
                                        polishResult = polishResult,
                                        latestAssistantProse = latestAssistantProse,
                                        hasAssistantProse = hasAssistantProse,
                                        isBusy = isBusy,
                                        isSyncingChapters = isSyncingChapters,
                                        onGenerate3Paths = onGenerate3Paths,
                                        onAdoptDraft = onAdoptDraft,
                                        onOutlineChat = onOutlineChat,
                                        onApplyOutlineToBook = onApplyOutlineToBook,
                                        onSyncChaptersFromOutline = onSyncChaptersFromOutline,
                                        onPolishLast = onPolishLast,
                                        onApplyPolishAsVariant = onApplyPolishAsVariant,
                                        onFillComposerDraft = onFillComposerDraft,
                                        onSaveForeshadowToLore = onSaveForeshadowToLore,
                                        onToast = onToast,
                                        onDismiss = onDismiss,
                                    )
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
                                                                onClick = { extraActions?.onGenerateCoverArt?.invoke() },
                                                                enabled = extraActions != null && !isGeneratingCover,
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
                                                                enabled = extraActions != null,
                                                                compact = true,
                                                                variant = GuButtonVariant.SUBTLE,
                                                            ) {
                                                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(13.dp))
                                                                Spacer(Modifier.width(4.dp))
                                                                Text(if (isZh) "相册选图" else "Pick", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                            if (coverFile != null) {
                                                                GuButton(
                                                                    onClick = { extraActions?.onClearCoverImage?.invoke() },
                                                                    enabled = extraActions != null,
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

                                                if (onUpdateMinOutput != null) {
                                                    CompactOutputLengthControl(
                                                        params = params,
                                                        mode = ConversationMode.novel,
                                                        isZh = isZh,
                                                        title = if (isZh) "单次最低输出字数" else "Min Output per Turn",
                                                        subtitle = if (isZh) {
                                                            "单次生成下限（与上方整章累计目标独立）"
                                                        } else {
                                                            "Minimum per reply (separate from chapter total target)"
                                                        },
                                                        onUpdateMinOutput = onUpdateMinOutput,
                                                    )
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
                                                    enabled = extraActions != null,
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
internal fun ArchiveAccordionCard(
    title: String,
    preview: String,
    emptyText: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    GuAccordionCard(
        title = title,
        expanded = expanded,
        onToggle = onToggle,
        preview = preview,
        emptyText = emptyText,
        content = { content() },
    )
}

@Composable
internal fun CompactTogglePill(
    checked: Boolean,
    onToggle: () -> Unit,
) {
    GuCompactSwitch(
        checked = checked,
        onCheckedChange = { onToggle() },
    )
}

internal fun localizeChapterStatus(status: String, isZh: Boolean): String = when (status.lowercase()) {
    "writing", "in_progress" -> if (isZh) "连载中" else "Writing"
    "done", "completed", "finished" -> if (isZh) "已完成" else "Done"
    "draft" -> if (isZh) "草稿" else "Draft"
    else -> status
}

internal fun formatStudioCount(value: Int): String =
    com.gugachat.app.core.util.FormatUtils.formatCommaCount(value)

internal fun copyTextToClipboard(ctx: Context, label: String, text: String) {
    com.gugachat.app.core.util.ClipboardUtils.copyText(ctx, label, text)
}
