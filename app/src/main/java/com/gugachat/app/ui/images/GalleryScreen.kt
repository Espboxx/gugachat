package com.gugachat.app.ui.images

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gugachat.app.AppContainer
import com.gugachat.app.R
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuCompactSearchBar
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.data.api.OnlineImageGenerationDto
import com.gugachat.app.domain.images.OnlineImageStudioUtils
import kotlinx.coroutines.delay

private val ACTIVE_STATUSES = setOf("pending", "queued", "processing", "running")
private val FAILED_STATUSES = setOf("failed", "interrupted", "error", "cancelled", "canceled")
private const val EXPIRING_WINDOW_MS = 12L * 3600_000L

private data class EmptyStarterTemplate(
    val id: String,
    val drawableRes: Int,
    val titleZh: String,
    val titleEn: String,
    val badgeZh: String,
    val badgeEn: String,
    val aspectRatio: String,
    val promptZh: String,
    val promptEn: String,
)

private val EMPTY_STARTER_TEMPLATES = listOf(
    EmptyStarterTemplate(
        id = "rp_portrait",
        drawableRes = R.drawable.showcase_t7,
        titleZh = "清冷角色立绘",
        titleEn = "Character Portrait",
        badgeZh = "2:3 立绘",
        badgeEn = "2:3 Portrait",
        aspectRatio = "2:3",
        promptZh = "银白色微卷碎发的清冷系角色半身立绘，细腻五官与通透眼神，柔和逆光轮廓光，剧场级日系厚涂插画，干净背景",
        promptEn = "Half-body character portrait with silver tousled hair, luminous eyes, soft rim light, refined anime painterly illustration, clean backdrop",
    ),
    EmptyStarterTemplate(
        id = "theater_cg",
        drawableRes = R.drawable.showcase_t3,
        titleZh = "夏日剧场 CG",
        titleEn = "Theater Scene CG",
        badgeZh = "16:9 CG",
        badgeEn = "16:9 CG",
        aspectRatio = "16:9",
        promptZh = "行驶在浅水镜面海面铁轨上的复古电车，远方高耸的夏日积雨云与温柔晚霞，动画电影级宽画幅剧场 CG，治愈色彩",
        promptEn = "Vintage pastel train gliding along mirror-sea tracks toward sunlit summer cumulus clouds and twilight sky, anime film theater CG, 16:9 wide shot",
    ),
    EmptyStarterTemplate(
        id = "cyber_cg",
        drawableRes = R.drawable.showcase_t5,
        titleZh = "赛博雨夜剧照",
        titleEn = "Cyber Rain Scene",
        badgeZh = "9:16 竖幅",
        badgeEn = "9:16 Tall",
        aspectRatio = "9:16",
        promptZh = "雨夜霓虹闪烁的东亚未来街巷，珊瑚红与冰蓝招牌倒映在湿润石板水洼中，撑着透明雨伞的背影，电影级氛围镜头",
        promptEn = "Rainy futuristic alley at night, coral and cyan neon signs reflecting on wet stone puddles, silhouette with clear umbrella, cinematic atmosphere",
    ),
)

@Composable
fun GalleryTabContent(
    container: AppContainer,
    viewModel: ImageViewModel,
    showSearchBar: Boolean,
    onCloseSearchBar: () -> Unit,
    onNavigateToInspire: () -> Unit,
    onScrollStateChange: (Boolean) -> Unit = {},
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val ctx = LocalContext.current
    val isZh = strings.language == AppLanguage.ZH

    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var searchInitialized by remember { mutableStateOf(false) }
    var quickPrompt by remember { mutableStateOf("") }

    LaunchedEffect(state.searchQuery) {
        if (!searchInitialized) {
            searchInitialized = true
            return@LaunchedEffect
        }
        delay(320L)
        viewModel.refreshGallery()
    }

    val zipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            viewModel.batchDownloadZip(uri, ctx.contentResolver, strings.images.zipSaved)
        }
    }

    if (showBatchDeleteConfirm) {
        GuConfirmDialog(
            title = strings.images.batchDelete,
            message = strings.images.batchDeleteConfirm.fmt("count" to state.selectedBatchIds.size),
            confirmText = strings.common.delete,
            cancelText = strings.common.cancel,
            isDestructive = true,
            onConfirm = {
                showBatchDeleteConfirm = false
                viewModel.batchDeleteSelected()
            },
            onDismiss = { showBatchDeleteConfirm = false },
        )
    }

    val nowMs = System.currentTimeMillis()
    val liveProcessing = remember(state.generations) {
        state.generations.count { it.status.lowercase() in ACTIVE_STATUSES }
    }
    val liveExpiring = remember(state.generations, nowMs) {
        state.generations.count { isGenerationExpiringSoon(it, nowMs) }
    }
    val liveFailed = remember(state.generations) {
        state.generations.count { it.status.lowercase() in FAILED_STATUSES }
    }

    var cachedProcessing by remember { mutableIntStateOf(0) }
    var cachedExpiring by remember { mutableIntStateOf(0) }
    var cachedFailed by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.activeFilter, state.searchQuery, liveProcessing, liveExpiring, liveFailed) {
        if (state.activeFilter == "all" && state.searchQuery.isBlank()) {
            cachedProcessing = liveProcessing
            cachedExpiring = liveExpiring
            cachedFailed = liveFailed
        } else if (state.activeFilter == "processing") {
            cachedProcessing = liveProcessing
        } else if (state.activeFilter == "expiring") {
            cachedExpiring = liveExpiring
        } else if (state.activeFilter == "failed") {
            cachedFailed = liveFailed
        }
    }

    val effectiveProcessing = if (state.activeFilter == "processing") liveProcessing else maxOf(liveProcessing, cachedProcessing)
    val effectiveExpiring = if (state.activeFilter == "expiring") liveExpiring else maxOf(liveExpiring, cachedExpiring)
    val effectiveFailed = if (state.activeFilter == "failed") liveFailed else maxOf(liveFailed, cachedFailed)

    val filterItems = remember(
        strings.images,
        state.activeFilter,
        effectiveProcessing,
        effectiveExpiring,
        effectiveFailed,
    ) {
        buildList {
            add("all" to strings.images.filterAll)
            add("generate" to strings.images.filterGenerate)
            add("edited" to strings.images.filterEdited)
            if (effectiveProcessing > 0 || state.activeFilter == "processing") {
                val countSuffix = if (effectiveProcessing > 0) " $effectiveProcessing" else ""
                add("processing" to "⚡ ${strings.images.filterProcessing}$countSuffix")
            }
            if (effectiveExpiring > 0 || state.activeFilter == "expiring") {
                val countSuffix = if (effectiveExpiring > 0) " $effectiveExpiring" else ""
                add("expiring" to "${strings.images.filterExpiring}$countSuffix")
            }
            if (effectiveFailed > 0 || state.activeFilter == "failed") {
                val countSuffix = if (effectiveFailed > 0) " $effectiveFailed" else ""
                add("failed" to "${strings.images.filterFailed}$countSuffix")
            }
        }
    }

    val filterListState = rememberLazyListState()
    val gridState = rememberLazyStaggeredGridState()
    val isGridScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 8
        }
    }

    LaunchedEffect(isGridScrolled, state.generations.isEmpty()) {
        onScrollStateChange(state.generations.isNotEmpty() && isGridScrolled)
    }

    val isFiltered = state.activeFilter != "all" || state.searchQuery.isNotBlank()
    val allSelected = state.generations.isNotEmpty() && state.selectedBatchIds.size >= state.generations.size
    val showCompanionCard = !isFiltered && !state.hasMore && state.generations.size in 1..3

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.page),
    ) {
        AnimatedVisibility(visible = showSearchBar || state.searchQuery.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 6.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                GuCompactSearchBar(
                    value = state.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    placeholder = strings.images.searchPlaceholder,
                    clearContentDescription = strings.common.clear,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        if (state.searchQuery.isNotEmpty()) {
                            viewModel.setSearchQuery("")
                        }
                        onCloseSearchBar()
                    },
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = strings.common.close,
                        tint = c.muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // Streamlined 32dp borderless filter capsule strip
        LazyRow(
            state = filterListState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .drawWithContent {
                    drawContent()
                    if (filterListState.canScrollForward) {
                        val fadeWidth = 24.dp.toPx()
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, c.page),
                                startX = size.width - fadeWidth,
                                endX = size.width,
                            ),
                            topLeft = Offset(size.width - fadeWidth, 0f),
                            size = Size(fadeWidth, size.height),
                        )
                    }
                },
        ) {
            items(filterItems, key = { it.first }) { (key, label) ->
                val selected = state.activeFilter == key
                val bg = when {
                    selected && key == "failed" -> c.dangerBg
                    selected && key == "expiring" -> c.warningBg
                    selected -> c.primarySoft
                    else -> c.surfaceSoft.copy(alpha = if (theme.isDark) 0.55f else 0.72f)
                }
                val fg = when {
                    selected && key == "failed" -> c.danger
                    selected && key == "expiring" -> c.warning
                    selected -> c.primarySoftInk
                    else -> c.muted
                }
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(bg)
                        .clickable { viewModel.setFilter(key) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = fg,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        AnimatedVisibility(visible = state.batchMode) {
            GuCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                backgroundColor = c.surface,
                cornerRadius = 14.dp,
                contentPadding = PaddingValues(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GuBadge(
                        strings.images.selectedCount.fmt("count" to state.selectedBatchIds.size),
                        variant = GuBadgeVariant.BLUE,
                    )
                    Spacer(Modifier.weight(1f))
                    GuButton(
                        onClick = {
                            if (allSelected) viewModel.clearBatchSelection() else viewModel.selectAllBatch()
                        },
                        compact = true,
                        variant = GuButtonVariant.SUBTLE,
                    ) {
                        Text(
                            if (allSelected) strings.images.clearSelection else strings.images.selectAll,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    GuButton(
                        onClick = { zipLauncher.launch("gugachat-works-${System.currentTimeMillis()}.zip") },
                        enabled = state.selectedBatchIds.isNotEmpty() && !state.isBatchBusy,
                        compact = true,
                        variant = GuButtonVariant.PRIMARY,
                    ) {
                        Icon(Icons.Default.FolderZip, contentDescription = strings.images.downloadZip, modifier = Modifier.size(15.dp))
                    }
                    GuButton(
                        onClick = { showBatchDeleteConfirm = true },
                        enabled = state.selectedBatchIds.isNotEmpty() && !state.isBatchBusy,
                        compact = true,
                        variant = GuButtonVariant.DANGER,
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = strings.images.batchDelete, modifier = Modifier.size(15.dp))
                    }
                    IconButton(
                        onClick = viewModel::toggleBatchMode,
                        modifier = Modifier.size(38.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = strings.common.close,
                            tint = c.muted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        if (state.isLoadingGallery && state.generations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        color = c.primary,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(30.dp),
                    )
                    Text(
                        text = strings.common.loading,
                        color = c.muted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        } else if (state.generations.isEmpty()) {
            if (isFiltered) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    GuEmptyState(
                        title = if (isZh) "当前筛选条件下暂无匹配作品" else "No artworks match the current filter",
                        icon = Icons.Default.FilterAltOff,
                        actionLabel = if (isZh) "重置筛选" else "Reset Filters",
                        onAction = {
                            viewModel.setSearchQuery("")
                            viewModel.setFilter("all")
                        },
                    )
                }
            } else {
                GalleryEmptyInspirationStage(
                    quickPrompt = quickPrompt,
                    onQuickPromptChange = { quickPrompt = it },
                    onQuickCreate = {
                        val trimmed = quickPrompt.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.openCreateWithPrompt(trimmed, styleAppend = false)
                        } else {
                            viewModel.setShowCreateSheet(true)
                        }
                    },
                    onSelectTemplate = { tpl ->
                        val p = if (isZh) tpl.promptZh else tpl.promptEn
                        viewModel.openCreateWithPrompt(
                            promptText = p,
                            styleAppend = false,
                            aspectRatio = tpl.aspectRatio,
                        )
                    },
                    onNavigateToInspire = onNavigateToInspire,
                )
            }
        } else {
            LazyVerticalStaggeredGrid(
                state = gridState,
                columns = StaggeredGridCells.Fixed(2),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 104.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.generations, key = { it.id }) { gen ->
                    GalleryWorkCard(
                        container = container,
                        generation = gen,
                        batchMode = state.batchMode,
                        selected = gen.id in state.selectedBatchIds,
                        onClick = {
                            if (state.batchMode) viewModel.toggleBatchSelection(gen.id)
                            else viewModel.openDetail(gen, 0)
                        },
                        onLongClick = {
                            if (!state.batchMode) {
                                viewModel.toggleBatchMode()
                            }
                            if (gen.id !in state.selectedBatchIds) {
                                viewModel.toggleBatchSelection(gen.id)
                            }
                        },
                    )
                }

                if (showCompanionCard) {
                    item(key = "__companion_create_card__") {
                        CompanionCreateCard(
                            isZh = isZh,
                            onCreateClick = { viewModel.setShowCreateSheet(true) },
                            onInspireClick = onNavigateToInspire,
                        )
                    }
                }

                if (state.hasMore) {
                    item(key = "__load_more__", span = StaggeredGridItemSpan.FullLine) {
                        GuButton(
                            onClick = viewModel::loadMoreGallery,
                            loading = state.isLoadingMore,
                            variant = GuButtonVariant.SECONDARY,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        ) {
                            Text(strings.images.loadMore, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryEmptyInspirationStage(
    quickPrompt: String,
    onQuickPromptChange: (String) -> Unit,
    onQuickCreate: () -> Unit,
    onSelectTemplate: (EmptyStarterTemplate) -> Unit,
    onNavigateToInspire: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GuCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = c.surface,
            cornerRadius = 18.dp,
            contentPadding = PaddingValues(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GuIconTile(
                        icon = Icons.Default.AutoAwesome,
                        tint = c.primarySoftInk,
                        backgroundColor = c.primarySoft,
                        size = 36.dp,
                        iconSize = 18.dp,
                        cornerRadius = 10.dp,
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (isZh) "一句话开启你的第一幅创作" else "Start with a single line",
                            color = c.ink,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        GuMetaText(
                            text = if (isZh) "写下画面灵感，或挑选下方模板一键直出" else "Type an idea or launch a curated template below",
                        )
                    }
                }

                // One-line quick inspiration input bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surfaceSoft)
                        .padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (quickPrompt.isEmpty()) {
                            Text(
                                text = if (isZh) "例如：雪夜古风剑客、赛博咖啡馆…" else "e.g. snowy shrine guardian, sunlit cafe…",
                                color = c.muted,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BasicTextField(
                            value = quickPrompt,
                            onValueChange = onQuickPromptChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = c.ink,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            cursorBrush = SolidColor(c.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { onQuickCreate() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Surface(
                        onClick = onQuickCreate,
                        shape = RoundedCornerShape(9.dp),
                        color = c.primary,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = if (isZh) "去创作" else "Go",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (isZh) "精选立绘与剧场 CG 模板" else "Curated Portrait & Theater CG",
                color = c.muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (isZh) "更多灵感 ▸" else "Inspire ▸",
                color = c.primarySoftInk,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onNavigateToInspire)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        // 3 curated RP portrait / theater CG templates
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EMPTY_STARTER_TEMPLATES.forEach { tpl ->
                GuCard(
                    onClick = { onSelectTemplate(tpl) },
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = c.surface,
                    cornerRadius = 16.dp,
                    contentPadding = PaddingValues(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Image(
                            painter = painterResource(id = tpl.drawableRes),
                            contentDescription = if (isZh) tpl.titleZh else tpl.titleEn,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(76.dp)
                                .height(76.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = if (isZh) tpl.titleZh else tpl.titleEn,
                                    color = c.ink,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                GuBadge(
                                    text = if (isZh) tpl.badgeZh else tpl.badgeEn,
                                    variant = GuBadgeVariant.BLUE,
                                )
                            }
                            Text(
                                text = if (isZh) tpl.promptZh else tpl.promptEn,
                                color = c.muted,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(c.primarySoft)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = if (isZh) "一键直出" else "Use",
                                color = c.primarySoftInk,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompanionCreateCard(
    isZh: Boolean,
    onCreateClick: () -> Unit,
    onInspireClick: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    val shape = RoundedCornerShape(14.dp)
    val dashColor = c.primary.copy(alpha = if (theme.isDark) 0.42f else 0.30f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.86f)
            .clip(shape)
            .background(c.surfaceSoft.copy(alpha = if (theme.isDark) 0.65f else 0.78f))
            .drawBehind {
                val strokeWidth = 1.2.dp.toPx()
                val radius = 14.dp.toPx()
                drawRoundRect(
                    color = dashColor,
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f),
                    ),
                    cornerRadius = CornerRadius(radius, radius),
                )
            }
            .clickable(onClick = onCreateClick)
            .padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GuIconTile(
                icon = Icons.Default.AutoAwesome,
                tint = c.primarySoftInk,
                backgroundColor = c.primarySoft,
                size = 38.dp,
                iconSize = 19.dp,
                cornerRadius = 11.dp,
            )
            Text(
                text = if (isZh) "✨ 继续创作" else "✨ Keep Creating",
                color = c.ink,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            GuMetaText(
                text = if (isZh) "新提示词 · 立绘/剧照" else "New prompt · Remix",
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(c.primarySoft)
                    .clickable(onClick = onInspireClick)
                    .padding(horizontal = 11.dp, vertical = 5.dp),
            ) {
                Text(
                    text = if (isZh) "从灵感画同款 ▸" else "From Inspire ▸",
                    color = c.primarySoftInk,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryWorkCard(
    container: AppContainer,
    generation: OnlineImageGenerationDto,
    batchMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val statusLower = generation.status.lowercase()
    val isPending = statusLower in ACTIVE_STATUSES
    val isFailed = statusLower in FAILED_STATUSES
    val shape = RoundedCornerShape(14.dp)
    val actionLabel = generation.action
        ?.takeIf { it.isNotBlank() && it != "generate" }
        ?.let { strings.images.actions[it] ?: it }

    val cardAspect = remember(generation.images, generation.aspect_ratio) {
        resolveCardAspectRatio(generation)
    }

    val statusDotColor = when {
        isPending -> c.accent
        isFailed -> c.danger
        statusLower == "partial" -> c.warning
        else -> Color(0xFF4ADE80)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(cardAspect)
            .clip(shape)
            .background(c.surfaceSoft)
            .then(
                if (selected) Modifier.border(2.dp, c.primary, shape) else Modifier,
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        if (generation.images.isNotEmpty()) {
            AuthenticatedImage(
                container = container,
                generationId = generation.id,
                imageIndex = 0,
                variant = "thumb",
                revision = generation.updated_at ?: generation.created_at,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Minimal bottom light scrim with soft status dot + GuMetaText(model)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.45f to Color.Black.copy(alpha = 0.28f),
                                1.0f to Color.Black.copy(alpha = 0.68f),
                            ),
                        ),
                    )
                    .padding(start = 9.dp, end = 9.dp, top = 22.dp, bottom = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusDotColor),
                    )
                    GuMetaText(
                        text = generation.model.ifBlank { strings.images.actions[generation.action] ?: "AI" },
                        color = Color.White.copy(alpha = 0.90f),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else if (isPending) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(c.surfaceSoft)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = c.primary, strokeWidth = 2.5.dp, modifier = Modifier.size(26.dp))
                Spacer(Modifier.height(10.dp))
                val qText = generation.queue_position?.let {
                    strings.images.queueWaiting.fmt("ahead" to it)
                } ?: (strings.images.statuses[generation.status] ?: generation.status)
                Text(qText, color = c.ink, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                if (generation.prompt.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = generation.prompt,
                        color = c.muted,
                        fontSize = 10.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(c.surfaceSoft)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                GuBadge(strings.images.statuses[generation.status] ?: generation.status, variant = GuBadgeVariant.DANGER)
                if (generation.prompt.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = generation.prompt,
                        color = c.muted,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        if (actionLabel != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(7.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.46f))
                    .padding(horizontal = 7.5.dp, vertical = 2.5.dp),
            ) {
                Text(actionLabel, color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (batchMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color.White else Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) c.primary else Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else if (generation.images.size > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.48f))
                    .padding(horizontal = 7.5.dp, vertical = 2.5.dp),
            ) {
                Text("${generation.images.size}P", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun resolveCardAspectRatio(generation: OnlineImageGenerationDto): Float {
    val first = generation.images.firstOrNull()
    val w = first?.width ?: 0
    val h = first?.height ?: 0
    val raw = when {
        w > 0 && h > 0 -> w.toFloat() / h.toFloat()
        generation.aspect_ratio.isNotBlank() && !generation.aspect_ratio.equals("auto", ignoreCase = true) -> {
            val (rw, rh) = OnlineImageStudioUtils.parseAspectRatio(generation.aspect_ratio)
            (rw / rh).toFloat()
        }
        else -> 1.0f
    }
    return raw.coerceIn(0.68f, 1.45f)
}

private fun normalizeEpochMs(raw: Long): Long =
    if (raw in 1L..9_999_999_999L) raw * 1000L else raw

private fun isGenerationExpiringSoon(generation: OnlineImageGenerationDto, nowMs: Long): Boolean {
    if (generation.expires_at <= 0L) return false
    val remaining = normalizeEpochMs(generation.expires_at) - nowMs
    return remaining <= EXPIRING_WINDOW_MS
}
