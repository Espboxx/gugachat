package com.gugachat.app.ui.images

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.gugachat.app.AppContainer
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.core.util.ServerUrlUtils

@Composable
fun AuthenticatedImage(
    container: AppContainer,
    generationId: String,
    imageIndex: Int,
    variant: String = "thumb",
    revision: Long? = null,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    modifier: Modifier = Modifier,
) {
    val settings by container.settingsState.collectAsStateWithLifecycle()
    val c = LocalAppTheme.current.colors
    val ctx = LocalContext.current
    val localFile = container.directImageStore.getImageFile(generationId, imageIndex)
    val baseUrl = ServerUrlUtils.normalizeServerUrl(settings.serverUrl)

    if (localFile == null && baseUrl.isEmpty()) {
        Box(
            modifier = modifier.background(c.surfaceSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.BrokenImage, contentDescription = contentDescription, tint = c.muted)
        }
        return
    }

    val modelData: Any = localFile ?: run {
        val relPath = container.onlineImagesApi.contentEndpointPath(
            id = generationId,
            index = imageIndex,
            variant = variant,
            revision = revision,
        )
        ServerUrlUtils.buildApiUrl(baseUrl, relPath)
    }

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(ctx)
            .data(modelData)
            .crossfade(true)
            .build(),
        imageLoader = container.imageLoader,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        loading = {
            Box(
                modifier = Modifier.fillMaxSize().background(c.surfaceSoft),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = c.primary,
                )
            }
        },
        error = {
            Box(
                modifier = Modifier.fillMaxSize().background(c.surfaceSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.BrokenImage, contentDescription = contentDescription, tint = c.muted)
            }
        },
    )
}
