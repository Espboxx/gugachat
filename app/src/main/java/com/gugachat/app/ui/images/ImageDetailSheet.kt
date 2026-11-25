package com.gugachat.app.ui.images

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.AppContainer
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.data.api.OnlineImageGenerationDto
import com.gugachat.app.domain.images.ImageEditMode

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
    val safeIdx = imageIndex.coerceIn(0, (generation.images.size - 1).coerceAtLeast(0))

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.page) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
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
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
                GuBadge(
                    text = strings.images.zoomPreview,
                    variant = GuBadgeVariant.NEUTRAL,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                )
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

            GuCard {
                Text(generation.prompt, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuBadge(generation.model, variant = GuBadgeVariant.BLUE)
                    GuBadge(generation.aspect_ratio, variant = GuBadgeVariant.NEUTRAL)
                    GuBadge(generation.resolution, variant = GuBadgeVariant.NEUTRAL)
                    GuBadge(
                        strings.images.statuses[generation.status] ?: generation.status,
                        variant = if (generation.status == "completed") GuBadgeVariant.EMERALD else GuBadgeVariant.AMBER,
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GuButton(onClick = { onOpenEditor(ImageEditMode.INPAINT) }, compact = true, variant = GuButtonVariant.HONEY) {
                    Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.images.actionStudio, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                GuButton(onClick = onDrawSame, compact = true, variant = GuButtonVariant.PRIMARY) {
                    Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.images.actionDrawSame, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                GuButton(onClick = onUseAsRef, compact = true, variant = GuButtonVariant.SUBTLE) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.images.actionPadRef, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (generation.can_regenerate == true || generation.status == "completed") {
                    GuButton(onClick = onRegenerate, compact = true, variant = GuButtonVariant.SECONDARY) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.images.regenerate, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                GuButton(onClick = onSaveToGallery, compact = true, variant = GuButtonVariant.SECONDARY) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.images.actionDownload, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                GuButton(onClick = onShare, compact = true, variant = GuButtonVariant.SECONDARY) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                GuButton(onClick = onDelete, compact = true, variant = GuButtonVariant.DANGER) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.common.delete, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
