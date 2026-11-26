package com.gugachat.app.ui.images

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gugachat.app.AppContainer
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.data.api.OnlineImageGenerationDto
import kotlin.math.roundToInt

@Composable
fun ImageLightboxDialog(
    container: AppContainer,
    generation: OnlineImageGenerationDto,
    imageIndex: Int,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val imageCount = generation.images.size.coerceAtLeast(1)
    val initialPage = imageIndex.coerceIn(0, imageCount - 1)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { imageCount })

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var showChrome by remember { mutableStateOf(true) }

    LaunchedEffect(pagerState.currentPage) {
        scale = 1f
        offset = Offset.Zero
    }

    val frostedBg = Color.Black.copy(alpha = 0.62f)
    val frostedBorder = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.22f))

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { viewportSize = it },
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = scale <= 1.05f,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val resolvedIdx = generation.images.getOrNull(page)?.index ?: page
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(page) {
                            detectTapGestures(
                                onTap = { showChrome = !showChrome },
                                onDoubleTap = {
                                    if (scale > 1.05f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2.4f
                                    }
                                },
                            )
                        }
                        .pointerInput(page) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextScale = (scale * zoom).coerceIn(1f, 5f)
                                scale = nextScale
                                if (nextScale <= 1.01f) {
                                    offset = Offset.Zero
                                } else {
                                    val maxX = ((viewportSize.width * (nextScale - 1f)) / 2f).coerceAtLeast(0f)
                                    val maxY = ((viewportSize.height * (nextScale - 1f)) / 2f).coerceAtLeast(0f)
                                    offset = Offset(
                                        x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                        y = (offset.y + pan.y).coerceIn(-maxY, maxY),
                                    )
                                }
                            }
                        },
                ) {
                    AuthenticatedImage(
                        container = container,
                        generationId = generation.id,
                        imageIndex = resolvedIdx,
                        variant = "original",
                        revision = generation.updated_at ?: generation.created_at,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = if (pagerState.currentPage == page) scale else 1f,
                                scaleY = if (pagerState.currentPage == page) scale else 1f,
                                translationX = if (pagerState.currentPage == page) offset.x else 0f,
                                translationY = if (pagerState.currentPage == page) offset.y else 0f,
                            ),
                    )
                }
            }

            AnimatedVisibility(
                visible = showChrome,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (imageCount > 1) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = frostedBg,
                                border = frostedBorder,
                            ) {
                                Text(
                                    text = "${pagerState.currentPage + 1} / $imageCount",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                        if (scale > 1.05f) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = frostedBg,
                                border = frostedBorder,
                            ) {
                                Text(
                                    text = "${(scale * 10).roundToInt() / 10.0}×",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                        if (imageCount <= 1 && scale <= 1.05f) {
                            Spacer(Modifier.size(1.dp))
                        }
                    }

                    Surface(
                        onClick = onDismiss,
                        shape = CircleShape,
                        color = frostedBg,
                        border = frostedBorder,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = strings.common.close,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showChrome,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 18.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = frostedBg,
                    border = frostedBorder,
                ) {
                    Text(
                        text = strings.images.swipeHint,
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.5.dp),
                    )
                }
            }
        }
    }
}
