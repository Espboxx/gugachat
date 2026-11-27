package com.gugachat.app.ui.chat

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.gugachat.app.core.storage.LocalMediaPathResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import coil.compose.rememberAsyncImagePainter
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.RpStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.AppColors
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuCompactSearchBar
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuHairlineDivider
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuInlineDeleteConfirmStrip
import com.gugachat.app.core.theme.GuInlineStepper
import com.gugachat.app.core.theme.GuLabeledSliderRow
import com.gugachat.app.core.theme.GuLinearProgressBar
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuModalBottomSheet
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuPushPopContainer
import com.gugachat.app.core.theme.GuRemovableChip
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuSegmentedControl
import com.gugachat.app.core.theme.GuSettingsRow
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuStatusBanner
import com.gugachat.app.core.theme.GuStatusTone
import com.gugachat.app.core.theme.GuSubpageTopBar
import com.gugachat.app.core.theme.GuSwitchRow
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.data.local.CharacterCardEntity
import com.gugachat.app.data.local.PersonaEntity
import com.gugachat.app.data.local.WorldbookEntity
import com.gugachat.app.domain.chat.NovelLoreEntry
import com.gugachat.app.domain.chat.RpCgRecord
import com.gugachat.app.domain.chat.RpCharacter
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpEndingRecord
import com.gugachat.app.domain.chat.RpEngine
import com.gugachat.app.domain.chat.RpGameState
import com.gugachat.app.domain.chat.RpPlayStyle
import com.gugachat.app.domain.chat.RpPresets
import com.gugachat.app.domain.chat.RpRegexEngine
import com.gugachat.app.domain.chat.RpRegexScript
import com.gugachat.app.domain.chat.RpSaveSlot
import com.gugachat.app.domain.chat.RpVisualTheme
import com.gugachat.app.domain.chat.WorldbookEngine
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RpWizardDialog(
    onDismiss: () -> Unit,
    onCreateFromPreset: (
        presetId: String,
        title: String,
        playStyle: RpPlayStyle,
        playerName: String,
        playerPersona: String,
    ) -> Unit,
    onImportCardUri: (Uri) -> Unit,
    globalCards: List<CharacterCardEntity> = emptyList(),
    personas: List<PersonaEntity> = emptyList(),
    onCreateFromCharacterCard: ((String) -> Unit)? = null,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val ctx = LocalContext.current
    val isZh = strings.language == AppLanguage.ZH
    val defaultPersona = personas.firstOrNull { it.isDefault }

    val firstPreset = RpPresets.ALL.first()
    var selectedPreset by remember { mutableStateOf(firstPreset.id) }
    var selectedGlobalCardId by remember { mutableStateOf<String?>(null) }
    var selectedStyle by remember { mutableStateOf(firstPreset.config.playStyle) }
    var showCustomPlayerFields by remember { mutableStateOf(false) }
    var worldTitle by remember { mutableStateOf("") }
    var playerName by remember { mutableStateOf("") }
    var playerPersona by remember { mutableStateOf("") }
    val selectedGlobalCard = selectedGlobalCardId?.let { id -> globalCards.firstOrNull { it.id == id } }

    val cardLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            onImportCardUri(uri)
            onDismiss()
        }
    }

    val playStyles = remember {
        listOf(RpPlayStyle.chengguang, RpPlayStyle.tavern, RpPlayStyle.trpg)
    }
    val playStyleLabels = remember(strings) {
        playStyles.map { rpPlayStyleLabel(it, strings.rp) }
    }
    val active = RpPresets.findById(selectedPreset)
    val greetingCount = (if (active.config.greeting.isNotBlank()) 1 else 0) + active.config.alternateGreetings.size
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    GuModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        scrimColor = Color(0x8C060912),
    ) {
        val dialogView = LocalView.current
        SideEffect {
            val window = (dialogView.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, dialogView)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = !theme.isDark
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = strings.rp.wizardTitle,
                subtitle = if (isZh) "挑选剧本封面或导入 SillyTavern 角色卡" else "Browse scenario cards or import a SillyTavern V2/V3 card",
                onClose = onDismiss,
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
                GuGroupedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    GuSettingsRow(
                        title = strings.rp.importCardBtn,
                        subtitle = if (isZh) "支持 PNG / JSON / CHARX 角色卡、世界书与正则脚本" else "Supports PNG / JSON / CHARX character cards, lorebooks & regex",
                        icon = Icons.Default.UploadFile,
                        iconTint = c.primarySoftInk,
                        iconBg = c.primarySoft,
                        showChevron = true,
                        onClick = { cardLauncher.launch(arrayOf("*/*")) },
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GuSectionHeader(title = strings.rp.playStyleLabel)
                    GuSegmentedControl(
                        items = playStyleLabels,
                        selectedIndex = playStyles.indexOf(selectedStyle).coerceAtLeast(0),
                        onSelect = { idx -> selectedStyle = playStyles[idx] },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (globalCards.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GuSectionHeader(
                            title = if (isZh) "我的角色卡" else "My Character Cards",
                            modifier = Modifier.padding(horizontal = 16.dp),
                            trailing = {
                                GuBadge("${globalCards.size}", variant = GuBadgeVariant.EMERALD)
                            },
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(globalCards, key = { it.id }) { card ->
                                val cardSelected = selectedGlobalCardId == card.id
                                val avatarModel = remember(card.avatarPath, card.spritePath) {
                                    LocalMediaPathResolver.resolveImageModel(
                                        ctx,
                                        card.avatarPath ?: card.spritePath,
                                    )
                                }
                                Surface(
                                    onClick = {
                                        selectedGlobalCardId = if (cardSelected) null else card.id
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (cardSelected) c.primarySoft else c.surface,
                                    border = if (cardSelected) BorderStroke(1.5.dp, c.primary) else null,
                                    shadowElevation = if (!theme.isDark && !cardSelected) 0.8.dp else 0.dp,
                                    modifier = Modifier.width(136.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(c.surfaceSoft),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (avatarModel != null) {
                                                Image(
                                                    painter = rememberAsyncImagePainter(avatarModel),
                                                    contentDescription = card.name,
                                                    contentScale = ContentScale.Crop,
                                                    alignment = Alignment.TopCenter,
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                            } else {
                                                Text(
                                                    text = card.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "✦",
                                                    color = c.primarySoftInk,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }
                                        Text(
                                            text = card.name,
                                            color = if (cardSelected) c.primarySoftInk else c.ink,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GuSectionHeader(
                        title = strings.rp.wizardPresetLabel,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        trailing = {
                            GuBadge("${RpPresets.ALL.size}", variant = GuBadgeVariant.BLUE)
                        },
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(RpPresets.ALL, key = { it.id }) { preset ->
                            val isSelected = selectedGlobalCard == null && selectedPreset == preset.id
                            val visual = remember(preset.id) { resolveRpPresetCoverVisual(preset.id) }
                            val presetCoverModel = remember(preset.id) {
                                LocalMediaPathResolver.resolveImageModel(ctx, preset.primarySpritePath)
                            }
                            val presetLabel = strings.rp.presets[preset.id] ?: preset.name
                            val presetGreetings = (if (preset.config.greeting.isNotBlank()) 1 else 0) +
                                preset.config.alternateGreetings.size
                            val presetMeta = if (isZh) {
                                "${preset.config.cast.size}角 · ${presetGreetings}开场 · ${preset.starterLore.size}词条"
                            } else {
                                "${preset.config.cast.size} cast · ${presetGreetings} open · ${preset.starterLore.size} lore"
                            }

                            Surface(
                                onClick = {
                                    selectedGlobalCardId = null
                                    selectedPreset = preset.id
                                    selectedStyle = preset.config.playStyle
                                },
                                shape = RoundedCornerShape(15.dp),
                                color = c.surface,
                                border = if (isSelected) BorderStroke(1.5.dp, c.primary) else null,
                                shadowElevation = if (isSelected && !theme.isDark) 3.dp else 0.dp,
                                modifier = Modifier
                                    .width(164.dp)
                                    .height(132.dp),
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .background(Brush.horizontalGradient(listOf(visual.startColor, visual.endColor))),
                                    ) {
                                        if (presetCoverModel != null) {
                                            Image(
                                                painter = rememberAsyncImagePainter(presetCoverModel),
                                                contentDescription = presetLabel,
                                                contentScale = ContentScale.Crop,
                                                alignment = Alignment.TopCenter,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .alpha(0.45f),
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.horizontalGradient(
                                                            listOf(
                                                                visual.startColor.copy(alpha = 0.72f),
                                                                visual.endColor.copy(alpha = 0.56f),
                                                            ),
                                                        ),
                                                    ),
                                            )
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                                        ) {
                                            if (presetCoverModel != null) {
                                                Image(
                                                    painter = rememberAsyncImagePainter(presetCoverModel),
                                                    contentDescription = presetLabel,
                                                    contentScale = ContentScale.Crop,
                                                    alignment = Alignment.TopCenter,
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(RoundedCornerShape(9.dp))
                                                        .border(
                                                            0.8.dp,
                                                            Color.White.copy(alpha = 0.35f),
                                                            RoundedCornerShape(9.dp),
                                                        ),
                                                )
                                            } else {
                                                GuIconTile(
                                                    icon = visual.icon,
                                                    tint = visual.accentTint,
                                                    backgroundColor = Color.White.copy(alpha = 0.14f),
                                                    size = 26.dp,
                                                    iconSize = 14.dp,
                                                    cornerRadius = 8.dp,
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = Color.Black.copy(alpha = 0.34f),
                                                modifier = Modifier.weight(1f, fill = false),
                                            ) {
                                                Text(
                                                    text = rpPlayStyleLabel(preset.config.playStyle, strings.rp),
                                                    color = Color(0xFFFDF7EC),
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                )
                                            }
                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .clip(CircleShape)
                                                        .background(c.primary),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(12.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = presetLabel,
                                            color = if (isSelected) c.primarySoftInk else c.ink,
                                            fontSize = 12.5.sp,
                                            lineHeight = 16.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        GuMetaText(text = presetMeta)
                                    }
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
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = active.config.title,
                                color = c.ink,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            GuBadge(
                                text = rpPlayStyleLabel(active.config.playStyle, strings.rp),
                                variant = GuBadgeVariant.BLUE,
                            )
                        }
                        Text(
                            text = active.description,
                            color = c.muted,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                        )
                        if (active.config.greeting.isNotBlank()) {
                            val excerpt = remember(active.config.greeting) {
                                active.config.greeting.replace("**", "").trim()
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = c.surface.copy(alpha = if (theme.isDark) 0.65f else 0.85f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "“$excerpt”",
                                    color = c.ink.copy(alpha = 0.88f),
                                    fontSize = 11.5.sp,
                                    lineHeight = 17.sp,
                                    fontStyle = FontStyle.Italic,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                )
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            GuBadge(
                                text = if (isZh) "${active.config.cast.size} 位角色" else "${active.config.cast.size} NPCs",
                                variant = GuBadgeVariant.BLUE,
                            )
                            if (greetingCount > 0) {
                                GuBadge(
                                    text = strings.rp.wizardGreetingCount.fmt("count" to greetingCount),
                                    variant = GuBadgeVariant.HONEY,
                                )
                            }
                            if (active.starterLore.isNotEmpty()) {
                                GuBadge(
                                    text = strings.rp.wizardLoreCount.fmt("count" to active.starterLore.size),
                                    variant = GuBadgeVariant.EMERALD,
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (selectedGlobalCard != null) {
                        GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = selectedGlobalCard.name,
                                        color = c.ink,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    GuBadge(
                                        text = if (isZh) "角色卡直启" else "Card Ready",
                                        variant = GuBadgeVariant.EMERALD,
                                    )
                                }
                                if (selectedGlobalCard.creatorNotes.isNotBlank() || selectedGlobalCard.scenario.isNotBlank()) {
                                    Text(
                                        text = selectedGlobalCard.scenario.ifBlank { selectedGlobalCard.creatorNotes },
                                        color = c.muted,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (defaultPersona != null) {
                                    GuMetaText(
                                        text = if (isZh) "使用当前默认身份：${defaultPersona.name}" else "Active persona: ${defaultPersona.name}",
                                        color = c.primarySoftInk,
                                    )
                                }
                            }
                        }
                    } else {
                        GuSectionHeader(
                            title = strings.rp.personaSectionTitle,
                            trailing = {
                                TextButton(
                                    onClick = { showCustomPlayerFields = !showCustomPlayerFields },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = if (showCustomPlayerFields) {
                                            if (isZh) "收起自定义" else "Collapse"
                                        } else {
                                            if (isZh) "自定义名称/人设" else "Customize"
                                        },
                                        fontSize = 12.sp,
                                        color = c.primary,
                                    )
                                }
                            },
                        )
                        if (defaultPersona != null && !showCustomPlayerFields) {
                            GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = if (isZh) "默认身份：${defaultPersona.name}" else "Persona: ${defaultPersona.name}",
                                        color = c.ink,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    GuBadge(if (isZh) "已绑定" else "Bound", variant = GuBadgeVariant.BLUE)
                                }
                            }
                        }
                        AnimatedVisibility(visible = showCustomPlayerFields) {
                            GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(11.dp),
                                ) {
                                    GuTextField(
                                        value = worldTitle,
                                        onValueChange = { worldTitle = it },
                                        label = strings.rp.worldTitleLabel,
                                        placeholder = active.config.title,
                                    )
                                    GuTextField(
                                        value = playerName,
                                        onValueChange = { playerName = it },
                                        label = strings.rp.wizardPlayerNameLabel,
                                        placeholder = defaultPersona?.name ?: active.config.playerName,
                                    )
                                    GuTextField(
                                        value = playerPersona,
                                        onValueChange = { playerPersona = it },
                                        label = strings.rp.wizardPlayerPersonaLabel,
                                        placeholder = defaultPersona?.description?.ifBlank { null }
                                            ?: active.config.playerPersona.ifBlank { strings.rp.playerPersonaLabel },
                                        singleLine = false,
                                        minLines = 2,
                                        maxLines = 4,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.65f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 42.dp)) {
                    Text(strings.common.cancel, color = c.muted, fontWeight = FontWeight.Medium)
                }
                GuButton(
                    onClick = {
                        if (selectedGlobalCard != null && onCreateFromCharacterCard != null) {
                            onCreateFromCharacterCard(selectedGlobalCard.id)
                        } else {
                            onCreateFromPreset(
                                selectedPreset,
                                worldTitle.trim(),
                                selectedStyle,
                                playerName.trim(),
                                playerPersona.trim(),
                            )
                        }
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Theaters, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.rp.wizardStartBtn, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ==================== Dark-Gold Fate Dice Panel ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RpDiceDialog(
    rp: RpConfig? = null,
    onDismiss: () -> Unit,
    onRoll: (expression: String, skill: String?, difficulty: Int?, mode: String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val outerTheme = LocalAppTheme.current
    val isZh = strings.language == AppLanguage.ZH
    val goldAccent = remember(rp?.visualTheme?.accentHex) {
        parseOptionalHexColor(rp?.visualTheme?.accentHex) ?: Color(0xFFF5C563)
    }
    val darkStageTheme = remember(outerTheme, goldAccent) {
        deriveRpStageAppTheme(outerTheme, goldAccent)
    }
    val isD100Ruleset = rp?.systemRules?.contains("d100", ignoreCase = true) == true

    var formula by remember(rp?.title, isD100Ruleset) {
        mutableStateOf(if (isD100Ruleset) "1d100" else "1d20")
    }
    var skill by remember(rp?.title) { mutableStateOf("") }
    var dcValue by remember(rp?.title, isD100Ruleset) {
        mutableIntStateOf(if (isD100Ruleset) 50 else 12)
    }
    var mode by remember { mutableStateOf("normal") }
    var showCustomInput by remember { mutableStateOf(false) }

    val diceModes = remember { listOf("disadvantage", "normal", "advantage") }
    val diceModeLabels = remember(isZh) {
        if (isZh) listOf("劣势", "普通", "优势") else listOf("Disadv", "Normal", "Adv")
    }
    val stats = rp?.state?.stats.orEmpty()
    val fallbackSkills = remember(isZh) {
        if (isZh) listOf("洞察" to 12, "交涉" to 12, "潜行" to 14, "体魄" to 12)
        else listOf("Insight" to 12, "Persuade" to 12, "Stealth" to 14, "Athletics" to 12)
    }
    val activeChipStats = if (stats.isNotEmpty()) stats.entries.map { it.key to it.value } else fallbackSkills
    val isD100Selected = formula.contains("d100", ignoreCase = true) || isD100Ruleset
    val maxDc = if (isD100Selected) 100 else 30

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CompositionLocalProvider(LocalAppTheme provides darkStageTheme) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .imePadding(),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0F1524),
                border = BorderStroke(0.8.dp, goldAccent.copy(alpha = 0.55f)),
                shadowElevation = 14.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Header + Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(goldAccent.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Casino,
                                contentDescription = null,
                                tint = goldAccent,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            Text(
                                text = strings.rp.diceTitle,
                                color = Color(0xFFFFF6E3),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            GuMetaText(
                                text = if (isZh) "点选属性自动代入技能修正与难度" else "Tap a stat chip to auto-fill check & DC",
                                color = Color(0xFFA7B3CB),
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = strings.common.close,
                                tint = Color(0xFFA7B3CB),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    // Top Horizontal Stat / NPC Check Chips
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        GuMetaText(
                            text = if (isZh) "属性 / 检定筹码" else "Stat / Check Chips",
                            color = Color(0xFFA7B3CB),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            activeChipStats.forEach { (statName, statVal) ->
                                val selected = skill == statName
                                Surface(
                                    onClick = {
                                        skill = statName
                                        if (isD100Selected) {
                                            formula = "1d100"
                                            dcValue = statVal.coerceIn(1, 100)
                                        } else {
                                            if (rp != null && rp.autoDiceSkillBonus && stats.containsKey(statName)) {
                                                formula = RpEngine.resolveSkillModifier(statName, rp).first
                                            } else {
                                                formula = "1d20"
                                            }
                                            dcValue = when {
                                                statVal in 1..30 -> statVal.coerceIn(1, 30)
                                                else -> 12
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (selected) goldAccent.copy(alpha = 0.24f) else Color(0xFF192136),
                                    border = BorderStroke(
                                        0.6.dp,
                                        if (selected) goldAccent else Color(0x38CBD5E1),
                                    ),
                                ) {
                                    Text(
                                        text = "$statName · $statVal",
                                        color = if (selected) Color(0xFFFFF4DC) else Color(0xFFD8E1F2),
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Center Visual Die Preview + DC GuInlineStepper
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF161D30),
                        border = BorderStroke(0.6.dp, goldAccent.copy(alpha = 0.34f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            // Large Skeuomorphic Dark-Gold Die Face
                            Box(
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                goldAccent.copy(alpha = 0.28f),
                                                Color(0xFF1E1712),
                                                Color(0xFF111624),
                                            ),
                                        ),
                                    )
                                    .border(1.2.dp, goldAccent.copy(alpha = 0.78f), RoundedCornerShape(20.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = formula.uppercase(),
                                        color = goldAccent,
                                        fontSize = if (formula.length > 5) 14.sp else 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                    GuMetaText(
                                        text = skill.ifBlank { if (isZh) "自由掷骰" else "Free Roll" },
                                        color = Color(0xFFFFF4DC),
                                    )
                                }
                            }

                            // DC Stepper + Formula Quick Switches
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(
                                            text = if (isZh) "目标难度 DC" else "Target DC",
                                            color = Color(0xFFF3F6FC),
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        GuMetaText(
                                            text = "1~$maxDc",
                                            color = Color(0xFFA7B3CB),
                                        )
                                    }
                                    GuInlineStepper(
                                        valueText = "DC $dcValue",
                                        canDecrement = dcValue > 1,
                                        canIncrement = dcValue < maxDc,
                                        onDecrement = { dcValue = (dcValue - 1).coerceIn(1, maxDc) },
                                        onIncrement = { dcValue = (dcValue + 1).coerceIn(1, maxDc) },
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    listOf("1d20", "1d100", "2d6+2", "1d12").forEach { f ->
                                        val selected = formula == f
                                        Surface(
                                            onClick = {
                                                formula = f
                                                dcValue = if (f.contains("d100")) dcValue.coerceIn(1, 100) else dcValue.coerceIn(1, 30)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (selected) goldAccent.copy(alpha = 0.22f) else Color(0xFF1F2840),
                                        ) {
                                            Text(
                                                text = f,
                                                color = if (selected) goldAccent else Color(0xFFCBD5E1),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Floating Segmented Control: [劣势 | 普通 | 优势]
                    GuSegmentedControl(
                        items = diceModeLabels,
                        selectedIndex = diceModes.indexOf(mode).coerceAtLeast(1),
                        onSelect = { idx -> mode = diceModes[idx] },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Optional Collapsed Custom Override
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCustomInput = !showCustomInput }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        GuMetaText(
                            text = if (isZh) "自定义公式与技能名称" else "Custom formula & skill name",
                            color = Color(0xFFA7B3CB),
                        )
                        Icon(
                            imageVector = if (showCustomInput) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Color(0xFFA7B3CB),
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    AnimatedVisibility(visible = showCustomInput) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            GuTextField(
                                value = formula,
                                onValueChange = { formula = it },
                                label = strings.rp.diceFormulaLabel,
                                modifier = Modifier.weight(0.9f),
                            )
                            GuTextField(
                                value = skill,
                                onValueChange = { skill = it },
                                label = strings.rp.diceSkillLabel,
                                placeholder = strings.rp.diceSkillPlaceholder,
                                modifier = Modifier.weight(1.1f),
                            )
                        }
                    }

                    // Primary Roll CTA
                    GuButton(
                        onClick = {
                            onRoll(
                                formula.trim(),
                                skill.trim().takeIf { it.isNotEmpty() },
                                dcValue.coerceIn(1, maxDc),
                                mode,
                            )
                            onDismiss()
                        },
                        enabled = formula.isNotBlank(),
                        variant = GuButtonVariant.HONEY,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(strings.rp.diceRollBtn, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==================== Dialog-Free Push/Pop RP Studio Sheet ====================
