package com.gugachat.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.gugachat.app.core.storage.LocalMediaPathResolver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
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
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuInlineStepper
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuModalBottomSheet
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuSegmentedControl
import com.gugachat.app.core.theme.GuSettingsRow
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuSwitchRow
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.data.local.ConversationWorldbookCrossRef
import com.gugachat.app.data.local.WorldbookEntity
import com.gugachat.app.domain.chat.ChatParams
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.NovelLoreEntry
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpPlayStyle
import com.gugachat.app.domain.chat.WorldbookEngine
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RpWorldAndLoreSection(
    rp: RpConfig,
    lore: List<NovelLoreEntry>,
    triggeredLoreIds: Set<String>,
    isGeneratingArt: Boolean,
    modifier: Modifier = Modifier,
    worldbooks: List<WorldbookEntity> = emptyList(),
    mountedWorldbookRefs: List<ConversationWorldbookCrossRef> = emptyList(),
    assetActions: RpStudioAssetActions? = null,
    onSetPlayStyle: (RpPlayStyle) -> Unit,
    onUpdateConfig: ((RpConfig) -> RpConfig) -> Unit,
    onPickSceneImage: () -> Unit,
    onClearSceneImage: () -> Unit,
    onGenerateSceneCg: () -> Unit,
    onExportCardPng: () -> Unit,
    onExportCardJson: () -> Unit,
    onAddLore: () -> Unit,
    onEditLore: (NovelLoreEntry) -> Unit,
    onImportLorebookOrPreset: () -> Unit,
    onToggleLoreEnabled: (String) -> Unit,
    onToggleLoreAlwaysActive: (String) -> Unit,
    onDeleteLore: (String) -> Unit,
    params: ChatParams = ChatParams(),
    onUpdateMinOutput: ((minChars: Int?, autoContinue: Boolean) -> Unit)? = null,
    studioArtModelState: StudioArtModelUiState = StudioArtModelUiState(),
    onConfigureArtModel: (() -> Unit)? = null,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val ctx = LocalContext.current
    val isZh = strings.language == AppLanguage.ZH

    var searchQuery by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf("all") }
    var expandedLoreId by remember { mutableStateOf<String?>(null) }
    var confirmDeleteLoreId by remember { mutableStateOf<String?>(null) }
    var worldSectionExpanded by remember { mutableStateOf(false) }
    var showWorldbookPicker by remember { mutableStateOf(false) }

    val allWorldbooks = worldbooks
    val mountedRefs = mountedWorldbookRefs
    val mountedWorldbooks = remember(allWorldbooks, mountedRefs) {
        val byId = allWorldbooks.associateBy { it.id }
        mountedRefs.sortedBy { it.sortOrder }.mapNotNull { ref -> byId[ref.worldbookId]?.let { ref to it } }
    }
    val globalWorldbooks = remember(allWorldbooks, mountedRefs) {
        val mountedIds = mountedRefs.map { it.worldbookId }.toSet()
        allWorldbooks.filter { it.globalEnabled && it.id !in mountedIds }
    }

    val playStyles = remember { listOf(RpPlayStyle.chengguang, RpPlayStyle.tavern, RpPlayStyle.trpg) }
    val playStyleLabels = remember(strings) { playStyles.map { rpPlayStyleLabel(it, strings.rp) } }

    var worldview by remember(rp.worldview) { mutableStateOf(rp.worldview) }
    var systemRules by remember(rp.systemRules) { mutableStateOf(rp.systemRules) }
    var narratorStyle by remember(rp.narratorStyle) { mutableStateOf(rp.narratorStyle) }
    var authorNote by remember(rp.authorNote) { mutableStateOf(rp.authorNote) }
    var postHistory by remember(rp.postHistoryInstructions) { mutableStateOf(rp.postHistoryInstructions) }
    var mesExample by remember(rp.mesExample) { mutableStateOf(rp.mesExample) }
    val hasWorldUnsaved = worldview != rp.worldview ||
        systemRules != rp.systemRules ||
        narratorStyle != rp.narratorStyle ||
        authorNote != rp.authorNote ||
        postHistory != rp.postHistoryInstructions ||
        mesExample != rp.mesExample

    val sceneFile = remember(rp.state.sceneImagePath) {
        LocalMediaPathResolver.resolveFile(ctx.applicationContext.filesDir.absolutePath, rp.state.sceneImagePath)
            ?.takeIf { it.isFile }
    }

    val filteredLore = remember(lore, searchQuery, selectedCat) {
        val q = searchQuery.trim().lowercase()
        lore.filter { entry ->
            val catMatch = selectedCat == "all" || entry.category.equals(selectedCat, ignoreCase = true)
            val qMatch = q.isEmpty() ||
                entry.title.lowercase().contains(q) ||
                entry.content.lowercase().contains(q) ||
                entry.keywords.any { it.lowercase().contains(q) }
            catMatch && qMatch
        }
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 0. Multi-Worldbook Mount Card (Global & Session Worldbooks)
        if (assetActions != null && (allWorldbooks.isNotEmpty() || mountedWorldbooks.isNotEmpty())) {
            item(key = "__mounted_worldbooks__") {
                GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isZh) "挂载世界书" else "Mounted Worldbooks",
                                    color = c.ink,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                GuMetaText(
                                    if (isZh) "点击切换启用状态，× 卸载" else "Tap to toggle enabled state, × to unmount",
                                )
                            }
                            GuButton(
                                onClick = { showWorldbookPicker = true },
                                compact = true,
                                variant = GuButtonVariant.SUBTLE,
                                modifier = Modifier.heightIn(min = 30.dp),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = if (isZh) "挂载世界书" else "Mount Book",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        if (mountedWorldbooks.isEmpty() && globalWorldbooks.isEmpty()) {
                            GuMetaText(
                                if (isZh) "当前会话尚未挂载全局世界书，点击右上角选择挂载" else "No mounted worldbooks yet; tap Mount Book to attach",
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                mountedWorldbooks.forEach { (ref, book) ->
                                    Surface(
                                        onClick = { assetActions.onToggleMountedWorldbook(book.id) },
                                        shape = RoundedCornerShape(999.dp),
                                        color = if (ref.enabled) c.primarySoft else c.surfaceSoft,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        ) {
                                            Text(
                                                text = "${book.name} (${book.entryCount})",
                                                color = if (ref.enabled) c.primarySoftInk else c.muted,
                                                fontSize = 11.5.sp,
                                                fontWeight = if (ref.enabled) FontWeight.SemiBold else FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = if (isZh) "卸载世界书" else "Unmount",
                                                tint = c.muted,
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clip(CircleShape)
                                                    .clickable { assetActions.onUnmountWorldbook(book.id) },
                                            )
                                        }
                                    }
                                }
                                globalWorldbooks.forEach { book ->
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = c.surfaceSoft,
                                    ) {
                                        Text(
                                            text = "${book.name} · ${if (isZh) "全局" else "Global"}",
                                            color = c.muted,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 1. Worldbook Header + Search + Category Filter (Top Focus)
        item(key = "__wb_controls__") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GuSectionHeader(
                    title = strings.rp.worldbookTab,
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GuButton(
                                onClick = onImportLorebookOrPreset,
                                compact = true,
                                variant = GuButtonVariant.SUBTLE,
                                modifier = Modifier.heightIn(min = 32.dp),
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isZh) "导入" else "Import", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                            GuButton(
                                onClick = onAddLore,
                                compact = true,
                                variant = GuButtonVariant.PRIMARY,
                                modifier = Modifier.heightIn(min = 32.dp),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(3.dp))
                                Text(if (isZh) "新增" else "Add", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    },
                )

                GuCompactSearchBar(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = if (isZh) "搜索词条标题、关键词或设定内容…" else "Search worldbook title, keywords, content...",
                    clearContentDescription = strings.common.clear,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GuPill(
                        label = if (isZh) "全部 (${lore.size})" else "All (${lore.size})",
                        selected = selectedCat == "all",
                        onClick = { selectedCat = "all" },
                        modifier = Modifier.heightIn(min = 30.dp),
                    )
                    RP_WORLDBOOK_CATEGORIES.forEach { cat ->
                        val cnt = lore.count { it.category.equals(cat, ignoreCase = true) }
                        if (cnt > 0 || selectedCat == cat) {
                            GuPill(
                                label = "${localizeRpLoreCategory(cat, strings.novel.loreCats, isZh)} $cnt",
                                selected = selectedCat == cat,
                                onClick = { selectedCat = cat },
                                modifier = Modifier.heightIn(min = 30.dp),
                            )
                        }
                    }
                }
            }
        }

        // High-density 2-line collapsible worldbook rows (5~6 entries per screen)
        if (filteredLore.isEmpty()) {
            item(key = "__wb_empty__") {
                GuEmptyState(
                    title = if (lore.isEmpty()) strings.rp.worldbookEmpty else (if (isZh) "无匹配词条" else "No matching entries"),
                    icon = Icons.Default.Book,
                )
            }
        } else {
            items(filteredLore, key = { it.id }) { entry ->
                val isExpanded = expandedLoreId == entry.id
                val isTriggered = entry.id in triggeredLoreIds
                val catLabel = localizeRpLoreCategory(entry.category, strings.novel.loreCats, isZh)
                val confirmDel = confirmDeleteLoreId == entry.id

                Surface(
                    onClick = { expandedLoreId = if (isExpanded) null else entry.id },
                    shape = RoundedCornerShape(12.dp),
                    color = c.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (entry.enabled) 1f else 0.62f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                if (isTriggered) {
                                    drawRect(
                                        color = c.success,
                                        topLeft = Offset.Zero,
                                        size = Size(3.dp.toPx(), size.height),
                                    )
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // Line 1: [Cat] Title + Constant dot + ~tok + Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            GuBadge(catLabel, variant = GuBadgeVariant.HONEY)
                            Text(
                                text = entry.title,
                                color = c.ink,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (entry.alwaysActive) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(c.primary),
                                )
                            }
                            GuMetaText(text = "~${entry.estimatedTokens.coerceAtLeast(1)}t")
                            Switch(
                                checked = entry.enabled,
                                onCheckedChange = { onToggleLoreEnabled(entry.id) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = c.primary,
                                    uncheckedThumbColor = c.muted,
                                    uncheckedTrackColor = c.surfaceSoft,
                                ),
                                modifier = Modifier.graphicsLayer { scaleX = 0.78f; scaleY = 0.78f },
                            )
                        }

                        // Line 2: Compact Keywords or 1-line snippet
                        val subLine = if (entry.keywords.isNotEmpty()) {
                            "🔑 ${entry.keywords.joinToString(", ")}"
                        } else {
                            entry.content
                        }
                        Text(
                            text = subLine,
                            color = c.muted,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        AnimatedVisibility(visible = isExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.5f))
                                Text(
                                    text = entry.content,
                                    color = c.ink.copy(alpha = 0.88f),
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                )
                                if (confirmDel) {
                                    RpInlineDeleteConfirmStrip(
                                        prompt = if (isZh) "确认删除词条「${entry.title}」？" else "Delete \"${entry.title}\"?",
                                        confirmText = strings.common.delete,
                                        cancelText = strings.common.cancel,
                                        onConfirm = {
                                            confirmDeleteLoreId = null
                                            onDeleteLore(entry.id)
                                        },
                                        onCancel = { confirmDeleteLoreId = null },
                                    )
                                } else {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        GuButton(
                                            onClick = { onToggleLoreAlwaysActive(entry.id) },
                                            compact = true,
                                            variant = if (entry.alwaysActive) GuButtonVariant.SUBTLE else GuButtonVariant.SECONDARY,
                                            modifier = Modifier.heightIn(min = 32.dp),
                                        ) {
                                            Text(strings.rp.worldbookConstantToggle, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        GuButton(
                                            onClick = { onEditLore(entry) },
                                            compact = true,
                                            variant = GuButtonVariant.SECONDARY,
                                            modifier = Modifier.heightIn(min = 32.dp),
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(strings.common.edit, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        GuButton(
                                            onClick = { confirmDeleteLoreId = entry.id },
                                            compact = true,
                                            variant = GuButtonVariant.SECONDARY,
                                            modifier = Modifier.heightIn(min = 32.dp),
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null, tint = c.danger, modifier = Modifier.size(13.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Worldview Backdrop, Rules & Export
        item(key = "__world_settings__") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GuSectionHeader(title = strings.rp.playStyleLabel)
                GuSegmentedControl(
                    items = playStyleLabels,
                    selectedIndex = playStyles.indexOf(rp.playStyle).coerceAtLeast(0),
                    onSelect = { idx -> onSetPlayStyle(playStyles[idx]) },
                    modifier = Modifier.fillMaxWidth(),
                )

                GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                        GuSwitchRow(
                            label = if (isZh) "剧场模式（视觉小说舞台）" else "Theater Mode (VN Stage)",
                            checked = rp.theaterMode,
                            onCheckedChange = { v -> onUpdateConfig { it.copy(theaterMode = v) } },
                        )
                        HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.6f))
                        GuSwitchRow(
                            label = strings.rp.enableChoicesToggle,
                            checked = rp.enableChoices,
                            onCheckedChange = { v -> onUpdateConfig { it.copy(enableChoices = v) } },
                        )
                        HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.6f))
                        GuSwitchRow(
                            label = strings.rp.enableDiceToggle,
                            checked = rp.enableDiceCheck,
                            onCheckedChange = { v -> onUpdateConfig { it.copy(enableDiceCheck = v) } },
                        )
                        HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.6f))
                        GuSwitchRow(
                            label = strings.rp.enableLedgerToggle,
                            checked = rp.enableLedger,
                            onCheckedChange = { v -> onUpdateConfig { it.copy(enableLedger = v) } },
                        )
                        HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.6f))
                        GuSwitchRow(
                            label = strings.rp.autoSkillBonusToggle,
                            checked = rp.autoDiceSkillBonus,
                            onCheckedChange = { v -> onUpdateConfig { it.copy(autoDiceSkillBonus = v) } },
                        )
                    }
                }

                if (onUpdateMinOutput != null) {
                    GuCard(modifier = Modifier.fillMaxWidth()) {
                        CompactOutputLengthControl(
                            params = params,
                            mode = ConversationMode.rp,
                            isZh = isZh,
                            onUpdateMinOutput = onUpdateMinOutput,
                        )
                    }
                }

                // Scene Backdrop Card
                GuSectionHeader(title = if (isZh) "场景 CG 背景图" else "Scene CG Backdrop")
                GuCard(modifier = Modifier.fillMaxWidth()) {
                    if (onConfigureArtModel != null) {
                        StudioArtModelStatusRow(
                            state = studioArtModelState,
                            onConfigure = onConfigureArtModel,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    if (sceneFile != null) {
                        Image(
                            painter = rememberAsyncImagePainter(sceneFile),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        GuButton(
                            onClick = onGenerateSceneCg,
                            enabled = !isGeneratingArt,
                            compact = true,
                            variant = GuButtonVariant.HONEY,
                            modifier = Modifier.heightIn(min = 34.dp),
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.rp.aiGenSceneCgBtn, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                        GuButton(
                            onClick = onPickSceneImage,
                            compact = true,
                            variant = GuButtonVariant.SUBTLE,
                            modifier = Modifier.heightIn(min = 34.dp),
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.rp.bindSceneImageBtn, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (!rp.state.sceneImagePath.isNullOrBlank()) {
                            GuButton(
                                onClick = onClearSceneImage,
                                compact = true,
                                variant = GuButtonVariant.SECONDARY,
                                modifier = Modifier.heightIn(min = 34.dp),
                            ) {
                                Text(strings.rp.clearSceneImageBtn, color = c.danger, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Worldview & Prompt Settings Accordion
                GuCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { worldSectionExpanded = !worldSectionExpanded },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (isZh) "世界观底色、叙事规则与酒馆指令" else "Worldview, Rules & Tavern Prompts",
                                color = c.ink,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            GuMetaText(
                                text = worldview.ifBlank { if (isZh) "点击展开编辑世界观、规则、导演备注" else "Tap to edit worldview, rules & notes" },
                            )
                        }
                        if (hasWorldUnsaved) {
                            GuBadge(if (isZh) "未保存" else "Unsaved", variant = GuBadgeVariant.AMBER)
                        }
                        Icon(
                            imageVector = if (worldSectionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = c.muted,
                        )
                    }

                    AnimatedVisibility(visible = worldSectionExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            GuTextField(
                                value = worldview,
                                onValueChange = { worldview = it },
                                label = strings.rp.worldviewLabel,
                                singleLine = false,
                                minLines = 2,
                                maxLines = 5,
                            )
                            GuTextField(
                                value = systemRules,
                                onValueChange = { systemRules = it },
                                label = strings.rp.systemRulesLabel,
                                singleLine = false,
                                minLines = 2,
                                maxLines = 4,
                            )
                            GuTextField(
                                value = narratorStyle,
                                onValueChange = { narratorStyle = it },
                                label = strings.rp.narratorStyleLabel,
                                singleLine = false,
                                minLines = 2,
                                maxLines = 4,
                            )
                            GuTextField(
                                value = authorNote,
                                onValueChange = { authorNote = it },
                                label = strings.rp.authorNoteLabel,
                                placeholder = strings.rp.authorNotePlaceholder,
                                singleLine = false,
                                minLines = 2,
                                maxLines = 4,
                            )
                            GuTextField(
                                value = postHistory,
                                onValueChange = { postHistory = it },
                                label = strings.rp.postHistoryLabel,
                                placeholder = strings.rp.postHistoryPlaceholder,
                                singleLine = false,
                                minLines = 2,
                                maxLines = 5,
                            )
                            GuTextField(
                                value = mesExample,
                                onValueChange = { mesExample = it },
                                label = strings.rp.mesExampleLabel,
                                singleLine = false,
                                minLines = 3,
                                maxLines = 6,
                            )
                            GuButton(
                                onClick = {
                                    onUpdateConfig {
                                        it.copy(
                                            worldview = worldview.trim(),
                                            systemRules = systemRules.trim(),
                                            narratorStyle = narratorStyle.trim(),
                                            authorNote = authorNote.trim(),
                                            postHistoryInstructions = postHistory.trim(),
                                            mesExample = mesExample.trim(),
                                        )
                                    }
                                },
                                enabled = hasWorldUnsaved,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.common.save, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Export
                GuSectionHeader(title = strings.rp.exportTitle)
                GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                    GuSettingsRow(
                        title = strings.rp.exportStPngBtn,
                        subtitle = if (isZh) "含立绘封面与 V2/V3 元数据 (.png)" else "Portrait cover with V2/V3 metadata (.png)",
                        icon = Icons.Default.Image,
                        iconTint = c.tagInk,
                        iconBg = c.tag,
                        showChevron = true,
                        showDivider = true,
                        onClick = onExportCardPng,
                    )
                    GuSettingsRow(
                        title = strings.rp.exportStBtn,
                        subtitle = if (isZh) "结构化角色卡与世界书 (.json)" else "Structured character card & lorebook (.json)",
                        icon = Icons.Default.Download,
                        iconTint = c.primarySoftInk,
                        iconBg = c.primarySoft,
                        showChevron = true,
                        onClick = onExportCardJson,
                    )
                }
            }
        }
    }

    if (showWorldbookPicker && assetActions != null) {
        RpWorldbookMountPickerSheet(
            worldbooks = allWorldbooks,
            mountedIds = mountedRefs.map { it.worldbookId }.toSet(),
            isZh = isZh,
            onMount = { wbId ->
                assetActions.onMountWorldbook(wbId)
                showWorldbookPicker = false
            },
            onUnmount = { wbId -> assetActions.onUnmountWorldbook(wbId) },
            onDismiss = { showWorldbookPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RpWorldbookMountPickerSheet(
    worldbooks: List<WorldbookEntity>,
    mountedIds: Set<String>,
    isZh: Boolean,
    onMount: (String) -> Unit,
    onUnmount: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalAppTheme.current.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    GuModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface,
        applyStatusBarsPadding = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GuSheetHeader(
                title = if (isZh) "选择要挂载的世界书" else "Mount Worldbook",
                onClose = onDismiss,
            )
            if (worldbooks.isEmpty()) {
                GuEmptyState(
                    title = if (isZh) "暂无全局世界书" else "No worldbooks available",
                    subtitle = if (isZh) "先在创作工坊或外部导入中添加世界书" else "Import or create a worldbook first",
                    icon = Icons.Default.Public,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    worldbooks.forEach { wb ->
                        val isMounted = wb.id in mountedIds
                        GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isMounted) onUnmount(wb.id) else onMount(wb.id)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = wb.name,
                                        color = c.ink,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    GuMetaText(
                                        buildString {
                                            append("${wb.entryCount} ${if (isZh) "条" else "entries"} · ${wb.totalEstimatedTokens}t")
                                            if (wb.globalEnabled) append(if (isZh) " · 全局启用" else " · Global")
                                        },
                                    )
                                }
                                GuBadge(
                                    text = if (isMounted) (if (isZh) "已挂载" else "Mounted") else (if (isZh) "挂载" else "Mount"),
                                    variant = if (isMounted) GuBadgeVariant.EMERALD else GuBadgeVariant.BLUE,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

// ==================== Push/Pop Subpage 3: Lore Entry Editor ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RpLoreEditorSubpage(
    initial: NovelLoreEntry?,
    onBack: () -> Unit,
    onSaveNew: (category: String, title: String, keywords: String, content: String, alwaysActive: Boolean) -> Unit,
    onSaveExisting: (NovelLoreEntry) -> Unit,
    onSaveV2LoreEntry: ((
        existingLegacyId: String?,
        category: String,
        title: String,
        keywordsRaw: String,
        secondaryKeysRaw: String,
        selectiveLogic: Int,
        useDepthInjection: Boolean,
        depth: Int,
        sticky: Int,
        cooldown: Int,
        delay: Int,
        content: String,
        alwaysActive: Boolean,
    ) -> Unit)? = null,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH

    var category by remember(initial?.id) { mutableStateOf(initial?.category ?: "character") }
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var keywords by remember(initial?.id) { mutableStateOf(initial?.keywords?.joinToString(", ").orEmpty()) }
    var content by remember(initial?.id) { mutableStateOf(initial?.content.orEmpty()) }
    var alwaysActive by remember(initial?.id) { mutableStateOf(initial?.alwaysActive ?: false) }

    var advancedExpanded by remember(initial?.id) { mutableStateOf(false) }
    var secondaryKeysRaw by remember(initial?.id) { mutableStateOf("") }
    var selectiveLogic by remember(initial?.id) { mutableIntStateOf(WorldbookEngine.LOGIC_AND_ANY) }
    var useDepthInjection by remember(initial?.id) { mutableStateOf(false) }
    var depth by remember(initial?.id) { mutableIntStateOf(4) }
    var sticky by remember(initial?.id) { mutableIntStateOf(0) }
    var cooldown by remember(initial?.id) { mutableIntStateOf(0) }
    var delayTurns by remember(initial?.id) { mutableIntStateOf(0) }

    val canSave = title.isNotBlank() && content.isNotBlank()
    val hasV2Triggers = useDepthInjection ||
        secondaryKeysRaw.trim().isNotEmpty() ||
        sticky > 0 ||
        cooldown > 0 ||
        delayTurns > 0

    fun submit() {
        if (hasV2Triggers && onSaveV2LoreEntry != null) {
            onSaveV2LoreEntry(
                initial?.id,
                category,
                title,
                keywords,
                secondaryKeysRaw,
                selectiveLogic,
                useDepthInjection,
                depth,
                sticky,
                cooldown,
                delayTurns,
                content,
                alwaysActive,
            )
            onBack()
            return
        }
        if (initial == null) {
            onSaveNew(category, title.trim(), keywords.trim(), content.trim(), alwaysActive)
        } else {
            val parsedKeywords = keywords
                .split(',', '，', '、')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            onSaveExisting(
                initial.copy(
                    category = category,
                    title = title.trim(),
                    keywords = parsedKeywords.ifEmpty { listOf(title.trim()) },
                    content = content.trim(),
                    alwaysActive = alwaysActive,
                ),
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StudioSubpageTopBar(
            backLabel = if (isZh) "返回设定" else "World",
            title = if (initial == null) stripLeadingPlus(strings.rp.worldbookAddBtn) else strings.common.edit,
            onBack = onBack,
            trailing = {
                GuButton(
                    onClick = ::submit,
                    enabled = canSave,
                    compact = true,
                    modifier = Modifier.heightIn(min = 34.dp),
                ) {
                    Text(strings.common.save, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RP_WORLDBOOK_CATEGORIES.forEach { cat ->
                    GuPill(
                        label = localizeRpLoreCategory(cat, strings.novel.loreCats, isZh),
                        selected = category == cat,
                        onClick = { category = cat },
                        modifier = Modifier.heightIn(min = 34.dp),
                    )
                }
            }
            GuTextField(
                value = title,
                onValueChange = { title = it },
                label = if (isZh) "词条标题" else "Title",
                placeholder = strings.novel.loreTitlePlaceholder,
            )
            GuTextField(
                value = keywords,
                onValueChange = { keywords = it },
                label = if (isZh) "触发关键词" else "Keywords",
                placeholder = strings.rp.worldbookKeywordsPlaceholder,
            )
            GuTextField(
                value = content,
                onValueChange = { content = it },
                label = if (isZh) "设定内容" else "Content",
                placeholder = strings.novel.loreContentPlaceholder,
                singleLine = false,
                minLines = 5,
                maxLines = 12,
            )
            GuSwitchRow(
                label = strings.rp.worldbookConstantToggle,
                checked = alwaysActive,
                onCheckedChange = { alwaysActive = it },
            )
            if (onSaveV2LoreEntry != null) {
                GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { advancedExpanded = !advancedExpanded }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = if (isZh) "高级触发器设定 (V2)" else "Advanced Triggers (V2)",
                                    color = c.ink,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                GuMetaText(
                                    if (isZh) "次级逻辑词、@D 深度注入、粘滞/冷却回合" else "Secondary logic keys, @D depth, sticky/cooldown",
                                )
                            }
                            if (hasV2Triggers) {
                                GuBadge(text = "V2", variant = GuBadgeVariant.BLUE)
                                Spacer(Modifier.width(6.dp))
                            }
                            Icon(
                                imageVector = if (advancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = c.muted,
                            )
                        }
                        AnimatedVisibility(visible = advancedExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.6f))
                                GuTextField(
                                    value = secondaryKeysRaw,
                                    onValueChange = { secondaryKeysRaw = it },
                                    label = if (isZh) "AND/NOT 次级词（逗号分隔）" else "Secondary Keys (comma-separated)",
                                    placeholder = if (isZh) "例如: 雨夜, 酒馆" else "e.g. rainy night, tavern",
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    GuMetaText(if (isZh) "次级词逻辑" else "Selective Logic")
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        listOf(
                                            WorldbookEngine.LOGIC_AND_ANY to "AND_ANY",
                                            WorldbookEngine.LOGIC_AND_ALL to "AND_ALL",
                                            WorldbookEngine.LOGIC_NOT_ALL to "NOT_ALL",
                                            WorldbookEngine.LOGIC_NOT_ANY to "NOT_ANY",
                                        ).forEach { (logicVal, label) ->
                                            GuPill(
                                                label = label,
                                                selected = selectiveLogic == logicVal,
                                                onClick = { selectiveLogic = logicVal },
                                                modifier = Modifier.heightIn(min = 32.dp),
                                            )
                                        }
                                    }
                                }
                                GuSwitchRow(
                                    label = if (isZh) "@D 深度注入" else "@D Depth Injection",
                                    checked = useDepthInjection,
                                    onCheckedChange = { useDepthInjection = it },
                                )
                                if (useDepthInjection) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = if (isZh) "注入深度 (Depth)" else "Injection Depth",
                                            color = c.ink,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        GuInlineStepper(
                                            valueText = "@D$depth",
                                            canDecrement = depth > 0,
                                            canIncrement = depth < 16,
                                            onDecrement = { depth = (depth - 1).coerceAtLeast(0) },
                                            onIncrement = { depth = (depth + 1).coerceAtMost(16) },
                                        )
                                    }
                                }
                                listOf(
                                    Triple(if (isZh) "粘滞回合 (Sticky)" else "Sticky Turns", sticky) { v: Int -> sticky = v },
                                    Triple(if (isZh) "冷却回合 (Cooldown)" else "Cooldown Turns", cooldown) { v: Int -> cooldown = v },
                                    Triple(if (isZh) "延迟回合 (Delay)" else "Delay Turns", delayTurns) { v: Int -> delayTurns = v },
                                ).forEach { (label, curVal, setter) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(text = label, color = c.ink, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                                        GuInlineStepper(
                                            valueText = curVal.toString(),
                                            canDecrement = curVal > 0,
                                            canIncrement = curVal < 20,
                                            onDecrement = { setter((curVal - 1).coerceAtLeast(0)) },
                                            onIncrement = { setter((curVal + 1).coerceAtMost(20)) },
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

// ==================== Tab 3: 舞台 (Sticky 132dp Live Preview + Controls + Collapsed Accordions) ====================
