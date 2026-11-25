package com.gugachat.app.ui.images

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gugachat.app.AppContainer
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.data.api.OnlineImageGenerationDto

@Composable
fun GalleryTabContent(
    container: AppContainer,
    viewModel: ImageViewModel,
    showSearchBar: Boolean,
    onCloseSearchBar: () -> Unit,
    onNavigateToInspire: () -> Unit,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val ctx = LocalContext.current
    val isZh = strings.language == AppLanguage.ZH

    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    val zipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            viewModel.batchDownloadZip(uri, ctx.contentResolver, strings.images.zipSaved)
        }
    }

    if (showBatchDeleteConfirm) {
        GuConfirmDialog(
            title = strings.images.batchDelete,
            message = strings.images.batchDeleteConfirm.fmt("count" to state.selectedBatchIds.size),
            confirmText = strings.common.delete,
            cancelText = strings.common.cancel,
            isDestructive = true,
            onConfirm = {
                showBatchDeleteConfirm = false
                viewModel.batchDeleteSelected()
            },
            onDismiss = { showBatchDeleteConfirm = false },
        )
    }

    val processingCount = state.generations.count {
        it.status.lowercase() in setOf("pending", "queued", "processing", "running")
    }

    val filterItems = listOf(
        "all" to strings.images.filterAll,
        "processing" to if (processingCount > 0) "${strings.images.filterProcessing} $processingCount" else strings.images.filterProcessing,
        "completed" to strings.images.filterCompleted,
        "generate" to strings.images.filterGenerate,
        "edited" to strings.images.filterEdited,
        "expiring" to strings.images.filterExpiring,
        "failed" to strings.images.filterFailed,
    )

    val filterListState = rememberLazyListState()
    val isFiltered = state.activeFilter != "all" || state.searchQuery.isNotBlank()
    val allSelected = state.generations.isNotEmpty() && state.selectedBatchIds.size >= state.generations.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.page),
    ) {
        AnimatedVisibility(visible = showSearchBar || state.searchQuery.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = c.surface,
                border = BorderStroke(1.dp, c.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (state.searchQuery.isEmpty()) {
                            Text(
                                text = strings.images.searchPlaceholder,
                                color = c.muted,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BasicTextField(
                            value = state.searchQuery,
                            onValueChange = viewModel::setSearchQuery,
                            singleLine = true,
                            textStyle = TextStyle(color = c.ink, fontSize = 13.sp),
                            cursorBrush = SolidColor(c.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { viewModel.refreshGallery() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    IconButton(
                        onClick = {
                            if (state.searchQuery.isNotEmpty()) {
                                viewModel.setSearchQuery("")
                                viewModel.refreshGallery()
                            } else {
                                onCloseSearchBar()
                            }
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = if (state.searchQuery.isNotEmpty()) strings.common.clear else strings.common.close,
                            tint = c.muted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        LazyRow(
            state = filterListState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    drawContent()
                    if (filterListState.canScrollForward) {
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
            items(filterItems, key = { it.first }) { (key, label) ->
                GuPill(
                    label = label,
                    selected = state.activeFilter == key,
                    onClick = { viewModel.setFilter(key) },
                )
            }
        }

        AnimatedVisibility(visible = state.batchMode) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = c.surface,
                border = BorderStroke(1.dp, c.primary.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GuBadge(
                        strings.images.selectedCount.fmt("count" to state.selectedBatchIds.size),
                        variant = GuBadgeVariant.BLUE,
                    )
                    Spacer(Modifier.weight(1f))
                    GuButton(
                        onClick = {
                            if (allSelected) viewModel.clearBatchSelection() else viewModel.selectAllBatch()
                        },
                        compact = true,
                        variant = GuButtonVariant.SUBTLE,
                    ) {
                        Text(
                            if (allSelected) strings.images.clearSelection else strings.images.selectAll,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    GuButton(
                        onClick = { zipLauncher.launch("gugachat-works-${System.currentTimeMillis()}.zip") },
                        enabled = state.selectedBatchIds.isNotEmpty() && !state.isBatchBusy,
                        compact = true,
                        variant = GuButtonVariant.PRIMARY,
                    ) {
                        Icon(Icons.Default.FolderZip, contentDescription = strings.images.downloadZip, modifier = Modifier.size(15.dp))
                    }
                    GuButton(
                        onClick = { showBatchDeleteConfirm = true },
                        enabled = state.selectedBatchIds.isNotEmpty() && !state.isBatchBusy,
                        compact = true,
                        variant = GuButtonVariant.DANGER,
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = strings.images.batchDelete, modifier = Modifier.size(15.dp))
                    }
                    IconButton(
                        onClick = viewModel::toggleBatchMode,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = strings.common.close,
                            tint = c.muted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        if (state.isLoadingGallery && state.generations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        color = c.primary,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(30.dp),
                    )
                    Text(
                        text = strings.common.loading,
                        color = c.muted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        } else if (state.generations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = c.surface,
                    border = BorderStroke(1.dp, c.border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(c.primarySoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (isFiltered) Icons.Default.FilterAltOff else Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = c.primary,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Text(
                            text = if (isFiltered) {
                                if (isZh) "当前筛选条件下暂无匹配作品" else "No artworks match the current filter"
                            } else {
                                strings.images.emptyWorks
                            },
                            color = c.ink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isFiltered) {
                                GuButton(
                                    onClick = {
                                        viewModel.setSearchQuery("")
                                        viewModel.setFilter("all")
                                    },
                                    compact = true,
                                    variant = GuButtonVariant.PRIMARY,
                                ) {
                                    Text(
                                        if (isZh) "重置筛选" else "Reset Filters",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            } else {
                                GuButton(
                                    onClick = { viewModel.setShowCreateSheet(true) },
                                    compact = true,
                                    variant = GuButtonVariant.PRIMARY,
                                ) {
                                    Text(strings.images.generateBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                GuButton(
                                    onClick = onNavigateToInspire,
                                    compact = true,
                                    variant = GuButtonVariant.SUBTLE,
                                ) {
                                    Text(strings.images.studioBrowseInspire, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.generations, key = { it.id }) { gen ->
                    GalleryWorkCard(
                        container = container,
                        generation = gen,
                        batchMode = state.batchMode,
                        selected = gen.id in state.selectedBatchIds,
                        onClick = {
                            if (state.batchMode) viewModel.toggleBatchSelection(gen.id)
                            else viewModel.openDetail(gen, 0)
                        },
                        onLongClick = {
                            if (!state.batchMode) {
                                viewModel.toggleBatchMode()
                            }
                            if (gen.id !in state.selectedBatchIds) {
                                viewModel.toggleBatchSelection(gen.id)
                            }
                        },
                    )
                }

                if (state.hasMore) {
                    item(span = { GridItemSpan(2) }) {
                        GuButton(
                            onClick = viewModel::loadMoreGallery,
                            loading = state.isLoadingMore,
                            variant = GuButtonVariant.SECONDARY,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        ) {
                            Text(strings.images.loadMore, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryWorkCard(
    container: AppContainer,
    generation: OnlineImageGenerationDto,
    batchMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isPending = generation.status.lowercase() in setOf("pending", "queued", "processing", "running")
    val shape = RoundedCornerShape(16.dp)
    val actionLabel = generation.action
        ?.takeIf { it.isNotBlank() && it != "generate" }
        ?.let { strings.images.actions[it] ?: it }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.92f)
            .clip(shape)
            .background(c.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) c.primary else c.border,
                shape = shape,
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        if (generation.images.isNotEmpty()) {
            AuthenticatedImage(
                container = container,
                generationId = generation.id,
                imageIndex = 0,
                variant = "thumb",
                revision = generation.updated_at ?: generation.created_at,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                        ),
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = generation.prompt.ifBlank { generation.model },
                        color = Color.White,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = generation.model,
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else if (isPending) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(c.surfaceSoft)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = c.primary, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(8.dp))
                val qText = generation.queue_position?.let {
                    strings.images.queueWaiting.fmt("ahead" to it)
                } ?: (strings.images.statuses[generation.status] ?: generation.status)
                Text(qText, color = c.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (generation.prompt.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = generation.prompt,
                        color = c.muted,
                        fontSize = 10.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(c.surfaceSoft)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                GuBadge(strings.images.statuses[generation.status] ?: generation.status, variant = GuBadgeVariant.DANGER)
                if (generation.prompt.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = generation.prompt,
                        color = c.muted,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        if (actionLabel != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(7.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.56f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(actionLabel, color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (batchMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) c.primary else Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else if (generation.images.size > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.58f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text("${generation.images.size}P", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
