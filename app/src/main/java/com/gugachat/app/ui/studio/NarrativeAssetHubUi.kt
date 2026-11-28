package com.gugachat.app.ui.studio

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuModalBottomSheet
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuSettingsRow
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.data.local.CharacterCardEntity
import com.gugachat.app.data.local.PersonaEntity
import com.gugachat.app.data.local.PromptPresetEntity
import com.gugachat.app.data.local.WorldbookEntity
import com.gugachat.app.domain.chat.PromptPresetBlock
import com.gugachat.app.domain.chat.RpCharacter
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpEngine
import com.gugachat.app.domain.chat.RpPresetTemplate
import com.gugachat.app.domain.chat.RpPresets
import com.gugachat.app.ui.images.ImageViewModel
import kotlinx.coroutines.launch

/**
 * Character & scenario showcase tab inside [CreativeStudioScreen].
 *
 * Hosts the top Persona switcher capsule, borderless filter pills, 2-column 2:3 staggered
 * poster grid combining imported SillyTavern/PNG/CHARX cards with built-in [RpPresets.ALL],
 * and the single-layer [CharacterCardLaunchSheet] / [PersonaManagerSheet].
 */
@Composable
fun CharactersHubTab(
    container: AppContainer,
    assetHubViewModel: AssetHubViewModel,
    imageViewModel: ImageViewModel,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    showSearchBar: Boolean,
    onCloseSearchBar: () -> Unit,
    showCreateCardDialog: Boolean,
    onDismissCreateCardDialog: () -> Unit,
    onRequestCreateOriginalCard: () -> Unit,
    onRequestImportCard: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onScrollStateChange: (Boolean) -> Unit,
) {
    val ui by assetHubViewModel.ui.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val isZh = strings.language == AppLanguage.ZH

    var activeFilter by rememberSaveable { mutableStateOf(CharacterFilterKind.ALL) }
    var showPersonaSheet by rememberSaveable { mutableStateOf(false) }
    var activeLaunchTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeLaunchIsPreset by rememberSaveable { mutableStateOf(false) }

    val gridState = rememberLazyStaggeredGridState()
    val isScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 6
        }
    }
    LaunchedEffect(isScrolled) { onScrollStateChange(isScrolled) }

    val cleanSearch = searchQuery.trim().lowercase()

    val filteredCards = remember(ui.characterCards, activeFilter, cleanSearch) {
        ui.characterCards.filter { card ->
            val matchFilter = when (activeFilter) {
                CharacterFilterKind.ALL -> true
                CharacterFilterKind.TAVERN -> !card.isOriginalCard()
                CharacterFilterKind.ORIGINAL -> card.isOriginalCard()
                CharacterFilterKind.OFFICIAL -> false
            }
            val matchQuery = cleanSearch.isEmpty() ||
                card.name.lowercase().contains(cleanSearch) ||
                card.description.lowercase().contains(cleanSearch) ||
                card.scenario.lowercase().contains(cleanSearch) ||
                card.creator.lowercase().contains(cleanSearch)
            matchFilter && matchQuery
        }
    }

    val filteredPresets = remember(activeFilter, cleanSearch) {
        if (activeFilter != CharacterFilterKind.ALL && activeFilter != CharacterFilterKind.OFFICIAL) {
            emptyList()
        } else {
            RpPresets.ALL.filter { preset ->
                cleanSearch.isEmpty() ||
                    preset.name.lowercase().contains(cleanSearch) ||
                    preset.description.lowercase().contains(cleanSearch)
            }
        }
    }

    val defaultPersonaName = ui.defaultPersona?.name?.takeIf { it.isNotBlank() }
        ?: if (isZh) "旅人 (默认)" else "Traveler"

    val activeLaunchTarget: LaunchTarget? = remember(activeLaunchTargetId, activeLaunchIsPreset, ui.characterCards) {
        val targetId = activeLaunchTargetId ?: return@remember null
        if (activeLaunchIsPreset) {
            RpPresets.ALL.firstOrNull { it.id == targetId }?.let { LaunchTarget.OfficialPreset(it) }
        } else {
            ui.characterCards.firstOrNull { it.id == targetId }?.let { LaunchTarget.Card(it) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top 40dp capsule + filter pills strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // [👤 自设: <defaultPersonaName> ▾]
            Surface(
                onClick = { showPersonaSheet = true },
                shape = RoundedCornerShape(theme.pillRadius),
                color = if (ui.defaultPersona != null) c.primarySoft else c.surfaceSoft,
                modifier = Modifier.height(32.dp),
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .widthIn(max = 176.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = if (ui.defaultPersona != null) c.primarySoftInk else c.muted,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = if (isZh) "自设: $defaultPersonaName" else "Persona: $defaultPersonaName",
                        color = if (ui.defaultPersona != null) c.primarySoftInk else c.ink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = if (ui.defaultPersona != null) c.primarySoftInk else c.muted,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }

            CharacterFilterPill(
                label = if (isZh) "全部" else "All",
                selected = activeFilter == CharacterFilterKind.ALL,
                onClick = { activeFilter = CharacterFilterKind.ALL },
            )
            CharacterFilterPill(
                label = if (isZh) "酒馆卡" else "Tavern",
                selected = activeFilter == CharacterFilterKind.TAVERN,
                onClick = { activeFilter = CharacterFilterKind.TAVERN },
            )
            CharacterFilterPill(
                label = if (isZh) "原创" else "Original",
                selected = activeFilter == CharacterFilterKind.ORIGINAL,
                onClick = { activeFilter = CharacterFilterKind.ORIGINAL },
            )
            CharacterFilterPill(
                label = if (isZh) "官方剧本" else "Official",
                selected = activeFilter == CharacterFilterKind.OFFICIAL,
                onClick = { activeFilter = CharacterFilterKind.OFFICIAL },
            )
        }

        GuCollapsibleSearchBar(
            visible = showSearchBar,
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            placeholder = if (isZh) "搜索角色名、标签或世界观..." else "Search characters or scenarios...",
            onClose = onCloseSearchBar,
            clearContentDescription = strings.common.clear,
            closeText = strings.common.close,
            useTextCloseButton = true,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        )

        LazyVerticalStaggeredGrid(
            state = gridState,
            columns = StaggeredGridCells.Fixed(2),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 92.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalItemSpacing = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            item(key = "card_import_banner", span = StaggeredGridItemSpan.FullLine) {
                GuCard(
                    onClick = onRequestImportCard,
                    backgroundColor = c.surface,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GuIconTile(
                            icon = Icons.Default.Download,
                            tint = c.primarySoftInk,
                            backgroundColor = c.primarySoft,
                            size = 34.dp,
                            iconSize = 18.dp,
                            cornerRadius = 10.dp,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isZh) "导入 .png / .charx / .json" else "Import .png / .charx / .json",
                                color = c.ink,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (isZh) "SillyTavern 角色卡 & 内嵌世界书" else "Tavern V2/V3 cards & embedded worldbooks",
                                color = c.muted,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (ui.isBusy) {
                            CircularProgressIndicator(
                                color = c.primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            GuButton(
                                onClick = onRequestCreateOriginalCard,
                                variant = GuButtonVariant.SECONDARY,
                                compact = true,
                            ) {
                                Text(if (isZh) "＋ 原创" else "+ New", fontSize = 11.5.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }

            if (filteredCards.isEmpty() && filteredPresets.isEmpty()) {
                item(key = "chars_empty", span = StaggeredGridItemSpan.FullLine) {
                    GuEmptyState(
                        title = if (isZh) "暂无匹配的角色卡" else "No matching characters",
                        subtitle = if (isZh) "点击上方卡片导入酒馆角色卡，或创建原创角色" else "Import a card above or create an original one",
                        actionLabel = if (isZh) "导入角色卡" else "Import Card",
                        onAction = onRequestImportCard,
                        icon = Icons.Default.Person,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            items(items = filteredCards, key = { "card_${it.id}" }) { card ->
                val avatarModel = remember(card.avatarPath, card.spritePath) {
                    LocalMediaPathResolver.resolveImageModel(ctx, card.avatarPath ?: card.spritePath)
                }
                val greetingsCount = remember(card.firstMes, card.alternateGreetingsJson) {
                    card.allGreetings().size
                }
                val bioText = card.scenario.ifBlank {
                    card.description.ifBlank { card.personality }
                }.replace('\n', ' ').trim()

                CharacterPosterCard(
                    title = card.name.ifBlank { if (isZh) "未命名角色" else "Untitled" },
                    subtitle = bioText.ifBlank { if (isZh) "点击查看开场白与开局配置" else "Tap to configure campaign" },
                    topBadge = buildString {
                        append(card.specBadgeLabel(isZh))
                        if (greetingsCount > 1) {
                            append(" · ")
                            append(if (isZh) "${greetingsCount}开场" else "${greetingsCount}G")
                        }
                    },
                    imageModel = avatarModel,
                    imageLoader = container.imageLoader,
                    gradientSpec = resolveCardGradient(card.id),
                    isFavorite = card.favorite,
                    onToggleFavorite = { assetHubViewModel.toggleCardFavorite(card.id) },
                    isLaunching = ui.isLaunching,
                    launchLabel = if (isZh) "开局" else "Play",
                    onCardClick = {
                        activeLaunchIsPreset = false
                        activeLaunchTargetId = card.id
                    },
                    onQuickLaunchClick = {
                        scope.launch {
                            val defaultWbIds = listOfNotNull(card.primaryWorldbookId?.takeIf { it.isNotBlank() })
                            assetHubViewModel.launchCampaignFromCard(
                                card = card,
                                selectedGreetingIndex = 0,
                                personaId = ui.defaultPersona?.id,
                                worldbookIds = defaultWbIds,
                                presetId = ui.promptPresets.firstOrNull { it.isDefaultRp }?.id,
                                onOpenConversation = onOpenConversation,
                            )
                        }
                    },
                )
            }

            items(items = filteredPresets, key = { "preset_${it.id}" }) { preset ->
                val presetCoverModel = remember(preset.id) {
                    LocalMediaPathResolver.resolveImageModel(ctx, preset.primarySpritePath)
                }
                CharacterPosterCard(
                    title = preset.name,
                    subtitle = preset.description,
                    topBadge = if (isZh) "官方剧本" else "Official",
                    imageModel = presetCoverModel,
                    imageLoader = container.imageLoader,
                    gradientSpec = resolvePresetGradient(preset.id),
                    isFavorite = null,
                    onToggleFavorite = null,
                    isLaunching = ui.isLaunching,
                    launchLabel = if (isZh) "开局" else "Play",
                    onCardClick = {
                        activeLaunchIsPreset = true
                        activeLaunchTargetId = preset.id
                    },
                    onQuickLaunchClick = {
                        scope.launch {
                            assetHubViewModel.launchCampaignFromPreset(
                                presetTemplate = preset,
                                selectedGreetingIndex = 0,
                                personaId = ui.defaultPersona?.id,
                                worldbookIds = emptyList(),
                                promptPresetId = ui.promptPresets.firstOrNull { it.isDefaultRp }?.id,
                                onOpenConversation = onOpenConversation,
                            )
                        }
                    },
                )
            }
        }
    }

    if (showPersonaSheet) {
        PersonaManagerSheet(
            personas = ui.personas,
            onSavePersona = { id, name, desc, pos, depth, makeDefault ->
                assetHubViewModel.savePersona(id, name, desc, pos, depth, makeDefault)
            },
            onSetDefault = { assetHubViewModel.setDefaultPersona(it) },
            onDeletePersona = { assetHubViewModel.deletePersona(it) },
            onDismiss = { showPersonaSheet = false },
        )
    }

    if (showCreateCardDialog) {
        CreateOriginalCardSheet(
            onCreate = { name, desc, personality, scenario, firstMes, style ->
                assetHubViewModel.createCustomCharacterCard(
                    name = name,
                    description = desc,
                    personality = personality,
                    scenario = scenario,
                    firstMes = firstMes,
                    playStyle = style,
                )
                onDismissCreateCardDialog()
            },
            onDismiss = onDismissCreateCardDialog,
        )
    }

    if (activeLaunchTarget != null) {
        CharacterCardLaunchSheet(
            container = container,
            target = activeLaunchTarget,
            personas = ui.personas,
            defaultPersona = ui.defaultPersona,
            worldbooks = ui.worldbooks,
            presets = ui.promptPresets,
            isLaunching = ui.isLaunching,
            onRequestAiPortrait = { card, prompt ->
                imageViewModel.openCreateForCardPortrait(
                    promptText = prompt,
                    aspectRatio = "2:3",
                ) { bytes ->
                    assetHubViewModel.attachCardPortraitBytes(card.id, bytes)
                }
            },
            onAttachLocalPortrait = { cardId, uri ->
                assetHubViewModel.attachCardPortraitUri(cardId, uri, ctx.contentResolver)
            },
            onToggleFavorite = { cardId -> assetHubViewModel.toggleCardFavorite(cardId) },
            onDeleteCard = { cardId ->
                assetHubViewModel.deleteCharacterCard(cardId)
                activeLaunchTargetId = null
            },
            onSavePersonaInline = { name, desc, makeDefault ->
                assetHubViewModel.savePersona(null, name, desc, 0, 4, makeDefault)
            },
            onLaunchCard = { card, greetingIdx, personaId, wbIds, presetId ->
                scope.launch {
                    assetHubViewModel.launchCampaignFromCard(
                        card = card,
                        selectedGreetingIndex = greetingIdx,
                        personaId = personaId,
                        worldbookIds = wbIds,
                        presetId = presetId,
                        onOpenConversation = { cid ->
                            activeLaunchTargetId = null
                            onOpenConversation(cid)
                        },
                    )
                }
            },
            onLaunchOfficialPreset = { preset, greetingIdx, personaId, wbIds, promptPresetId ->
                scope.launch {
                    assetHubViewModel.launchCampaignFromPreset(
                        presetTemplate = preset,
                        selectedGreetingIndex = greetingIdx,
                        personaId = personaId,
                        worldbookIds = wbIds,
                        promptPresetId = promptPresetId,
                        onOpenConversation = { cid ->
                            activeLaunchTargetId = null
                            onOpenConversation(cid)
                        },
                    )
                }
            },
            onDismiss = { activeLaunchTargetId = null },
        )
    }
}

@Composable
internal fun CharacterPosterCard(
    title: String,
    subtitle: String,
    topBadge: String,
    imageModel: Any?,
    imageLoader: coil.ImageLoader,
    gradientSpec: CardGradientSpec,
    isFavorite: Boolean?,
    onToggleFavorite: (() -> Unit)?,
    isLaunching: Boolean,
    launchLabel: String,
    onCardClick: () -> Unit,
    onQuickLaunchClick: () -> Unit,
) {
    val theme = LocalAppTheme.current
    Surface(
        onClick = onCardClick,
        shape = RoundedCornerShape(16.dp),
        color = gradientSpec.end,
        border = null,
        shadowElevation = if (theme.isDark) 0.dp else 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.667f),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Backdrop: real local image or bundled asset if present, else deterministic aesthetic gradient
            if (imageModel != null) {
                AsyncImage(
                    model = imageModel,
                    imageLoader = imageLoader,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(gradientSpec.start, gradientSpec.end),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = gradientSpec.icon,
                        contentDescription = null,
                        tint = gradientSpec.accent.copy(alpha = 0.22f),
                        modifier = Modifier.size(64.dp),
                    )
                    Text(
                        text = title.take(1).uppercase(),
                        color = gradientSpec.accent.copy(alpha = 0.42f),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Bottom gradient scrim for clear contrast
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.56f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.56f),
                                Color.Black.copy(alpha = 0.88f),
                            ),
                        ),
                    ),
            )

            // Top micro-pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Black.copy(alpha = 0.48f),
                ) {
                    Text(
                        text = topBadge,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }

                if (isFavorite != null && onToggleFavorite != null) {
                    Surface(
                        onClick = onToggleFavorite,
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.42f),
                        modifier = Modifier.size(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null,
                                tint = if (isFavorite) Color(0xFFF6C65B) else Color.White.copy(alpha = 0.88f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            // Bottom info + [▶ 开局] pill
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Surface(
                        onClick = { if (!isLaunching) onQuickLaunchClick() },
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.92f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color(0xFF141824),
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = launchLabel,
                                color = Color(0xFF141824),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== Launch Configuration Sheet (Single-Layer 0.90f) ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CharacterCardLaunchSheet(
    container: AppContainer,
    target: LaunchTarget,
    personas: List<PersonaEntity>,
    defaultPersona: PersonaEntity?,
    worldbooks: List<WorldbookEntity>,
    presets: List<PromptPresetEntity>,
    isLaunching: Boolean,
    onRequestAiPortrait: (CharacterCardEntity, String) -> Unit,
    onAttachLocalPortrait: (String, android.net.Uri) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDeleteCard: (String) -> Unit,
    onSavePersonaInline: (name: String, desc: String, makeDefault: Boolean) -> Unit,
    onLaunchCard: (CharacterCardEntity, Int, String?, List<String>, String?) -> Unit,
    onLaunchOfficialPreset: (RpPresetTemplate, Int, String?, List<String>, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val ctx = LocalContext.current
    val isZh = strings.language == AppLanguage.ZH
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val cardTarget = (target as? LaunchTarget.Card)?.card
    val presetTarget = (target as? LaunchTarget.OfficialPreset)?.preset

    val title = cardTarget?.name?.ifBlank { if (isZh) "未命名角色" else "Untitled" }
        ?: presetTarget?.name.orEmpty()
    val greetings = remember(cardTarget?.firstMes, cardTarget?.alternateGreetingsJson, presetTarget?.id) {
        cardTarget?.allGreetings() ?: presetTarget?.allGreetings().orEmpty()
    }

    var subpage by remember(cardTarget?.id, presetTarget?.id) { mutableStateOf(LaunchSheetSubpage.MAIN) }
    var selectedGreetingIdx by remember(cardTarget?.id, presetTarget?.id) { mutableIntStateOf(0) }
    var selectedPersonaId by remember(cardTarget?.id, presetTarget?.id, defaultPersona?.id) {
        mutableStateOf(defaultPersona?.id)
    }
    val selectedWorldbookIds = remember(cardTarget?.id, presetTarget?.id) {
        mutableStateListOf<String>().apply {
            cardTarget?.primaryWorldbookId?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
    }
    var selectedPresetId by remember(cardTarget?.id, presetTarget?.id) {
        mutableStateOf(presets.firstOrNull { it.isDefaultRp }?.id)
    }
    var confirmDelete by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null && cardTarget != null) {
            onAttachLocalPortrait(cardTarget.id, uri)
        }
    }

    val activePersona = personas.firstOrNull { it.id == selectedPersonaId } ?: defaultPersona
    val activePreset = presets.firstOrNull { it.id == selectedPresetId }
    val activePersonaName = activePersona?.name?.takeIf { it.isNotBlank() }
        ?: presetTarget?.config?.playerName
        ?: if (isZh) "旅人" else "Traveler"

    val previewRpConfig = remember(cardTarget, presetTarget, activePersonaName, activePersona?.description) {
        if (cardTarget != null) {
            val leadChar = decodeJsonList<RpCharacter>(cardTarget.castJson).firstOrNull { !it.isPlayer }
            RpConfig(
                title = cardTarget.name,
                playerName = activePersonaName,
                playerPersona = activePersona?.description.orEmpty(),
                cast = listOfNotNull(leadChar ?: RpCharacter(id = "npc", name = cardTarget.name)),
            )
        } else {
            val base = presetTarget?.config ?: RpConfig()
            base.copy(
                playerName = activePersonaName,
                playerPersona = activePersona?.description ?: base.playerPersona,
            )
        }
    }

    if (confirmDelete && cardTarget != null) {
        GuConfirmDialog(
            title = if (isZh) "删除角色卡" else "Delete Card",
            message = if (isZh) "确认将「${cardTarget.name}」从角色库移除？已开启的对话记录不会被删除。"
            else "Remove \"${cardTarget.name}\" from library? Existing conversations are kept.",
            confirmText = strings.common.delete,
            cancelText = strings.common.cancel,
            isDestructive = true,
            onConfirm = {
                confirmDelete = false
                onDeleteCard(cardTarget.id)
            },
            onDismiss = { confirmDelete = false },
        )
    }

    GuModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        BackHandler(enabled = subpage != LaunchSheetSubpage.MAIN) {
            subpage = LaunchSheetSubpage.MAIN
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            when (subpage) {
                LaunchSheetSubpage.MAIN -> {
                    GuSheetHeader(
                        title = title,
                        subtitle = if (cardTarget != null) {
                            cardTarget.creator.ifBlank { cardTarget.specBadgeLabel(isZh) }
                        } else {
                            if (isZh) "官方经典剧本" else "Official Scenario"
                        },
                        closeContentDescription = strings.common.close,
                        onClose = onDismiss,
                        trailing = {
                            if (cardTarget != null) {
                                IconButton(onClick = { onToggleFavorite(cardTarget.id) }) {
                                    Icon(
                                        imageVector = if (cardTarget.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = null,
                                        tint = if (cardTarget.favorite) Color(0xFFF6C65B) else c.muted,
                                    )
                                }
                                IconButton(onClick = { confirmDelete = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = strings.common.delete,
                                        tint = c.danger,
                                    )
                                }
                            }
                        },
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Top visual hero row + AI Portrait trigger
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val coverModel = remember(cardTarget?.avatarPath, cardTarget?.spritePath, presetTarget?.id) {
                                LocalMediaPathResolver.resolveImageModel(
                                    ctx,
                                    cardTarget?.avatarPath ?: cardTarget?.spritePath ?: presetTarget?.primarySpritePath,
                                )
                            }
                            val grad = if (cardTarget != null) resolveCardGradient(cardTarget.id)
                            else resolvePresetGradient(presetTarget?.id.orEmpty())

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = grad.end,
                                modifier = Modifier.size(width = 68.dp, height = 102.dp),
                            ) {
                                if (coverModel != null) {
                                    AsyncImage(
                                        model = coverModel,
                                        imageLoader = container.imageLoader,
                                        contentDescription = title,
                                        contentScale = ContentScale.Crop,
                                        alignment = Alignment.TopCenter,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Brush.linearGradient(listOf(grad.start, grad.end))),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = grad.icon,
                                            contentDescription = null,
                                            tint = grad.accent.copy(alpha = 0.7f),
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = cardTarget?.description?.ifBlank { cardTarget.scenario }
                                        ?: presetTarget?.description.orEmpty(),
                                    color = c.muted,
                                    fontSize = 12.5.sp,
                                    lineHeight = 17.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                if (cardTarget != null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        GuButton(
                                            onClick = {
                                                val cast = decodeJsonList<RpCharacter>(cardTarget.castJson)
                                                val lead = cast.firstOrNull { !it.isPlayer }
                                                val descHint = lead?.appearancePrompt?.ifBlank { null }
                                                    ?: lead?.appearance?.ifBlank { null }
                                                    ?: cardTarget.description.take(180)
                                                val prompt = buildString {
                                                    append("character portrait, detailed illustration, ")
                                                    append(cardTarget.name)
                                                    if (descHint.isNotBlank()) {
                                                        append(", ")
                                                        append(descHint)
                                                    }
                                                }
                                                onRequestAiPortrait(cardTarget, prompt)
                                            },
                                            variant = GuButtonVariant.HONEY,
                                            compact = true,
                                        ) {
                                            Icon(
                                                Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (isZh) "✨ AI 补立绘" else "✨ AI Portrait", fontSize = 12.sp)
                                        }

                                        GuButton(
                                            onClick = { imagePicker.launch("image/*") },
                                            variant = GuButtonVariant.SECONDARY,
                                            compact = true,
                                        ) {
                                            Icon(
                                                Icons.Default.Image,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (isZh) "相册" else "Photo", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // Horizontal Greeting Switcher + Live {{user}} preview
                        if (greetings.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = if (isZh) "开场白与 {{user}} 实时预览" else "Greetings & Live Preview",
                                        color = c.ink,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    GuBadge(
                                        text = if (isZh) "共 ${greetings.size} 个" else "${greetings.size}",
                                        variant = GuBadgeVariant.BLUE,
                                    )
                                }

                                if (greetings.size > 1) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(greetings.size, key = { it }) { idx ->
                                            GuPill(
                                                label = if (isZh) "开场 ${idx + 1}" else "Greeting ${idx + 1}",
                                                selected = selectedGreetingIdx == idx,
                                                onClick = { selectedGreetingIdx = idx },
                                            )
                                        }
                                    }
                                }

                                val safeIdx = selectedGreetingIdx.coerceIn(0, greetings.lastIndex)
                                val renderedPreview = remember(greetings, safeIdx, previewRpConfig) {
                                    RpEngine.replaceMacros(greetings[safeIdx], previewRpConfig)
                                }
                                GuCard(
                                    backgroundColor = c.surfaceSoft,
                                    contentPadding = PaddingValues(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = renderedPreview,
                                        color = c.ink,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                        maxLines = 7,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        // 3-row GuGroupedCard: Persona / Multi-Worldbook / Prompt Preset
                        GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                            GuSettingsRow(
                                title = if (isZh) "玩家自设 (Persona)" else "Player Persona",
                                icon = Icons.Default.Person,
                                iconTint = c.primarySoftInk,
                                iconBg = c.primarySoft,
                                valueText = activePersona?.name ?: if (isZh) "旅人 (默认)" else "Default",
                                showChevron = true,
                                showDivider = true,
                                onClick = { subpage = LaunchSheetSubpage.PICK_PERSONA },
                            )
                            GuSettingsRow(
                                title = if (isZh) "挂载世界书" else "Mount Worldbooks",
                                icon = Icons.Default.Book,
                                iconTint = c.tagInk,
                                iconBg = c.tag,
                                valueText = if (isZh) "已选 ${selectedWorldbookIds.size} 本" else "${selectedWorldbookIds.size} selected",
                                showChevron = true,
                                showDivider = true,
                                onClick = { subpage = LaunchSheetSubpage.PICK_WORLDBOOKS },
                            )
                            GuSettingsRow(
                                title = if (isZh) "提示词预设" else "Prompt Preset",
                                icon = Icons.Default.Tune,
                                iconTint = c.success,
                                iconBg = c.successBg,
                                valueText = activePreset?.name ?: if (isZh) "原生默认" else "Default",
                                showChevron = true,
                                showDivider = false,
                                onClick = { subpage = LaunchSheetSubpage.PICK_PRESET },
                            )
                        }
                    }

                    // Bottom action bar: [仅存库] & [🎭 开启新周目 →]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GuButton(
                            onClick = onDismiss,
                            variant = GuButtonVariant.SECONDARY,
                        ) {
                            Text(if (isZh) "仅存库" else "Keep Saved")
                        }
                        GuButton(
                            onClick = {
                                val safeIdx = if (greetings.isEmpty()) 0 else selectedGreetingIdx.coerceIn(0, greetings.lastIndex)
                                if (cardTarget != null) {
                                    onLaunchCard(
                                        cardTarget,
                                        safeIdx,
                                        selectedPersonaId,
                                        selectedWorldbookIds.toList(),
                                        selectedPresetId,
                                    )
                                } else if (presetTarget != null) {
                                    onLaunchOfficialPreset(
                                        presetTarget,
                                        safeIdx,
                                        selectedPersonaId,
                                        selectedWorldbookIds.toList(),
                                        selectedPresetId,
                                    )
                                }
                            },
                            loading = isLaunching,
                            enabled = !isLaunching,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (isZh) "🎭 开启新周目 →" else "🎭 Launch Campaign →")
                        }
                    }
                }

                LaunchSheetSubpage.PICK_PERSONA -> {
                    LaunchSubpageHeader(
                        title = if (isZh) "选择玩家自设" else "Select Persona",
                        onBack = { subpage = LaunchSheetSubpage.MAIN },
                        onClose = onDismiss,
                    )
                    var quickPersonaName by remember { mutableStateOf("") }
                    var quickPersonaDesc by remember { mutableStateOf("") }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "default_traveler") {
                            GuCard(
                                onClick = {
                                    selectedPersonaId = null
                                    subpage = LaunchSheetSubpage.MAIN
                                },
                                backgroundColor = if (selectedPersonaId == null) c.primarySoft else c.surface,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isZh) "旅人 (默认无自设)" else "Traveler (Default)",
                                            color = c.ink,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = if (isZh) "使用剧本或角色卡默认玩家设定" else "Use card/scenario default player identity",
                                            color = c.muted,
                                            fontSize = 12.sp,
                                        )
                                    }
                                    RadioButton(
                                        selected = selectedPersonaId == null,
                                        onClick = {
                                            selectedPersonaId = null
                                            subpage = LaunchSheetSubpage.MAIN
                                        },
                                    )
                                }
                            }
                        }
                        items(personas, key = { it.id }) { p ->
                            GuCard(
                                onClick = {
                                    selectedPersonaId = p.id
                                    subpage = LaunchSheetSubpage.MAIN
                                },
                                backgroundColor = if (selectedPersonaId == p.id) c.primarySoft else c.surface,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(p.name, color = c.ink, fontWeight = FontWeight.SemiBold)
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
                                    RadioButton(
                                        selected = selectedPersonaId == p.id,
                                        onClick = {
                                            selectedPersonaId = p.id
                                            subpage = LaunchSheetSubpage.MAIN
                                        },
                                    )
                                }
                            }
                        }
                        item(key = "quick_add_persona") {
                            GuCard(backgroundColor = c.surfaceSoft) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = if (isZh) "快速新建自设" else "Quick New Persona",
                                        color = c.ink,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                    )
                                    GuTextField(
                                        value = quickPersonaName,
                                        onValueChange = { quickPersonaName = it },
                                        placeholder = if (isZh) "玩家称呼 ({{user}})" else "Persona name ({{user}})",
                                    )
                                    GuTextField(
                                        value = quickPersonaDesc,
                                        onValueChange = { quickPersonaDesc = it },
                                        placeholder = if (isZh) "身份、外貌与背景简介" else "Background / appearance",
                                        singleLine = false,
                                        minLines = 2,
                                        maxLines = 4,
                                    )
                                    GuButton(
                                        onClick = {
                                            if (quickPersonaName.isNotBlank()) {
                                                onSavePersonaInline(quickPersonaName, quickPersonaDesc, personas.isEmpty())
                                                quickPersonaName = ""
                                                quickPersonaDesc = ""
                                            }
                                        },
                                        enabled = quickPersonaName.isNotBlank(),
                                        compact = true,
                                    ) {
                                        Text(if (isZh) "保存并添加到列表" else "Save Persona")
                                    }
                                }
                            }
                        }
                    }
                }

                LaunchSheetSubpage.PICK_WORLDBOOKS -> {
                    LaunchSubpageHeader(
                        title = if (isZh) "挂载世界书 (${selectedWorldbookIds.size})" else "Mount Worldbooks (${selectedWorldbookIds.size})",
                        onBack = { subpage = LaunchSheetSubpage.MAIN },
                        onClose = onDismiss,
                    )
                    if (worldbooks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            GuEmptyState(
                                title = if (isZh) "设定库暂无世界书" else "No worldbooks in library",
                                subtitle = if (isZh) "可在「设定」页导入或新建世界书" else "Import or create worldbooks in Settings tab",
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
                            items(worldbooks, key = { it.id }) { wb ->
                                val checked = wb.id in selectedWorldbookIds
                                GuCard(
                                    onClick = {
                                        if (checked) selectedWorldbookIds.remove(wb.id) else selectedWorldbookIds.add(wb.id)
                                    },
                                    backgroundColor = if (checked) c.primarySoft else c.surface,
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = {
                                                if (checked) selectedWorldbookIds.remove(wb.id) else selectedWorldbookIds.add(wb.id)
                                            },
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Text(wb.name, color = c.ink, fontWeight = FontWeight.SemiBold)
                                                if (wb.globalEnabled) {
                                                    GuBadge(
                                                        text = if (isZh) "全局已激活" else "Global",
                                                        variant = GuBadgeVariant.EMERALD,
                                                    )
                                                }
                                            }
                                            Text(
                                                text = if (isZh) "${wb.entryCount} 条词条 · ~${wb.totalEstimatedTokens} tokens"
                                                else "${wb.entryCount} entries · ~${wb.totalEstimatedTokens} tokens",
                                                color = c.muted,
                                                fontSize = 12.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        GuButton(
                            onClick = { subpage = LaunchSheetSubpage.MAIN },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(strings.common.done)
                        }
                    }
                }

                LaunchSheetSubpage.PICK_PRESET -> {
                    LaunchSubpageHeader(
                        title = if (isZh) "选择提示词预设" else "Select Prompt Preset",
                        onBack = { subpage = LaunchSheetSubpage.MAIN },
                        onClose = onDismiss,
                    )
                    val nonRegexPresets = remember(presets) {
                        presets.filter { it.sourceFormat != "sillytavern_regex" }
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "preset_none") {
                            GuCard(
                                onClick = {
                                    selectedPresetId = null
                                    subpage = LaunchSheetSubpage.MAIN
                                },
                                backgroundColor = if (selectedPresetId == null) c.primarySoft else c.surface,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = if (isZh) "原生默认系统规则" else "Native Default Rules",
                                        color = c.ink,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    RadioButton(
                                        selected = selectedPresetId == null,
                                        onClick = {
                                            selectedPresetId = null
                                            subpage = LaunchSheetSubpage.MAIN
                                        },
                                    )
                                }
                            }
                        }
                        items(nonRegexPresets, key = { it.id }) { preset ->
                            val blocks = remember(preset.blocksJson) {
                                decodeJsonList<PromptPresetBlock>(preset.blocksJson)
                            }
                            GuCard(
                                onClick = {
                                    selectedPresetId = preset.id
                                    subpage = LaunchSheetSubpage.MAIN
                                },
                                backgroundColor = if (selectedPresetId == preset.id) c.primarySoft else c.surface,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(preset.name, color = c.ink, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            text = if (isZh) "${blocks.count { it.enabled }}/${blocks.size} 模块启用"
                                            else "${blocks.count { it.enabled }}/${blocks.size} blocks enabled",
                                            color = c.muted,
                                            fontSize = 12.sp,
                                        )
                                    }
                                    RadioButton(
                                        selected = selectedPresetId == preset.id,
                                        onClick = {
                                            selectedPresetId = preset.id
                                            subpage = LaunchSheetSubpage.MAIN
                                        },
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

// ==================== Persona Manager Sheet ====================

