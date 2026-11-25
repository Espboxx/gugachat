package com.gugachat.app.ui.images

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gugachat.app.AppContainer
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.GuAwningHeader
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.data.api.OnlineImageGenerationDto

@Composable
fun GalleryScreen(
    container: AppContainer,
    viewModel: ImageViewModel,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val ctx = LocalContext.current

    val zipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            viewModel.batchDownloadZip(uri, ctx.contentResolver, strings.images.zipSaved)
        }
    }

    val filterItems = listOf(
        "all" to strings.images.filterAll,
        "processing" to strings.images.filterProcessing,
        "completed" to strings.images.filterCompleted,
        "generate" to strings.images.filterGenerate,
        "edited" to strings.images.filterEdited,
        "expiring" to strings.images.filterExpiring,
        "failed" to strings.images.filterFailed,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.page),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            GuAwningHeader {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = strings.nav.tabGallery,
                        color = c.ink,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    GuBadge(strings.images.worksCount.fmt("count" to state.totalWorks), variant = GuBadgeVariant.BLUE)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { viewModel.setShowPresetSheet(true) }) {
                        Icon(Icons.Default.Bookmarks, contentDescription = strings.images.presetsTitle, tint = c.ink)
                    }
                    IconButton(onClick = viewModel::toggleBatchMode) {
                        Icon(
                            Icons.Default.Checklist,
                            contentDescription = strings.images.batchManage,
                            tint = if (state.batchMode) c.primary else c.ink,
                        )
                    }
                    IconButton(onClick = viewModel::refreshGallery, enabled = !state.isLoadingGallery) {
                        Icon(Icons.Default.Refresh, contentDescription = strings.common.refresh, tint = c.ink)
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GuTextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        placeholder = strings.images.searchPlaceholder,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = c.muted) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.refreshGallery() }),
                        modifier = Modifier.weight(1f),
                    )
                    GuButton(onClick = viewModel::refreshGallery, compact = true, variant = GuButtonVariant.SECONDARY) {
                        Text(strings.common.search, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    filterItems.forEach { (key, label) ->
                        GuPill(label, state.activeFilter == key, { viewModel.setFilter(key) })
                    }
                }

                if (state.batchMode) {
                    GuCard(backgroundColor = c.surfaceSoft) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            GuBadge(strings.images.selectedCount.fmt("count" to state.selectedBatchIds.size), variant = GuBadgeVariant.HONEY)
                            Spacer(Modifier.weight(1f))
                            GuButton(onClick = viewModel::selectAllBatch, compact = true, variant = GuButtonVariant.SUBTLE) {
                                Text(strings.images.selectAll, fontSize = 11.sp)
                            }
                            GuButton(
                                onClick = { zipLauncher.launch("gugachat-works-${System.currentTimeMillis()}.zip") },
                                enabled = state.selectedBatchIds.isNotEmpty() && !state.isBatchBusy,
                                compact = true,
                                variant = GuButtonVariant.PRIMARY,
                            ) {
                                Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                            GuButton(
                                onClick = viewModel::batchDeleteSelected,
                                enabled = state.selectedBatchIds.isNotEmpty() && !state.isBatchBusy,
                                compact = true,
                                variant = GuButtonVariant.DANGER,
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            if (state.generations.isEmpty() && !state.isLoadingGallery) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    GuEmptyState(
                        title = strings.images.emptyWorks,
                        actionLabel = strings.images.generateBtn,
                        onAction = { viewModel.setShowCreateSheet(true) },
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 84.dp),
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

        FloatingActionButton(
            onClick = { viewModel.setShowCreateSheet(true) },
            containerColor = c.primary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = strings.images.generateBtn)
        }
    }
}

@Composable
private fun GalleryWorkCard(
    container: AppContainer,
    generation: OnlineImageGenerationDto,
    batchMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isPending = generation.status.lowercase() in setOf("pending", "queued", "processing", "running")

    GuCard(
        contentPadding = PaddingValues(0.dp),
        borderColor = if (selected) c.primary else null,
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(c.surfaceSoft),
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
            } else if (isPending) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = c.primary, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(6.dp))
                    val qText = generation.queue_position?.let {
                        strings.images.queueWaiting.fmt("ahead" to it)
                    } ?: (strings.images.statuses[generation.status] ?: generation.status)
                    Text(qText, color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GuBadge(strings.images.statuses[generation.status] ?: generation.status, variant = GuBadgeVariant.DANGER)
                }
            }

            if (batchMode) {
                Icon(
                    if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) c.primary else Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp),
                )
            } else if (generation.images.size > 1) {
                GuBadge(
                    text = "${generation.images.size}P",
                    variant = GuBadgeVariant.HONEY,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }
        }

        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = generation.prompt.ifBlank { generation.model },
                color = c.ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = generation.model,
                color = c.muted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
