package com.gugachat.app.ui.images

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.AppContainer
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuModalDragHandle
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.data.api.OnlineImageGenerationDto
import com.gugachat.app.domain.images.ImageEditMode
import com.gugachat.app.domain.images.OnlineImageStudioUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImageDetailSheet(
    container: AppContainer,
    generation: OnlineImageGenerationDto,
    imageIndex: Int,
    versions: List<OnlineImageGenerationDto>,
    onSelectImageIndex: (Int) -> Unit,
    onSelectVersion: (OnlineImageGenerationDto) -> Unit,
    onOpenLightbox: () -> Unit,
    onOpenEditor: (ImageEditMode) -> Unit,
    onDrawSame: () -> Unit,
    onUseAsRef: () -> Unit,
    onRegenerate: () -> Unit,
    onSaveToGallery: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val ctx = LocalContext.current
    val isZh = strings.language == AppLanguage.ZH
    val safeIdx = imageIndex.coerceIn(0, (generation.images.size - 1).coerceAtLeast(0))

    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        GuConfirmDialog(
            title = strings.common.delete,
            message = strings.images.deleteConfirm,
            confirmText = strings.common.delete,
            cancelText = strings.common.cancel,
            isDestructive = true,
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    val activeAsset = generation.images.getOrNull(safeIdx)
    val previewAspect = remember(activeAsset?.width, activeAsset?.height, generation.aspect_ratio) {
        val assetW = activeAsset?.width ?: 0
        val assetH = activeAsset?.height ?: 0
        val raw = if (assetW > 0 && assetH > 0) {
            assetW.toFloat() / assetH.toFloat()
        } else {
            val (rw, rh) = OnlineImageStudioUtils.parseAspectRatio(generation.aspect_ratio)
            (rw / rh).toFloat()
        }
        raw.coerceIn(0.68f, 1.6f)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                title = strings.images.detailParams,
                subtitle = generation.model.takeIf { it.isNotBlank() },
                onClose = onDismiss,
                closeContentDescription = strings.common.close,
                trailing = {
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = strings.common.delete,
                            tint = c.muted,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val heroShape = RoundedCornerShape(16.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .aspectRatio(previewAspect)
                        .clip(heroShape)
                        .background(c.surfaceSoft)
                        .clickable(onClick = onOpenLightbox),
                ) {
                    AuthenticatedImage(
                        container = container,
                        generationId = generation.id,
                        imageIndex = safeIdx,
                        variant = "original",
                        revision = generation.updated_at ?: generation.created_at,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .aspectRatio(previewAspect),
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.Black.copy(alpha = 0.56f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Text(
                            text = strings.images.zoomPreview,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                if (generation.images.size > 1) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        generation.images.forEach { asset ->
                            val selected = asset.index == safeIdx
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (selected) c.primarySoft else c.surfaceSoft)
                                    .clickable { onSelectImageIndex(asset.index) }
                                    .padding(horizontal = 11.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    text = "#${asset.index + 1}",
                                    color = if (selected) c.primarySoftInk else c.muted,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }

                if (versions.size > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        GuSectionHeader(title = strings.images.editorVersions)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            versions.forEachIndexed { idx, v ->
                                val selected = v.id == generation.id
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(if (selected) c.primarySoft else c.surfaceSoft)
                                        .clickable { onSelectVersion(v) }
                                        .padding(horizontal = 11.dp, vertical = 5.dp),
                                ) {
                                    Text(
                                        text = "v${idx + 1} · ${strings.images.actions[v.action] ?: (v.action ?: "gen")}",
                                        color = if (selected) c.primarySoftInk else c.muted,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }

                // Prompt & Metadata GuGroupedCard
                GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                    if (generation.prompt.isNotBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = strings.images.promptLabel,
                                    color = c.muted,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                IconButton(
                                    onClick = {
                                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("prompt", generation.prompt))
                                        container.toastController.show(strings.common.copied)
                                    },
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = strings.common.copy,
                                        tint = c.muted,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            Text(
                                text = generation.prompt,
                                color = c.ink,
                                fontSize = 13.sp,
                                lineHeight = 19.5.sp,
                                fontWeight = FontWeight.Normal,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = c.border.copy(alpha = 0.55f),
                        )
                    }

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (generation.model.isNotBlank()) {
                            GuBadge(generation.model, variant = GuBadgeVariant.BLUE)
                        }
                        if (generation.aspect_ratio.isNotBlank()) {
                            GuBadge(generation.aspect_ratio, variant = GuBadgeVariant.NEUTRAL)
                        }
                        if (generation.resolution.isNotBlank()) {
                            GuBadge(generation.resolution, variant = GuBadgeVariant.NEUTRAL)
                        }
                        val naiCharCount = remember(generation.novelai) {
                            com.gugachat.app.domain.images.OnlineImageNovelAI.countCharactersInPayload(generation.novelai)
                        }
                        if (naiCharCount > 0) {
                            GuBadge(
                                text = if (isZh) "${naiCharCount} 个角色" else "$naiCharCount chars",
                                variant = GuBadgeVariant.HONEY,
                            )
                        }
                        GuBadge(
                            strings.images.statuses[generation.status] ?: generation.status,
                            variant = if (generation.status == "completed") GuBadgeVariant.EMERALD else GuBadgeVariant.AMBER,
                        )
                    }
                }
            }

            // Sticky positive action dock: [画同款] / [局部重绘 / 扩图] / [保存相册] + compact utilities
            HorizontalDivider(thickness = 0.5.dp, color = c.border.copy(alpha = 0.55f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DockUtilityChip(
                        icon = Icons.Default.Download,
                        label = if (isZh) "保存相册" else strings.images.actionDownload,
                        onClick = onSaveToGallery,
                        modifier = Modifier.weight(1f),
                    )
                    DockUtilityChip(
                        icon = Icons.Default.AspectRatio,
                        label = if (isZh) "智能扩图" else "Expand",
                        onClick = { onOpenEditor(ImageEditMode.OUTPAINT) },
                        modifier = Modifier.weight(1f),
                    )
                    DockUtilityChip(
                        icon = Icons.Default.AddPhotoAlternate,
                        label = strings.images.actionPadRef,
                        onClick = onUseAsRef,
                        modifier = Modifier.weight(1f),
                    )
                    if (generation.can_regenerate == true || generation.status == "completed") {
                        DockUtilityChip(
                            icon = Icons.Default.Refresh,
                            label = if (isZh) "重绘" else strings.images.regenerate,
                            onClick = onRegenerate,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    DockUtilityChip(
                        icon = Icons.Default.Share,
                        label = if (isZh) "分享" else "Share",
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GuButton(
                        onClick = { onOpenEditor(ImageEditMode.INPAINT) },
                        variant = GuButtonVariant.SUBTLE,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isZh) "局部重绘" else strings.images.actionStudio,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    GuButton(
                        onClick = onDrawSame,
                        variant = GuButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1.15f),
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = strings.images.actionDrawSame,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DockUtilityChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppTheme.current.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.surfaceSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = c.ink,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            color = c.muted,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
