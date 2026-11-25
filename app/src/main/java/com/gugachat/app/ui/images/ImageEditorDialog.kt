package com.gugachat.app.ui.images

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gugachat.app.AppContainer
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuAwningHeader
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.images.ImageEditMode
import com.gugachat.app.domain.images.MaskStroke
import com.gugachat.app.domain.images.OnlineImageStudioUtils
import com.gugachat.app.domain.images.OutpaintAnchor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageEditorDialog(
    container: AppContainer,
    target: EditorTarget,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (
        target: EditorTarget,
        instruction: String,
        strokes: List<MaskStroke>,
        sourceW: Int,
        sourceH: Int,
        outpaintAspect: String,
        outpaintResolution: String,
        anchor: OutpaintAnchor,
        scale: Float,
        variationStrength: Double,
    ) -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors

    val asset = target.generation.images.getOrNull(target.imageIndex)
    val sourceW = (asset?.width ?: 1024).coerceAtLeast(64)
    val sourceH = (asset?.height ?: 1024).coerceAtLeast(64)

    var mode by remember(target) { mutableStateOf(target.mode) }
    var instruction by remember { mutableStateOf("") }
    var strokes by remember { mutableStateOf<List<MaskStroke>>(emptyList()) }
    var brushRadius by remember { mutableFloatStateOf(0.055f) }
    var isEraser by remember { mutableStateOf(false) }

    var outpaintAspect by remember { mutableStateOf("16:9") }
    var outpaintResolution by remember { mutableStateOf("1K") }
    var anchor by remember { mutableStateOf(OutpaintAnchor.CENTER) }
    var imageScale by remember { mutableFloatStateOf(0.65f) }
    var variationStrength by remember { mutableDoubleStateOf(0.5) }

    val (targetW, targetH) = remember(outpaintAspect, outpaintResolution) {
        OnlineImageStudioUtils.computeCanvasDimensions(outpaintAspect, outpaintResolution, null)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(modifier = Modifier.fillMaxSize().background(c.page)) {
            GuAwningHeader(includeStatusBarPadding = false) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = strings.common.close, tint = c.ink)
                    }
                    Text(
                        text = strings.images.editorTitle,
                        color = c.ink,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f),
                    )
                    GuButton(
                        onClick = {
                            onSubmit(
                                target.copy(mode = mode),
                                instruction,
                                strokes,
                                sourceW,
                                sourceH,
                                outpaintAspect,
                                outpaintResolution,
                                anchor,
                                imageScale,
                                variationStrength,
                            )
                        },
                        loading = isSubmitting,
                        compact = true,
                    ) {
                        Text(strings.images.editorSubmit, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuPill(strings.images.actions["edit"] ?: "Edit", mode == ImageEditMode.EDIT, { mode = ImageEditMode.EDIT })
                    GuPill(strings.images.actions["variation"] ?: "Variation", mode == ImageEditMode.VARIATION, { mode = ImageEditMode.VARIATION })
                    GuPill(strings.images.actions["inpaint"] ?: "Inpaint", mode == ImageEditMode.INPAINT, { mode = ImageEditMode.INPAINT }, useHoneySelected = true)
                    GuPill(strings.images.actions["outpaint"] ?: "Outpaint", mode == ImageEditMode.OUTPAINT, { mode = ImageEditMode.OUTPAINT }, useHoneySelected = true)
                }

                when (mode) {
                    ImageEditMode.INPAINT -> {
                        InpaintMaskCanvas(
                            container = container,
                            generationId = target.generation.id,
                            imageIndex = target.imageIndex,
                            sourceWidth = sourceW,
                            sourceHeight = sourceH,
                            strokes = strokes,
                            brushRadiusNormalized = brushRadius,
                            isEraser = isEraser,
                            onAddStroke = { s -> strokes = strokes + s },
                        )
                        Text(strings.images.editorMaskHint, color = c.muted, fontSize = 11.sp)

                        GuCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                GuPill(strings.images.editorBrush, !isEraser, { isEraser = false })
                                GuPill(strings.images.editorErase, isEraser, { isEraser = true })
                                Spacer(Modifier.weight(1f))
                                IconButton(
                                    onClick = { if (strokes.isNotEmpty()) strokes = strokes.dropLast(1) },
                                    enabled = strokes.isNotEmpty(),
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = strings.images.editorUndo, tint = c.ink)
                                }
                                IconButton(
                                    onClick = { strokes = emptyList() },
                                    enabled = strokes.isNotEmpty(),
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = strings.images.editorClear, tint = c.danger)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Brush, contentDescription = null, tint = c.muted, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(strings.images.editorBrushSize, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Slider(
                                    value = brushRadius,
                                    onValueChange = { brushRadius = it },
                                    valueRange = 0.015f..0.14f,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    ImageEditMode.OUTPAINT -> {
                        OutpaintPreviewCanvas(
                            container = container,
                            generationId = target.generation.id,
                            imageIndex = target.imageIndex,
                            sourceWidth = sourceW,
                            sourceHeight = sourceH,
                            targetWidth = targetW,
                            targetHeight = targetH,
                            anchor = anchor,
                            imageScale = imageScale,
                        )
                        Text(strings.images.editorExpandHint, color = c.muted, fontSize = 11.sp)

                        GuCard {
                            Text(strings.images.aspectRatio, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("16:9", "9:16", "1:1", "4:3", "3:4", "21:9").forEach { r ->
                                    GuPill(r, outpaintAspect == r, { outpaintAspect = r })
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(strings.images.editorImageScale, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Slider(value = imageScale, onValueChange = { imageScale = it }, valueRange = 0.25f..0.95f)

                            Text(strings.images.editorAnchorLabel, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (rowIdx in 0..2) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        for (colIdx in 0..2) {
                                            val item = OutpaintAnchor.entries.first { it.row == rowIdx && it.col == colIdx }
                                            GuPill(
                                                label = item.name.replace('_', ' '),
                                                selected = anchor == item,
                                                onClick = { anchor = item },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        AuthenticatedImage(
                            container = container,
                            generationId = target.generation.id,
                            imageIndex = target.imageIndex,
                            variant = "original",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio((sourceW.toFloat() / sourceH).coerceIn(0.5f, 2f))
                                .clip(RoundedCornerShape(12.dp)),
                        )
                        if (mode == ImageEditMode.VARIATION) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GuPill(strings.images.editorSubtle, variationStrength < 0.5, {
                                    variationStrength = 0.35
                                    instruction = strings.images.editorSubtlePrompt
                                })
                                GuPill(strings.images.editorBold, variationStrength >= 0.5, {
                                    variationStrength = 0.72
                                    instruction = strings.images.editorBoldPrompt
                                })
                            }
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                GuButton(onClick = { instruction = strings.images.editorQuickBgPrompt }, compact = true, variant = GuButtonVariant.SUBTLE) {
                                    Text(strings.images.editorQuickBg, fontSize = 11.sp)
                                }
                                GuButton(onClick = { instruction = strings.images.editorQuickColorPrompt }, compact = true, variant = GuButtonVariant.SUBTLE) {
                                    Text(strings.images.editorQuickColor, fontSize = 11.sp)
                                }
                                GuButton(onClick = { instruction = strings.images.editorQuickStylePrompt }, compact = true, variant = GuButtonVariant.SUBTLE) {
                                    Text(strings.images.editorQuickStyle, fontSize = 11.sp)
                                }
                                GuButton(onClick = { instruction = strings.images.editorQuickTextPrompt }, compact = true, variant = GuButtonVariant.SUBTLE) {
                                    Text(strings.images.editorQuickText, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                GuTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    label = strings.images.editorInstruction,
                    placeholder = strings.images.editorInstructionPlaceholder,
                    singleLine = false,
                    minLines = 2,
                    maxLines = 5,
                )
            }
        }
    }
}
