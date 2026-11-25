# GuGuChat（咕咕嘎 AI 客户端）

**GuGuChat**（`com.gugachat.app`）是一款基于 **Kotlin + Jetpack Compose** 开发的原生 Android AI 客户端，集 **多协议 AI 对话、互动文游（RP）、长篇小说创作工坊、多平台历史导入、在线生图与精修** 于一体。

支持 **咕咕嘎（sub2api-auto）账号登录**，也支持 **完全脱离后端的自定义 API 直连模式**（兼容 4 大主流 AI 协议），所有本地会话、记忆与密钥均安全保存在本机。

---

## 核心特性

### 1. 四大标准 AI API 协议与混合接入
- **OpenAI Chat Completions**（`/v1/chat/completions`、`/v1/models`、`/v1/images/generations`、`/v1/images/edits`）
- **OpenAI Responses**（`/v1/responses`，支持推理摘要与用量流式事件）
- **Anthropic Claude Messages**（`/v1/messages`，支持 Extended Thinking 思考块与多模态识图）
- **Google Gemini Native**（`/v1beta/models/*:streamGenerateContent?alt=sse`、Imagen 与原生生图）
- **双接入模式**：支持咕咕嘎服务端账号登录（含 2FA、内嵌人机验证与 WebView 登录）或直接在登录页进入「自定义 API 直连」，也可在 **我的 → API 接口管理** 中管理多个自定义接口并与服务端分组自由混用；API Key 使用 `EncryptedSharedPreferences` 本地加密存储。

### 2. 对话、文游（RP）与小说三合一创作空间
- **流畅流式对话**：后台 IO 线程实时解析 SSE 增量吐字，支持折叠深度思考链（Reasoning）、Markdown 表格与代码块一键复制、划词选中及多模态图片附件。
- **上下文管理与长期记忆**：实时 Token 水位监控、自动/手动上下文压缩、中文分词 BM25 记忆召回、用户偏好记忆、账本与编年史归档。
- **互动文游（RP）工坊**：内置多套剧本预设，支持角色卡与 NPC 好感度管理、HP/MP/金币/背包/任务状态面板、多面骰子掷骰（普通/优势/劣势）、互动分支选项按钮、分支回溯与多档位 SL 存档。
- **长篇小说工坊**：开书向导、分章管理、独立世界书、三路剧情草稿、大纲共创、润色工具、护眼多纸张**纯净阅读模式**以及 `.txt` / `.md` 一键导出。

### 3. 多平台外部对话 / 剧本一键导入
支持从主流 AI 与文字互动平台迁移历史记录，自动识别格式并支持预览筛选与目标模式切换（对话 / 文游 / 小说）：
- **DeepSeek**：支持「导入官方 `.zip` / `conversations.json`」与「粘贴下载链接直接解析」，保留思考链与多分支回复。
- **酒馆（SillyTavern）**：支持 V1/V2/V3 角色卡（`.png` / `.json`，含 `character_book` 世界书）、独立世界书 `.json`、聊天记录 `.jsonl`（保留多分支 `swipes` 与思考链）及打包 `.zip`。
- **橙光 / 互动剧本**：支持导入 `.txt` / `.md` / `.json` 剧本文件或直接粘贴剧本文本，自动提取章节、角色台词与分支选项，直接以文游或小说续写。
- **Chatbox / ChatGPT / Claude / Markdown**：支持 Chatbox JSON 备份、ChatGPT 与 Claude 官方导出包及常见 Markdown 对话记录。

### 4. 统一创作工坊（作品 · 灵感 · 工具）
- **作品画廊**：统一展示服务端与直连 API 本地保存的作品，支持搜索、筛选、批量管理、详情查看、双指缩放多图灯箱、保存到相册与系统分享。
- **灵感与风格词库**：瀑布流精选提示词与风格词条，一键复制或携带比例直接「画同款」。
- **多引擎生图与精修**：支持分组/模型快速折叠选择、参数预设库、参考图垫图、**NovelAI V3 / V4.5 / V5** 参数精调，以及 **涂抹重绘（Inpaint）、智能扩图（Outpaint）、文字改图、生成变体** 四合一画布编辑器。

### 5. 双视觉风格与深浅色自适应
- 提供 **咕咕嘎漫画风** 与 **经典极简** 两套视觉风格，支持 **跟随系统 / 浅色 / 深色** 主题以及 **简体中文 / English** 实时切换。
- 支持完整的本地 JSON 备份导出与恢复。

---

## 下载安装

前往本仓库的 [**Releases 页面**](https://github.com/Espboxx/gugachat/releases) 下载最新版本的 `.apk` 安装包（要求 **Android 8.0 / API 26** 及以上）。

---

## 本地构建指南

### 环境要求
- **JDK**：JDK 17
- **Android SDK**：`compileSdk = 34`，`targetSdk = 34`，`minSdk = 26`
- **Gradle**：内置 Wrapper `8.14.3`（已配置国内镜像加速）

### 运行单元测试

```bash
./gradlew :app:testDebugUnitTest --no-daemon --console=plain
```

### 构建 APK

```bash
# 构建 Debug APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleDebug --no-daemon --console=plain

# 构建 Release APK -> app/build/outputs/apk/release/app-release.apk
./gradlew :app:assembleRelease --no-daemon --console=plain
```

---

## 交流与反馈

欢迎提交 Issue 或 Pull Request，一起完善 GuGuChat！

- **QQ 交流群**：`587660465`
