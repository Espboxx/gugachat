package com.gugachat.app.ui.images

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gugachat.app.AppContainer
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuAwningHeader
import com.gugachat.app.core.theme.LocalAppTheme
import kotlinx.coroutines.launch

@Composable
fun CreativeStudioScreen(
    container: AppContainer,
    imageViewModel: ImageViewModel,
) {
    val state by imageViewModel.ui.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    var inspireSubtab by rememberSaveable { mutableIntStateOf(0) }
    var showGallerySearch by rememberSaveable { mutableStateOf(false) }

    val processingCount = state.generations.count {
        it.status.lowercase() in setOf("pending", "queued", "processing", "running")
    }

    val segmentTitles = listOf(
        strings.nav.tabGallery,
        strings.nav.tabInspire,
        strings.nav.tabTools,
    )

    val showUnavailableBanner = !state.isLoadingCapabilities &&
        (!state.enabled || !state.unavailableReason.isNullOrBlank())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.page),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            GuAwningHeader {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = c.surfaceSoft,
                        border = BorderStroke(1.dp, c.border),
                    ) {
                        Row(
                            modifier = Modifier.padding(3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            segmentTitles.forEachIndexed { idx, label ->
                                val selected = pagerState.currentPage == idx
                                val badgeCount = if (idx == 0 && state.totalWorks > 0) state.totalWorks else null
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(if (selected) c.primary else Color.Transparent)
                                        .clickable {
                                            scope.launch { pagerState.animateScrollToPage(idx) }
                                        }
                                        .padding(horizontal = 11.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = label,
                                        color = if (selected) Color.White else c.muted,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                    if (badgeCount != null) {
                                        Text(
                                            text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                                            color = if (selected) Color.White.copy(alpha = 0.85f) else c.muted,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    if (idx == 0 && processingCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (selected) Color.White else c.accent),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    when (pagerState.currentPage) {
                        0 -> {
                            IconButton(
                                onClick = { showGallerySearch = !showGallerySearch },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = strings.common.search,
                                    tint = if (showGallerySearch || state.searchQuery.isNotEmpty()) c.primary else c.ink,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            IconButton(
                                onClick = imageViewModel::toggleBatchMode,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    Icons.Default.Checklist,
                                    contentDescription = strings.images.batchManage,
                                    tint = if (state.batchMode) c.primary else c.ink,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            IconButton(
                                onClick = imageViewModel::refreshGallery,
                                enabled = !state.isLoadingGallery,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = strings.common.refresh,
                                    tint = if (state.isLoadingGallery) c.muted else c.ink,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        1 -> {
                            IconButton(
                                onClick = { imageViewModel.setShowPresetSheet(true) },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    Icons.Default.Bookmarks,
                                    contentDescription = strings.images.presetsTitle,
                                    tint = c.ink,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        else -> {
                            IconButton(
                                onClick = imageViewModel::refreshGallery,
                                enabled = !state.isLoadingGallery,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = strings.common.refresh,
                                    tint = if (state.isLoadingGallery) c.muted else c.ink,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }

            if (showUnavailableBanner) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = c.warningBg,
                    border = BorderStroke(1.dp, c.warning.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = c.warning,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = state.unavailableReason?.takeIf { it.isNotBlank() } ?: strings.images.unavailable,
                            color = c.warning,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
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
                    )

                    1 -> InspireTabContent(
                        imageViewModel = imageViewModel,
                        subtab = inspireSubtab,
                        onSubtabChange = { inspireSubtab = it },
                        onToast = { container.toastController.show(it) },
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
                    )
                }
            }
        }

        // Hide floating Create FAB on Tools tab (which has its own primary hero CTA & bottom cards)
        // and while batch selection mode is active on Gallery.
        AnimatedVisibility(
            visible = pagerState.currentPage != 2 && !(pagerState.currentPage == 0 && state.batchMode),
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 14.dp),
        ) {
            Surface(
                onClick = { imageViewModel.setShowCreateSheet(true) },
                shape = RoundedCornerShape(999.dp),
                color = c.primary,
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}
