package com.gugachat.app.ui.images

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gugachat.app.AppContainer
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuAwningHeader
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuSegmentedControl
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.images.ImageEditMode
import com.gugachat.app.domain.images.MaskStroke
import com.gugachat.app.domain.images.OnlineImageStudioUtils
import com.gugachat.app.domain.images.OutpaintAnchor
import kotlin.math.roundToInt

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
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH

    val asset = target.generation.images.getOrNull(target.imageIndex)
    val sourceW = (asset?.width ?: 1024).coerceAtLeast(64)
    val sourceH = (asset?.height ?: 1024).coerceAtLeast(64)

    var mode by remember(target) { mutableStateOf(target.mode) }
    var instruction by remember { mutableStateOf("") }
    var strokes by remember { mutableStateOf<List<MaskStroke>>(emptyList()) }
    var brushRadius by remember { mutableFloatStateOf(0.055f) }
    var isEraser by remember { mutableStateOf(false) }
    var isDrawingMask by remember { mutableStateOf(false) }

    var outpaintAspect by remember { mutableStateOf("16:9") }
    var outpaintResolution by remember { mutableStateOf("1K") }
    var anchor by remember { mutableStateOf(OutpaintAnchor.CENTER) }
    var imageScale by remember { mutableFloatStateOf(0.65f) }
    var variationStrength by remember { mutableDoubleStateOf(0.5) }

    val (targetW, targetH) = remember(outpaintAspect, outpaintResolution) {
        OnlineImageStudioUtils.computeCanvasDimensions(outpaintAspect, outpaintResolution, null)
    }

    val modes = remember {
        listOf(
            ImageEditMode.EDIT,
            ImageEditMode.VARIATION,
            ImageEditMode.INPAINT,
            ImageEditMode.OUTPAINT,
        )
    }
    val modeLabels = listOf(
        strings.images.actions["edit"] ?: "Edit",
        strings.images.actions["variation"] ?: "Variation",
        strings.images.actions["inpaint"] ?: "Inpaint",
        strings.images.actions["outpaint"] ?: "Outpaint",
    )

    val canSubmit = when (mode) {
        ImageEditMode.INPAINT -> strokes.isNotEmpty()
        ImageEditMode.EDIT -> instruction.isNotBlank()
        ImageEditMode.OUTPAINT, ImageEditMode.VARIATION -> true
    }

    val validationHint = when {
        mode == ImageEditMode.INPAINT && strokes.isEmpty() -> strings.images.editorSelectionRequired
        mode == ImageEditMode.EDIT && instruction.isBlank() -> strings.images.editorInstructionRequired
        else -> null
    }

    val scrollState = rememberScrollState()
    val sliderColors = SliderDefaults.colors(
        thumbColor = c.primary,
        activeTrackColor = c.primary,
        inactiveTrackColor = c.outline,
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(c.page)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            GuAwningHeader(includeStatusBarPadding = true) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = strings.common.close, tint = c.ink)
                    }
                    Text(
                        text = strings.images.editorTitle,
                        color = c.ink,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                        enabled = canSubmit && !isSubmitting,
                        loading = isSubmitting,
                        compact = true,
                    ) {
                        Text(strings.images.editorSubmit, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                GuSegmentedControl(
                    items = modeLabels,
                    selectedIndex = modes.indexOf(mode).coerceAtLeast(0),
                    onSelect = { idx -> mode = modes[idx] },
                    compact = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState, enabled = !isDrawingMask)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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
                            onDrawingChange = { active -> isDrawingMask = active },
                        )
                        Text(strings.images.editorMaskHint, color = c.muted, fontSize = 11.5.sp)

                        GuCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                GuSegmentedControl(
                                    items = listOf(strings.images.editorBrush, strings.images.editorErase),
                                    selectedIndex = if (isEraser) 1 else 0,
                                    onSelect = { isEraser = it == 1 },
                                    compact = true,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { if (strokes.isNotEmpty()) strokes = strokes.dropLast(1) },
                                    enabled = strokes.isNotEmpty(),
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Undo,
                                        contentDescription = strings.images.editorUndo,
                                        tint = if (strokes.isNotEmpty()) c.ink else c.muted.copy(alpha = 0.35f),
                                    )
                                }
                                IconButton(
                                    onClick = { strokes = emptyList() },
                                    enabled = strokes.isNotEmpty(),
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = strings.images.editorClear,
                                        tint = if (strokes.isNotEmpty()) c.danger else c.danger.copy(alpha = 0.35f),
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))
                            HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.72f))
                            Spacer(Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Brush, contentDescription = null, tint = c.muted, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.images.editorBrushSize, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier.size(24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size((brushRadius * 170f).dp.coerceIn(7.dp, 22.dp))
                                            .clip(CircleShape)
                                            .background(if (isEraser) c.muted.copy(alpha = 0.55f) else Color(0xCCEF4444)),
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Slider(
                                    value = brushRadius,
                                    onValueChange = { brushRadius = it },
                                    valueRange = 0.015f..0.14f,
                                    colors = sliderColors,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = c.primarySoft,
                                    border = BorderStroke(theme.borderWidth, c.primary.copy(alpha = 0.3f)),
                                ) {
                                    Text(
                                        text = "${((brushRadius / 0.14f) * 100).roundToInt()}%",
                                        color = c.primarySoftInk,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    )
                                }
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
                        Text(strings.images.editorExpandHint, color = c.muted, fontSize = 11.5.sp)

                        GuCard {
                            Text(strings.images.aspectRatio, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("16:9", "9:16", "1:1", "4:3", "3:4", "21:9").forEach { r ->
                                    GuPill(r, outpaintAspect == r, { outpaintAspect = r })
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.72f))
                            Spacer(Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(strings.images.editorImageScale, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = c.primarySoft,
                                    border = BorderStroke(theme.borderWidth, c.primary.copy(alpha = 0.32f)),
                                ) {
                                    Text(
                                        text = "${(imageScale * 100).roundToInt()}%",
                                        color = c.primarySoftInk,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp),
                                    )
                                }
                            }
                            Slider(
                                value = imageScale,
                                onValueChange = { imageScale = it },
                                valueRange = 0.25f..0.95f,
                                colors = sliderColors,
                            )

                            Spacer(Modifier.height(6.dp))
                            HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.72f))
                            Spacer(Modifier.height(10.dp))

                            Text(strings.images.editorAnchorLabel, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(c.surfaceSoft)
                                    .border(theme.borderWidth, c.border, RoundedCornerShape(12.dp))
                                    .padding(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                for (rowIdx in 0..2) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        for (colIdx in 0..2) {
                                            val item = OutpaintAnchor.entries.first { it.row == rowIdx && it.col == colIdx }
                                            val selected = anchor == item
                                            Surface(
                                                onClick = { anchor = item },
                                                shape = RoundedCornerShape(9.dp),
                                                color = if (selected) c.primary else c.surface,
                                                border = BorderStroke(theme.borderWidth, if (selected) c.primary else c.border),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(38.dp),
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = anchorShortLabel(item, isZh),
                                                        color = if (selected) Color.White else c.ink,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                        maxLines = 1,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        val previewShape = RoundedCornerShape(14.dp)
                        AuthenticatedImage(
                            container = container,
                            generationId = target.generation.id,
                            imageIndex = target.imageIndex,
                            variant = "original",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio((sourceW.toFloat() / sourceH).coerceIn(0.5f, 2f))
                                .clip(previewShape)
                                .background(c.surfaceSoft)
                                .border(theme.borderWidth, c.border, previewShape),
                        )
                        if (mode == ImageEditMode.VARIATION) {
                            GuSegmentedControl(
                                items = listOf(strings.images.editorSubtle, strings.images.editorBold),
                                selectedIndex = if (variationStrength < 0.5) 0 else 1,
                                onSelect = { idx ->
                                    if (idx == 0) {
                                        variationStrength = 0.35
                                        instruction = strings.images.editorSubtlePrompt
                                    } else {
                                        variationStrength = 0.72
                                        instruction = strings.images.editorBoldPrompt
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                GuButton(onClick = { instruction = strings.images.editorQuickBgPrompt }, compact = true, variant = GuButtonVariant.SUBTLE) {
                                    Text(strings.images.editorQuickBg, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                                GuButton(onClick = { instruction = strings.images.editorQuickColorPrompt }, compact = true, variant = GuButtonVariant.SUBTLE) {
                                    Text(strings.images.editorQuickColor, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                                GuButton(onClick = { instruction = strings.images.editorQuickStylePrompt }, compact = true, variant = GuButtonVariant.SUBTLE) {
                                    Text(strings.images.editorQuickStyle, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                                GuButton(onClick = { instruction = strings.images.editorQuickTextPrompt }, compact = true, variant = GuButtonVariant.SUBTLE) {
                                    Text(strings.images.editorQuickText, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
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

                if (validationHint != null) {
                    Text(
                        text = validationHint,
                        color = c.muted,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private fun anchorShortLabel(anchor: OutpaintAnchor, isZh: Boolean): String = when (anchor) {
    OutpaintAnchor.TOP_LEFT -> if (isZh) "左上" else "TL"
    OutpaintAnchor.TOP -> if (isZh) "上方" else "Top"
    OutpaintAnchor.TOP_RIGHT -> if (isZh) "右上" else "TR"
    OutpaintAnchor.LEFT -> if (isZh) "左侧" else "Left"
    OutpaintAnchor.CENTER -> if (isZh) "居中" else "Center"
    OutpaintAnchor.RIGHT -> if (isZh) "右侧" else "Right"
    OutpaintAnchor.BOTTOM_LEFT -> if (isZh) "左下" else "BL"
    OutpaintAnchor.BOTTOM -> if (isZh) "下方" else "Bottom"
    OutpaintAnchor.BOTTOM_RIGHT -> if (isZh) "右下" else "BR"
}
