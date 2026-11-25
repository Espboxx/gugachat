package com.gugachat.app.ui.images

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
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
    val c = LocalAppTheme.current.colors

    val latestCompleted = state.generations.firstOrNull { it.images.isNotEmpty() }

    fun openLatestInMode(mode: ImageEditMode) {
        if (latestCompleted != null) {
            imageViewModel.openEditor(latestCompleted, 0, mode)
        } else {
            container.toastController.show(strings.images.emptyWorks, isError = true)
            imageViewModel.setShowCreateSheet(true)
        }
    }

    val editTools = listOf(
        ToolCardSpec(strings.images.toolInpaintTitle, strings.images.toolInpaintDesc, Icons.Default.Brush, c.primary, c.primarySoft) {
            openLatestInMode(ImageEditMode.INPAINT)
        },
        ToolCardSpec(strings.images.toolOutpaintTitle, strings.images.toolOutpaintDesc, Icons.Default.AspectRatio, c.success, c.successBg) {
            openLatestInMode(ImageEditMode.OUTPAINT)
        },
        ToolCardSpec(strings.images.toolEditTitle, strings.images.toolEditDesc, Icons.Default.Edit, c.accent, c.accentSoft) {
            openLatestInMode(ImageEditMode.EDIT)
        },
        ToolCardSpec(strings.images.toolVariationTitle, strings.images.toolVariationDesc, Icons.Default.AutoAwesome, c.primaryDark, c.primarySoft) {
            openLatestInMode(ImageEditMode.VARIATION)
        },
    )

    val workflowTools = listOf(
        ToolCardSpec(strings.images.toolPadRefTitle, strings.images.toolPadRefDesc, Icons.Default.AddPhotoAlternate, c.success, c.successBg) {
            if (latestCompleted != null) imageViewModel.useGenerationAsReference(latestCompleted, 0)
            else imageViewModel.setShowCreateSheet(true)
        },
        ToolCardSpec(strings.images.toolNovelAITitle, strings.images.toolNovelAIDesc, Icons.Default.ColorLens, c.primary, c.primarySoft) {
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
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 84.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            if (latestCompleted != null) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = c.surface,
                    border = BorderStroke(1.dp, c.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.horizontalGradient(listOf(c.primarySoft.copy(alpha = 0.45f), c.surface)))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AuthenticatedImage(
                                container = container,
                                generationId = latestCompleted.id,
                                imageIndex = 0,
                                variant = "thumb",
                                revision = latestCompleted.updated_at ?: latestCompleted.created_at,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { imageViewModel.openDetail(latestCompleted, 0) },
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = strings.images.studioQuickCanvasTitle,
                                    color = c.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = latestCompleted.prompt.ifBlank { latestCompleted.model },
                                    color = c.ink,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = latestCompleted.model,
                                    color = c.muted,
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = strings.images.studioSwitchWork,
                                color = c.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable(onClick = onNavigateToWorks)
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            QuickActionChip(strings.images.toolInpaintTitle, Icons.Default.Brush, c.primary) {
                                openLatestInMode(ImageEditMode.INPAINT)
                            }
                            QuickActionChip(strings.images.toolOutpaintTitle, Icons.Default.AspectRatio, c.success) {
                                openLatestInMode(ImageEditMode.OUTPAINT)
                            }
                            QuickActionChip(strings.images.toolEditTitle, Icons.Default.Edit, c.accent) {
                                openLatestInMode(ImageEditMode.EDIT)
                            }
                            QuickActionChip(strings.images.toolVariationTitle, Icons.Default.AutoAwesome, c.primary) {
                                openLatestInMode(ImageEditMode.VARIATION)
                            }
                            QuickActionChip(strings.images.toolPadRefTitle, Icons.Default.AddPhotoAlternate, c.success) {
                                imageViewModel.useGenerationAsReference(latestCompleted, 0)
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = c.surface,
                    border = BorderStroke(1.dp, c.border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.horizontalGradient(listOf(c.primarySoft.copy(alpha = 0.5f), c.accentSoft.copy(alpha = 0.35f))))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(c.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(strings.images.title, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                            Text(strings.images.promptPlaceholder, color = c.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            ToolSectionHeader(title = strings.images.studioSectionEdit)
        }

        item {
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

        item {
            ToolSectionHeader(title = strings.images.studioSectionWorkflow)
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                workflowTools.forEach { card ->
                    Surface(
                        onClick = card.onClick,
                        shape = RoundedCornerShape(15.dp),
                        color = c.surface,
                        border = BorderStroke(1.dp, c.border),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(card.bg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(card.icon, contentDescription = null, tint = card.tint, modifier = Modifier.size(20.dp))
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(card.title, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(card.desc, color = c.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolSectionHeader(title: String) {
    val c = LocalAppTheme.current.colors
    Text(
        text = title,
        color = c.muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 2.dp),
    )
}

@Composable
private fun CompactToolCard(
    card: ToolCardSpec,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppTheme.current.colors
    Surface(
        onClick = card.onClick,
        shape = RoundedCornerShape(16.dp),
        color = c.surface,
        border = BorderStroke(1.dp, c.border),
        modifier = modifier.height(104.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(card.bg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(card.icon, contentDescription = null, tint = card.tint, modifier = Modifier.size(19.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = card.title,
                    color = c.ink,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = card.desc,
                    color = c.muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun QuickActionChip(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    val c = LocalAppTheme.current.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = c.surfaceSoft,
        border = BorderStroke(1.dp, c.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
            Text(label, color = c.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}
