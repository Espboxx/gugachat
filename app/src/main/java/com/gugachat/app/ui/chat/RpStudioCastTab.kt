package com.gugachat.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
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
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuLinearProgressBar
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuSegmentedControl
import com.gugachat.app.core.theme.GuSettingsRow
import com.gugachat.app.core.theme.GuSwitchRow
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.data.local.CharacterCardEntity
import com.gugachat.app.data.local.PersonaEntity
import com.gugachat.app.domain.chat.RpCharacter
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpEngine
import java.util.UUID
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RpCastSection(
    rp: RpConfig,
    modifier: Modifier = Modifier,
    boundCharacterCard: CharacterCardEntity? = null,
    personas: List<PersonaEntity> = emptyList(),
    boundPersonaId: String? = null,
    assetActions: RpStudioAssetActions? = null,
    onUpdatePlayer: (name: String, persona: String) -> Unit,
    onAddNpc: () -> Unit,
    onOpenNpcDetail: (RpCharacter) -> Unit,
    onDeleteNpc: (String) -> Unit,
    onAdjustAffinity: (String, Int) -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val ctx = LocalContext.current
    val isZh = strings.language == AppLanguage.ZH
    val boundCard = boundCharacterCard

    var playerExpanded by remember { mutableStateOf(false) }
    var pNameDraft by remember(rp.playerName) { mutableStateOf(rp.playerName) }
    var pPersonaDraft by remember(rp.playerPersona) { mutableStateOf(rp.playerPersona) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    val npcList = remember(rp.cast) { rp.cast.filter { !it.isPlayer } }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Collapsible Protagonist Card
        item(key = "__player_card__") {
            GuCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = c.primarySoft.copy(alpha = if (theme.isDark) 0.55f else 0.70f),
                onClick = { playerExpanded = !playerExpanded },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(c.primary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = c.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = rp.playerName.ifBlank { if (isZh) "旅人" else "Traveler" },
                                color = c.ink,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            GuBadge(localizeRpRole("protagonist", isZh), variant = GuBadgeVariant.BLUE)
                        }
                        Text(
                            text = rp.playerPersona.ifBlank {
                                if (isZh) "点击展开编辑你的角色名与自设背景" else "Tap to edit player name & persona"
                            },
                            color = c.muted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        imageVector = if (playerExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = c.muted,
                    )
                }

                AnimatedVisibility(visible = playerExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GuTextField(
                            value = pNameDraft,
                            onValueChange = { pNameDraft = it },
                            label = strings.rp.playerNameLabel,
                        )
                        GuTextField(
                            value = pPersonaDraft,
                            onValueChange = { pPersonaDraft = it },
                            label = strings.rp.playerPersonaLabel,
                            singleLine = false,
                            minLines = 2,
                            maxLines = 4,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            GuButton(
                                onClick = {
                                    onUpdatePlayer(pNameDraft.trim(), pPersonaDraft.trim())
                                    playerExpanded = false
                                },
                                enabled = pNameDraft != rp.playerName || pPersonaDraft != rp.playerPersona,
                                compact = true,
                            ) {
                                Text(strings.common.save, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        if (personas.isNotEmpty() && assetActions != null) {
            item(key = "__persona_switcher__") {
                GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = if (isZh) "扮演身份（Persona）" else "Player Persona",
                            color = c.ink,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item(key = "__persona_default__") {
                                GuPill(
                                    label = if (isZh) "会话自设" else "Session Default",
                                    selected = boundPersonaId == null,
                                    onClick = { assetActions.onSelectPersona(null) },
                                )
                            }
                            items(personas, key = { it.id }) { p ->
                                GuPill(
                                    label = p.name.ifBlank { if (isZh) "身份" else "Persona" },
                                    selected = boundPersonaId == p.id,
                                    onClick = { assetActions.onSelectPersona(p.id) },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (boundCard != null && assetActions != null) {
            item(key = "__sync_bound_card__") {
                GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                    GuSettingsRow(
                        title = if (isZh) "🔄 同步原卡最新设定" else "🔄 Sync Latest Card Fields",
                        subtitle = if (isZh) "从「${boundCard.name}」同步世界观、立绘与开场白" else "Pull updates from \"${boundCard.name}\"",
                        icon = Icons.Default.Refresh,
                        iconTint = c.primarySoftInk,
                        iconBg = c.primarySoft,
                        showChevron = true,
                        onClick = { assetActions.onSyncBoundCharacterCard() },
                    )
                }
            }
        }

        // Cast Header + Add CTA
        item(key = "__cast_header__") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = if (isZh) "登场人物" else "Cast",
                        color = c.muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    GuBadge("${npcList.size}", variant = GuBadgeVariant.NEUTRAL)
                }
                GuButton(
                    onClick = onAddNpc,
                    compact = true,
                    variant = GuButtonVariant.SUBTLE,
                    modifier = Modifier.heightIn(min = 34.dp),
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(stripLeadingPlus(strings.rp.addNpcBtn), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (npcList.isEmpty()) {
            item(key = "__cast_empty__") {
                GuEmptyState(
                    title = if (isZh) "暂无 NPC 角色" else "No NPCs yet",
                    subtitle = if (isZh) "点击右上角新增角色，设定人设、好感与 8 表情立绘" else "Tap Add to create companions, rivals, and expression sprites",
                    icon = Icons.Default.Face,
                )
            }
        } else {
            items(npcList, key = { it.id }) { npc ->
                val resolvedPath = remember(npc.spritePath, npc.avatarPath, npc.mood, npc.expressionSprites) {
                    RpEngine.resolveCharacterSpritePath(npc)
                }
                val portraitModel = remember(resolvedPath) {
                    LocalMediaPathResolver.resolveImageModel(ctx, resolvedPath)
                }
                val boundExprCount = remember(npc.expressionSprites, npc.spritePath) {
                    val exprHits = RpEngine.STANDARD_EXPRESSIONS.count { key ->
                        npc.expressionSprites.entries.any {
                            it.key.equals(key, ignoreCase = true) && it.value.isNotBlank()
                        }
                    }
                    if (exprHits > 0) exprHits else if (!npc.spritePath.isNullOrBlank()) 1 else 0
                }
                val tier = RpEngine.affinityTier(npc.affinity)
                val tierLabel = strings.rp.affinityTiers[tier.code] ?: if (isZh) tier.zhLabel else tier.enLabel
                val roleLabel = localizeRpRole(npc.role, isZh)
                val isConfirmDelete = pendingDeleteId == npc.id

                GuCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpenNpcDetail(npc) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(c.surfaceSoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (portraitModel != null) {
                                Image(
                                    painter = rememberAsyncImagePainter(portraitModel),
                                    contentDescription = npc.name,
                                    contentScale = ContentScale.Crop,
                                    alignment = Alignment.TopCenter,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Text(
                                    text = npc.name.trim().take(1).uppercase().ifBlank { "?" },
                                    color = c.primarySoftInk,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = npc.name,
                                    color = c.ink,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (roleLabel.isNotBlank()) {
                                    GuBadge(roleLabel, variant = GuBadgeVariant.NEUTRAL)
                                }
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                GuBadge(
                                    text = if (npc.inScene) {
                                        strings.rp.npcInSceneToggle
                                    } else {
                                        if (isZh) "暂离场" else "Away"
                                    },
                                    variant = if (npc.inScene) GuBadgeVariant.EMERALD else GuBadgeVariant.NEUTRAL,
                                )
                                if (boundExprCount > 0) {
                                    GuBadge(
                                        text = if (isZh) "立绘 $boundExprCount/8" else "Sprites $boundExprCount/8",
                                        variant = GuBadgeVariant.BLUE,
                                    )
                                }
                                if (npc.mood.isNotBlank()) {
                                    GuMetaText(
                                        text = npc.mood,
                                        modifier = Modifier.align(Alignment.CenterVertically),
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { pendingDeleteId = if (isConfirmDelete) null else npc.id },
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = strings.common.delete,
                                tint = c.muted,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    if (npc.personality.isNotBlank()) {
                        Spacer(Modifier.height(7.dp))
                        Text(
                            text = npc.personality,
                            color = c.muted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(Modifier.height(9.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = affinityBarColor(tier.code, c),
                            modifier = Modifier.size(13.5.dp),
                        )
                        Text(
                            text = strings.rp.npcAffinityLabel.fmt("val" to npc.affinity),
                            color = c.ink,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        GuBadge(tierLabel, variant = affinityBadgeVariant(tier.code))
                        Spacer(Modifier.weight(1f))
                        GuButton(
                            onClick = { onAdjustAffinity(npc.id, -5) },
                            compact = true,
                            variant = GuButtonVariant.SUBTLE,
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp),
                            modifier = Modifier.heightIn(min = 30.dp),
                        ) {
                            Text("-5", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                        GuButton(
                            onClick = { onAdjustAffinity(npc.id, 5) },
                            compact = true,
                            variant = GuButtonVariant.SUBTLE,
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp),
                            modifier = Modifier.heightIn(min = 30.dp),
                        ) {
                            Text("+5", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    GuLinearProgressBar(
                        progress = tier.progress,
                        height = 5.dp,
                        color = affinityBarColor(tier.code, c),
                        trackColor = c.surfaceSoft,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (isConfirmDelete) {
                        Spacer(Modifier.height(10.dp))
                        RpInlineDeleteConfirmStrip(
                            prompt = strings.rp.deleteNpcConfirm.fmt("name" to npc.name),
                            confirmText = strings.common.delete,
                            cancelText = strings.common.cancel,
                            onConfirm = {
                                pendingDeleteId = null
                                onDeleteNpc(npc.id)
                            },
                            onCancel = { pendingDeleteId = null },
                        )
                    } else {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            GuMetaText(
                                text = if (isZh) "点击编辑档案与 8 表情立绘 ▸" else "Edit profile & 8 sprites ▸",
                                color = c.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== Push/Pop Subpage 1: Character Profile + 8 Sprites Unified ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RpCharacterUnifiedSubpage(
    rp: RpConfig,
    npc: RpCharacter,
    isNew: Boolean,
    isGeneratingArt: Boolean,
    artStatusLabel: String,
    onBack: () -> Unit,
    onSave: (RpCharacter) -> Unit,
    onAutoSaveBeforeBind: (RpCharacter) -> Unit,
    onPickAvatar: (String) -> Unit,
    onPickExpressionSprite: (charId: String, expressionKey: String) -> Unit,
    onClearExpressionSprite: (charId: String, expressionKey: String) -> Unit,
    onGenerateExpressionSprite: (charId: String, expressionKey: String) -> Unit,
    studioArtModelState: StudioArtModelUiState = StudioArtModelUiState(),
    onConfigureArtModel: (() -> Unit)? = null,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val ctx = LocalContext.current
    val isZh = strings.language == AppLanguage.ZH

    var name by remember(npc.id) { mutableStateOf(npc.name) }
    var role by remember(npc.id) { mutableStateOf(npc.role.ifBlank { "companion" }) }
    var mood by remember(npc.id) { mutableStateOf(npc.mood) }
    var appearance by remember(npc.id) { mutableStateOf(npc.appearance) }
    var appearancePrompt by remember(npc.id) { mutableStateOf(npc.appearancePrompt) }
    var personality by remember(npc.id) { mutableStateOf(npc.personality) }
    var speakingStyle by remember(npc.id) { mutableStateOf(npc.speakingStyle) }
    var secret by remember(npc.id) { mutableStateOf(npc.secret) }
    var inScene by remember(npc.id) { mutableStateOf(npc.inScene) }
    var affinity by remember(npc.id) { mutableIntStateOf(npc.affinity.coerceIn(-100, 100)) }
    var stageSlot by remember(npc.id) { mutableStateOf(npc.stageSlot.ifBlank { "auto" }) }
    var spriteScale by remember(npc.id) { mutableFloatStateOf(npc.spriteScale.coerceIn(0.7f, 1.4f)) }
    var spriteOffsetY by remember(npc.id) { mutableFloatStateOf(npc.spriteOffsetY.coerceIn(-60f, 60f)) }

    fun buildDraft(): RpCharacter = npc.copy(
        id = npc.id.ifBlank { UUID.randomUUID().toString() },
        name = name.trim().ifBlank { if (isZh) "未命名角色" else "Unnamed" },
        role = role.trim().ifBlank { "companion" },
        mood = mood.trim(),
        appearance = appearance.trim(),
        appearancePrompt = appearancePrompt.trim(),
        personality = personality.trim(),
        speakingStyle = speakingStyle.trim(),
        secret = secret.trim(),
        inScene = inScene,
        affinity = affinity.coerceIn(-100, 100),
        stageSlot = stageSlot,
        spriteScale = spriteScale.coerceIn(0.7f, 1.4f),
        spriteOffsetY = spriteOffsetY.roundToInt().toFloat().coerceIn(-60f, 60f),
    )

    val previewChar = buildDraft()
    val previewPath = remember(previewChar.mood, npc.expressionSprites, npc.spritePath, npc.avatarPath) {
        RpEngine.resolveCharacterSpritePath(previewChar)
    }
    val previewModel = remember(previewPath) {
        LocalMediaPathResolver.resolveImageModel(ctx, previewPath)
    }
    val avatarModel = remember(npc.avatarPath) {
        LocalMediaPathResolver.resolveImageModel(ctx, npc.avatarPath)
    }
    val boundCount = remember(npc.expressionSprites) {
        RpEngine.STANDARD_EXPRESSIONS.count { key ->
            npc.expressionSprites.entries.any { it.key.equals(key, ignoreCase = true) && it.value.isNotBlank() }
        }
    }
    val tier = RpEngine.affinityTier(affinity)
    val tierLabel = strings.rp.affinityTiers[tier.code] ?: if (isZh) tier.zhLabel else tier.enLabel

    Column(modifier = Modifier.fillMaxSize()) {
        StudioSubpageTopBar(
            backLabel = if (isZh) "返回角色" else "Cast",
            title = if (isNew) stripLeadingPlus(strings.rp.addNpcBtn) else name.ifBlank { strings.rp.editNpcTitle },
            onBack = onBack,
            trailing = {
                GuButton(
                    onClick = { onSave(buildDraft()) },
                    enabled = name.isNotBlank(),
                    compact = true,
                    modifier = Modifier.heightIn(min = 34.dp),
                ) {
                    Text(strings.common.save, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            },
        )

        AnimatedVisibility(visible = isGeneratingArt) {
            RpArtProgressBanner(
                statusLabel = artStatusLabel,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Live Mini Stage Preview Window
            val stageAccent = remember(rp.visualTheme.accentHex) {
                resolveRpStageAccent(rp.visualTheme.accentHex)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(142.dp)
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                TheaterStageBackdrop(
                    rp = rp,
                    stageAccent = stageAccent,
                    activeSlot = stageSlot,
                    backdropDim = 0.28f,
                    dimForReading = true,
                    modifier = Modifier.fillMaxSize(),
                )
                val align = when (stageSlot) {
                    "left" -> Alignment.BottomStart
                    "right" -> Alignment.BottomEnd
                    else -> Alignment.BottomCenter
                }
                if (previewModel != null) {
                    Image(
                        painter = rememberAsyncImagePainter(previewModel),
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier
                            .align(align)
                            .width(126.dp)
                            .fillMaxHeight(0.92f)
                            .graphicsLayer {
                                scaleX = spriteScale
                                scaleY = spriteScale
                                translationY = (spriteOffsetY * 0.45f).dp.toPx()
                                transformOrigin = TransformOrigin(0.5f, 1.0f)
                            },
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .align(align)
                            .padding(14.dp),
                    ) {
                        RpNpcPortraitAvatar(
                            character = previewChar,
                            fallbackName = name.ifBlank { "NPC" },
                            size = 64.dp,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xBA0D1220),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                ) {
                    Text(
                        text = "${localizeStageSlot(stageSlot, strings.rp)} · ${(spriteScale * 100f).roundToInt()}%",
                        color = Color(0xFFFFF4DC),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            // Profile Fields
            GuCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(c.primarySoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (avatarModel != null) {
                            Image(
                                painter = rememberAsyncImagePainter(avatarModel),
                                contentDescription = name,
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.TopCenter,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                text = name.trim().take(1).uppercase().ifBlank { "?" },
                                color = c.primarySoftInk,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    GuButton(
                        onClick = {
                            val draft = buildDraft()
                            if (isNew && name.isNotBlank()) onAutoSaveBeforeBind(draft)
                            onPickAvatar(draft.id)
                        },
                        enabled = name.isNotBlank(),
                        compact = true,
                        variant = GuButtonVariant.SUBTLE,
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(strings.rp.npcAvatarBtn, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(10.dp))
                GuTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = strings.rp.npcNameLabel,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = strings.rp.npcRoleLabel,
                    color = c.ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RP_ROLE_KEYS.forEach { roleKey ->
                        GuPill(
                            label = localizeRpRole(roleKey, isZh),
                            selected = role.equals(roleKey, ignoreCase = true),
                            onClick = { role = roleKey },
                            modifier = Modifier.heightIn(min = 32.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                GuTextField(
                    value = mood,
                    onValueChange = { mood = it },
                    label = strings.rp.npcMoodLabel,
                )
                Spacer(Modifier.height(10.dp))
                GuTextField(
                    value = appearance,
                    onValueChange = { appearance = it },
                    label = strings.rp.npcAppearanceLabel,
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3,
                )
                Spacer(Modifier.height(8.dp))
                GuTextField(
                    value = appearancePrompt,
                    onValueChange = { appearancePrompt = it },
                    label = strings.rp.appearancePromptLabel,
                    placeholder = strings.rp.appearancePromptPlaceholder,
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3,
                )
                Spacer(Modifier.height(8.dp))
                GuTextField(
                    value = personality,
                    onValueChange = { personality = it },
                    label = strings.rp.leadPersonalityLabel,
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                )
                Spacer(Modifier.height(8.dp))
                GuTextField(
                    value = speakingStyle,
                    onValueChange = { speakingStyle = it },
                    label = strings.rp.npcSpeakingStyleLabel,
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3,
                )
                Spacer(Modifier.height(8.dp))
                GuTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = strings.rp.npcSecretLabel,
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3,
                )
                Spacer(Modifier.height(6.dp))
                GuSwitchRow(
                    label = strings.rp.npcInSceneToggle,
                    checked = inScene,
                    onCheckedChange = { inScene = it },
                )
                if (!npc.isPlayer) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = strings.rp.npcAffinityLabel.fmt("val" to affinity),
                            color = c.ink,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        GuBadge(tierLabel, variant = affinityBadgeVariant(tier.code))
                    }
                    Slider(
                        value = affinity.toFloat(),
                        onValueChange = { affinity = it.roundToInt().coerceIn(-100, 100) },
                        valueRange = -100f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = c.primary,
                            activeTrackColor = c.primary,
                            inactiveTrackColor = c.outline,
                        ),
                    )
                }
            }

            // Stage Transform Controls
            val slotLabels = remember(strings) {
                RP_STAGE_SLOT_KEYS.map { localizeStageSlot(it, strings.rp) }
            }
            val selectedSlotIdx = RP_STAGE_SLOT_KEYS.indexOfFirst {
                it.equals(stageSlot, ignoreCase = true)
            }.coerceAtLeast(0)
            GuSectionHeader(title = strings.rp.stageSlotLabel)
            GuCard(modifier = Modifier.fillMaxWidth()) {
                GuSegmentedControl(
                    items = slotLabels,
                    selectedIndex = selectedSlotIdx,
                    onSelect = { idx -> stageSlot = RP_STAGE_SLOT_KEYS[idx] },
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(strings.rp.spriteScaleLabel, color = c.ink, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                    GuBadge("${(spriteScale * 100f).roundToInt()}%", variant = GuBadgeVariant.BLUE)
                }
                Slider(
                    value = spriteScale,
                    onValueChange = { spriteScale = it.coerceIn(0.7f, 1.4f) },
                    valueRange = 0.7f..1.4f,
                    colors = SliderDefaults.colors(
                        thumbColor = c.primary,
                        activeTrackColor = c.primary,
                        inactiveTrackColor = c.outline,
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(strings.rp.spriteOffsetYLabel, color = c.ink, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                    GuBadge("${spriteOffsetY.roundToInt()} dp", variant = GuBadgeVariant.NEUTRAL)
                }
                Slider(
                    value = spriteOffsetY,
                    onValueChange = { spriteOffsetY = it.coerceIn(-60f, 60f) },
                    valueRange = -60f..60f,
                    colors = SliderDefaults.colors(
                        thumbColor = c.primary,
                        activeTrackColor = c.primary,
                        inactiveTrackColor = c.outline,
                    ),
                )
            }

            // 8 Expression Sprite Slots
            GuSectionHeader(
                title = strings.rp.expressionsTitle,
                trailing = { GuBadge("$boundCount / 8", variant = GuBadgeVariant.BLUE) },
            )
            if (onConfigureArtModel != null) {
                StudioArtModelStatusRow(
                    state = studioArtModelState,
                    onConfigure = onConfigureArtModel,
                )
            }
            GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                RpEngine.STANDARD_EXPRESSIONS.forEachIndexed { idx, exprKey ->
                    val boundPath = npc.expressionSprites.entries
                        .firstOrNull { it.key.equals(exprKey, ignoreCase = true) }
                        ?.value
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                    val exprModel = remember(boundPath) {
                        LocalMediaPathResolver.resolveImageModel(ctx, boundPath)
                    }
                    val exprLabel = strings.rp.expressionLabels[exprKey] ?: exprKey

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 48.dp, height = 60.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.surfaceSoft)
                                .clickable {
                                    val draft = buildDraft()
                                    if (isNew && name.isNotBlank()) onAutoSaveBeforeBind(draft)
                                    onPickExpressionSprite(draft.id, exprKey)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (exprModel != null) {
                                Image(
                                    painter = rememberAsyncImagePainter(exprModel),
                                    contentDescription = exprLabel,
                                    contentScale = ContentScale.Crop,
                                    alignment = Alignment.TopCenter,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Face,
                                    contentDescription = null,
                                    tint = c.muted.copy(alpha = 0.65f),
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = exprLabel,
                                    color = c.ink,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (boundPath != null) {
                                    GuBadge(if (isZh) "已设" else "Set", variant = GuBadgeVariant.EMERALD)
                                }
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                GuButton(
                                    onClick = {
                                        val draft = buildDraft()
                                        if (isNew && name.isNotBlank()) onAutoSaveBeforeBind(draft)
                                        onPickExpressionSprite(draft.id, exprKey)
                                    },
                                    enabled = !isNew || name.isNotBlank(),
                                    compact = true,
                                    variant = GuButtonVariant.SUBTLE,
                                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                                    modifier = Modifier.heightIn(min = 32.dp),
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.rp.pickSpriteBtn, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                                GuButton(
                                    onClick = {
                                        val draft = buildDraft()
                                        if (isNew && name.isNotBlank()) onAutoSaveBeforeBind(draft)
                                        onGenerateExpressionSprite(draft.id, exprKey)
                                    },
                                    enabled = !isGeneratingArt && (!isNew || name.isNotBlank()),
                                    compact = true,
                                    variant = GuButtonVariant.HONEY,
                                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                                    modifier = Modifier.heightIn(min = 32.dp),
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.rp.aiGenExpressionBtn, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                                if (boundPath != null) {
                                    GuButton(
                                        onClick = { onClearExpressionSprite(npc.id, exprKey) },
                                        compact = true,
                                        variant = GuButtonVariant.SECONDARY,
                                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                                        modifier = Modifier.heightIn(min = 32.dp),
                                    ) {
                                        Text(strings.rp.clearSpriteBtn, color = c.danger, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                    if (idx < RpEngine.STANDARD_EXPRESSIONS.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 70.dp),
                            thickness = theme.borderWidth,
                            color = c.border.copy(alpha = 0.65f),
                        )
                    }
                }
            }
        }
    }
}

// ==================== Tab 1: 状态 (Act/Scene + Inline Vitals + Stepper Stats + Saves + CG/Endings) ====================
