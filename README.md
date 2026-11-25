# GuGuChat — Native Android Client for sub2api-auto

GuGuChat (`com.gugachat.app`) is a native Kotlin + Jetpack Compose Android client implementing **Online Chat** and **Online Images** from `sub2api-auto`, with Room persistence, encrypted token storage, dual visual themes, bilingual UI, and MuMu emulator verification.

## Features

### Authentication & Profile
- **Flexible Sign-In**: Server URL normalization (`ServerUrlUtils`), email/password login, 2FA TOTP verification, and WebView SSO login (`WebLoginDialog`) against `/api/v1/*`.
- **Session Security**: Single-flight bearer token refresh in `ApiClient` backed by `EncryptedSharedPreferences` (`SecureTokenStore`) and DataStore preferences (`SettingsStore`).
- **Dual Themes & Live i18n**: Supports `GuGuGa` (playful striped awning theme) and `Classic Clean` across `System / Light / Dark` modes (`ThemeTokens`, `GuComponents`), plus instant `中文 / English` switching (`AppStrings`, `ZhStrings`, `EnStrings`).
- **Local Backup**: Full JSON conversation, memory ledger, novel, and RP backup export/import via `ChatBackupCodec` + Room `AppDatabase`.

### Online Chat (`ui/chat`, `domain/chat`)
- **Group & Model Picker**: `ModelGroupSheet` with free-quota group/model sorting (`FreeQuotaUtils`), vision/reasoning/context badges, and search filtering.
- **Streaming SSE & Markdown**: `SseParser` + `OnlineChatApi` supporting OpenAI-compatible SSE chunks and reasoning fields (`reasoning_content`, `reasoning`, `thinking`), collapsible thinking timer, syntax-styled code blocks with one-tap copy, and multimodal image attachments (`AttachmentFileStore`).
- **Context & Long-Term Memory**: Token waterline monitor (`ChatParamsSheet`), automatic/manual context compaction (`ContextCompactor`), CJK bigram/unigram BM25 + demonstrative noun memory recall (`CjkSearch`, `ContextBuilder`), user memories, ledger items, and hierarchical episode consolidation (`MemoryExtractor`, `EpisodeConsolidator`).
- **Novel Studio (`NovelStudioUi`, `NovelEngine`, `NovelExport`)**: Genre open-book wizard, chapter manager, independent lorebook, 3-path plot drafting, AI outline co-creation, prose polishing, interactive Pure Reading overlay (`cream / parchment / night` paper + serif toggle + font sizing), and `.txt` / `.md` export.
- **Roleplay & RPG Studio (`RpStudioUi`, `RpEngine`, `RpCardCodec`)**: Campaign presets, SillyTavern V2/V3 JSON & PNG embedded card import/export, NPC cast & affinity controls, RPG stats/inventory/quests/flags, dice roller, action choices, variant rewind (`TimelineReconciler`), and SL save slots.

### Online Images (`ui/images`, `domain/images`)
- **3 Creative Tabs (`GalleryScreen`, `InspireScreen`, `ToolsScreen`)**: Works gallery with pull/status refresh, 2-column Inspiration style showcase (`showcase_t1..t8`), and quick-launch tool tiles.
- **Creation Studio (`ImageCreateSheet`)**: Supports standard GPT-Image / multimodal keys (including negative synthetic `api_key_id` groups), up to 5 reference images (`reference_images[]`), idempotency keys (`Idempotency-Key`), parameter presets (`ImagePresetSheet`), cost estimation, and **NovelAI V4.5 / V5** parameter tuning (`NovelAIParamsPanel`).
- **4-Mode Image Editor (`ImageEditorDialog`, `ImageMaskCanvas`, `MaskPngEncoder`)**: Text-guided Edit (`文字改图`), Variation (`生成变体`), interactive finger-painted Inpaint mask canvas with brush/eraser/undo/clear (`局部重绘`), and interactive Outpaint framing with aspect ratios, scaling, and 3x3 anchor grid (`智能扩图` / `OnlineImageCanvasMath`).
- **Detail, Lightbox & Export**: Authenticated image loading (`AuthenticatedImage`), version lineage switching, full-screen lightbox (`ImageLightboxDialog`), MediaStore gallery save, and `FileProvider` share sheet.

## Package Structure

```text
app/src/main/java/com/gugachat/app/
├── AppContainer.kt          # App-wide singletons (OkHttp APIs, Room, DataStore, EncryptedPrefs)
├── GuGuChatApp.kt           # Application entry point
├── MainActivity.kt          # Compose host Activity
├── core/                    # i18n, theme tokens & components, secure storage, utilities
├── data/
│   ├── api/                 # ApiClient, AuthApi, OnlineChatApi, OnlineImagesApi, SseParser, DTOs
│   └── local/               # Room Entities, DAOs, AppDatabase, AttachmentFileStore, ChatBackupCodec
├── domain/
│   ├── chat/                # ContextBuilder, CjkSearch, Memory/Novel/RP engines, Card codec, Reconciler
│   └── images/              # OnlineImageStudioUtils, OnlineImageNovelAI, CanvasMath, MaskPngEncoder
└── ui/
    ├── auth/                # LoginScreen, WebLoginDialog, AuthViewModel
    ├── chat/                # ChatWorkspaceScreen, Drawer, Composer, Markdown, Memory, Novel & RP Studios
    ├── images/              # Gallery, Inspire, Tools, CreateSheet, EditorDialog, DetailSheet, Lightbox
    ├── navigation/          # GuGuChatRoot (Auth gate + 5-tab navigation + toast overlay)
    └── profile/             # ProfileScreen, ProfileViewModel
```

## Prerequisites & Build

- **JDK**: 17 (`/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home`)
- **Android SDK**: `compileSdk = 34`, `targetSdk = 34`, `minSdk = 26`
- **Gradle**: Wrapper `8.14.3` (`./gradlew`, configured with Aliyun & Tencent Maven mirrors in `settings.gradle.kts`)

### Run JVM Unit Tests

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
./gradlew :app:testDebugUnitTest --no-daemon --console=plain
```

### Assemble Debug APK

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
./gradlew :app:assembleDebug --no-daemon --console=plain
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

## Emulator Verification (MuMu Player)

Verified interactively on MuMu Player (`127.0.0.1:16384`, `1440x2560`) using `adb`:

```bash
adb connect 127.0.0.1:16384
adb -s 127.0.0.1:16384 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 127.0.0.1:16384 shell am start -n com.gugachat.app/.MainActivity
```
