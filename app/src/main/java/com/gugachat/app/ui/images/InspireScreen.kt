package com.gugachat.app.ui.images

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.R
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuCompactSearchBar
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuModalDragHandle
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.LocalAppTheme

private data class ShowcaseTemplate(
    val id: String,
    val drawableRes: Int,
    val category: String,
    val ratioLabel: String,
    val aspectFloat: Float,
)

private val SHOWCASE_ITEMS = listOf(
    ShowcaseTemplate("t1", R.drawable.showcase_t1, "anime", "3:4", 0.78f),
    ShowcaseTemplate("t2", R.drawable.showcase_t2, "ecommerce", "1:1", 1.0f),
    ShowcaseTemplate("t3", R.drawable.showcase_t3, "landscape", "16:9", 1.45f),
    ShowcaseTemplate("t4", R.drawable.showcase_t4, "ecommerce", "4:3", 1.18f),
    ShowcaseTemplate("t5", R.drawable.showcase_t5, "cyber", "9:16", 0.72f),
    ShowcaseTemplate("t6", R.drawable.showcase_t6, "landscape", "21:9", 1.55f),
    ShowcaseTemplate("t7", R.drawable.showcase_t7, "portrait", "3:4", 0.78f),
    ShowcaseTemplate("t8", R.drawable.showcase_t8, "clay3d", "1:1", 1.0f),
)

private val STYLE_SPECS = listOf(
    "cinematic" to Color(0xFF6366F1),
    "anime" to Color(0xFFEC4899),
    "watercolor" to Color(0xFF14B8A6),
    "productShot" to Color(0xFF38BDF8),
    "cyberpunk" to Color(0xFFA855F7),
    "portraitLight" to Color(0xFFF59E0B),
    "isometric3d" to Color(0xFF3B82F6),
    "minimalPoster" to Color(0xFFEF4444),
)

private val INSPIRE_CATEGORY_KEYS = listOf("all", "portrait", "anime", "ecommerce", "landscape", "cyber", "clay3d")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun InspireTabContent(
    imageViewModel: ImageViewModel,
    subtab: Int = 0,
    onSubtabChange: (Int) -> Unit = {},
    onToast: (String) -> Unit,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    showSearchBar: Boolean = false,
    onCloseSearchBar: () -> Unit = {},
    onScrollStateChange: (Boolean) -> Unit = {},
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val ctx = LocalContext.current
    val isZh = strings.language == AppLanguage.ZH

    var selectedCategory by remember { mutableStateOf("all") }
    var previewItem by remember { mutableStateOf<ShowcaseTemplate?>(null) }

    val gridState = rememberLazyStaggeredGridState()
    val categoryListState = rememberLazyListState()
    val styleStripState = rememberLazyListState()

    val isScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 8
        }
    }

    LaunchedEffect(isScrolled) {
        onScrollStateChange(isScrolled)
    }

    LaunchedEffect(subtab) {
        if (subtab == 1) {
            gridState.animateScrollToItem(0)
            styleStripState.animateScrollToItem(0)
            onSubtabChange(0)
        }
    }

    val cleanQuery = searchQuery.trim().lowercase()
    val filteredStyles = remember(cleanQuery, strings.images) {
        if (cleanQuery.isEmpty()) STYLE_SPECS
        else STYLE_SPECS.filter { (key, _) ->
            val label = (strings.images.styleLabels[key] ?: key).lowercase()
            val prompt = strings.images.stylePrompts[key].orEmpty().lowercase()
            label.contains(cleanQuery) || prompt.contains(cleanQuery)
        }
    }

    val filteredTemplates = remember(selectedCategory, cleanQuery, strings.images) {
        SHOWCASE_ITEMS.filter { item ->
            val matchesCat = selectedCategory == "all" || item.category == selectedCategory
            val matchesSearch = if (cleanQuery.isEmpty()) {
                true
            } else {
                val title = (strings.images.templateTitles[item.id] ?: item.id).lowercase()
                val desc = strings.images.templateDescs[item.id].orEmpty().lowercase()
                val prompt = strings.images.templatePrompts[item.id].orEmpty().lowercase()
                val cat = (strings.images.inspireCategories[item.category] ?: item.category).lowercase()
                title.contains(cleanQuery) || desc.contains(cleanQuery) || prompt.contains(cleanQuery) || cat.contains(cleanQuery)
            }
            matchesCat && matchesSearch
        }
    }

    fun copyPrompt(text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("prompt", text))
        onToast(strings.common.copied)
    }

    previewItem?.let { item ->
        val title = strings.images.templateTitles[item.id] ?: item.id
        val prompt = strings.images.templatePrompts[item.id].orEmpty()
        val categoryLabel = strings.images.inspireCategories[item.category] ?: item.category
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { previewItem = null },
            sheetState = sheetState,
            containerColor = c.page,
            dragHandle = { GuModalDragHandle() },
            modifier = Modifier.statusBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                GuSheetHeader(
                    title = title,
                    subtitle = "$categoryLabel · ${item.ratioLabel}",
                    onClose = { previewItem = null },
                    closeContentDescription = strings.common.close,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val previewShape = RoundedCornerShape(16.dp)
                    Image(
                        painter = painterResource(id = item.drawableRes),
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .aspectRatio(item.aspectFloat.coerceIn(0.85f, 1.55f))
                            .clip(previewShape),
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        GuBadge(item.ratioLabel, variant = GuBadgeVariant.BLUE)
                        GuBadge(categoryLabel, variant = GuBadgeVariant.NEUTRAL)
                    }

                    GuCard(
                        backgroundColor = c.surfaceSoft,
                        cornerRadius = 14.dp,
                        contentPadding = PaddingValues(13.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = strings.images.studioPromptPreviewTitle,
                                color = c.muted,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = prompt,
                                color = c.ink,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                            )
                        }
                    }
                }

                HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.55f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GuButton(
                        onClick = { copyPrompt(prompt) },
                        variant = GuButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.common.copy, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    GuButton(
                        onClick = {
                            previewItem = null
                            imageViewModel.openCreateWithPrompt(
                                promptText = prompt,
                                styleAppend = false,
                                aspectRatio = item.ratioLabel,
                            )
                        },
                        variant = GuButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1.4f),
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.images.drawSame, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.page),
    ) {
        AnimatedVisibility(visible = showSearchBar || searchQuery.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 6.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                GuCompactSearchBar(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = if (isZh) "搜索灵感模板或风格词条…" else "Search templates or styles…",
                    clearContentDescription = strings.common.clear,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        if (searchQuery.isNotEmpty()) {
                            onSearchQueryChange("")
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

        // Single-line category filter strip (no nested GuSegmentedControl!)
        LazyRow(
            state = categoryListState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .drawWithContent {
                    drawContent()
                    if (categoryListState.canScrollForward) {
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
            items(INSPIRE_CATEGORY_KEYS, key = { it }) { key ->
                val selected = selectedCategory == key
                val bg = if (selected) c.primarySoft else c.surfaceSoft.copy(alpha = if (theme.isDark) 0.55f else 0.72f)
                val fg = if (selected) c.primarySoftInk else c.muted
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(bg)
                        .clickable { selectedCategory = key }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = strings.images.inspireCategories[key] ?: key,
                        color = fg,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (filteredTemplates.isEmpty() && filteredStyles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                GuEmptyState(
                    title = strings.images.emptyInspire,
                    icon = Icons.Default.AutoAwesome,
                    actionLabel = if (cleanQuery.isNotEmpty() || selectedCategory != "all") {
                        if (isZh) "重置筛选" else "Reset"
                    } else {
                        null
                    },
                    onAction = if (cleanQuery.isNotEmpty() || selectedCategory != "all") {
                        {
                            selectedCategory = "all"
                            onSearchQueryChange("")
                        }
                    } else {
                        null
                    },
                )
            }
        } else {
            LazyVerticalStaggeredGrid(
                state = gridState,
                columns = StaggeredGridCells.Fixed(2),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 104.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalItemSpacing = 9.dp,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (filteredStyles.isNotEmpty()) {
                    item(key = "__style_presets_strip__", span = StaggeredGridItemSpan.FullLine) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = if (isZh) "风格预设 · 点按叠加" else "Style Presets · Tap to append",
                                    color = c.muted,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                GuMetaText(text = "${filteredStyles.size}")
                            }
                            LazyRow(
                                state = styleStripState,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp),
                            ) {
                                items(filteredStyles, key = { it.first }) { (key, swatch) ->
                                    val label = strings.images.styleLabels[key] ?: key
                                    val prompt = strings.images.stylePrompts[key].orEmpty()
                                    StyleSwatchMicroCard(
                                        label = label,
                                        prompt = prompt,
                                        swatch = swatch,
                                        onClick = {
                                            imageViewModel.openCreateWithPrompt(prompt, styleAppend = true)
                                        },
                                        onLongClick = { copyPrompt(prompt) },
                                    )
                                }
                            }
                        }
                    }
                }

                items(filteredTemplates, key = { it.id }) { item ->
                    val title = strings.images.templateTitles[item.id] ?: item.id
                    val desc = strings.images.templateDescs[item.id].orEmpty()
                    val prompt = strings.images.templatePrompts[item.id].orEmpty()

                    GuCard(
                        onClick = { previewItem = item },
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = c.surface,
                        cornerRadius = 15.dp,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Image(
                                painter = painterResource(id = item.drawableRes),
                                contentDescription = title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(item.aspectFloat),
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color.Black.copy(alpha = 0.48f))
                                    .padding(horizontal = 7.5.dp, vertical = 2.5.dp),
                            ) {
                                Text(item.ratioLabel, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = title,
                                color = c.ink,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = desc,
                                color = c.muted,
                                fontSize = 11.sp,
                                lineHeight = 14.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                GuMetaText(
                                    text = strings.images.inspireCategories[item.category] ?: item.category,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(c.primarySoft)
                                        .clickable {
                                            imageViewModel.openCreateWithPrompt(
                                                promptText = prompt,
                                                styleAppend = false,
                                                aspectRatio = item.ratioLabel,
                                            )
                                        }
                                        .padding(horizontal = 9.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        text = "🎨 ${strings.images.drawSame}",
                                        color = c.primarySoftInk,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StyleSwatchMicroCard(
    label: String,
    prompt: String,
    swatch: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    val shape = RoundedCornerShape(12.dp)

    Surface(
        shape = shape,
        color = c.surface,
        shadowElevation = if (theme.isDark) 0.dp else 0.8.dp,
        modifier = Modifier
            .width(114.dp)
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(swatch),
                )
                Text(
                    text = label,
                    color = c.ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = prompt,
                color = c.muted,
                fontSize = 10.5.sp,
                lineHeight = 13.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
