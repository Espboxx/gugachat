package com.gugachat.app.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuPill
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
    val activeCh = chapters.firstOrNull { it.id == novel.activeChapterId } ?: chapters.lastOrNull()
    val hud = novel.currentStatus

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.accentSoft)
            .border(1.dp, c.border)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${novel.title} · ${activeCh?.title ?: strings.novel.chapterLabel}",
                color = c.ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = buildString {
                if (hud != null) {
                    if (hud.location.isNotBlank()) append("📍${hud.location} ")
                    if (hud.time.isNotBlank()) append("🕒${hud.time} ")
                    if (hud.conflict.isNotBlank()) append("⚡${hud.conflict}")
                } else {
                    append("${activeCh?.wordCount ?: 0} 字 · ${novel.genre}")
                }
            }
            Text(sub, color = c.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        GuButton(onClick = onEnterPureReading, compact = true, variant = GuButtonVariant.SUBTLE) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = strings.novel.pureReading, modifier = Modifier.size(14.dp))
        }
        GuButton(onClick = onOpenStudio, compact = true, variant = GuButtonVariant.HONEY) {
            Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(4.dp))
            Text(strings.novel.studioBtn, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.page,
        title = { Text(strings.novel.wizardTitle, color = c.ink, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(strings.novel.wizardGenreStep, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NovelPresets.ALL.forEach { p ->
                        GuPill(
                            label = strings.novel.genres[p.id] ?: p.name,
                            selected = selectedPreset == p.id,
                            onClick = { selectedPreset = p.id },
                            useHoneySelected = true,
                        )
                    }
                }
                val activePreset = NovelPresets.findById(selectedPreset)
                Text(activePreset.synopsis, color = c.muted, fontSize = 11.sp)

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
        },
        confirmButton = {
            GuButton(onClick = { onCreate(selectedPreset, bookTitle, idea); onDismiss() }, compact = true) {
                Text(strings.novel.wizardEmptyBtn, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.common.cancel, color = c.muted)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

    val exportTxtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) onExportTxt(uri)
    }
    val exportMdLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) onExportMd(uri)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.page) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("${strings.novel.studioTitle} · ${novel.title}", color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GuPill(strings.novel.tabChapters, tab == 0, { tab = 0 })
                GuPill(strings.novel.tabLore, tab == 1, { tab = 1 })
                GuPill(strings.novel.outlineChatBtn, tab == 2, { tab = 2 }, useHoneySelected = true)
                GuPill(strings.novel.tabExport, tab == 3, { tab = 3 })
            }

            when (tab) {
                0 -> {
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
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                    GuTextField(newChapOutline, { newChapOutline = it }, placeholder = strings.novel.chapterOutlinePlaceholder)

                    LazyColumn(modifier = Modifier.fillMaxWidth().height(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(chapters, key = { it.id }) { ch ->
                            val isActive = ch.id == novel.activeChapterId
                            GuCard(
                                modifier = Modifier.fillMaxWidth(),
                                backgroundColor = if (isActive) c.primarySoft else c.surface,
                                onClick = { onSelectChapter(ch.id) },
                            ) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(ch.title, color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                    GuBadge("${ch.wordCount}字 · ${ch.status}", variant = if (isActive) GuBadgeVariant.BLUE else GuBadgeVariant.NEUTRAL)
                                }
                                if (ch.outline.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(ch.outline, color = c.muted, fontSize = 11.sp, maxLines = 2)
                                }
                            }
                        }
                    }
                }

                1 -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("character", "location", "item", "faction", "rule").forEach { cat ->
                            GuPill(strings.novel.loreCats[cat] ?: cat, loreCat == cat, { loreCat = cat })
                        }
                    }
                    GuTextField(loreTitle, { loreTitle = it }, placeholder = strings.novel.loreTitlePlaceholder)
                    GuTextField(loreKeywords, { loreKeywords = it }, placeholder = strings.novel.loreKeywordsPlaceholder)
                    GuTextField(loreContent, { loreContent = it }, placeholder = strings.novel.loreContentPlaceholder, singleLine = false, minLines = 2)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = loreAlways, onCheckedChange = { loreAlways = it })
                            Text(strings.novel.loreAlways, color = c.ink, fontSize = 12.sp)
                        }
                        GuButton(
                            onClick = {
                                onAddLore(loreCat, loreTitle, loreKeywords, loreContent, loreAlways)
                                loreTitle = ""
                                loreKeywords = ""
                                loreContent = ""
                            },
                            compact = true,
                        ) {
                            Text(strings.novel.loreAddBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (lore.isEmpty()) {
                        GuEmptyState(title = strings.novel.loreEmpty)
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().height(200.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(lore, key = { it.id }) { entry ->
                                GuCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        GuBadge(entry.category, variant = GuBadgeVariant.HONEY)
                                        Spacer(Modifier.size(6.dp))
                                        Text(entry.title, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        if (entry.alwaysActive) GuBadge(strings.novel.loreAlways, variant = GuBadgeVariant.BLUE)
                                        IconButton(onClick = { onDeleteLore(entry.id) }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.Delete, contentDescription = null, tint = c.danger, modifier = Modifier.size(15.dp))
                                        }
                                    }
                                    Text(entry.content, color = c.muted, fontSize = 11.sp, maxLines = 2)
                                }
                            }
                        }
                    }
                }

                2 -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().height(340.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GuCard(modifier = Modifier.fillMaxWidth()) {
                            Text(strings.novel.threePathsBtn, color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                GuTextField(draftHint, { draftHint = it }, placeholder = "分歧提示词（可选）", modifier = Modifier.weight(1f))
                                GuButton(onClick = { onGenerate3Paths(draftHint) }, loading = isBusy, compact = true, variant = GuButtonVariant.HONEY) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            }
                            draftOptions.forEach { opt ->
                                Spacer(Modifier.height(8.dp))
                                GuCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { onAdoptDraft(opt.opening.ifBlank { opt.summary }); onDismiss() },
                                ) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text(opt.title, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        GuBadge(opt.tone, variant = GuBadgeVariant.BLUE)
                                    }
                                    Text(opt.summary, color = c.muted, fontSize = 11.sp)
                                    if (opt.opening.isNotBlank()) {
                                        Text(opt.opening, color = c.ink, fontSize = 12.sp, maxLines = 3)
                                    }
                                }
                            }
                        }

                        GuCard(modifier = Modifier.fillMaxWidth()) {
                            Text(strings.novel.outlineChatTitle, color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                GuTextField(outlinePrompt, { outlinePrompt = it }, placeholder = strings.novel.outlineChatPlaceholder, modifier = Modifier.weight(1f))
                                GuButton(onClick = { onOutlineChat(outlinePrompt) }, loading = isBusy, compact = true) {
                                    Text(strings.chat.send, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (outlineReply.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(outlineReply, color = c.ink, fontSize = 12.sp)
                            }
                        }

                        GuCard(modifier = Modifier.fillMaxWidth()) {
                            Text(strings.novel.polishDescribe, color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                GuTextField(polishInstruction, { polishInstruction = it }, placeholder = "增强画面细节 / 去AI味", modifier = Modifier.weight(1f))
                                GuButton(onClick = { onPolishLast(polishInstruction) }, loading = isBusy, compact = true, variant = GuButtonVariant.SUBTLE) {
                                    Text(strings.novel.polishDeai, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (polishResult.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(polishResult, color = c.ink, fontSize = 12.sp)
                            }
                        }
                    }
                }

                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        GuCard(modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Text(strings.novel.hudToggle, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Switch(checked = novel.enableStatusHud, onCheckedChange = { v -> onUpdateConfig { it.copy(enableStatusHud = v) } })
                            }
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Text(strings.novel.autoSyncLoreToggle, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Switch(checked = novel.autoSyncLore, onCheckedChange = { v -> onUpdateConfig { it.copy(autoSyncLore = v) } })
                            }
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Text(strings.novel.autoAdvanceChapterToggle, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Switch(checked = novel.autoAdvanceChapter, onCheckedChange = { v -> onUpdateConfig { it.copy(autoAdvanceChapter = v) } })
                            }
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Text(strings.novel.choicesToggle, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Switch(checked = novel.enableChoices, onCheckedChange = { v -> onUpdateConfig { it.copy(enableChoices = v) } })
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
            Spacer(Modifier.height(14.dp))
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
    val (bg, ink) = when (paper) {
        "parchment" -> Color(0xFFF3E6C8) to Color(0xFF3E2C1C)
        "night" -> Color(0xFF141820) to Color(0xFFD6DCE5)
        else -> Color(0xFFFDFBF7) to Color(0xFF231F20)
    }

    val assistantProse = remember(messages) {
        TimelineReconciler.activeMessages(messages)
            .filter { it.role == "assistant" }
            .mapNotNull { NovelExport.stripInternalTags(it.content).takeIf { txt -> txt.isNotBlank() } }
    }

    Dialog(
        onDismissRequest = onExit,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(modifier = Modifier.fillMaxSize().background(bg)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(novel.title, color = ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1)
                GuPill(strings.novel.paperCream, paper == "cream", { onPaperChange("cream") })
                GuPill(strings.novel.paperParchment, paper == "parchment", { onPaperChange("parchment") })
                GuPill(strings.novel.paperNight, paper == "night", { onPaperChange("night") })
                IconButton(onClick = onExit) {
                    Icon(Icons.Default.Close, contentDescription = strings.novel.exitPureReading, tint = ink)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("A-", color = ink, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onFontSizeChange((fontSizeSp - 1).coerceAtLeast(13)) }.padding(6.dp))
                Text("${fontSizeSp}sp", color = ink, fontSize = 12.sp)
                Text("A+", color = ink, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onFontSizeChange((fontSizeSp + 1).coerceAtMost(24)) }.padding(6.dp))
                Spacer(Modifier.weight(1f))
                GuPill(strings.novel.serifToggle, useSerif, { onSerifToggle(!useSerif) })
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(assistantProse) { chunk ->
                    MarkdownMessageText(
                        markdown = chunk,
                        textColor = ink,
                        fontSize = fontSizeSp.sp,
                        lineHeight = (fontSizeSp * 1.65f).sp,
                        useSerif = useSerif,
                    )
                }
            }
        }
    }
}
