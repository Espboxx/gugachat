# GuGuChat — Native Android Client for sub2api-auto & Direct AI APIs

GuGuChat (`com.gugachat.app`) is a native Kotlin + Jetpack Compose Android client supporting both **sub2api-auto** accounts and direct connections to the **4 standard AI API protocols** (**OpenAI Chat Completions**, **OpenAI Responses**, **Anthropic Messages / Claude**, and **Google Gemini**) with custom `Base URL`, custom `API Key`, and custom models across Online Chat and Online Images.

## Features

### 4 Standard AI API Protocols & Custom Endpoints (`ApiProviderStore`, `DirectApiClient`, `DirectProtocolCodec`)
- **Protocols Supported**:
  - **OpenAI Chat Completions** (`/v1/chat/completions`, `/v1/models`, `/v1/images/generations`, `/v1/images/edits`, `Authorization: Bearer ...`)
  - **OpenAI Responses** (`/v1/responses`, reasoning summaries + usage events, `Authorization: Bearer ...`)
  - **Anthropic Claude Messages** (`/v1/messages`, extended thinking blocks + base64 vision images, `x-api-key` + `anthropic-version: 2023-06-01`)
  - **Google Gemini Native** (`/v1beta/models/*:streamGenerateContent?alt=sse`, `:generateContent`, `:predict` Imagen, thought parts + inline image generation, `x-goog-api-key`)
- **Custom Base URL & API Key**: Enter directly on the Sign-In screen (`自定义 API 直连` / Direct API mode) without a `sub2api-auto` backend, or manage multiple endpoints across all 4 protocols inside **Mine -> API Endpoints** (`ApiProviderManagerCard`). API keys are encrypted locally via `EncryptedSharedPreferences`.
- **Live Connection Test & Custom Models**: Test connectivity + auto-fetch models (`/models`), add comma-separated custom models, or type any arbitrary model name directly inside `ModelGroupSheet`.

### Authentication & Profile (`ui/auth`, `ui/profile`)
- **Dual Entry Modes**: Sign in with `sub2api-auto` (URL normalization via `ServerUrlUtils`, email/password, 2FA TOTP, WebView SSO via `WebLoginDialog`, single-flight token refresh in `ApiClient`) OR jump straight into Direct API mode.
- **Dual Themes & Live i18n**: Supports `GuGuGa` (striped awning comic style) and `Classic Clean` across `System / Light / Dark` (`ThemeTokens`, `GuComponents`), plus instant `中文 / English` switching (`AppStrings`, `ZhStrings`, `EnStrings`).
- **Local Backup**: Full JSON conversation, memory ledger, novel, and RP backup export/import via `ChatBackupCodec` + Room `AppDatabase`.

### Online Chat (`ui/chat`, `domain/chat`)
- **Group & Model Picker**: `ModelGroupSheet` blends `sub2api-auto` groups (with `FreeQuotaUtils` badges) alongside enabled custom API endpoints, and allows ad-hoc custom model selection.
- **Streaming SSE & Markdown**: Unified streaming across `OnlineChatApi` and `DirectApiClient` with collapsible reasoning/thinking timer, syntax-styled code blocks with one-tap copy (`MarkdownMessage`), and multimodal image attachments (`AttachmentFileStore`).
- **Context & Long-Term Memory**: Token waterline monitor (`ChatParamsSheet`), automatic/manual compaction (`ContextCompactor`), CJK bigram/unigram BM25 + demonstrative noun memory recall (`CjkSearch`, `ContextBuilder`), user memories, ledger items, and episode consolidation (`MemoryExtractor`, `EpisodeConsolidator`).
- **Novel Studio (`NovelStudioUi`, `NovelEngine`, `NovelExport`)**: Genre open-book wizard, chapter manager, lorebook, 3-path plot drafting, outline co-creation, prose polishing, interactive Pure Reading overlay, and `.txt` / `.md` export.
- **Roleplay & RPG Studio (`RpStudioUi`, `RpEngine`, `RpCardCodec`)**: Presets, SillyTavern V2/V3 JSON & PNG card import/export, NPC affinity, stats/inventory/quests/flags, dice roller, choices, variant rewind (`TimelineReconciler`), and SL save slots.

### Online Images (`ui/images`, `domain/images`, `DirectImageStore`)
- **3 Creative Tabs (`GalleryScreen`, `InspireScreen`, `ToolsScreen`)**: Unified gallery combining server generations and locally stored direct API generations (`DirectImageStore`), 2-column Inspiration showcase, and quick-launch tool tiles.
- **Creation Studio (`ImageCreateSheet`)**: Supports `sub2api-auto` channels + direct OpenAI / Responses / Gemini image endpoints, reference images, presets (`ImagePresetSheet`), cost estimates, and **NovelAI V4.5 / V5** parameter tuning (`NovelAIParamsPanel`).
- **4-Mode Image Editor (`ImageEditorDialog`, `ImageMaskCanvas`, `MaskPngEncoder`)**: Text Edit (`文字改图`), Variation (`生成变体`), finger-painted Inpaint mask canvas (`局部重绘`), and Outpaint canvas/anchor framing (`智能扩图` / `OnlineImageCanvasMath`).
- **Detail, Lightbox & Export**: Authenticated remote or direct-local image loading (`AuthenticatedImage`), version lineage, lightbox zoom (`ImageLightboxDialog`), MediaStore save, and `FileProvider` share.

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
