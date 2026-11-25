package com.gugachat.app.ui.images

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gugachat.app.AppContainer
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuAwningHeader
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.images.ImageEditMode

private data class ToolCardSpec(
    val title: String,
    val desc: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
fun ToolsScreen(
    container: AppContainer,
    imageViewModel: ImageViewModel,
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

    val cards = listOf(
        ToolCardSpec(strings.images.toolInpaintTitle, strings.images.toolInpaintDesc, Icons.Default.Brush) {
            openLatestInMode(ImageEditMode.INPAINT)
        },
        ToolCardSpec(strings.images.toolOutpaintTitle, strings.images.toolOutpaintDesc, Icons.Default.AspectRatio) {
            openLatestInMode(ImageEditMode.OUTPAINT)
        },
        ToolCardSpec(strings.images.toolEditTitle, strings.images.toolEditDesc, Icons.Default.Edit) {
            openLatestInMode(ImageEditMode.EDIT)
        },
        ToolCardSpec(strings.images.toolVariationTitle, strings.images.toolVariationDesc, Icons.Default.AutoAwesome) {
            openLatestInMode(ImageEditMode.VARIATION)
        },
        ToolCardSpec(strings.images.toolPadRefTitle, strings.images.toolPadRefDesc, Icons.Default.AddPhotoAlternate) {
            if (latestCompleted != null) imageViewModel.useGenerationAsReference(latestCompleted, 0)
            else imageViewModel.setShowCreateSheet(true)
        },
        ToolCardSpec(strings.images.toolStylesTitle, strings.images.toolStylesDesc, Icons.Default.Style) {
            onNavigateToInspireStyles()
        },
        ToolCardSpec(strings.images.toolNovelAITitle, strings.images.toolNovelAIDesc, Icons.Default.ColorLens) {
            imageViewModel.openNovelAICreate()
        },
        ToolCardSpec(strings.images.toolPresetsTitle, strings.images.toolPresetsDesc, Icons.Default.Bookmarks) {
            imageViewModel.setShowPresetSheet(true)
        },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.page),
    ) {
        GuAwningHeader {
            Text(
                text = strings.images.toolsTitle,
                color = c.ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(cards) { card ->
                GuCard(
                    onClick = card.onClick,
                    modifier = Modifier.fillMaxWidth().height(132.dp),
                ) {
                    Icon(card.icon, contentDescription = null, tint = c.primary, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(card.title, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    Text(card.desc, color = c.muted, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 3)
                }
            }
        }
    }
}
