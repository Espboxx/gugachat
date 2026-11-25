package com.gugachat.app.ui.images

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.gugachat.app.R
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuPill
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

@Composable
fun InspireTabContent(
    imageViewModel: ImageViewModel,
    subtab: Int,
    onSubtabChange: (Int) -> Unit,
    onToast: (String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val ctx = LocalContext.current
    var selectedCategory by remember { mutableStateOf("all") }
    var previewItem by remember { mutableStateOf<ShowcaseTemplate?>(null) }

    val filteredTemplates = remember(selectedCategory) {
        if (selectedCategory == "all") SHOWCASE_ITEMS
        else SHOWCASE_ITEMS.filter { it.category == selectedCategory }
    }

    fun copyPrompt(text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("prompt", text))
        onToast(strings.common.copied)
    }

    previewItem?.let { item ->
        val title = strings.images.templateTitles[item.id] ?: item.id
        val prompt = strings.images.templatePrompts[item.id].orEmpty()
        Dialog(onDismissRequest = { previewItem = null }) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = c.surface,
                border = BorderStroke(1.dp, c.border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = item.drawableRes),
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(item.aspectFloat.coerceIn(0.85f, 1.55f)),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { previewItem = null },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Close, contentDescription = strings.common.close, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = title,
                                color = c.ink,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.weight(1f),
                            )
                            GuBadge(item.ratioLabel, variant = GuBadgeVariant.BLUE)
                            GuBadge(strings.images.inspireCategories[item.category] ?: item.category, variant = GuBadgeVariant.NEUTRAL)
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = c.surfaceSoft,
                            border = BorderStroke(1.dp, c.border),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = strings.images.studioPromptPreviewTitle,
                                    color = c.muted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = prompt,
                                    color = c.ink,
                                    fontSize = 12.5.sp,
                                    lineHeight = 19.sp,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            GuButton(
                                onClick = { copyPrompt(prompt) },
                                compact = true,
                                variant = GuButtonVariant.SUBTLE,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.common.copy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            GuButton(
                                onClick = {
                                    previewItem = null
                                    imageViewModel.openCreateWithPrompt(prompt, styleAppend = false)
                                },
                                compact = true,
                                variant = GuButtonVariant.PRIMARY,
                                modifier = Modifier.weight(1.3f),
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.images.drawSame, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GuPill(
                label = strings.images.subtabPrompts,
                selected = subtab == 0,
                onClick = { onSubtabChange(0) },
                useHoneySelected = true,
            )
            GuPill(
                label = strings.images.subtabStyles,
                selected = subtab == 1,
                onClick = { onSubtabChange(1) },
                useHoneySelected = true,
            )
            if (subtab == 0) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .size(width = 1.dp, height = 16.dp)
                        .background(c.border),
                )
                listOf("all", "portrait", "anime", "ecommerce", "landscape", "cyber", "clay3d").forEach { key ->
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
                GuEmptyState(title = strings.images.emptyInspire, modifier = Modifier.padding(16.dp))
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 84.dp),
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
                            border = BorderStroke(1.dp, c.border),
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
                                            .padding(7.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(Color.Black.copy(alpha = 0.56f))
                                            .padding(horizontal = 7.dp, vertical = 2.dp),
                                    ) {
                                        Text(item.ratioLabel, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = title,
                                        color = c.ink,
                                        fontSize = 13.sp,
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
                                    Spacer(Modifier.height(2.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = strings.images.inspireCategories[item.category] ?: item.category,
                                            color = c.muted,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                        )
                                        Surface(
                                            onClick = { imageViewModel.openCreateWithPrompt(prompt, styleAppend = false) },
                                            shape = RoundedCornerShape(999.dp),
                                            color = c.tag,
                                            border = BorderStroke(1.dp, c.accent.copy(alpha = 0.4f)),
                                        ) {
                                            Text(
                                                text = strings.images.drawSame,
                                                color = c.tagInk,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
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
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 84.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(STYLE_SPECS, key = { it.first }) { (key, swatch) ->
                    val label = strings.images.styleLabels[key] ?: key
                    val prompt = strings.images.stylePrompts[key].orEmpty()
                    Surface(
                        onClick = { imageViewModel.openCreateWithPrompt(prompt, styleAppend = true) },
                        shape = RoundedCornerShape(16.dp),
                        color = c.surface,
                        border = BorderStroke(1.dp, c.border),
                        modifier = Modifier.fillMaxWidth().height(136.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(
                                        onClick = { copyPrompt(prompt) },
                                        modifier = Modifier.size(24.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = strings.common.copy,
                                            tint = c.muted,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                                Text(
                                    text = prompt,
                                    color = c.muted,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(c.primarySoft)
                                        .border(1.dp, c.primary.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        text = strings.images.applyStyle,
                                        color = c.primary,
                                        fontSize = 10.5.sp,
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
