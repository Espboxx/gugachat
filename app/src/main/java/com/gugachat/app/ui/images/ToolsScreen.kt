package com.gugachat.app.ui.images

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gugachat.app.AppContainer
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuSettingsRow
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.images.ImageEditMode

private data class ToolCardSpec(
    val title: String,
    val desc: String,
    val icon: ImageVector,
    val tint: Color,
    val bg: Color,
    val onClick: () -> Unit,
)

@Composable
fun ToolsTabContent(
    container: AppContainer,
    imageViewModel: ImageViewModel,
    onNavigateToWorks: () -> Unit,
    onNavigateToInspireStyles: () -> Unit,
) {
    val state by imageViewModel.ui.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH

    val completedWorks = remember(state.generations) {
        state.generations.filter { it.images.isNotEmpty() }
    }
    var selectedWorkId by rememberSaveable { mutableStateOf<String?>(null) }
    val activeWork = remember(completedWorks, selectedWorkId) {
        completedWorks.firstOrNull { it.id == selectedWorkId } ?: completedWorks.firstOrNull()
    }

    fun openActiveInMode(mode: ImageEditMode) {
        if (activeWork != null) {
            imageViewModel.openEditor(activeWork, 0, mode)
        } else {
            container.toastController.show(
                if (isZh) "请先生成一张作品后再使用精修工具" else "Generate an artwork first to use edit tools",
                isError = false,
            )
            imageViewModel.setShowCreateSheet(true)
        }
    }

    val editTools = listOf(
        ToolCardSpec(strings.images.toolInpaintTitle, strings.images.toolInpaintDesc, Icons.Default.Brush, c.primary, c.primarySoft) {
            openActiveInMode(ImageEditMode.INPAINT)
        },
        ToolCardSpec(strings.images.toolOutpaintTitle, strings.images.toolOutpaintDesc, Icons.Default.AspectRatio, c.success, c.successBg) {
            openActiveInMode(ImageEditMode.OUTPAINT)
        },
        ToolCardSpec(strings.images.toolEditTitle, strings.images.toolEditDesc, Icons.Default.Edit, c.accent, c.accentSoft) {
            openActiveInMode(ImageEditMode.EDIT)
        },
        ToolCardSpec(strings.images.toolVariationTitle, strings.images.toolVariationDesc, Icons.Default.AutoAwesome, c.primarySoftInk, c.primarySoft) {
            openActiveInMode(ImageEditMode.VARIATION)
        },
    )

    val workflowTools = listOf(
        ToolCardSpec(strings.images.toolPadRefTitle, strings.images.toolPadRefDesc, Icons.Default.AddPhotoAlternate, c.success, c.successBg) {
            if (activeWork != null) imageViewModel.useGenerationAsReference(activeWork, 0)
            else imageViewModel.setShowCreateSheet(true)
        },
        ToolCardSpec(strings.images.toolNovelAITitle, strings.images.toolNovelAIDesc, Icons.Default.ColorLens, c.primarySoftInk, c.primarySoft) {
            imageViewModel.openNovelAICreate()
        },
        ToolCardSpec(strings.images.toolStylesTitle, strings.images.toolStylesDesc, Icons.Default.Style, c.accent, c.accentSoft) {
            onNavigateToInspireStyles()
        },
        ToolCardSpec(strings.images.toolPresetsTitle, strings.images.toolPresetsDesc, Icons.Default.Bookmarks, c.warning, c.warningBg) {
            imageViewModel.setShowPresetSheet(true)
        },
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(c.page),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            if (activeWork != null) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = c.surface,
                    border = BorderStroke(theme.borderWidth, c.primary.copy(alpha = 0.36f)),
                    shadowElevation = if (theme.isDark) 0.dp else 0.5.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(c.primarySoft.copy(alpha = if (theme.isDark) 0.55f else 0.48f), c.surface),
                                ),
                            )
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val heroThumbShape = RoundedCornerShape(14.dp)
                            AuthenticatedImage(
                                container = container,
                                generationId = activeWork.id,
                                imageIndex = 0,
                                variant = "thumb",
                                revision = activeWork.updated_at ?: activeWork.created_at,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(heroThumbShape)
                                    .border(theme.borderWidth, c.outline.copy(alpha = 0.7f), heroThumbShape)
                                    .clickable { imageViewModel.openDetail(activeWork, 0) },
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = if (isZh) "当前精修画布" else "Active Canvas Target",
                                    color = c.primarySoftInk,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = activeWork.prompt.ifBlank { activeWork.model },
                                    color = c.ink,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = activeWork.model,
                                    color = c.muted,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            GuButton(
                                onClick = { imageViewModel.setShowCreateSheet(true) },
                                compact = true,
                                variant = GuButtonVariant.PRIMARY,
                            ) {
                                Text(strings.images.studioCreateFab, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (completedWorks.size > 1) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                LazyRow(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                ) {
                                    items(completedWorks.take(8), key = { it.id }) { work ->
                                        val isCurrent = work.id == activeWork.id
                                        val thumbShape = RoundedCornerShape(10.dp)
                                        AuthenticatedImage(
                                            container = container,
                                            generationId = work.id,
                                            imageIndex = 0,
                                            variant = "thumb",
                                            revision = work.updated_at ?: work.created_at,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(thumbShape)
                                                .border(
                                                    width = if (isCurrent) 2.dp else theme.borderWidth,
                                                    color = if (isCurrent) c.primary else c.border,
                                                    shape = thumbShape,
                                                )
                                                .clickable { selectedWorkId = work.id },
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(c.surfaceSoft)
                                        .border(theme.borderWidth, c.border, RoundedCornerShape(999.dp))
                                        .clickable(onClick = onNavigateToWorks)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        text = strings.images.studioSwitchWork,
                                        color = c.primarySoftInk,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = c.surface,
                    border = BorderStroke(theme.borderWidth, c.border),
                    shadowElevation = if (theme.isDark) 0.dp else 0.5.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(c.primarySoft.copy(alpha = 0.52f), c.accentSoft.copy(alpha = 0.34f)),
                                ),
                            )
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        GuIconTile(
                            icon = Icons.Default.AutoAwesome,
                            tint = Color.White,
                            backgroundColor = c.primary,
                            size = 46.dp,
                            iconSize = 23.dp,
                            cornerRadius = 13.dp,
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(strings.images.title, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                            Text(
                                text = if (isZh) "文生图、涂抹重绘与 NovelAI 一站创作" else "Text-to-image, inpaint & NovelAI studio",
                                color = c.muted,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        GuButton(
                            onClick = { imageViewModel.setShowCreateSheet(true) },
                            compact = true,
                            variant = GuButtonVariant.PRIMARY,
                        ) {
                            Text(strings.images.studioCreateFab, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GuSectionHeader(title = strings.images.studioSectionEdit)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    editTools.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            rowItems.forEach { card ->
                                CompactToolCard(card = card, modifier = Modifier.weight(1f))
                            }
                            if (rowItems.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GuSectionHeader(title = strings.images.studioSectionWorkflow)
                GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                    workflowTools.forEachIndexed { idx, card ->
                        GuSettingsRow(
                            title = card.title,
                            subtitle = card.desc,
                            icon = card.icon,
                            iconTint = card.tint,
                            iconBg = card.bg,
                            showChevron = true,
                            showDivider = idx < workflowTools.lastIndex,
                            onClick = card.onClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactToolCard(
    card: ToolCardSpec,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    Surface(
        onClick = card.onClick,
        shape = RoundedCornerShape(16.dp),
        color = c.surface,
        border = BorderStroke(theme.borderWidth, c.border),
        shadowElevation = if (theme.isDark) 0.dp else 0.5.dp,
        modifier = modifier.heightIn(min = 108.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(13.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            GuIconTile(
                icon = card.icon,
                tint = card.tint,
                backgroundColor = card.bg,
                size = 38.dp,
                iconSize = 20.dp,
                cornerRadius = 11.dp,
            )
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = card.title,
                    color = c.ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = card.desc,
                    color = c.muted,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
