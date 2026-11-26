package com.gugachat.app.ui.images

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuModalDragHandle
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuSegmentedControl
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InspireTabContent(
    imageViewModel: ImageViewModel,
    subtab: Int,
    onSubtabChange: (Int) -> Unit,
    onToast: (String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val ctx = LocalContext.current
    var selectedCategory by remember { mutableStateOf("all") }
    var previewItem by remember { mutableStateOf<ShowcaseTemplate?>(null) }

    val filteredTemplates = remember(selectedCategory) {
        if (selectedCategory == "all") SHOWCASE_ITEMS
        else SHOWCASE_ITEMS.filter { it.category == selectedCategory }
    }

    val categoryListState = rememberLazyListState()

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
                            .clip(previewShape)
                            .border(theme.borderWidth, c.border, previewShape),
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
                                fontWeight = FontWeight.Bold,
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

                HorizontalDivider(thickness = theme.borderWidth, color = c.border)
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
                        Text(strings.common.copy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                        Text(strings.images.drawSame, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
        // Unified pinned 2-segment subtab switcher
        GuSegmentedControl(
            items = listOf(strings.images.subtabPrompts, strings.images.subtabStyles),
            selectedIndex = subtab.coerceIn(0, 1),
            onSelect = onSubtabChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = if (subtab == 0) 4.dp else 8.dp),
        )

        // Horizontally scrollable category chips for Prompts
        if (subtab == 0) {
            LazyRow(
                state = categoryListState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithContent {
                        drawContent()
                        if (categoryListState.canScrollForward) {
                            val fadeWidth = 28.dp.toPx()
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
                    GuPill(
                        label = strings.images.inspireCategories[key] ?: key,
                        selected = selectedCategory == key,
                        onClick = { selectedCategory = key },
                    )
                }
            }
        }

        if (subtab == 0) {
            if (filteredTemplates.isEmpty()) {
                GuEmptyState(
                    title = strings.images.emptyInspire,
                    icon = Icons.Default.AutoAwesome,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 104.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalItemSpacing = 10.dp,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(filteredTemplates, key = { it.id }) { item ->
                        val title = strings.images.templateTitles[item.id] ?: item.id
                        val desc = strings.images.templateDescs[item.id].orEmpty()
                        val prompt = strings.images.templatePrompts[item.id].orEmpty()
                        val shape = RoundedCornerShape(16.dp)

                        Surface(
                            onClick = { previewItem = item },
                            shape = shape,
                            color = c.surface,
                            border = BorderStroke(theme.borderWidth, c.border),
                            shadowElevation = if (theme.isDark) 0.dp else 0.5.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
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
                                            .background(Color.Black.copy(alpha = 0.56f))
                                            .border(0.8.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(999.dp))
                                            .padding(horizontal = 8.dp, vertical = 2.5.dp),
                                    ) {
                                        Text(item.ratioLabel, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Column(
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = title,
                                        color = c.ink,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = desc,
                                        color = c.muted,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = strings.images.inspireCategories[item.category] ?: item.category,
                                            color = c.muted,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            onClick = {
                                                imageViewModel.openCreateWithPrompt(
                                                    promptText = prompt,
                                                    styleAppend = false,
                                                    aspectRatio = item.ratioLabel,
                                                )
                                            },
                                            shape = RoundedCornerShape(999.dp),
                                            color = c.tag,
                                            border = BorderStroke(theme.borderWidth, c.accent.copy(alpha = 0.45f)),
                                        ) {
                                            Text(
                                                text = strings.images.drawSame,
                                                color = c.tagInk,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 104.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(STYLE_SPECS, key = { it.first }) { (key, swatch) ->
                    val label = strings.images.styleLabels[key] ?: key
                    val prompt = strings.images.stylePrompts[key].orEmpty()
                    val cardShape = RoundedCornerShape(16.dp)
                    Surface(
                        onClick = { imageViewModel.openCreateWithPrompt(prompt, styleAppend = true) },
                        shape = cardShape,
                        color = c.surface,
                        border = BorderStroke(theme.borderWidth, c.border),
                        shadowElevation = if (theme.isDark) 0.dp else 0.5.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 144.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Crisp 3dp top color accent strip
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(swatch),
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        GuIconTile(
                                            icon = Icons.Default.Style,
                                            tint = swatch,
                                            backgroundColor = swatch.copy(alpha = 0.14f),
                                            size = 30.dp,
                                            iconSize = 16.dp,
                                            cornerRadius = 8.dp,
                                        )
                                        Text(
                                            text = label,
                                            color = c.ink,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        IconButton(
                                            onClick = { copyPrompt(prompt) },
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = strings.common.copy,
                                                tint = c.muted,
                                                modifier = Modifier.size(15.dp),
                                            )
                                        }
                                    }
                                    Text(
                                        text = prompt,
                                        color = c.muted,
                                        fontSize = 11.5.sp,
                                        lineHeight = 16.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(end = 6.dp),
                                    )
                                }

                                Spacer(Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(end = 6.dp),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(c.primarySoft)
                                            .border(theme.borderWidth, c.primary.copy(alpha = 0.34f), RoundedCornerShape(999.dp))
                                            .padding(horizontal = 11.dp, vertical = 4.5.dp),
                                    ) {
                                        Text(
                                            text = strings.images.applyStyle,
                                            color = c.primarySoftInk,
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
        }
    }
}
