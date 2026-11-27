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
import com.gugachat.app.core.theme.GuCompactSearchBar
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuInlineStepper
import com.gugachat.app.core.theme.GuModalDragHandle
import com.gugachat.app.core.theme.GuPill
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
import kotlin.math.abs

private enum class CharacterFilterKind { ALL, TAVERN, ORIGINAL, OFFICIAL }
private enum class SettingFilterKind { ALL, WORLDBOOK, PRESET, REGEX }

private sealed interface LaunchTarget {
    data class Card(val card: CharacterCardEntity) : LaunchTarget
    data class OfficialPreset(val preset: RpPresetTemplate) : LaunchTarget
}

private data class CardGradientSpec(
    val start: Color,
    val end: Color,
    val accent: Color,
    val icon: ImageVector,
)

private fun resolveCardGradient(seed: String): CardGradientSpec {
    val palettes = listOf(
        CardGradientSpec(Color(0xFF2E3B6E), Color(0xFF141830), Color(0xFF9FB8FF), Icons.Default.AutoStories),
        CardGradientSpec(Color(0xFF5D2A42), Color(0xFF21101C), Color(0xFFF6B7CB), Icons.Default.TheaterComedy),
        CardGradientSpec(Color(0xFF1F4E5F), Color(0xFF0E212B), Color(0xFF7BE0EA), Icons.Default.Public),
        CardGradientSpec(Color(0xFF543C22), Color(0xFF22170D), Color(0xFFF6C67D), Icons.Default.EmojiEvents),
        CardGradientSpec(Color(0xFF2C4A3B), Color(0xFF122019), Color(0xFF8BE2B6), Icons.Default.Landscape),
        CardGradientSpec(Color(0xFF423266), Color(0xFF1A122E), Color(0xFFCBB6FF), Icons.Default.AutoAwesome),
    )
    val idx = abs(seed.hashCode()) % palettes.size
    return palettes[idx]
}

private fun resolvePresetGradient(presetId: String): CardGradientSpec = when (presetId.lowercase()) {
    "palace_intrigue" -> CardGradientSpec(Color(0xFF6E1D34), Color(0xFF2B0E1A), Color(0xFFF6D38A), Icons.Default.EmojiEvents)
    "modern_tavern" -> CardGradientSpec(Color(0xFF2B2D63), Color(0xFF141736), Color(0xFFF6B95E), Icons.Default.AutoStories)
    "fantasy_rpg" -> CardGradientSpec(Color(0xFF5A3718), Color(0xFF251A10), Color(0xFFFCD34D), Icons.Default.Security)
    "cyberpunk_noir" -> CardGradientSpec(Color(0xFF0E4A60), Color(0xFF091B2C), Color(0xFF67E8F9), Icons.Default.Code)
    "wuxia" -> CardGradientSpec(Color(0xFF2F3D52), Color(0xFF141B28), Color(0xFFE2E8F0), Icons.Default.Landscape)
    "steampunk_coc" -> CardGradientSpec(Color(0xFF453224), Color(0xFF1C1612), Color(0xFFD9B27C), Icons.Default.Search)
    "space_mercenary" -> CardGradientSpec(Color(0xFF1B3A63), Color(0xFF0B1830), Color(0xFF7DD3FC), Icons.Default.Public)
    else -> resolveCardGradient(presetId)
}

private fun CharacterCardEntity.isOriginalCard(): Boolean {
    val s = sourceSpec.lowercase()
    return s == "gugachat_original" || s == "legacy_rp" || s.isBlank()
}

private fun CharacterCardEntity.specBadgeLabel(isZh: Boolean): String {
    val s = sourceSpec.lowercase()
    return when {
        s.contains("v3") || s.contains("charx") -> "V3"
        s.contains("v2") || s.contains("tavern") || s.contains("chara") -> "V2"
        s == "legacy_rp" -> if (isZh) "归档" else "Legacy"
        else -> if (isZh) "原创" else "Original"
    }
}

private fun CharacterCardEntity.allGreetings(): List<String> {
    val alts = decodeJsonList<String>(alternateGreetingsJson)
    return (listOf(firstMes) + alts).map { it.trim() }.filter { it.isNotEmpty() }
}

private fun RpPresetTemplate.allGreetings(): List<String> =
    (listOf(config.greeting) + config.alternateGreetings).map { it.trim() }.filter { it.isNotEmpty() }

private inline fun <reified T> decodeJsonList(raw: String?): List<T> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching { AppJson.decodeFromString<List<T>>(raw) }.getOrDefault(emptyList())
}

// ==================== Characters Hub Page (`角色`) ====================

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

        AnimatedVisibility(visible = showSearchBar || searchQuery.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                GuCompactSearchBar(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = if (isZh) "搜索角色名、标签或世界观..." else "Search characters or scenarios...",
                    clearContentDescription = strings.common.clear,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        onSearchQueryChange("")
                        onCloseSearchBar()
                    },
                ) {
                    Text(strings.common.close, color = c.muted, fontSize = 12.sp)
                }
            }
        }

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
                val avatarFile = remember(card.avatarPath, card.spritePath) {
                    LocalMediaPathResolver.resolveFile(ctx, card.avatarPath ?: card.spritePath)
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
                    avatarFile = avatarFile,
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
                CharacterPosterCard(
                    title = preset.name,
                    subtitle = preset.description,
                    topBadge = if (isZh) "官方剧本" else "Official",
                    avatarFile = null,
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
private fun CharacterFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    GuPill(
        label = label,
        selected = selected,
        onClick = onClick,
        modifier = Modifier.height(32.dp),
    )
}

@Composable
private fun CharacterPosterCard(
    title: String,
    subtitle: String,
    topBadge: String,
    avatarFile: File?,
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
            // Backdrop: real local image if present, else deterministic aesthetic gradient
            if (avatarFile != null && avatarFile.exists()) {
                AsyncImage(
                    model = avatarFile,
                    imageLoader = imageLoader,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
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

private enum class LaunchSheetSubpage { MAIN, PICK_PERSONA, PICK_WORLDBOOKS, PICK_PRESET }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CharacterCardLaunchSheet(
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.page,
        dragHandle = { GuModalDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
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
                            val coverFile = remember(cardTarget?.avatarPath, cardTarget?.spritePath) {
                                cardTarget?.let { LocalMediaPathResolver.resolveFile(ctx, it.avatarPath ?: it.spritePath) }
                            }
                            val grad = if (cardTarget != null) resolveCardGradient(cardTarget.id)
                            else resolvePresetGradient(presetTarget?.id.orEmpty())

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = grad.end,
                                modifier = Modifier.size(width = 68.dp, height = 102.dp),
                            ) {
                                if (coverFile != null && coverFile.exists()) {
                                    AsyncImage(
                                        model = coverFile,
                                        imageLoader = container.imageLoader,
                                        contentDescription = title,
                                        contentScale = ContentScale.Crop,
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
                                        items(greetings.size) { idx ->
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
                        items(presets.filter { it.sourceFormat != "sillytavern_regex" }, key = { it.id }) { preset ->
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

@Composable
private fun LaunchSubpageHeader(
    title: String,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalAppTheme.current.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = c.ink)
        }
        Text(
            text = title,
            color = c.ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClose) {
            Text("✕", color = c.muted, fontSize = 15.sp)
        }
    }
}

// ==================== Persona Manager Sheet ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonaManagerSheet(
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.page,
        dragHandle = { GuModalDragHandle() },
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
private fun CreateOriginalCardSheet(
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.page,
        dragHandle = { GuModalDragHandle() },
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

/**
 * Worldbook, Prompt Preset & Regex asset management tab (`设定`) inside [CreativeStudioScreen].
 */
@Composable
fun LoreSettingsHubTab(
    assetHubViewModel: AssetHubViewModel,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    showSearchBar: Boolean,
    onCloseSearchBar: () -> Unit,
    onRequestImportSetting: () -> Unit,
    onScrollStateChange: (Boolean) -> Unit,
) {
    val ui by assetHubViewModel.ui.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH

    var activeFilter by rememberSaveable { mutableStateOf(SettingFilterKind.ALL) }
    var showCreateWorldbookDialog by rememberSaveable { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 6
        }
    }
    LaunchedEffect(isScrolled) { onScrollStateChange(isScrolled) }

    val cleanSearch = searchQuery.trim().lowercase()

    val filteredWorldbooks = remember(ui.worldbooks, activeFilter, cleanSearch) {
        if (activeFilter != SettingFilterKind.ALL && activeFilter != SettingFilterKind.WORLDBOOK) {
            emptyList()
        } else {
            ui.worldbooks.filter { wb ->
                cleanSearch.isEmpty() ||
                    wb.name.lowercase().contains(cleanSearch) ||
                    wb.description.lowercase().contains(cleanSearch)
            }
        }
    }

    val filteredPresets = remember(ui.promptPresets, activeFilter, cleanSearch) {
        if (activeFilter != SettingFilterKind.ALL && activeFilter != SettingFilterKind.PRESET) {
            emptyList()
        } else {
            ui.promptPresets.filter { preset ->
                preset.sourceFormat != "sillytavern_regex" &&
                    (cleanSearch.isEmpty() || preset.name.lowercase().contains(cleanSearch))
            }
        }
    }

    val regexPresets = remember(ui.promptPresets, activeFilter, cleanSearch) {
        if (activeFilter != SettingFilterKind.ALL && activeFilter != SettingFilterKind.REGEX) {
            emptyList()
        } else {
            ui.promptPresets.filter { preset ->
                val hasRegex = decodeJsonList<RpRegexScript>(preset.regexScriptsJson).isNotEmpty()
                (preset.sourceFormat == "sillytavern_regex" || (activeFilter == SettingFilterKind.REGEX && hasRegex)) &&
                    (cleanSearch.isEmpty() || preset.name.lowercase().contains(cleanSearch))
            }
        }
    }

    val activeWorldbook = remember(ui.activeWorldbookId, ui.worldbooks) {
        ui.activeWorldbookId?.let { id -> ui.worldbooks.firstOrNull { it.id == id } }
    }
    val activePreset = remember(ui.activePresetId, ui.promptPresets) {
        ui.activePresetId?.let { id -> ui.promptPresets.firstOrNull { it.id == id } }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Single-row 32dp borderless pills: 全部 · 世界书 · 提示词预设 · 正则
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CharacterFilterPill(
                label = if (isZh) "全部" else "All",
                selected = activeFilter == SettingFilterKind.ALL,
                onClick = { activeFilter = SettingFilterKind.ALL },
            )
            CharacterFilterPill(
                label = if (isZh) "世界书" else "Worldbooks",
                selected = activeFilter == SettingFilterKind.WORLDBOOK,
                onClick = { activeFilter = SettingFilterKind.WORLDBOOK },
            )
            CharacterFilterPill(
                label = if (isZh) "提示词预设" else "Presets",
                selected = activeFilter == SettingFilterKind.PRESET,
                onClick = { activeFilter = SettingFilterKind.PRESET },
            )
            CharacterFilterPill(
                label = if (isZh) "正则" else "Regex",
                selected = activeFilter == SettingFilterKind.REGEX,
                onClick = { activeFilter = SettingFilterKind.REGEX },
            )
        }

        AnimatedVisibility(visible = showSearchBar || searchQuery.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                GuCompactSearchBar(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = if (isZh) "搜索世界书、预设或正则..." else "Search worldbooks or presets...",
                    clearContentDescription = strings.common.clear,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        onSearchQueryChange("")
                        onCloseSearchBar()
                    },
                ) {
                    Text(strings.common.close, color = c.muted, fontSize = 12.sp)
                }
            }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            item(key = "settings_quick_actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GuButton(
                        onClick = onRequestImportSetting,
                        variant = GuButtonVariant.SECONDARY,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isZh) "导入设定" else "Import JSON", fontSize = 12.sp, maxLines = 1)
                    }
                    GuButton(
                        onClick = { showCreateWorldbookDialog = true },
                        variant = GuButtonVariant.SECONDARY,
                        compact = true,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(if (isZh) "新建世界书" else "New Book", fontSize = 12.sp, maxLines = 1)
                    }
                    GuButton(
                        onClick = { assetHubViewModel.harvestLegacyNovelLoreToWorldbooks() },
                        variant = GuButtonVariant.HONEY,
                        compact = true,
                        enabled = !ui.isBusy,
                    ) {
                        Text(
                            text = if (ui.legacyLoreSessionCount > 0) {
                                if (isZh) "提炼旧设定(${ui.legacyLoreSessionCount})" else "Extract(${ui.legacyLoreSessionCount})"
                            } else {
                                if (isZh) "从旧会话提炼设定" else "Extract Legacy"
                            },
                            fontSize = 11.5.sp,
                            maxLines = 1,
                        )
                    }
                }
            }

            if (filteredWorldbooks.isEmpty() && filteredPresets.isEmpty() && regexPresets.isEmpty()) {
                item(key = "settings_empty") {
                    GuEmptyState(
                        title = if (isZh) "设定库暂无内容" else "No assets in library",
                        subtitle = if (isZh) "支持导入 SillyTavern 世界书、Chat 预设、RikkaHub 设定与正则脚本"
                        else "Import Tavern worldbooks, chat presets, RikkaHub lore or regex scripts",
                        actionLabel = if (isZh) "导入设定文件" else "Import Setting",
                        onAction = onRequestImportSetting,
                        icon = Icons.Default.Layers,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            items(filteredWorldbooks, key = { "wb_${it.id}" }) { wb ->
                val vectorReady = ui.worldbookVectorReady[wb.id] == true
                val isIndexing = ui.vectorIndexProgress.running
                WorldbookSummaryCard(
                    worldbook = wb,
                    vectorReady = vectorReady,
                    isIndexing = isIndexing,
                    isZh = isZh,
                    onToggleGlobal = { enabled ->
                        assetHubViewModel.toggleWorldbookGlobalEnabled(wb.id, enabled)
                    },
                    onOpenDetail = { assetHubViewModel.openWorldbookDetail(wb.id) },
                )
            }

            items(filteredPresets, key = { "pr_${it.id}" }) { preset ->
                val blocks = remember(preset.blocksJson) { decodeJsonList<PromptPresetBlock>(preset.blocksJson) }
                val regexes = remember(preset.regexScriptsJson) { decodeJsonList<RpRegexScript>(preset.regexScriptsJson) }
                PresetSummaryCard(
                    preset = preset,
                    blocksCount = blocks.size,
                    enabledBlocksCount = blocks.count { it.enabled },
                    regexCount = regexes.size,
                    isZh = isZh,
                    onToggleDefault = { enabled ->
                        assetHubViewModel.setDefaultRpPreset(preset.id, enabled)
                    },
                    onOpenDetail = { assetHubViewModel.openPresetDetail(preset.id) },
                )
            }

            items(regexPresets, key = { "rx_${it.id}" }) { pack ->
                val scripts = remember(pack.regexScriptsJson) { decodeJsonList<RpRegexScript>(pack.regexScriptsJson) }
                RegexPackSummaryCard(
                    preset = pack,
                    scripts = scripts,
                    isZh = isZh,
                    onOpenDetail = { assetHubViewModel.openPresetDetail(pack.id) },
                )
            }
        }
    }

    if (showCreateWorldbookDialog) {
        CreateWorldbookSheet(
            onCreate = { name, desc ->
                assetHubViewModel.createBlankWorldbook(name, desc)
                showCreateWorldbookDialog = false
            },
            onDismiss = { showCreateWorldbookDialog = false },
        )
    }

    if (activeWorldbook != null) {
        WorldbookDetailSheet(
            worldbook = activeWorldbook,
            entries = ui.activeWorldbookEntries,
            vectorReady = ui.worldbookVectorReady[activeWorldbook.id] == true,
            isIndexing = ui.vectorIndexProgress.running,
            onToggleGlobal = { assetHubViewModel.toggleWorldbookGlobalEnabled(activeWorldbook.id, it) },
            onUpdateMeta = { name, desc, scanDepth, tokenBudget, recursive ->
                assetHubViewModel.updateWorldbookMeta(
                    activeWorldbook.id,
                    name,
                    desc,
                    scanDepth,
                    tokenBudget,
                    recursive,
                )
            },
            onReindex = { assetHubViewModel.reindexWorldbook(activeWorldbook.id) },
            onDeleteBook = { assetHubViewModel.deleteWorldbook(activeWorldbook.id) },
            onToggleEntryEnabled = { entry, enabled ->
                assetHubViewModel.toggleWorldbookEntryEnabled(entry, enabled)
            },
            onSaveEntry = { entryId, title, content, keys, secKeys, logic, constant, enabled, pos, depth, sticky, cooldown ->
                assetHubViewModel.saveWorldbookEntry(
                    worldbookId = activeWorldbook.id,
                    entryId = entryId,
                    title = title,
                    content = content,
                    primaryKeys = keys,
                    secondaryKeys = secKeys,
                    selectiveLogic = logic,
                    constant = constant,
                    enabled = enabled,
                    position = pos,
                    depth = depth,
                    sticky = sticky,
                    cooldown = cooldown,
                )
            },
            onDeleteEntry = { assetHubViewModel.deleteWorldbookEntry(it) },
            onDismiss = { assetHubViewModel.openWorldbookDetail(null) },
        )
    }

    if (activePreset != null) {
        PromptPresetDetailSheet(
            preset = activePreset,
            onToggleDefault = { assetHubViewModel.setDefaultRpPreset(activePreset.id, it) },
            onToggleBlock = { identifier, enabled ->
                assetHubViewModel.togglePresetBlockEnabled(activePreset.id, identifier, enabled)
            },
            onToggleRegex = { scriptId, enabled ->
                assetHubViewModel.togglePresetRegexEnabled(activePreset.id, scriptId, enabled)
            },
            onDeletePreset = { assetHubViewModel.deletePromptPreset(activePreset.id) },
            onDismiss = { assetHubViewModel.openPresetDetail(null) },
        )
    }
}

@Composable
private fun WorldbookSummaryCard(
    worldbook: WorldbookEntity,
    vectorReady: Boolean,
    isIndexing: Boolean,
    isZh: Boolean,
    onToggleGlobal: (Boolean) -> Unit,
    onOpenDetail: () -> Unit,
) {
    val c = LocalAppTheme.current.colors
    GuCard(
        onClick = onOpenDetail,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GuIconTile(
                    icon = Icons.Default.Book,
                    tint = c.tagInk,
                    backgroundColor = c.tag,
                    size = 32.dp,
                    iconSize = 17.dp,
                    cornerRadius = 9.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = worldbook.name.ifBlank { if (isZh) "未命名世界书" else "Untitled Worldbook" },
                        color = c.ink,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val statusDotColor = when {
                    isIndexing -> c.warning
                    vectorReady -> c.success
                    else -> c.muted.copy(alpha = 0.45f)
                }
                val statusLabel = when {
                    isIndexing -> if (isZh) "◌ 索引中" else "Indexing"
                    vectorReady -> if (isZh) "● 向量就绪" else "Vector Ready"
                    else -> if (isZh) "词法检索" else "Lexical"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(statusDotColor),
                    )
                    Text(
                        text = statusLabel,
                        color = if (vectorReady) c.success else c.muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Switch(
                    checked = worldbook.globalEnabled,
                    onCheckedChange = onToggleGlobal,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (isZh) {
                        "${worldbook.entryCount} 条词条 · ~${worldbook.totalEstimatedTokens} tokens"
                    } else {
                        "${worldbook.entryCount} entries · ~${worldbook.totalEstimatedTokens} tokens"
                    },
                    color = c.muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (isZh) "全局启用 · 点按编辑 ▸" else "Global · Edit ▸",
                    color = c.primary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun PresetSummaryCard(
    preset: PromptPresetEntity,
    blocksCount: Int,
    enabledBlocksCount: Int,
    regexCount: Int,
    isZh: Boolean,
    onToggleDefault: (Boolean) -> Unit,
    onOpenDetail: () -> Unit,
) {
    val c = LocalAppTheme.current.colors
    GuCard(
        onClick = onOpenDetail,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GuIconTile(
                    icon = Icons.Default.Tune,
                    tint = c.primarySoftInk,
                    backgroundColor = c.primarySoft,
                    size = 32.dp,
                    iconSize = 17.dp,
                    cornerRadius = 9.dp,
                )
                Text(
                    text = preset.name.ifBlank { if (isZh) "未命名预设" else "Untitled Preset" },
                    color = c.ink,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (preset.isDefaultRp) {
                    GuBadge(text = if (isZh) "默认 RP" else "Default", variant = GuBadgeVariant.BLUE)
                }
                Switch(
                    checked = preset.isDefaultRp,
                    onCheckedChange = onToggleDefault,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = buildString {
                        append(if (isZh) "$enabledBlocksCount/$blocksCount 模块启用" else "$enabledBlocksCount/$blocksCount blocks active")
                        if (regexCount > 0) {
                            append(if (isZh) " · $regexCount 正则" else " · $regexCount regex")
                        }
                    },
                    color = c.muted,
                    fontSize = 12.sp,
                )
                Text(
                    text = if (isZh) "模块预览 ▸" else "Blocks ▸",
                    color = c.primary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun RegexPackSummaryCard(
    preset: PromptPresetEntity,
    scripts: List<RpRegexScript>,
    isZh: Boolean,
    onOpenDetail: () -> Unit,
) {
    val c = LocalAppTheme.current.colors
    GuCard(
        onClick = onOpenDetail,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GuIconTile(
                    icon = Icons.Default.Code,
                    tint = c.success,
                    backgroundColor = c.successBg,
                    size = 32.dp,
                    iconSize = 17.dp,
                    cornerRadius = 9.dp,
                )
                Text(
                    text = preset.name,
                    color = c.ink,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                GuBadge(text = if (isZh) "正则脚本" else "Regex", variant = GuBadgeVariant.EMERALD)
            }
            Text(
                text = if (isZh) {
                    "${scripts.count { it.enabled }}/${scripts.size} 条规则生效 · 点按管理"
                } else {
                    "${scripts.count { it.enabled }}/${scripts.size} scripts enabled · Tap to manage"
                },
                color = c.muted,
                fontSize = 12.sp,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateWorldbookSheet(
    onCreate: (name: String, description: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.page,
        dragHandle = { GuModalDragHandle() },
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
private fun WorldbookDetailSheet(
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.page,
        dragHandle = { GuModalDragHandle() },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptPresetDetailSheet(
    preset: PromptPresetEntity,
    onToggleDefault: (Boolean) -> Unit,
    onToggleBlock: (identifier: String, enabled: Boolean) -> Unit,
    onToggleRegex: (scriptId: String, enabled: Boolean) -> Unit,
    onDeletePreset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val blocks = remember(preset.blocksJson) { decodeJsonList<PromptPresetBlock>(preset.blocksJson) }
    val regexScripts = remember(preset.regexScriptsJson) { decodeJsonList<RpRegexScript>(preset.regexScriptsJson) }
    var expandedBlockId by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        GuConfirmDialog(
            title = if (isZh) "删除设定预设" else "Delete Preset",
            message = if (isZh) "确认删除「${preset.name}」？" else "Delete \"${preset.name}\"?",
            confirmText = strings.common.delete,
            cancelText = strings.common.cancel,
            isDestructive = true,
            onConfirm = {
                confirmDelete = false
                onDeletePreset()
            },
            onDismiss = { confirmDelete = false },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.page,
        dragHandle = { GuModalDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = preset.name,
                subtitle = "${preset.sourceFormat} · ${blocks.size} blocks · ${regexScripts.size} regex",
                closeContentDescription = strings.common.close,
                onClose = onDismiss,
                trailing = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = strings.common.delete, tint = c.danger)
                    }
                },
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (preset.sourceFormat != "sillytavern_regex") {
                    item(key = "preset_default_switch") {
                        GuCard(backgroundColor = c.surfaceSoft) {
                            GuSwitchRow(
                                label = if (isZh) "设为新周目默认预设" else "Default for New RP Campaigns",
                                checked = preset.isDefaultRp,
                                onCheckedChange = onToggleDefault,
                            )
                        }
                    }
                }

                if (blocks.isNotEmpty()) {
                    item(key = "blocks_header") {
                        Text(
                            text = if (isZh) "提示词模块 (${blocks.count { it.enabled }}/${blocks.size})" else "Prompt Blocks",
                            color = c.ink,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    items(blocks, key = { "blk_${it.identifier}" }) { blk ->
                        val isExpanded = expandedBlockId == blk.identifier
                        GuCard(
                            onClick = { expandedBlockId = if (isExpanded) null else blk.identifier },
                            contentPadding = PaddingValues(12.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = blk.name.ifBlank { blk.identifier },
                                            color = c.ink,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = "${blk.role} · pos=${blk.injectionPosition} @D${blk.injectionDepth}",
                                            color = c.muted,
                                            fontSize = 11.5.sp,
                                        )
                                    }
                                    Switch(
                                        checked = blk.enabled,
                                        onCheckedChange = { onToggleBlock(blk.identifier, it) },
                                    )
                                }
                                if (isExpanded && blk.content.isNotBlank()) {
                                    Text(
                                        text = blk.content,
                                        color = c.muted,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                if (regexScripts.isNotEmpty()) {
                    item(key = "regex_header") {
                        Text(
                            text = if (isZh) "正则脚本 (${regexScripts.count { it.enabled }}/${regexScripts.size})" else "Regex Scripts",
                            color = c.ink,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    items(regexScripts, key = { "rx_${it.id}" }) { rx ->
                        GuCard(contentPadding = PaddingValues(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rx.name.ifBlank { rx.findRegex.take(28) },
                                        color = c.ink,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${rx.placement} · /${rx.findRegex.take(40)}/",
                                        color = c.muted,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Switch(
                                    checked = rx.enabled,
                                    onCheckedChange = { onToggleRegex(rx.id, it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
