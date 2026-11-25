package com.gugachat.app.ui.images

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
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
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuPill
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
    val c = LocalAppTheme.current.colors
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
        raw.coerceIn(0.75f, 1.6f)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.page,
        modifier = Modifier.statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = strings.images.detailParams,
                    color = c.ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = strings.common.close,
                        tint = c.muted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            HorizontalDivider(color = c.border)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .aspectRatio(previewAspect)
                        .clip(RoundedCornerShape(16.dp))
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
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).aspectRatio(previewAspect),
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.Black.copy(alpha = 0.62f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Text(
                            text = strings.images.zoomPreview,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                if (generation.images.size > 1) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        generation.images.forEach { asset ->
                            GuPill(
                                label = "#${asset.index + 1}",
                                selected = asset.index == safeIdx,
                                onClick = { onSelectImageIndex(asset.index) },
                            )
                        }
                    }
                }

                if (versions.size > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(strings.images.editorVersions, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            versions.forEachIndexed { idx, v ->
                                GuPill(
                                    label = "v${idx + 1} · ${strings.images.actions[v.action] ?: (v.action ?: "gen")}",
                                    selected = v.id == generation.id,
                                    onClick = { onSelectVersion(v) },
                                )
                            }
                        }
                    }
                }

                GuCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = strings.images.promptLabel,
                            color = c.muted,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        if (generation.prompt.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("prompt", generation.prompt))
                                    container.toastController.show(strings.common.copied)
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = strings.common.copy,
                                    tint = c.muted,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                    if (generation.prompt.isNotBlank()) {
                        Text(
                            text = generation.prompt,
                            color = c.ink,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (generation.model.isNotBlank()) {
                            GuBadge(generation.model, variant = GuBadgeVariant.BLUE)
                        }
                        if (generation.aspect_ratio.isNotBlank()) {
                            GuBadge(generation.aspect_ratio, variant = GuBadgeVariant.NEUTRAL)
                        }
                        if (generation.resolution.isNotBlank()) {
                            GuBadge(generation.resolution, variant = GuBadgeVariant.NEUTRAL)
                        }
                        GuBadge(
                            strings.images.statuses[generation.status] ?: generation.status,
                            variant = if (generation.status == "completed") GuBadgeVariant.EMERALD else GuBadgeVariant.AMBER,
                        )
                    }
                }

                // Tier 1: Primary Creative Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GuButton(
                        onClick = { onOpenEditor(ImageEditMode.INPAINT) },
                        variant = GuButtonVariant.HONEY,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.images.actionStudio, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    GuButton(
                        onClick = onDrawSame,
                        variant = GuButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.images.actionDrawSame, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Tier 2: Utility Action Tiles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DetailUtilityTile(
                        icon = Icons.Default.AddPhotoAlternate,
                        label = strings.images.actionPadRef,
                        onClick = onUseAsRef,
                        modifier = Modifier.weight(1f),
                    )
                    if (generation.can_regenerate == true || generation.status == "completed") {
                        DetailUtilityTile(
                            icon = Icons.Default.Refresh,
                            label = strings.images.regenerate,
                            onClick = onRegenerate,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    DetailUtilityTile(
                        icon = Icons.Default.Download,
                        label = strings.images.actionDownload,
                        onClick = onSaveToGallery,
                        modifier = Modifier.weight(1f),
                    )
                    DetailUtilityTile(
                        icon = Icons.Default.Share,
                        label = if (isZh) "分享" else "Share",
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                    )
                }

                // Tier 3: Guarded Destructive Delete
                Surface(
                    onClick = { showDeleteConfirm = true },
                    shape = RoundedCornerShape(999.dp),
                    color = c.dangerBg.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, c.danger.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = c.danger, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = strings.common.delete,
                            color = c.danger,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun DetailUtilityTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppTheme.current.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = c.surface,
        border = BorderStroke(1.dp, c.border),
        modifier = modifier.heightIn(min = 58.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp))
            Text(
                text = label,
                color = c.ink,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
