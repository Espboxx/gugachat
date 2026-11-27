package com.gugachat.app.ui.images

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gugachat.app.AppContainer
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuAwningHeader
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuPopupMenu
import com.gugachat.app.core.theme.LocalAppShellController
import com.gugachat.app.core.theme.LocalAppTheme
import kotlinx.coroutines.launch

@Composable
fun CreativeStudioScreen(
    container: AppContainer,
    imageViewModel: ImageViewModel,
    onOpenProfile: (() -> Unit)? = null,
) {
    val state by imageViewModel.ui.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val shellController = LocalAppShellController.current
    val scope = rememberCoroutineScope()
    val isZh = strings.language == AppLanguage.ZH

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    var inspireSubtab by rememberSaveable { mutableIntStateOf(0) }
    var showGallerySearch by rememberSaveable { mutableStateOf(false) }
    var showInspireSearch by rememberSaveable { mutableStateOf(false) }
    var inspireSearchQuery by rememberSaveable { mutableStateOf("") }
    var showMoreMenu by remember { mutableStateOf(false) }

    var galleryScrolled by remember { mutableStateOf(false) }
    var inspireScrolled by remember { mutableStateOf(false) }
    var toolsScrolled by remember { mutableStateOf(false) }

    val processingCount = state.generations.count {
        it.status.lowercase() in setOf("pending", "queued", "processing", "running")
    }

    val isCurrentScrolled = when (pagerState.currentPage) {
        0 -> galleryScrolled
        1 -> inspireScrolled
        else -> toolsScrolled
    }
    val headerDividerColor by animateColorAsState(
        targetValue = if (isCurrentScrolled) c.border.copy(alpha = 0.55f) else Color.Transparent,
        label = "studio_header_divider",
    )

    val tabTitles = listOf(
        strings.nav.tabGallery,
        strings.nav.tabInspire,
        if (isZh) "工坊" else strings.nav.tabTools,
    )

    val showUnavailableBanner = !state.isLoadingCapabilities &&
        (!state.enabled || !state.unavailableReason.isNullOrBlank())

    val isSearchActiveForPage = when (pagerState.currentPage) {
        0 -> showGallerySearch || state.searchQuery.isNotEmpty()
        1 -> showInspireSearch || inspireSearchQuery.isNotEmpty()
        else -> false
    }
    val isSearchEnabled = pagerState.currentPage != 2

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.page),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Unified 48dp scroll-aware studio top bar
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
                            StudioTopTextTab(
                                label = label,
                                selected = selected,
                                count = if (idx == 0) state.totalWorks else 0,
                                showLiveDot = idx == 0 && processingCount > 0,
                                onClick = {
                                    scope.launch { pagerState.animateScrollToPage(idx) }
                                },
                            )
                        }
                    }

                    // Consistent 36dp right icon slots across all 3 pages (zero size jumping)
                    IconButton(
                        onClick = {
                            when (pagerState.currentPage) {
                                0 -> showGallerySearch = !showGallerySearch
                                1 -> showInspireSearch = !showInspireSearch
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
                                tint = if (state.batchMode) c.primary else c.ink,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        GuPopupMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                        ) {
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
                                    if (pagerState.currentPage != 0) {
                                        scope.launch { pagerState.animateScrollToPage(0) }
                                    }
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

            if (showUnavailableBanner) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = c.warningBg,
                    border = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 5.dp),
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
                            text = state.unavailableReason?.takeIf { it.isNotBlank() } ?: strings.images.unavailable,
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

            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                when (page) {
                    0 -> GalleryTabContent(
                        container = container,
                        viewModel = imageViewModel,
                        showSearchBar = showGallerySearch,
                        onCloseSearchBar = { showGallerySearch = false },
                        onNavigateToInspire = {
                            inspireSubtab = 0
                            scope.launch { pagerState.animateScrollToPage(1) }
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
                        onNavigateToWorks = {
                            scope.launch { pagerState.animateScrollToPage(0) }
                        },
                        onNavigateToInspireStyles = {
                            inspireSubtab = 1
                            scope.launch { pagerState.animateScrollToPage(1) }
                        },
                        onScrollStateChange = { toolsScrolled = it },
                    )
                }
            }
        }

        // Single prominent Create FAB (hidden only while batch selection mode is active on Gallery)
        AnimatedVisibility(
            visible = !(pagerState.currentPage == 0 && state.batchMode),
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 14.dp),
        ) {
            Surface(
                onClick = { imageViewModel.setShowCreateSheet(true) },
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
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = strings.images.studioCreateFab,
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
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
