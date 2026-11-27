package com.gugachat.app.ui.studio

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gugachat.app.AppContainer
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.storage.LocalMediaPathResolver
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuCollapsibleSearchBar
import com.gugachat.app.core.theme.GuCompactSearchBar
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuHairlineDivider
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuInlineStepper
import com.gugachat.app.core.theme.GuModalBottomSheet
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuPushPopContainer
import com.gugachat.app.core.theme.GuSettingsRow
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuSwitchRow
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.local.CharacterCardEntity
import com.gugachat.app.data.local.PersonaEntity
import com.gugachat.app.data.local.PromptPresetEntity
import com.gugachat.app.data.local.WorldbookEntity
import com.gugachat.app.data.local.WorldbookEntryEntity
import com.gugachat.app.domain.chat.PromptPresetBlock
import com.gugachat.app.domain.chat.RpCharacter
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpEngine
import com.gugachat.app.domain.chat.RpPlayStyle
import com.gugachat.app.domain.chat.RpPresetTemplate
import com.gugachat.app.domain.chat.RpPresets
import com.gugachat.app.domain.chat.RpRegexScript
import com.gugachat.app.domain.chat.WorldbookEngine
import com.gugachat.app.ui.images.ImageViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PersonaManagerSheet(
    personas: List<PersonaEntity>,
    onSavePersona: (id: String?, name: String, desc: String, pos: Int, depth: Int, makeDefault: Boolean) -> Unit,
    onSetDefault: (String) -> Unit,
    onDeletePersona: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var editingId by remember { mutableStateOf<String?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var draftName by remember { mutableStateOf("") }
    var draftDesc by remember { mutableStateOf("") }
    var draftPos by remember { mutableIntStateOf(0) }
    var draftDepth by remember { mutableIntStateOf(4) }
    var draftMakeDefault by remember { mutableStateOf(false) }

    GuModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = if (isZh) "玩家自设管理 (Persona)" else "Player Personas",
                subtitle = if (isZh) "点击设为默认身份，或自动填入开局" else "Choose default identity for campaigns",
                closeContentDescription = strings.common.close,
                onClose = onDismiss,
                trailing = {
                    if (!showForm) {
                        GuButton(
                            onClick = {
                                editingId = null
                                draftName = ""
                                draftDesc = ""
                                draftPos = 0
                                draftDepth = 4
                                draftMakeDefault = personas.isEmpty()
                                showForm = true
                            },
                            compact = true,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(if (isZh) "新建" else "New", fontSize = 12.sp)
                        }
                    }
                },
            )

            AnimatedContent(targetState = showForm, label = "persona_sheet_content") { inForm ->
                if (inForm) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        GuTextField(
                            value = draftName,
                            onValueChange = { draftName = it },
                            label = if (isZh) "玩家名字 ({{user}})" else "Player Name ({{user}})",
                            placeholder = if (isZh) "如：云疏月 / 旅人" else "e.g. Traveler",
                        )
                        GuTextField(
                            value = draftDesc,
                            onValueChange = { draftDesc = it },
                            label = if (isZh) "外貌、身份与背景简介" else "Background & Appearance",
                            placeholder = if (isZh) "写入角色性格、立场或与主角关系..." else "Persona details...",
                            singleLine = false,
                            minLines = 3,
                            maxLines = 6,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GuPill(
                                label = if (isZh) "注入于世界书前" else "Before Lore",
                                selected = draftPos == 0,
                                onClick = { draftPos = 0 },
                            )
                            GuPill(
                                label = if (isZh) "按 @D 深度注入" else "At @D Depth",
                                selected = draftPos == 1,
                                onClick = { draftPos = 1 },
                            )
                        }
                        if (draftPos == 1) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(if (isZh) "注入深度 (@D)" else "Injection Depth (@D)", color = c.ink, fontSize = 13.sp)
                                GuInlineStepper(
                                    valueText = "$draftDepth",
                                    onDecrement = { if (draftDepth > 0) draftDepth-- },
                                    onIncrement = { if (draftDepth < 24) draftDepth++ },
                                    canDecrement = draftDepth > 0,
                                    canIncrement = draftDepth < 24,
                                )
                            }
                        }
                        GuSwitchRow(
                            label = if (isZh) "设为当前默认自设" else "Set as default persona",
                            checked = draftMakeDefault,
                            onCheckedChange = { draftMakeDefault = it },
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            GuButton(
                                onClick = { showForm = false },
                                variant = GuButtonVariant.SECONDARY,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(strings.common.cancel)
                            }
                            GuButton(
                                onClick = {
                                    onSavePersona(editingId, draftName, draftDesc, draftPos, draftDepth, draftMakeDefault)
                                    showForm = false
                                },
                                enabled = draftName.isNotBlank(),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(strings.common.save)
                            }
                        }
                    }
                } else if (personas.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                    ) {
                        GuEmptyState(
                            title = if (isZh) "还没有自定义自设" else "No custom personas yet",
                            subtitle = if (isZh) "保存常用的玩家身份与人设，开局一键复用" else "Create reusable player personas",
                            actionLabel = if (isZh) "创建首个自设" else "Create Persona",
                            onAction = {
                                editingId = null
                                draftName = ""
                                draftDesc = ""
                                draftPos = 0
                                draftDepth = 4
                                draftMakeDefault = true
                                showForm = true
                            },
                            icon = Icons.Default.Person,
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(personas, key = { it.id }) { p ->
                            GuCard(
                                onClick = {
                                    editingId = p.id
                                    draftName = p.name
                                    draftDesc = p.description
                                    draftPos = p.injectionPosition
                                    draftDepth = p.injectionDepth
                                    draftMakeDefault = p.isDefault
                                    showForm = true
                                },
                                backgroundColor = if (p.isDefault) c.primarySoft.copy(alpha = 0.55f) else c.surface,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    RadioButton(
                                        selected = p.isDefault,
                                        onClick = { onSetDefault(p.id) },
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(p.name, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                            if (p.isDefault) {
                                                GuBadge(text = if (isZh) "默认" else "Default", variant = GuBadgeVariant.BLUE)
                                            }
                                        }
                                        if (p.description.isNotBlank()) {
                                            Text(
                                                text = p.description,
                                                color = c.muted,
                                                fontSize = 12.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    IconButton(onClick = { onDeletePersona(p.id) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = strings.common.delete,
                                            tint = c.muted,
                                            modifier = Modifier.size(18.dp),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateOriginalCardSheet(
    onCreate: (name: String, desc: String, personality: String, scenario: String, firstMes: String, style: RpPlayStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf("") }
    var style by remember { mutableStateOf(RpPlayStyle.chengguang) }
    var desc by remember { mutableStateOf("") }
    var personality by remember { mutableStateOf("") }
    var scenario by remember { mutableStateOf("") }
    var firstMes by remember { mutableStateOf("") }

    GuModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = if (isZh) "创建原创角色卡" else "Create Original Card",
                closeContentDescription = strings.common.close,
                onClose = onDismiss,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GuTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = if (isZh) "角色 / 剧本名称" else "Name",
                    placeholder = if (isZh) "例如：顾星澜" else "Character name",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuPill(
                        label = if (isZh) "剧场模式" else "Theater",
                        selected = style == RpPlayStyle.chengguang,
                        onClick = { style = RpPlayStyle.chengguang },
                    )
                    GuPill(
                        label = if (isZh) "酒馆对白" else "Tavern",
                        selected = style == RpPlayStyle.tavern,
                        onClick = { style = RpPlayStyle.tavern },
                    )
                    GuPill(
                        label = "TRPG",
                        selected = style == RpPlayStyle.trpg,
                        onClick = { style = RpPlayStyle.trpg },
                    )
                }
                GuTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = if (isZh) "角色外貌与背景" else "Description",
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                )
                GuTextField(
                    value = personality,
                    onValueChange = { personality = it },
                    label = if (isZh) "性格特点" else "Personality",
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3,
                )
                GuTextField(
                    value = scenario,
                    onValueChange = { scenario = it },
                    label = if (isZh) "开局场景 / 世界观" else "Scenario",
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                )
                GuTextField(
                    value = firstMes,
                    onValueChange = { firstMes = it },
                    label = if (isZh) "首条开场白（支持 {{user}} / {{char}}）" else "First Greeting",
                    singleLine = false,
                    minLines = 3,
                    maxLines = 5,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GuButton(
                    onClick = onDismiss,
                    variant = GuButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f),
                ) { Text(strings.common.cancel) }
                GuButton(
                    onClick = { onCreate(name, desc, personality, scenario, firstMes, style) },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text(if (isZh) "保存至角色库" else "Save Card") }
            }
        }
    }
}

// ==================== Lore Settings Hub Page (`设定`) ====================


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateWorldbookSheet(
    onCreate: (name: String, description: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    GuModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GuSheetHeader(
                title = if (isZh) "新建空白世界书" else "New Worldbook",
                closeContentDescription = strings.common.close,
                onClose = onDismiss,
            )
            GuTextField(
                value = name,
                onValueChange = { name = it },
                label = if (isZh) "世界书名称" else "Worldbook Name",
                placeholder = if (isZh) "例如：大宁朝官制与地理设定" else "Title",
            )
            GuTextField(
                value = desc,
                onValueChange = { desc = it },
                label = if (isZh) "备注说明 (可选)" else "Description",
                singleLine = false,
                minLines = 2,
                maxLines = 3,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GuButton(
                    onClick = onDismiss,
                    variant = GuButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f),
                ) { Text(strings.common.cancel) }
                GuButton(
                    onClick = { onCreate(name, desc) },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text(if (isZh) "创建" else "Create") }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

// ==================== Worldbook Detail + 7-Field Entry Editor ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorldbookDetailSheet(
    worldbook: WorldbookEntity,
    entries: List<WorldbookEntryEntity>,
    vectorReady: Boolean,
    isIndexing: Boolean,
    onToggleGlobal: (Boolean) -> Unit,
    onUpdateMeta: (name: String, desc: String, scanDepth: Int, tokenBudget: Int, recursive: Boolean) -> Unit,
    onReindex: () -> Unit,
    onDeleteBook: () -> Unit,
    onToggleEntryEnabled: (WorldbookEntryEntity, Boolean) -> Unit,
    onSaveEntry: (
        entryId: String?,
        title: String,
        content: String,
        keys: List<String>,
        secKeys: List<String>,
        selectiveLogic: Int,
        constant: Boolean,
        enabled: Boolean,
        position: Int,
        depth: Int,
        sticky: Int,
        cooldown: Int,
    ) -> Unit,
    onDeleteEntry: (WorldbookEntryEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var editingEntryId by remember { mutableStateOf<String?>(null) }
    var isEditingMode by remember { mutableStateOf(false) }
    var confirmDeleteBook by remember { mutableStateOf(false) }
    var showBookConfig by remember { mutableStateOf(false) }

    // 7-field entry editor drafts
    var draftTitle by remember { mutableStateOf("") }
    var draftPrimaryKeys by remember { mutableStateOf("") }
    var draftSecondaryKeys by remember { mutableStateOf("") }
    var draftSelectiveLogic by remember { mutableIntStateOf(0) }
    var draftContent by remember { mutableStateOf("") }
    var draftConstant by remember { mutableStateOf(false) }
    var draftEnabled by remember { mutableStateOf(true) }
    var draftPosition by remember { mutableIntStateOf(WorldbookEngine.POSITION_AFTER_CHAR) }
    var draftDepth by remember { mutableIntStateOf(4) }
    var draftSticky by remember { mutableIntStateOf(0) }
    var draftCooldown by remember { mutableIntStateOf(0) }

    fun openEntryEditor(entry: WorldbookEntryEntity?) {
        editingEntryId = entry?.id
        draftTitle = entry?.title.orEmpty()
        draftPrimaryKeys = decodeJsonList<String>(entry?.keysJson).joinToString(", ")
        draftSecondaryKeys = decodeJsonList<String>(entry?.secondaryKeysJson).joinToString(", ")
        draftSelectiveLogic = entry?.selectiveLogic ?: 0
        draftContent = entry?.content.orEmpty()
        draftConstant = entry?.constant ?: false
        draftEnabled = entry?.enabled ?: true
        draftPosition = entry?.position ?: WorldbookEngine.POSITION_AFTER_CHAR
        draftDepth = entry?.depth ?: 4
        draftSticky = entry?.sticky ?: 0
        draftCooldown = entry?.cooldown ?: 0
        isEditingMode = true
    }

    if (confirmDeleteBook) {
        GuConfirmDialog(
            title = if (isZh) "删除世界书" else "Delete Worldbook",
            message = if (isZh) "确认删除「${worldbook.name}」及其中 ${entries.size} 条词条？"
            else "Delete \"${worldbook.name}\" and its ${entries.size} entries?",
            confirmText = strings.common.delete,
            cancelText = strings.common.cancel,
            isDestructive = true,
            onConfirm = {
                confirmDeleteBook = false
                onDeleteBook()
            },
            onDismiss = { confirmDeleteBook = false },
        )
    }

    GuModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding(),
        ) {
            if (!isEditingMode) {
                GuSheetHeader(
                    title = worldbook.name,
                    subtitle = if (isZh) {
                        "${entries.size} 词条 · ~${worldbook.totalEstimatedTokens} tokens · " +
                            if (vectorReady) "向量就绪" else "词法待索引"
                    } else {
                        "${entries.size} entries · ~${worldbook.totalEstimatedTokens} tokens"
                    },
                    closeContentDescription = strings.common.close,
                    onClose = onDismiss,
                    trailing = {
                        IconButton(onClick = onReindex, enabled = !isIndexing) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = c.primary)
                        }
                        IconButton(onClick = { showBookConfig = !showBookConfig }) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = c.muted)
                        }
                        IconButton(onClick = { confirmDeleteBook = true }) {
                            Icon(Icons.Default.Delete, contentDescription = strings.common.delete, tint = c.danger)
                        }
                    },
                )

                AnimatedVisibility(visible = showBookConfig) {
                    var nameDraft by remember(worldbook.name) { mutableStateOf(worldbook.name) }
                    var descDraft by remember(worldbook.description) { mutableStateOf(worldbook.description) }
                    var scanDepthDraft by remember(worldbook.scanDepth) { mutableIntStateOf(worldbook.scanDepth) }
                    var budgetDraft by remember(worldbook.tokenBudget) { mutableIntStateOf(worldbook.tokenBudget) }
                    var recursiveDraft by remember(worldbook.recursiveScanning) { mutableStateOf(worldbook.recursiveScanning) }

                    GuCard(
                        backgroundColor = c.surfaceSoft,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GuTextField(
                                value = nameDraft,
                                onValueChange = { nameDraft = it },
                                label = if (isZh) "世界书名称" else "Name",
                            )
                            GuTextField(
                                value = descDraft,
                                onValueChange = { descDraft = it },
                                label = if (isZh) "简介" else "Description",
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(if (isZh) "扫描深度 (最近消息)" else "Scan Depth", color = c.ink, fontSize = 13.sp)
                                GuInlineStepper(
                                    valueText = "$scanDepthDraft",
                                    onDecrement = { if (scanDepthDraft > 1) scanDepthDraft-- },
                                    onIncrement = { if (scanDepthDraft < 24) scanDepthDraft++ },
                                    canDecrement = scanDepthDraft > 1,
                                    canIncrement = scanDepthDraft < 24,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(if (isZh) "Token 预算上限" else "Token Budget", color = c.ink, fontSize = 13.sp)
                                GuInlineStepper(
                                    valueText = "$budgetDraft",
                                    onDecrement = { budgetDraft = (budgetDraft - 200).coerceAtLeast(400) },
                                    onIncrement = { budgetDraft = (budgetDraft + 200).coerceAtMost(16000) },
                                    canDecrement = budgetDraft > 400,
                                    canIncrement = budgetDraft < 16000,
                                )
                            }
                            GuSwitchRow(
                                label = if (isZh) "允许 1-hop 递归触发" else "Recursive Scanning",
                                checked = recursiveDraft,
                                onCheckedChange = { recursiveDraft = it },
                            )
                            GuButton(
                                onClick = {
                                    onUpdateMeta(nameDraft, descDraft, scanDepthDraft, budgetDraft, recursiveDraft)
                                    showBookConfig = false
                                },
                                compact = true,
                            ) { Text(strings.common.save) }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = if (isZh) "全局启用此世界书" else "Global Active",
                            color = c.ink,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Switch(checked = worldbook.globalEnabled, onCheckedChange = onToggleGlobal)
                    }
                    GuButton(onClick = { openEntryEditor(null) }, compact = true) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(if (isZh) "新建词条" else "Add Entry", fontSize = 12.sp)
                    }
                }

                if (entries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        GuEmptyState(
                            title = if (isZh) "暂无世界书词条" else "No entries yet",
                            subtitle = if (isZh) "点击「新建词条」添加常驻设定或关键词触发条目" else "Tap Add Entry to create lore",
                            actionLabel = if (isZh) "新建词条" else "Add Entry",
                            onAction = { openEntryEditor(null) },
                            icon = Icons.Default.Book,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(entries, key = { it.id }) { entry ->
                            val primaryKeys = remember(entry.keysJson) { decodeJsonList<String>(entry.keysJson) }
                            GuCard(
                                onClick = { openEntryEditor(entry) },
                                contentPadding = PaddingValues(12.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                text = entry.title.ifBlank { if (isZh) "词条 #${entry.uid}" else "Entry #${entry.uid}" },
                                                color = c.ink,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                            if (entry.constant) {
                                                GuBadge(text = if (isZh) "常驻" else "Const", variant = GuBadgeVariant.AMBER)
                                            } else if (entry.position == WorldbookEngine.POSITION_AT_DEPTH) {
                                                GuBadge(text = "@D${entry.depth}", variant = GuBadgeVariant.BLUE)
                                            }
                                        }
                                        val keyLine = primaryKeys.joinToString(" · ")
                                        if (keyLine.isNotBlank()) {
                                            Text(
                                                text = keyLine,
                                                color = c.primary,
                                                fontSize = 11.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Text(
                                            text = entry.content.replace('\n', ' ').trim(),
                                            color = c.muted,
                                            fontSize = 12.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Switch(
                                            checked = entry.enabled,
                                            onCheckedChange = { onToggleEntryEnabled(entry, it) },
                                        )
                                        IconButton(
                                            onClick = { onDeleteEntry(entry) },
                                            modifier = Modifier.size(30.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = strings.common.delete,
                                                tint = c.muted,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // 7-field WorldbookEntry editor view
                LaunchSubpageHeader(
                    title = if (editingEntryId == null) {
                        if (isZh) "新建词条 (7 项核心字段)" else "New Entry"
                    } else {
                        if (isZh) "编辑词条" else "Edit Entry"
                    },
                    onBack = { isEditingMode = false },
                    onClose = onDismiss,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 1. Title
                    GuTextField(
                        value = draftTitle,
                        onValueChange = { draftTitle = it },
                        label = if (isZh) "1. 词条标题" else "1. Entry Title",
                        placeholder = if (isZh) "例如：永安宫夜禁律令" else "Entry title",
                    )

                    // 2. Primary Keywords
                    GuTextField(
                        value = draftPrimaryKeys,
                        onValueChange = { draftPrimaryKeys = it },
                        label = if (isZh) "2. 主关键词（英文逗号或换行分隔）" else "2. Primary Keywords",
                        placeholder = if (isZh) "夜禁, 侍卫, 坤宁宫" else "key1, key2",
                    )

                    // 3. Secondary Keywords + selectiveLogic (AND/NOT)
                    GuTextField(
                        value = draftSecondaryKeys,
                        onValueChange = { draftSecondaryKeys = it },
                        label = if (isZh) "3. 次级关键词 & AND/NOT 逻辑" else "3. Secondary Keys & Logic",
                        placeholder = if (isZh) "留空表示不使用次级条件" else "Optional secondary keys",
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val logicOptions = listOf(
                            0 to if (isZh) "任一匹配 AND_ANY" else "AND ANY",
                            3 to if (isZh) "全部匹配 AND_ALL" else "AND ALL",
                            1 to if (isZh) "非全包含 NOT_ALL" else "NOT ALL",
                            2 to if (isZh) "均不包含 NOT_ANY" else "NOT ANY",
                        )
                        logicOptions.forEach { (code, label) ->
                            GuPill(
                                label = label,
                                selected = draftSelectiveLogic == code,
                                onClick = { draftSelectiveLogic = code },
                            )
                        }
                    }

                    // 4. Content
                    GuTextField(
                        value = draftContent,
                        onValueChange = { draftContent = it },
                        label = if (isZh) "4. 设定正文" else "4. Lore Content",
                        placeholder = if (isZh) "详细描述设定内容..." else "Lore text...",
                        singleLine = false,
                        minLines = 4,
                        maxLines = 8,
                    )

                    // 5. Constant + Enabled switches
                    GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                        GuSwitchRow(
                            label = if (isZh) "5. 常驻激活 (Constant)" else "5. Always Active (Constant)",
                            subtitle = if (isZh) "无需触发关键词，每轮稳态拼入缓存前缀" else "Always included without keyword match",
                            checked = draftConstant,
                            onCheckedChange = { draftConstant = it },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        HorizontalDivider(color = c.border.copy(alpha = 0.45f))
                        GuSwitchRow(
                            label = if (isZh) "启用此词条" else "Entry Enabled",
                            checked = draftEnabled,
                            onCheckedChange = { draftEnabled = it },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }

                    // 6. Position + @D Depth
                    Text(
                        text = if (isZh) "6. 注入位置与 @D 深度" else "6. Injection Position & @D Depth",
                        color = c.ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val posList = listOf(
                            WorldbookEngine.POSITION_BEFORE_CHAR to if (isZh) "角色前" else "Before Char",
                            WorldbookEngine.POSITION_AFTER_CHAR to if (isZh) "角色后" else "After Char",
                            WorldbookEngine.POSITION_BEFORE_AN to if (isZh) "作者注前" else "Before AN",
                            WorldbookEngine.POSITION_AFTER_AN to if (isZh) "作者注后" else "After AN",
                            WorldbookEngine.POSITION_AT_DEPTH to "@D",
                        )
                        posList.forEach { (posCode, label) ->
                            GuPill(
                                label = label,
                                selected = draftPosition == posCode,
                                onClick = { draftPosition = posCode },
                            )
                        }
                    }
                    if (draftPosition == WorldbookEngine.POSITION_AT_DEPTH) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(if (isZh) "插入深度 (@D)" else "Depth (@D)", color = c.ink, fontSize = 13.sp)
                            GuInlineStepper(
                                valueText = "@D$draftDepth",
                                onDecrement = { if (draftDepth > 0) draftDepth-- },
                                onIncrement = { if (draftDepth < 32) draftDepth++ },
                                canDecrement = draftDepth > 0,
                                canIncrement = draftDepth < 32,
                            )
                        }
                    }

                    // 7. Timed effects: Sticky & Cooldown
                    Text(
                        text = if (isZh) "7. 粘滞 (Sticky) 与冷却 (Cooldown)" else "7. Sticky & Cooldown Turns",
                        color = c.ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    GuCard(backgroundColor = c.surfaceSoft) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(if (isZh) "Sticky 粘滞回合" else "Sticky Turns", color = c.ink, fontSize = 13.sp)
                                GuInlineStepper(
                                    valueText = "$draftSticky",
                                    onDecrement = { if (draftSticky > 0) draftSticky-- },
                                    onIncrement = { if (draftSticky < 50) draftSticky++ },
                                    canDecrement = draftSticky > 0,
                                    canIncrement = draftSticky < 50,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(if (isZh) "Cooldown 冷却回合" else "Cooldown Turns", color = c.ink, fontSize = 13.sp)
                                GuInlineStepper(
                                    valueText = "$draftCooldown",
                                    onDecrement = { if (draftCooldown > 0) draftCooldown-- },
                                    onIncrement = { if (draftCooldown < 50) draftCooldown++ },
                                    canDecrement = draftCooldown > 0,
                                    canIncrement = draftCooldown < 50,
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GuButton(
                        onClick = { isEditingMode = false },
                        variant = GuButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f),
                    ) { Text(strings.common.cancel) }
                    GuButton(
                        onClick = {
                            val pKeys = draftPrimaryKeys.split(',', '，', '\n').map { it.trim() }.filter { it.isNotEmpty() }
                            val sKeys = draftSecondaryKeys.split(',', '，', '\n').map { it.trim() }.filter { it.isNotEmpty() }
                            onSaveEntry(
                                editingEntryId,
                                draftTitle,
                                draftContent,
                                pKeys,
                                sKeys,
                                draftSelectiveLogic,
                                draftConstant,
                                draftEnabled,
                                draftPosition,
                                draftDepth,
                                draftSticky,
                                draftCooldown,
                            )
                            isEditingMode = false
                        },
                        enabled = draftContent.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) { Text(strings.common.save) }
                }
            }
        }
    }
}

// ==================== Prompt Preset & Regex Detail Sheet ====================

