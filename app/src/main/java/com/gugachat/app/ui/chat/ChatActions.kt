package com.gugachat.app.ui.chat

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember

/**
 * Immutable action holders scoped to the chat workspace and its sheets, replacing
 * implicit CompositionLocal ViewModel lookups with explicit unidirectional event flows.
 */
@Immutable
data class ChatSessionActions(
    val onCreateRpFromCard: (String) -> Unit,
    val onImportTavernAssetBytes: suspend (ByteArray, String?) -> Boolean,
)

@Immutable
data class ChatParamsActions(
    val onSelectPreset: (String?) -> Unit,
    val onTogglePresetBlock: (presetId: String, identifier: String, index: Int) -> Unit,
)

@Immutable
data class NovelStudioExtraActions(
    val onExportEpub: (Uri, Boolean) -> Unit,
    val onBindCoverImage: (Uri) -> Unit,
    val onGenerateCoverArt: () -> Unit,
    val onClearCoverImage: () -> Unit,
)

@Immutable
data class RpGreetingActions(
    val onSelectGreetingVariant: (Int) -> Unit,
)

@Immutable
data class RpStudioAssetActions(
    val onSelectPersona: (String?) -> Unit,
    val onSyncBoundCharacterCard: () -> Unit,
    val onMountWorldbook: (String) -> Unit,
    val onUnmountWorldbook: (String) -> Unit,
    val onToggleMountedWorldbook: (String) -> Unit,
    val onSaveV2LoreEntry: (
        existingLegacyId: String?,
        category: String,
        title: String,
        keywordsRaw: String,
        secondaryKeysRaw: String,
        selectiveLogic: Int,
        useDepthInjection: Boolean,
        depth: Int,
        sticky: Int,
        cooldown: Int,
        delay: Int,
        content: String,
        alwaysActive: Boolean,
    ) -> Unit,
)

@Immutable
data class ChatWorkspaceActionBundle(
    val session: ChatSessionActions,
    val params: ChatParamsActions,
    val novelStudio: NovelStudioExtraActions,
    val rpGreeting: RpGreetingActions,
    val rpStudioAssets: RpStudioAssetActions,
)

@Composable
fun rememberChatWorkspaceActions(
    viewModel: ChatViewModel,
    contentResolver: ContentResolver,
): ChatWorkspaceActionBundle = remember(viewModel, contentResolver) {
    ChatWorkspaceActionBundle(
        session = ChatSessionActions(
            onCreateRpFromCard = viewModel::createRpSessionFromCharacterCard,
            onImportTavernAssetBytes = { bytes, fileName ->
                viewModel.importTavernAssetFromBytes(bytes, fileName)
            },
        ),
        params = ChatParamsActions(
            onSelectPreset = viewModel::selectConversationPreset,
            onTogglePresetBlock = viewModel::togglePromptPresetBlockEnabled,
        ),
        novelStudio = NovelStudioExtraActions(
            onExportEpub = { uri, includeLore ->
                viewModel.exportNovelEpubToUri(uri, contentResolver, includeLore = includeLore)
            },
            onBindCoverImage = { uri ->
                viewModel.bindNovelCoverImage(uri, contentResolver)
            },
            onGenerateCoverArt = viewModel::generateNovelCoverArt,
            onClearCoverImage = viewModel::clearNovelCoverImage,
        ),
        rpGreeting = RpGreetingActions(
            onSelectGreetingVariant = viewModel::selectGreetingVariant,
        ),
        rpStudioAssets = RpStudioAssetActions(
            onSelectPersona = viewModel::selectConversationPersona,
            onSyncBoundCharacterCard = viewModel::syncBoundCharacterCardFields,
            onMountWorldbook = { worldbookId -> viewModel.mountWorldbook(worldbookId) },
            onUnmountWorldbook = viewModel::unmountWorldbook,
            onToggleMountedWorldbook = viewModel::toggleMountedWorldbookEnabled,
            onSaveV2LoreEntry = viewModel::saveV2LoreEntryForActiveConversation,
        ),
    )
}
