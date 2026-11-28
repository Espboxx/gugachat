package com.gugachat.app.ui.studio

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuInlineStepper
import com.gugachat.app.core.theme.GuModalBottomSheet
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuSwitchRow
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.data.local.PersonaEntity
import com.gugachat.app.domain.chat.RpPlayStyle

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
        BackHandler(enabled = showForm) {
            showForm = false
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .imePadding()
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

