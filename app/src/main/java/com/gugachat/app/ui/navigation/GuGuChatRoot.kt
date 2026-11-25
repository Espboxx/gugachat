package com.gugachat.app.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import com.gugachat.app.ui.images.GalleryScreen
import com.gugachat.app.ui.images.ImageCreateSheet
import com.gugachat.app.ui.images.ImageDetailSheet
import com.gugachat.app.ui.images.ImageEditorDialog
import com.gugachat.app.ui.images.ImageLightboxDialog
import com.gugachat.app.ui.images.ImagePresetSheet
import com.gugachat.app.ui.images.ImageViewModel
import com.gugachat.app.ui.images.InspireScreen
import com.gugachat.app.ui.images.ToolsScreen
import com.gugachat.app.ui.profile.ProfileScreen
import com.gugachat.app.ui.profile.ProfileViewModel

private enum class MainTab {
    CHAT,
    GALLERY,
    INSPIRE,
    TOOLS,
    MINE,
}

@Composable
fun GuGuChatRoot(container: AppContainer) {
    val settings by container.settingsState.collectAsStateWithLifecycle()
    val session by container.authSession.collectAsStateWithLifecycle()
    val strings = remember(settings.language) {
        if (settings.language == AppLanguage.EN) EnStrings else ZhStrings
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
                        .padding(top = 54.dp),
                )
            }
        }
    }
}

@Composable
private fun SplashGate() {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
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
                .size(116.dp)
                .clip(RoundedCornerShape(28.dp)),
        )
        Spacer(Modifier.height(16.dp))
        Text(strings.common.appName, color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(14.dp))
        CircularProgressIndicator(color = c.primary, modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
    }
}

@Composable
private fun AuthenticatedShell(container: AppContainer) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors

    val chatVm = remember(container) { ChatViewModel(container) }
    val imageVm = remember(container) { ImageViewModel(container) }
    val profileVm = remember(container) { ProfileViewModel(container) }

    var activeTabOrdinal by rememberSaveable { mutableIntStateOf(MainTab.CHAT.ordinal) }
    var inspirePreferStyles by rememberSaveable { mutableIntStateOf(0) }
    val activeTab = MainTab.entries.getOrElse(activeTabOrdinal) { MainTab.CHAT }

    Scaffold(
        containerColor = c.page,
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .navigationBarsPadding(),
            ) {
                HorizontalDivider(color = c.border)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactBottomNavItem(
                        selected = activeTab == MainTab.CHAT,
                        icon = Icons.AutoMirrored.Filled.Chat,
                        label = strings.nav.tabChat,
                        onClick = { activeTabOrdinal = MainTab.CHAT.ordinal },
                        modifier = Modifier.weight(1f),
                    )
                    CompactBottomNavItem(
                        selected = activeTab == MainTab.GALLERY,
                        icon = Icons.Default.Collections,
                        label = strings.nav.tabGallery,
                        onClick = { activeTabOrdinal = MainTab.GALLERY.ordinal },
                        modifier = Modifier.weight(1f),
                    )
                    CompactBottomNavItem(
                        selected = activeTab == MainTab.INSPIRE,
                        icon = Icons.Default.AutoAwesome,
                        label = strings.nav.tabInspire,
                        onClick = { activeTabOrdinal = MainTab.INSPIRE.ordinal },
                        modifier = Modifier.weight(1f),
                    )
                    CompactBottomNavItem(
                        selected = activeTab == MainTab.TOOLS,
                        icon = Icons.Default.Build,
                        label = strings.nav.tabTools,
                        onClick = { activeTabOrdinal = MainTab.TOOLS.ordinal },
                        modifier = Modifier.weight(1f),
                    )
                    CompactBottomNavItem(
                        selected = activeTab == MainTab.MINE,
                        icon = Icons.Default.Person,
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
            when (activeTab) {
                MainTab.CHAT -> ChatWorkspaceScreen(container = container, viewModel = chatVm)
                MainTab.GALLERY -> GalleryScreen(container = container, viewModel = imageVm)
                MainTab.INSPIRE -> InspireScreen(
                    imageViewModel = imageVm,
                    initialSubtab = inspirePreferStyles,
                )
                MainTab.TOOLS -> ToolsScreen(
                    container = container,
                    imageViewModel = imageVm,
                    onNavigateToInspireStyles = {
                        inspirePreferStyles = 1
                        activeTabOrdinal = MainTab.INSPIRE.ordinal
                    },
                )
                MainTab.MINE -> ProfileScreen(viewModel = profileVm)
            }
        }
    }

    ImageStudioModals(container = container, imageVm = imageVm)
}

@Composable
private fun CompactBottomNavItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppTheme.current.colors
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (selected) c.primarySoft else Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) c.primary else c.muted,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) c.primary else c.muted,
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
