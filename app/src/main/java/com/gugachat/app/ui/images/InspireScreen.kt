package com.gugachat.app.ui.images

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.R
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuAwningHeader
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
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

private val STYLE_KEYS = listOf(
    "cinematic",
    "anime",
    "watercolor",
    "productShot",
    "cyberpunk",
    "portraitLight",
    "isometric3d",
    "minimalPoster",
)

@Composable
fun InspireScreen(
    imageViewModel: ImageViewModel,
    initialSubtab: Int = 0,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    var subtab by remember { mutableIntStateOf(initialSubtab) }
    var selectedCategory by remember { mutableStateOf("all") }

    LaunchedEffect(initialSubtab) {
        if (initialSubtab in 0..1) subtab = initialSubtab
    }

    val filteredTemplates = remember(selectedCategory) {
        if (selectedCategory == "all") SHOWCASE_ITEMS
        else SHOWCASE_ITEMS.filter { it.category == selectedCategory }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.page),
    ) {
        GuAwningHeader {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = strings.images.inspireTitle,
                    color = c.ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                GuPill(strings.images.subtabPrompts, subtab == 0, { subtab = 0 }, useHoneySelected = true)
                GuPill(strings.images.subtabStyles, subtab == 1, { subtab = 1 }, useHoneySelected = true)
            }
        }

        if (subtab == 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("all", "portrait", "anime", "ecommerce", "landscape", "cyber", "clay3d").forEach { key ->
                    GuPill(
                        label = strings.images.inspireCategories[key] ?: key,
                        selected = selectedCategory == key,
                        onClick = { selectedCategory = key },
                    )
                }
            }

            if (filteredTemplates.isEmpty()) {
                GuEmptyState(title = strings.images.emptyInspire, modifier = Modifier.padding(16.dp))
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalItemSpacing = 10.dp,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(filteredTemplates, key = { it.id }) { item ->
                        val title = strings.images.templateTitles[item.id] ?: item.id
                        val desc = strings.images.templateDescs[item.id].orEmpty()
                        val prompt = strings.images.templatePrompts[item.id].orEmpty()
                        GuCard(contentPadding = PaddingValues(0.dp)) {
                            Image(
                                painter = painterResource(id = item.drawableRes),
                                contentDescription = title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(item.aspectFloat)
                                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                            )
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    GuBadge(item.ratioLabel, variant = GuBadgeVariant.BLUE)
                                    GuBadge(strings.images.inspireCategories[item.category] ?: item.category, variant = GuBadgeVariant.NEUTRAL)
                                }
                                Text(title, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(desc, color = c.muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(2.dp))
                                GuButton(
                                    onClick = { imageViewModel.openCreateWithPrompt(prompt, styleAppend = false) },
                                    compact = true,
                                    variant = GuButtonVariant.HONEY,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(strings.images.drawSame, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(STYLE_KEYS, key = { it }) { key ->
                    val label = strings.images.styleLabels[key] ?: key
                    val prompt = strings.images.stylePrompts[key].orEmpty()
                    GuCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(label, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                            GuButton(
                                onClick = { imageViewModel.openCreateWithPrompt(prompt, styleAppend = true) },
                                compact = true,
                                variant = GuButtonVariant.PRIMARY,
                            ) {
                                Text(strings.images.applyStyle, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(prompt, color = c.muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
