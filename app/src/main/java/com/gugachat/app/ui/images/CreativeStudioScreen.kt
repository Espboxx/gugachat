package com.gugachat.app.ui.images

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gugachat.app.AppContainer
import com.gugachat.app.AuthSessionState
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuAwningHeader
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuPopupMenu
import com.gugachat.app.core.theme.LocalAppShellController
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.data.local.PromptPresetEntity
import com.gugachat.app.data.local.WorldbookEntity
import com.gugachat.app.ui.studio.AssetHubViewModel
import com.gugachat.app.ui.studio.CharactersHubTab
import com.gugachat.app.ui.studio.LoreSettingsHubTab
import com.gugachat.app.ui.studio.PromptPresetEditorMode
import com.gugachat.app.ui.studio.PromptPresetEditorScreen
import com.gugachat.app.ui.studio.StudioRoutingPolicy
import com.gugachat.app.ui.studio.WorldbookDetailScreen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Three-domain Creative Studio hub unifying:
 * 0. `角色` ([CharactersHubTab])
 * 1. `设定` ([LoreSettingsHubTab])
 * 2. `画室` ([GalleryTabContent], [InspireTabContent], [ToolsTabContent])
 */
@Composable
fun CreativeStudioScreen(
    container: AppContainer,
    imageViewModel: ImageViewModel,
    onOpenProfile: (() -> Unit)? = null,
    onOpenConversation: (String) -> Unit = {},
    assetHubViewModel: AssetHubViewModel? = null,
) {
    val authSession by container.authSession.collectAsStateWithLifecycle()
    val sessionScopeKey = remember(authSession) {
        when (val s = authSession) {
            is AuthSessionState.LoggedIn -> "asset_hub_${s.user.id}_${s.isDirectApiOnly}"
            else -> "asset_hub_guest"
        }
    }
    val resolvedAssetHubVm: AssetHubViewModel = assetHubViewModel ?: viewModel(
        key = sessionScopeKey,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AssetHubViewModel(container) as T
            }
        },
    )

    val state by imageViewModel.ui.collectAsStateWithLifecycle()
    val assetState by resolvedAssetHubVm.ui.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val ctx = LocalContext.current
    val shellController = LocalAppShellController.current
    val scope = rememberCoroutineScope()
    val isZh = strings.language == AppLanguage.ZH

    val pagerState = rememberPagerState(
        initialPage = assetState.activeStudioSubtab.coerceIn(0, 2),
        pageCount = { 3 },
    )

    var lastAppliedInitialSubtab by remember { mutableIntStateOf(-1) }
    LaunchedEffect(assetState.initialSubtabResolved, assetState.activeStudioSubtab) {
        if (assetState.initialSubtabResolved && lastAppliedInitialSubtab != assetState.activeStudioSubtab) {
            lastAppliedInitialSubtab = assetState.activeStudioSubtab
            if (pagerState.currentPage != assetState.activeStudioSubtab) {
                pagerState.scrollToPage(assetState.activeStudioSubtab)
            }
        }
    }

    LaunchedEffect(pagerState, assetState.initialSubtabResolved) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settled ->
                if (assetState.initialSubtabResolved && settled != assetState.activeStudioSubtab) {
                    resolvedAssetHubVm.selectStudioSubtab(settled)
                }
            }
    }

    var atelierSubtab by rememberSaveable { mutableIntStateOf(0) } // 0=作品, 1=灵感, 2=工坊
    var inspireSubtab by rememberSaveable { mutableIntStateOf(0) }

    var showCharactersSearch by rememberSaveable { mutableStateOf(false) }
    var charactersSearchQuery by rememberSaveable { mutableStateOf("") }
    var showSettingsSearch by rememberSaveable { mutableStateOf(false) }
    var settingsSearchQuery by rememberSaveable { mutableStateOf("") }
    var showGallerySearch by rememberSaveable { mutableStateOf(false) }
    var showInspireSearch by rememberSaveable { mutableStateOf(false) }
    var inspireSearchQuery by rememberSaveable { mutableStateOf("") }

    var showMoreMenu by remember { mutableStateOf(false) }
    var showCreateCardDialog by rememberSaveable { mutableStateOf(false) }

    var charactersScrolled by remember { mutableStateOf(false) }
    var settingsScrolled by remember { mutableStateOf(false) }
    var galleryScrolled by remember { mutableStateOf(false) }
    var inspireScrolled by remember { mutableStateOf(false) }
    var toolsScrolled by remember { mutableStateOf(false) }

    val cardImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            resolvedAssetHubVm.importCharacterCardUri(uri, ctx.contentResolver)
        }
    }

    val settingImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            resolvedAssetHubVm.importSettingAssetUri(uri, ctx.contentResolver)
        }
    }

    val processingCount = state.generations.count {
        com.gugachat.app.domain.images.OnlineImageStudioUtils.isActiveJobStatus(it.status)
    }

    val isCurrentScrolled = when (pagerState.currentPage) {
        StudioRoutingPolicy.TAB_CHARACTERS -> charactersScrolled
        StudioRoutingPolicy.TAB_SETTINGS -> settingsScrolled
        else -> when (atelierSubtab) {
            0 -> galleryScrolled
            1 -> inspireScrolled
            else -> toolsScrolled
        }
    }
    val headerDividerColor by animateColorAsState(
        targetValue = if (isCurrentScrolled) c.border.copy(alpha = 0.55f) else Color.Transparent,
        label = "studio_header_divider",
    )

    val tabTitles = listOf(
        if (isZh) "角色" else "Characters",
        if (isZh) "设定" else "Lore",
        if (isZh) "画室" else "Atelier",
    )

    val isDirectOnly = (authSession as? AuthSessionState.LoggedIn)?.isDirectApiOnly == true
    val showUnavailableBanner = !state.isLoadingCapabilities &&
        (!state.enabled || (!isDirectOnly && !state.unavailableReason.isNullOrBlank()))
    val unavailableText = remember(isDirectOnly, state.unavailableReason, isZh, strings.images.unavailable) {
        if (isDirectOnly) {
            if (isZh) {
                "当前未配置支持生图的直连 API 渠道，请在「我的」中添加 OpenAI / Gemini / NovelAI 等生图通道"
            } else {
                "No image-capable Direct API channel configured yet. Configure one in Mine."
            }
        } else {
            state.unavailableReason?.takeIf { it.isNotBlank() } ?: strings.images.unavailable
        }
    }

    val isSearchActiveForPage = when (pagerState.currentPage) {
        StudioRoutingPolicy.TAB_CHARACTERS -> showCharactersSearch || charactersSearchQuery.isNotEmpty()
        StudioRoutingPolicy.TAB_SETTINGS -> showSettingsSearch || settingsSearchQuery.isNotEmpty()
        else -> when (atelierSubtab) {
            0 -> showGallerySearch || state.searchQuery.isNotEmpty()
            1 -> showInspireSearch || inspireSearchQuery.isNotEmpty()
            else -> false
        }
    }
    val isSearchEnabled = pagerState.currentPage != StudioRoutingPolicy.TAB_ATELIER || atelierSubtab != 2

    val navigateToConversation: (String) -> Unit = { convId ->
        onOpenConversation(convId)
        shellController.selectMainTab(0)
    }

    val activeWorldbook = remember(assetState.activeWorldbookId, assetState.worldbooks) {
        assetState.activeWorldbookId?.let { id -> assetState.worldbooks.firstOrNull { it.id == id } }
    }
    val activePreset = remember(assetState.activePresetId, assetState.promptPresets) {
        assetState.activePresetId?.let { id -> assetState.promptPresets.firstOrNull { it.id == id } }
    }
    val hasFullscreenSubpage = activeWorldbook != null || activePreset != null
    var lastWorldbook by remember { mutableStateOf<WorldbookEntity?>(null) }
    var lastPreset by remember { mutableStateOf<PromptPresetEntity?>(null) }
    if (activeWorldbook != null) lastWorldbook = activeWorldbook
    if (activePreset != null) lastPreset = activePreset

    LaunchedEffect(hasFullscreenSubpage) {
        shellController.setFullscreenSubpageOpen(hasFullscreenSubpage)
    }
    DisposableEffect(Unit) {
        onDispose {
            shellController.setFullscreenSubpageOpen(false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.page),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Unified 48dp scroll-aware 3-domain studio top bar: 角色 · 设定 · 画室
            GuAwningHeader(dividerColor = headerDividerColor) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(start = 14.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        tabTitles.forEachIndexed { idx, label ->
                            val selected = pagerState.currentPage == idx
                            val badgeCount = when (idx) {
                                StudioRoutingPolicy.TAB_CHARACTERS -> assetState.characterCards.size
                                StudioRoutingPolicy.TAB_SETTINGS -> assetState.worldbooks.size + assetState.promptPresets.size
                                else -> state.totalWorks
                            }
                            StudioTopTextTab(
                                label = label,
                                selected = selected,
                                count = badgeCount,
                                showLiveDot = idx == StudioRoutingPolicy.TAB_ATELIER && processingCount > 0,
                                onClick = {
                                    resolvedAssetHubVm.selectStudioSubtab(idx)
                                    scope.launch { pagerState.animateScrollToPage(idx) }
                                },
                            )
                        }
                    }

                    // Consistent 36dp right icon slots across all 3 pages (zero size jumping)
                    IconButton(
                        onClick = {
                            when (pagerState.currentPage) {
                                StudioRoutingPolicy.TAB_CHARACTERS -> showCharactersSearch = !showCharactersSearch
                                StudioRoutingPolicy.TAB_SETTINGS -> showSettingsSearch = !showSettingsSearch
                                else -> when (atelierSubtab) {
                                    0 -> showGallerySearch = !showGallerySearch
                                    1 -> showInspireSearch = !showInspireSearch
                                }
                            }
                        },
                        enabled = isSearchEnabled,
                        modifier = Modifier
                            .size(36.dp)
                            .alpha(if (isSearchEnabled) 1f else 0.32f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = strings.common.search,
                            tint = if (isSearchActiveForPage) c.primary else c.ink,
                            modifier = Modifier.size(19.dp),
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreHoriz,
                                contentDescription = if (isZh) "更多操作" else "More",
                                tint = if (pagerState.currentPage == StudioRoutingPolicy.TAB_ATELIER && state.batchMode) c.primary else c.ink,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        GuPopupMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                        ) {
                            when (pagerState.currentPage) {
                                StudioRoutingPolicy.TAB_CHARACTERS -> {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (isZh) "导入角色卡 (.png/.charx/.json)" else "Import Card (.png/.charx/.json)",
                                                color = c.ink,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Download, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            cardImportLauncher.launch(arrayOf("*/*"))
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (isZh) "创建原创角色卡" else "Create Original Card",
                                                color = c.ink,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.PersonAdd, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            showCreateCardDialog = true
                                        },
                                    )
                                }

                                StudioRoutingPolicy.TAB_SETTINGS -> {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (isZh) "导入世界书 / 预设 / 正则" else "Import Worldbook / Preset / Regex",
                                                color = c.ink,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Download, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            settingImportLauncher.launch(arrayOf("*/*"))
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (isZh) "从旧会话提炼设定" else "Extract Legacy Session Lore",
                                                color = c.ink,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            resolvedAssetHubVm.harvestLegacyNovelLoreToWorldbooks()
                                        },
                                    )
                                }

                                else -> {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = strings.images.batchManage,
                                                color = if (state.batchMode) c.primary else c.ink,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Checklist,
                                                contentDescription = null,
                                                tint = if (state.batchMode) c.primary else c.muted,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        },
                                        trailingIcon = if (state.batchMode) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = c.primary,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            atelierSubtab = 0
                                            imageViewModel.toggleBatchMode()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (isZh) "刷新画廊" else strings.common.refresh,
                                                color = if (state.isLoadingGallery) c.muted else c.ink,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                tint = c.muted,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        },
                                        enabled = !state.isLoadingGallery,
                                        onClick = {
                                            showMoreMenu = false
                                            imageViewModel.refreshGallery()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (isZh) "我的预设" else strings.images.presetsTitle,
                                                color = c.ink,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Bookmarks,
                                                contentDescription = null,
                                                tint = c.muted,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            imageViewModel.setShowPresetSheet(true)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                userScrollEnabled = !hasFullscreenSubpage,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                when (page) {
                    StudioRoutingPolicy.TAB_CHARACTERS -> CharactersHubTab(
                        container = container,
                        assetHubViewModel = resolvedAssetHubVm,
                        imageViewModel = imageViewModel,
                        searchQuery = charactersSearchQuery,
                        onSearchQueryChange = { charactersSearchQuery = it },
                        showSearchBar = showCharactersSearch,
                        onCloseSearchBar = { showCharactersSearch = false },
                        showCreateCardDialog = showCreateCardDialog,
                        onDismissCreateCardDialog = { showCreateCardDialog = false },
                        onRequestCreateOriginalCard = { showCreateCardDialog = true },
                        onRequestImportCard = { cardImportLauncher.launch(arrayOf("*/*")) },
                        onOpenConversation = navigateToConversation,
                        onScrollStateChange = { charactersScrolled = it },
                    )

                    StudioRoutingPolicy.TAB_SETTINGS -> LoreSettingsHubTab(
                        assetHubViewModel = resolvedAssetHubVm,
                        searchQuery = settingsSearchQuery,
                        onSearchQueryChange = { settingsSearchQuery = it },
                        showSearchBar = showSettingsSearch,
                        onCloseSearchBar = { showSettingsSearch = false },
                        onRequestImportSetting = { settingImportLauncher.launch(arrayOf("*/*")) },
                        onScrollStateChange = { settingsScrolled = it },
                    )

                    else -> Column(modifier = Modifier.fillMaxSize()) {
                        // Single 32dp borderless pill switcher: [作品] · [灵感] · [工坊]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GuPill(
                                label = strings.nav.tabGallery,
                                selected = atelierSubtab == 0,
                                onClick = { atelierSubtab = 0 },
                                modifier = Modifier.height(32.dp),
                            )
                            GuPill(
                                label = strings.nav.tabInspire,
                                selected = atelierSubtab == 1,
                                onClick = { atelierSubtab = 1 },
                                modifier = Modifier.height(32.dp),
                            )
                            GuPill(
                                label = if (isZh) "工坊" else strings.nav.tabTools,
                                selected = atelierSubtab == 2,
                                onClick = { atelierSubtab = 2 },
                                modifier = Modifier.height(32.dp),
                            )
                        }

                        // Unavailable banner renders ONLY inside 画室 page
                        if (showUnavailableBanner) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = c.warningBg,
                                border = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 11.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                                ) {
                                    GuIconTile(
                                        icon = Icons.Default.WarningAmber,
                                        tint = c.warning,
                                        backgroundColor = c.warning.copy(alpha = 0.14f),
                                        size = 26.dp,
                                        iconSize = 15.dp,
                                        cornerRadius = 8.dp,
                                    )
                                    Text(
                                        text = unavailableText,
                                        color = c.warning,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(c.warning.copy(alpha = 0.15f))
                                            .clickable {
                                                if (onOpenProfile != null) {
                                                    onOpenProfile.invoke()
                                                } else {
                                                    shellController.selectMainTab(2)
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                    ) {
                                        Text(
                                            text = if (isZh) "去配置 ▸" else "Configure ▸",
                                            color = c.warning,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            when (atelierSubtab) {
                                0 -> GalleryTabContent(
                                    container = container,
                                    viewModel = imageViewModel,
                                    showSearchBar = showGallerySearch,
                                    onCloseSearchBar = { showGallerySearch = false },
                                    onNavigateToInspire = {
                                        inspireSubtab = 0
                                        atelierSubtab = 1
                                    },
                                    onScrollStateChange = { galleryScrolled = it },
                                )

                                1 -> InspireTabContent(
                                    imageViewModel = imageViewModel,
                                    subtab = inspireSubtab,
                                    onSubtabChange = { inspireSubtab = it },
                                    onToast = { container.toastController.show(it) },
                                    searchQuery = inspireSearchQuery,
                                    onSearchQueryChange = { inspireSearchQuery = it },
                                    showSearchBar = showInspireSearch,
                                    onCloseSearchBar = { showInspireSearch = false },
                                    onScrollStateChange = { inspireScrolled = it },
                                )

                                else -> ToolsTabContent(
                                    container = container,
                                    imageViewModel = imageViewModel,
                                    onNavigateToWorks = { atelierSubtab = 0 },
                                    onNavigateToInspireStyles = {
                                        inspireSubtab = 1
                                        atelierSubtab = 1
                                    },
                                    onScrollStateChange = { toolsScrolled = it },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Smart context-sensitive FAB
        val showFab = !hasFullscreenSubpage &&
            !(pagerState.currentPage == StudioRoutingPolicy.TAB_ATELIER && atelierSubtab == 0 && state.batchMode)
        AnimatedVisibility(
            visible = showFab,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 14.dp),
        ) {
            val fabLabel = when (pagerState.currentPage) {
                StudioRoutingPolicy.TAB_CHARACTERS -> if (isZh) "导入角色" else "Import Card"
                StudioRoutingPolicy.TAB_SETTINGS -> if (isZh) "导入设定" else "Import Lore"
                else -> strings.images.studioCreateFab
            }
            val fabIcon = when (pagerState.currentPage) {
                StudioRoutingPolicy.TAB_CHARACTERS,
                StudioRoutingPolicy.TAB_SETTINGS,
                -> Icons.Default.Add
                else -> Icons.Default.AutoAwesome
            }
            Surface(
                onClick = {
                    when (pagerState.currentPage) {
                        StudioRoutingPolicy.TAB_CHARACTERS -> cardImportLauncher.launch(arrayOf("*/*"))
                        StudioRoutingPolicy.TAB_SETTINGS -> settingImportLauncher.launch(arrayOf("*/*"))
                        else -> imageViewModel.setShowCreateSheet(true)
                    }
                },
                shape = RoundedCornerShape(theme.pillRadius),
                color = c.primary,
                border = null,
                shadowElevation = if (theme.isDark) 6.dp else 4.dp,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = fabIcon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = fabLabel,
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Root-level fullscreen subpage overlays (above Studio header, HorizontalPager & FAB)
        AnimatedVisibility(
            visible = activeWorldbook != null,
            enter = slideInHorizontally(tween(220)) { it / 3 } + fadeIn(tween(180)),
            exit = slideOutHorizontally(tween(200)) { it / 3 } + fadeOut(tween(150)),
        ) {
            val wb = activeWorldbook ?: lastWorldbook
            if (wb != null) {
                WorldbookDetailScreen(
                    worldbook = wb,
                    entries = assetState.activeWorldbookEntries,
                    vectorReady = assetState.worldbookVectorReady[wb.id] == true,
                    isIndexing = assetState.vectorIndexProgress.running,
                    onToggleGlobal = { resolvedAssetHubVm.toggleWorldbookGlobalEnabled(wb.id, it) },
                    onUpdateMeta = { name, desc, scanDepth, tokenBudget, recursive ->
                        resolvedAssetHubVm.updateWorldbookMeta(
                            wb.id,
                            name,
                            desc,
                            scanDepth,
                            tokenBudget,
                            recursive,
                        )
                    },
                    onReindex = { resolvedAssetHubVm.reindexWorldbook(wb.id) },
                    onDeleteBook = { resolvedAssetHubVm.deleteWorldbook(wb.id) },
                    onToggleEntryEnabled = { entry, enabled ->
                        resolvedAssetHubVm.toggleWorldbookEntryEnabled(entry, enabled)
                    },
                    onBatchSetEntriesEnabled = { entryIds, enabled ->
                        resolvedAssetHubVm.setWorldbookEntriesEnabled(wb.id, entryIds, enabled)
                    },
                    onMoveEntryOrder = { entryId, delta ->
                        resolvedAssetHubVm.moveWorldbookEntryOrder(wb.id, entryId, delta)
                    },
                    onSaveEntry = { entryId, title, content, keys, secKeys, logic, constant, enabled, pos, depth, sticky, cooldown ->
                        resolvedAssetHubVm.saveWorldbookEntry(
                            worldbookId = wb.id,
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
                    onDeleteEntry = { resolvedAssetHubVm.deleteWorldbookEntry(it) },
                    onBack = { resolvedAssetHubVm.openWorldbookDetail(null) },
                )
            }
        }

        AnimatedVisibility(
            visible = activePreset != null,
            enter = slideInHorizontally(tween(220)) { it / 3 } + fadeIn(tween(180)),
            exit = slideOutHorizontally(tween(200)) { it / 3 } + fadeOut(tween(150)),
        ) {
            val preset = activePreset ?: lastPreset
            if (preset != null) {
                PromptPresetEditorScreen(
                    mode = PromptPresetEditorMode.STUDIO,
                    activePreset = preset,
                    onToggleDefaultRp = { id, enabled -> resolvedAssetHubVm.setDefaultRpPreset(id, enabled) },
                    onDeletePreset = { id -> resolvedAssetHubVm.deletePromptPreset(id) },
                    onToggleBlock = { id, identifier, index, enabled ->
                        resolvedAssetHubVm.togglePresetBlockEnabled(
                            presetId = id,
                            identifier = identifier,
                            enabled = enabled,
                            index = index,
                        )
                    },
                    onBatchSetBlocksEnabled = { id, indices, enabled ->
                        resolvedAssetHubVm.setPresetBlocksEnabled(id, indices, enabled)
                    },
                    onMoveBlock = { id, index, delta ->
                        resolvedAssetHubVm.movePresetBlock(id, index, delta)
                    },
                    onToggleRegex = { id, scriptId, enabled ->
                        resolvedAssetHubVm.togglePresetRegexEnabled(id, scriptId, enabled)
                    },
                    onBack = { resolvedAssetHubVm.openPresetDetail(null) },
                )
            }
        }
    }
}

@Composable
private fun StudioTopTextTab(
    label: String,
    selected: Boolean,
    count: Int,
    showLiveDot: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalAppTheme.current.colors
    val textColor by animateColorAsState(
        targetValue = if (selected) c.ink else c.muted,
        label = "studio_tab_color",
    )
    val indicatorWidth by animateDpAsState(
        targetValue = if (selected) 18.dp else 0.dp,
        label = "studio_tab_indicator",
    )

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                color = textColor,
                fontSize = 15.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) c.primarySoft else c.surfaceSoft)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (count > 99) "99+" else "$count",
                        color = if (selected) c.primarySoftInk else c.muted,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                        maxLines = 1,
                    )
                }
            }
            if (showLiveDot) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(c.accent),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .width(indicatorWidth)
                .height(2.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(c.primary),
        )
    }
}
