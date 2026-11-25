package com.gugachat.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import com.gugachat.app.core.theme.GuPill
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

@Composable
fun NovelStatusHudBar(
    novel: NovelConfig,
    chapters: List<NovelChapter>,
    onOpenStudio: () -> Unit,
    onEnterPureReading: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    val activeCh = chapters.firstOrNull { it.id == novel.activeChapterId } ?: chapters.lastOrNull()
    val hud = novel.currentStatus
    var expanded by remember(novel.title, novel.activeChapterId) { mutableStateOf(false) }

    val wordLabel = if (isZh) "${activeCh?.wordCount ?: 0} 字" else "${activeCh?.wordCount ?: 0} words"
    val summarySub = hud?.location?.takeIf { it.isNotBlank() }?.let { "📍$it" } ?: wordLabel
    val detailLine = buildString {
        if (hud != null) {
            if (hud.location.isNotBlank()) append("📍${hud.location}  ")
            if (hud.time.isNotBlank()) append("🕒${hud.time}  ")
            if (hud.conflict.isNotBlank()) append("⚡${hud.conflict}")
        }
        if (isEmpty()) {
            append(wordLabel)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surfaceSoft.copy(alpha = 0.78f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 38.dp)
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (expanded) {
                    "📖 ${activeCh?.title ?: novel.title}"
                } else {
                    "📖 ${activeCh?.title ?: novel.title} · $summarySub"
                },
                color = if (expanded) c.ink else c.muted,
                fontSize = 11.5.sp,
                fontWeight = if (expanded) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!expanded) {
                Text(
                    text = strings.novel.studioBtn,
                    color = c.accent,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenStudio)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
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
                }
                Text(detailLine, color = c.muted, fontSize = 11.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GuButton(onClick = onEnterPureReading, compact = true, variant = GuButtonVariant.SUBTLE) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(strings.novel.pureReading, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    GuButton(onClick = onOpenStudio, compact = true, variant = GuButtonVariant.HONEY) {
                        Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(strings.novel.studioTitle, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        HorizontalDivider(color = c.border.copy(alpha = 0.6f))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NovelWizardDialog(
    onDismiss: () -> Unit,
    onCreate: (presetId: String, title: String, idea: String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    var selectedPreset by remember { mutableStateOf(NovelPresets.ALL.first().id) }
    var bookTitle by remember { mutableStateOf("") }
    var idea by remember { mutableStateOf("") }

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
                .imePadding()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = strings.novel.wizardTitle,
                onClose = onDismiss,
                closeContentDescription = strings.common.close,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(strings.novel.wizardGenreStep, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NovelPresets.ALL.forEach { p ->
                        GuPill(
                            label = strings.novel.genres[p.id] ?: p.name,
                            selected = selectedPreset == p.id,
                            onClick = { selectedPreset = p.id },
                        )
                    }
                }

                val activePreset = NovelPresets.findById(selectedPreset)
                GuCard(modifier = Modifier.fillMaxWidth(), backgroundColor = c.surfaceSoft) {
                    Text(activePreset.synopsis, color = c.muted, fontSize = 12.5.sp, lineHeight = 18.sp)
                }

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

            HorizontalDivider(color = c.border.copy(alpha = 0.7f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(strings.common.cancel, color = c.muted, fontWeight = FontWeight.SemiBold)
                }
                GuButton(
                    onClick = { onCreate(selectedPreset, bookTitle, idea); onDismiss() },
                    modifier = Modifier.weight(1f),
                ) {
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
    val c = LocalAppTheme.current.colors
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

    val tabs = remember(strings) {
        listOf(
            0 to strings.novel.tabChapters,
            1 to strings.novel.tabLore,
            2 to strings.novel.outlineChatBtn,
            3 to strings.novel.tabExport,
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
                .fillMaxHeight(0.84f)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = strings.novel.studioTitle,
                subtitle = novel.title,
                onClose = onDismiss,
                closeContentDescription = strings.common.close,
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tabs, key = { it.first }) { (idx, label) ->
                    GuPill(
                        label = label,
                        selected = tab == idx,
                        onClick = { tab = idx },
                        useHoneySelected = false,
                    )
                }
            }

            when (tab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "__chapter_form__") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    GuTextField(newChapTitle, { newChapTitle = it }, placeholder = strings.novel.newChapterBtn, modifier = Modifier.weight(1f))
                                    GuButton(
                                        onClick = {
                                            onAddChapter(newChapTitle, newChapOutline)
                                            newChapTitle = ""
                                            newChapOutline = ""
                                        },
                                        compact = true,
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = strings.novel.newChapterBtn, modifier = Modifier.size(18.dp))
                                    }
                                }
                                GuTextField(newChapOutline, { newChapOutline = it }, placeholder = strings.novel.chapterOutlinePlaceholder)
                            }
                        }

                        items(chapters, key = { it.id }) { ch ->
                            val isActive = ch.id == novel.activeChapterId
                            val statusText = localizeChapterStatus(ch.status, isZh)
                            val badgeText = if (isZh) "${ch.wordCount} 字 · $statusText" else "${ch.wordCount}w · $statusText"
                            GuCard(
                                modifier = Modifier.fillMaxWidth(),
                                backgroundColor = if (isActive) c.primarySoft else c.surface,
                                borderColor = if (isActive) c.primary.copy(alpha = 0.5f) else c.border,
                                onClick = { onSelectChapter(ch.id) },
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
                                        fontSize = 13.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    GuBadge(badgeText, variant = if (isActive) GuBadgeVariant.BLUE else GuBadgeVariant.NEUTRAL)
                                }
                                if (ch.outline.isNotBlank()) {
                                    Spacer(Modifier.height(5.dp))
                                    Text(ch.outline, color = c.muted, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "__lore_form__") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(listOf("character", "location", "item", "faction", "rule"), key = { it }) { cat ->
                                        GuPill(strings.novel.loreCats[cat] ?: cat, loreCat == cat, { loreCat = cat })
                                    }
                                }
                                GuTextField(loreTitle, { loreTitle = it }, placeholder = strings.novel.loreTitlePlaceholder)
                                GuTextField(loreKeywords, { loreKeywords = it }, placeholder = strings.novel.loreKeywordsPlaceholder)
                                GuTextField(loreContent, { loreContent = it }, placeholder = strings.novel.loreContentPlaceholder, singleLine = false, minLines = 2, maxLines = 4)
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

                        if (lore.isEmpty()) {
                            item(key = "__lore_empty__") {
                                GuEmptyState(title = strings.novel.loreEmpty)
                            }
                        } else {
                            items(lore, key = { it.id }) { entry ->
                                GuCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        GuBadge(strings.novel.loreCats[entry.category] ?: entry.category, variant = GuBadgeVariant.HONEY)
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
                                        IconButton(onClick = { onDeleteLore(entry.id) }, modifier = Modifier.size(36.dp)) {
                                            Icon(Icons.Default.Delete, contentDescription = strings.common.delete, tint = c.danger, modifier = Modifier.size(17.dp))
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(entry.content, color = c.muted, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
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
                            .padding(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        GuCard(modifier = Modifier.fillMaxWidth()) {
                            Text(strings.novel.threePathsBtn, color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = strings.novel.threePathsBtn, modifier = Modifier.size(16.dp))
                                }
                            }
                            draftOptions.forEach { opt ->
                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    onClick = { onAdoptDraft(opt.opening.ifBlank { opt.summary }); onDismiss() },
                                    shape = RoundedCornerShape(12.dp),
                                    color = c.surfaceSoft,
                                    border = BorderStroke(1.dp, c.border),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
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
                                        Text(opt.summary, color = c.muted, fontSize = 12.sp, lineHeight = 17.sp)
                                        if (opt.opening.isNotBlank()) {
                                            Text(opt.opening, color = c.ink, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }

                        GuCard(modifier = Modifier.fillMaxWidth()) {
                            Text(strings.novel.outlineChatTitle, color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                ) {
                                    Text(strings.chat.send, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (outlineReply.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(outlineReply, color = c.ink, fontSize = 12.5.sp, lineHeight = 19.sp)
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                        GuCard(modifier = Modifier.fillMaxWidth()) {
                            Text(strings.novel.polishDescribe, color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                ) {
                                    Text(strings.novel.polishDeai, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (polishResult.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(polishResult, color = c.ink, fontSize = 12.5.sp, lineHeight = 19.sp)
                                Spacer(Modifier.height(6.dp))
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
                                        onClick = { onAdoptDraft(polishResult); onDismiss() },
                                        compact = true,
                                    ) {
                                        Text(if (isZh) "填入输入框" else "Use as Draft", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
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
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        GuCard(modifier = Modifier.fillMaxWidth()) {
                            GuSwitchRow(
                                label = strings.novel.hudToggle,
                                checked = novel.enableStatusHud,
                                onCheckedChange = { v -> onUpdateConfig { it.copy(enableStatusHud = v) } },
                            )
                            HorizontalDivider(color = c.border.copy(alpha = 0.6f))
                            GuSwitchRow(
                                label = strings.novel.autoSyncLoreToggle,
                                checked = novel.autoSyncLore,
                                onCheckedChange = { v -> onUpdateConfig { it.copy(autoSyncLore = v) } },
                            )
                            HorizontalDivider(color = c.border.copy(alpha = 0.6f))
                            GuSwitchRow(
                                label = strings.novel.autoAdvanceChapterToggle,
                                checked = novel.autoAdvanceChapter,
                                onCheckedChange = { v -> onUpdateConfig { it.copy(autoAdvanceChapter = v) } },
                            )
                            HorizontalDivider(color = c.border.copy(alpha = 0.6f))
                            GuSwitchRow(
                                label = strings.novel.choicesToggle,
                                checked = novel.enableChoices,
                                onCheckedChange = { v -> onUpdateConfig { it.copy(enableChoices = v) } },
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            GuButton(
                                onClick = { exportTxtLauncher.launch(NovelExport.sanitizeFileName(novel.title, "txt")) },
                                variant = GuButtonVariant.SECONDARY,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(strings.novel.exportTxt, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            GuButton(
                                onClick = { exportMdLauncher.launch(NovelExport.sanitizeFileName(novel.title, "md")) },
                                variant = GuButtonVariant.HONEY,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(strings.novel.exportMd, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
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

    val readerColors = remember(paper) {
        when (paper) {
            "parchment" -> ReaderPaperTheme(
                bg = Color(0xFFF3E6C8),
                surface = Color(0xFFEAD9B4),
                ink = Color(0xFF3E2C1C),
                muted = Color(0xFF6F563E),
                accent = Color(0xFFB45309),
                border = Color(0xFFD5BE90),
                codeBg = Color(0xFFE6D4AC),
                codeHeaderBg = Color(0xFFDDC797),
            )
            "night" -> ReaderPaperTheme(
                bg = Color(0xFF141820),
                surface = Color(0xFF1E2533),
                ink = Color(0xFFDCE2EC),
                muted = Color(0xFF8C9BB2),
                accent = Color(0xFF6E8EFF),
                border = Color(0xFF2B3548),
                codeBg = Color(0xFF0E121A),
                codeHeaderBg = Color(0xFF192130),
            )
            else -> ReaderPaperTheme(
                bg = Color(0xFFFDFBF7),
                surface = Color(0xFFF3EFE6),
                ink = Color(0xFF231F20),
                muted = Color(0xFF6B6560),
                accent = Color(0xFF4D6BFE),
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(readerColors.bg)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Top bar: Title + Chapter summary + 44dp Close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = novel.title,
                        color = readerColors.ink,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val activeCh = chapters.firstOrNull { it.id == novel.activeChapterId } ?: chapters.lastOrNull()
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
                IconButton(onClick = onExit, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = strings.novel.exitPureReading,
                        tint = readerColors.ink,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            // Paper-aware reader toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ReaderChip(strings.novel.paperCream, paper == "cream", readerColors) { onPaperChange("cream") }
                ReaderChip(strings.novel.paperParchment, paper == "parchment", readerColors) { onPaperChange("parchment") }
                ReaderChip(strings.novel.paperNight, paper == "night", readerColors) { onPaperChange("night") }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReaderFontStepBtn("A-", readerColors) { onFontSizeChange((fontSizeSp - 1).coerceAtLeast(13)) }
                Text("${fontSizeSp}sp", color = readerColors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                ReaderFontStepBtn("A+", readerColors) { onFontSizeChange((fontSizeSp + 1).coerceAtMost(24)) }
                Spacer(Modifier.weight(1f))
                ReaderChip(strings.novel.serifToggle, useSerif, readerColors) { onSerifToggle(!useSerif) }
            }

            HorizontalDivider(
                color = readerColors.border,
                modifier = Modifier.padding(top = 6.dp),
            )

            if (assistantProse.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = readerColors.muted,
                            modifier = Modifier.size(40.dp),
                        )
                        Text(
                            text = if (isZh) "本书尚无正文内容" else "No prose written yet",
                            color = readerColors.ink,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = if (isZh) "返回对话窗口发送剧情指令，AI 续写的正文将自动排版在这里。" else "Return to the chat workspace to write your first chapter.",
                            color = readerColors.muted,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(assistantProse) { chunk ->
                        MarkdownMessageText(
                            markdown = chunk,
                            textColor = readerColors.ink,
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.68f).sp,
                            useSerif = useSerif,
                            palette = mdPalette,
                        )
                    }
                }
            }
        }
    }
}

private data class ReaderPaperTheme(
    val bg: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val border: Color,
    val codeBg: Color,
    val codeHeaderBg: Color,
)

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
            .heightIn(min = 34.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
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
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = palette.ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
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
