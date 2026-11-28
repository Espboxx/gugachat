package com.gugachat.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuCompactSearchBar
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.core.util.FuzzyMatcher
import com.gugachat.app.domain.chat.NovelConfig
import com.gugachat.app.domain.chat.NovelDraftOption
import com.gugachat.app.domain.chat.NovelEngine
import com.gugachat.app.domain.chat.NovelLoreEntry

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.NovelWorldbookTab(
    novel: NovelConfig,
    lore: List<NovelLoreEntry>,
    triggeredLoreIds: Set<String>,
    hasAssistantProse: Boolean,
    isExtractingLore: Boolean,
    onOpenLoreEditor: (String?) -> Unit,
    onLaunchImportLorebook: () -> Unit,
    onExtractLoreNow: () -> Unit,
    onToggleLoreEnabled: (String) -> Unit,
    onToggleLoreAlwaysActive: (String) -> Unit,
    onDeleteLore: (String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH

    var loreSearch by remember { mutableStateOf("") }
    var loreFilterCat by remember { mutableStateOf("all") }
    var expandedLoreId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteLoreId by remember { mutableStateOf<String?>(null) }

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
                    onClick = { onOpenLoreEditor(null) },
                    compact = true,
                    variant = GuButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.novel.loreAddBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                GuButton(
                    onClick = onLaunchImportLorebook,
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
                                            onClick = { onOpenLoreEditor(entry.id) },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.NovelCraftDeskTab(
    novel: NovelConfig,
    lore: List<NovelLoreEntry>,
    draftOptions: List<NovelDraftOption>,
    outlineReply: String,
    polishResult: String,
    latestAssistantProse: String,
    hasAssistantProse: Boolean,
    isBusy: Boolean,
    isSyncingChapters: Boolean,
    onGenerate3Paths: (String) -> Unit,
    onAdoptDraft: (NovelDraftOption, Boolean) -> Unit,
    onOutlineChat: (String) -> Unit,
    onApplyOutlineToBook: (String) -> Unit,
    onSyncChaptersFromOutline: (String) -> Unit,
    onPolishLast: (String, String?) -> Unit,
    onApplyPolishAsVariant: (String) -> Unit,
    onFillComposerDraft: (String) -> Unit,
    onSaveForeshadowToLore: (String) -> Unit,
    onToast: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val ctx = LocalContext.current
    val isZh = strings.language == AppLanguage.ZH

    var outlinePrompt by remember { mutableStateOf("") }
    var polishInstruction by remember { mutableStateOf("") }
    var draftHint by remember { mutableStateOf("") }
    var showFullPolishSource by remember { mutableStateOf(false) }

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
