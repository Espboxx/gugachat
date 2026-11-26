package com.gugachat.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gugachat.app.AppContainer
import com.gugachat.app.AuthSessionState
import com.gugachat.app.R
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.EnStrings
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.ZhStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.GuGuChatTheme
import com.gugachat.app.core.theme.GuToastHost
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.ui.auth.AuthViewModel
import com.gugachat.app.ui.auth.LoginScreen
import com.gugachat.app.ui.chat.ChatViewModel
import com.gugachat.app.ui.chat.ChatWorkspaceScreen
import com.gugachat.app.ui.images.CreativeStudioScreen
import com.gugachat.app.ui.images.ImageCreateSheet
import com.gugachat.app.ui.images.ImageDetailSheet
import com.gugachat.app.ui.images.ImageEditorDialog
import com.gugachat.app.ui.images.ImageLightboxDialog
import com.gugachat.app.ui.images.ImagePresetSheet
import com.gugachat.app.ui.images.ImageViewModel
import com.gugachat.app.ui.profile.ProfileScreen
import com.gugachat.app.ui.profile.ProfileViewModel
import com.gugachat.app.ui.update.AppUpdateDialog

private enum class MainTab {
    CHAT,
    STUDIO,
    MINE,
}

@Composable
fun GuGuChatRoot(container: AppContainer) {
    val settings by container.settingsState.collectAsStateWithLifecycle()
    val session by container.authSession.collectAsStateWithLifecycle()
    val updateState by container.updateController.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val strings = remember(settings.language) {
        if (settings.language == AppLanguage.EN) EnStrings else ZhStrings
    }

    LaunchedEffect(container) {
        container.updateController.checkForUpdate(silent = true)
    }

    CompositionLocalProvider(LocalAppStrings provides strings) {
        GuGuChatTheme(
            themeStyle = settings.themeStyle,
            colorMode = settings.colorMode,
        ) {
            val c = LocalAppTheme.current.colors
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(c.page),
            ) {
                when (session) {
                    is AuthSessionState.Checking -> SplashGate()
                    is AuthSessionState.LoggedOut -> {
                        val authVm = remember(container) { AuthViewModel(container) }
                        LoginScreen(viewModel = authVm)
                    }
                    is AuthSessionState.LoggedIn -> AuthenticatedShell(container = container)
                }

                GuToastHost(
                    controller = container.toastController,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 58.dp),
                )

                val updateInfo = updateState.availableUpdate
                if (updateState.showDialog && updateInfo != null) {
                    AppUpdateDialog(
                        info = updateInfo,
                        downloadState = updateState.downloadState,
                        onConfirmUpdate = { container.updateController.startDownloadOrInstall(ctx) },
                        onOpenDownloadPage = { container.updateController.openManualDownloadPage(ctx) },
                        onDismiss = { container.updateController.dismissDialog() },
                    )
                }
            }
        }
    }
}

@Composable
private fun SplashGate() {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.page),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_splash),
            contentDescription = strings.common.appName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(108.dp)
                .clip(RoundedCornerShape(26.dp))
                .border(theme.borderWidth, c.border, RoundedCornerShape(26.dp)),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = strings.common.appName,
            color = c.ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(14.dp))
        CircularProgressIndicator(
            color = c.primary,
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.4.dp,
        )
    }
}

@Composable
private fun AuthenticatedShell(container: AppContainer) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors

    val chatVm = remember(container) { ChatViewModel(container) }
    val imageVm = remember(container) { ImageViewModel(container) }
    val profileVm = remember(container) { ProfileViewModel(container) }

    val imageState by imageVm.ui.collectAsStateWithLifecycle()
    val activeImageTasks = remember(imageState.generations) {
        imageState.generations.count {
            it.status.lowercase() in setOf("pending", "queued", "processing", "running")
        }
    }
    val resolvedImageGroupsCount = if (!imageState.isLoadingCapabilities || imageState.keys.isNotEmpty()) {
        imageState.keys.size
    } else {
        null
    }

    var activeTabOrdinal by rememberSaveable { mutableIntStateOf(MainTab.CHAT.ordinal) }
    val activeTab = MainTab.entries.getOrElse(activeTabOrdinal) { MainTab.CHAT }
    val saveableStateHolder = rememberSaveableStateHolder()

    Scaffold(
        containerColor = c.page,
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .navigationBarsPadding(),
            ) {
                HorizontalDivider(
                    thickness = theme.borderWidth,
                    color = c.border.copy(alpha = 0.85f),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactBottomNavItem(
                        selected = activeTab == MainTab.CHAT,
                        selectedIcon = Icons.AutoMirrored.Filled.Chat,
                        unselectedIcon = Icons.AutoMirrored.Outlined.Chat,
                        label = strings.nav.tabChat,
                        onClick = { activeTabOrdinal = MainTab.CHAT.ordinal },
                        modifier = Modifier.weight(1f),
                    )
                    CompactBottomNavItem(
                        selected = activeTab == MainTab.STUDIO,
                        selectedIcon = Icons.Filled.AutoAwesome,
                        unselectedIcon = Icons.Outlined.AutoAwesome,
                        label = strings.nav.tabStudio,
                        showIndicatorDot = activeImageTasks > 0,
                        onClick = { activeTabOrdinal = MainTab.STUDIO.ordinal },
                        modifier = Modifier.weight(1f),
                    )
                    CompactBottomNavItem(
                        selected = activeTab == MainTab.MINE,
                        selectedIcon = Icons.Filled.Person,
                        unselectedIcon = Icons.Outlined.Person,
                        label = strings.nav.tabMine,
                        onClick = { activeTabOrdinal = MainTab.MINE.ordinal },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            saveableStateHolder.SaveableStateProvider(key = activeTab.name) {
                when (activeTab) {
                    MainTab.CHAT -> ChatWorkspaceScreen(container = container, viewModel = chatVm)
                    MainTab.STUDIO -> CreativeStudioScreen(container = container, imageViewModel = imageVm)
                    MainTab.MINE -> ProfileScreen(
                        container = container,
                        viewModel = profileVm,
                        imageGroupsCount = resolvedImageGroupsCount,
                    )
                }
            }
        }
    }

    ImageStudioModals(container = container, imageVm = imageVm)
}

@Composable
private fun CompactBottomNavItem(
    selected: Boolean,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showIndicatorDot: Boolean = false,
) {
    val c = LocalAppTheme.current.colors
    val capsuleBg by animateColorAsState(
        targetValue = if (selected) c.primarySoft else Color.Transparent,
        label = "nav_capsule_bg",
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) c.primarySoftInk else c.muted,
        label = "nav_icon_tint",
    )
    val labelTint by animateColorAsState(
        targetValue = if (selected) c.primarySoftInk else c.muted,
        label = "nav_label_tint",
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .width(54.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(capsuleBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (selected) selectedIcon else unselectedIcon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(21.dp),
                )
            }
            if (showIndicatorDot) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-6).dp, y = 2.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(c.accent)
                        .border(1.5.dp, c.surface, CircleShape),
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = labelTint,
            maxLines = 1,
        )
    }
}

@Composable
private fun ImageStudioModals(
    container: AppContainer,
    imageVm: ImageViewModel,
) {
    val state by imageVm.ui.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val ctx = LocalContext.current

    if (state.showCreateSheet) {
        ImageCreateSheet(
            container = container,
            state = state,
            onSelectKey = imageVm::selectKey,
            onSelectModel = imageVm::selectModel,
            onPromptChange = imageVm::setPrompt,
            onCountChange = imageVm::setCount,
            onAspectChange = imageVm::setAspectRatio,
            onResolutionChange = imageVm::setResolution,
            onUpdateNovelAI = imageVm::updateNovelAIParams,
            onAddRefUri = { uri -> imageVm.addReferenceImageUri(uri, ctx.contentResolver) },
            onRemoveRefUpload = imageVm::removeReferenceUpload,
            onRemoveRefAsset = imageVm::removeReferenceAsset,
            onOpenPresets = { imageVm.setShowPresetSheet(true) },
            onSubmit = imageVm::submitGeneration,
            onDismiss = { imageVm.setShowCreateSheet(false) },
        )
    }

    if (state.showPresetSheet) {
        ImagePresetSheet(
            presets = state.presets,
            hasSelectedModel = state.models.any { it.id == state.selectedModelId },
            onApply = { p -> imageVm.applyPreset(p, strings.images.presetsApplied.fmt("name" to p.name)) },
            onCreate = { name, inc -> imageVm.createPreset(name, inc, strings.images.presetsSaved.fmt("name" to name)) },
            onDelete = { id ->
                val pName = state.presets.firstOrNull { it.id == id }?.name ?: ""
                imageVm.deletePreset(id, strings.images.presetsDeleted.fmt("name" to pName))
            },
            onDismiss = { imageVm.setShowPresetSheet(false) },
        )
    }

    state.selectedDetail?.let { detail ->
        ImageDetailSheet(
            container = container,
            generation = detail,
            imageIndex = state.detailImageIndex,
            versions = state.detailVersions,
            onSelectImageIndex = imageVm::setDetailImageIndex,
            onSelectVersion = { v -> imageVm.openDetail(v, 0) },
            onOpenLightbox = { imageVm.openLightbox(detail, state.detailImageIndex) },
            onOpenEditor = { mode -> imageVm.openEditor(detail, state.detailImageIndex, mode) },
            onDrawSame = { imageVm.reuseParamsToCreate(detail) },
            onUseAsRef = { imageVm.useGenerationAsReference(detail, state.detailImageIndex) },
            onRegenerate = { imageVm.regenerate(detail.id) },
            onSaveToGallery = { imageVm.saveImageToGallery(ctx, detail.id, state.detailImageIndex, strings.images.savedToGallery) },
            onShare = { imageVm.shareImage(ctx, detail.id, state.detailImageIndex) },
            onDelete = { imageVm.deleteGeneration(detail.id) },
            onDismiss = imageVm::closeDetail,
        )
    }

    state.lightboxTarget?.let { (gen, idx) ->
        ImageLightboxDialog(
            container = container,
            generation = gen,
            imageIndex = idx,
            onDismiss = imageVm::closeLightbox,
        )
    }

    state.editorTarget?.let { target ->
        ImageEditorDialog(
            container = container,
            target = target,
            isSubmitting = state.isSubmitting,
            onDismiss = imageVm::closeEditor,
            onSubmit = imageVm::submitEditorAction,
        )
    }
}
