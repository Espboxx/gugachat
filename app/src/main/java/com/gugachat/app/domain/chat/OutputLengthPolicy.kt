package com.gugachat.app.domain.chat

import com.gugachat.app.core.storage.ApiProtocol
import com.gugachat.app.data.api.DirectProtocolCodec
import com.gugachat.app.data.api.OnlineChatModelDto
import com.gugachat.app.data.api.WireChatMessageDto
import kotlin.math.ceil

/**
 * Pure domain policy for customizable minimum output length across all 3 modes
 * (Chat / RP / Novel):
 * - Prompt-cache-safe Zone 3 directive generation (ZH & EN)
 * - Bilingual effective prose/body counter (stripping RP/Novel internal tags, excluding reasoning)
 * - Model-aware & thinking-budget-aware `max_tokens` floor calculation
 * - Continuation nudge & Standard Chat manual continue message builders
 */
object OutputLengthPolicy {
    const val MIN_OUTPUT_CHARS_LOWER = 50
    const val MIN_OUTPUT_CHARS_UPPER = 20_000
    const val SHORT_REPLY_TRIGGER_RATIO = 0.85
    const val MAX_AUTO_CONTINUE_PASSES = 2
    const val MIN_AUTO_CONTINUE_DELTA = 40
    const val MAX_EFFECTIVE_FLOOR_CAP = 65_536

    private const val DEFAULT_REASONING_HEADROOM = 4096
    private const val ANTHROPIC_DEFAULT_MAX_TOKENS = 4096
    private const val ENGLISH_WORD_TOKEN_RATE = 1.35
    private const val FINISH_SENTENCE_MULTIPLIER = 1.35

    val PRESET_LENGTHS: List<Int?> = listOf(null, 300, 800, 1500, 3000)

    fun normalizeMinOutputChars(raw: Int?): Int? {
        if (raw == null || raw <= 0) return null
        return raw.coerceIn(MIN_OUTPUT_CHARS_LOWER, MIN_OUTPUT_CHARS_UPPER)
    }

    fun shortReplyThreshold(minOutputChars: Int): Int {
        val normalized = normalizeMinOutputChars(minOutputChars) ?: return 0
        return (normalized * SHORT_REPLY_TRIGGER_RATIO).toInt().coerceAtLeast(1)
    }

    fun isCjkChar(ch: Char): Boolean {
        val script = Character.UnicodeScript.of(ch.code)
        return script == Character.UnicodeScript.HAN ||
            script == Character.UnicodeScript.HIRAGANA ||
            script == Character.UnicodeScript.KATAKANA ||
            script == Character.UnicodeScript.HANGUL
    }

    /**
     * Detects whether the active turn should receive an English (`words`) directive
     * versus a Chinese/CJK (`字`) directive.
     */
    fun isEnglishPreference(
        recentUserText: String?,
        appLanguageIsEnglish: Boolean = false,
    ): Boolean {
        val clean = recentUserText?.trim().orEmpty()
        if (clean.isEmpty()) return appLanguageIsEnglish
        var hasCjk = false
        var hasLatin = false
        for (ch in clean) {
            if (isCjkChar(ch)) {
                hasCjk = true
                break
            }
            if (ch in 'A'..'Z' || ch in 'a'..'z') {
                hasLatin = true
            }
        }
        return when {
            hasCjk -> false
            hasLatin -> true
            else -> appLanguageIsEnglish
        }
    }

    fun stripInternalTagsForMode(rawText: String, mode: ConversationMode): String = when (mode) {
        ConversationMode.rp -> RpEngine.stripRpInternalTags(rawText).trim()
        ConversationMode.novel -> NovelExport.stripInternalTags(rawText).trim()
        ConversationMode.chat -> rawText.trim()
    }

    /**
     * Counts effective visible body length, stripping mode-specific structured tags
     * (never counting reasoning/thinking text).
     *
     * - Each CJK character (Han / Hiragana / Katakana / Hangul) counts as 1.
     * - Each contiguous Latin/numeric word (including contractions/hyphenations like `don't` or `well-known`) counts as 1.
     * - CJK punctuation counts as 1 when the text is CJK-dominant so Chinese counts align closely with [NovelExport.countProseWords].
     */
    fun countEffectiveLength(rawText: String, mode: ConversationMode): Int {
        val clean = stripInternalTagsForMode(rawText, mode)
        if (clean.isEmpty()) return 0

        var cjkChars = 0
        var cjkPunct = 0
        var latinWords = 0
        var inWord = false
        val len = clean.length

        for (i in 0 until len) {
            val ch = clean[i]
            when {
                isCjkChar(ch) -> {
                    inWord = false
                    cjkChars++
                }
                TokenEstimator.isCjkPunctChar(ch) -> {
                    inWord = false
                    cjkPunct++
                }
                ch.isLetterOrDigit() -> {
                    if (!inWord) {
                        latinWords++
                        inWord = true
                    }
                }
                (ch == '\'' || ch == '\u2019' || ch == '-') &&
                    inWord &&
                    i + 1 < len &&
                    clean[i + 1].isLetterOrDigit() &&
                    !isCjkChar(clean[i + 1]) -> {
                    // Keep contractions / hyphenated compounds as a single Latin word.
                }
                else -> {
                    inWord = false
                }
            }
        }

        val includeCjkPunct = cjkChars > 0 && cjkChars >= latinWords
        return cjkChars + latinWords + if (includeCjkPunct) cjkPunct else 0
    }

    fun isShortReply(
        rawText: String,
        mode: ConversationMode,
        minOutputChars: Int?,
    ): Boolean {
        val target = normalizeMinOutputChars(minOutputChars) ?: return false
        val actual = countEffectiveLength(rawText, mode)
        return actual > 0 && actual < shortReplyThreshold(target)
    }

    /**
     * Builds a concise, high-compliance Zone 3 post-history directive tailored to each mode.
     * Designed for injection at the tail of the final user turn so Zone 0/1/2 KV-cache stays intact.
     */
    fun buildLengthDirective(
        mode: ConversationMode,
        minOutputChars: Int?,
        novelConfig: NovelConfig? = null,
        activeChapter: NovelChapter? = null,
        isEnglish: Boolean = false,
    ): String {
        val target = normalizeMinOutputChars(minOutputChars) ?: return ""
        return when (mode) {
            ConversationMode.chat -> if (isEnglish) {
                "[Output Length Requirement] Write a substantive response of at least $target words. " +
                    "Reach this length through deeper reasoning, concrete examples, step-by-step breakdown, and edge cases—never pad with filler or repetition."
            } else {
                "【输出长度要求】本轮回复正文不少于 ${target} 字。请通过充分的论证深度、具体案例、分步拆解与边界分析来充实内容，严禁堆砌空话、重复措辞或无意义凑字数。"
            }

            ConversationMode.rp -> if (isEnglish) {
                "[Output Length Requirement] Write at least $target words of pure narrative prose and dialogue " +
                    "(excluding structured tags such as <rp_state>, <rp_choices>, <rp_ledger>, <rp_ending>, <rp_speaker>, and any thinking/reasoning). " +
                    "Enrich the scene with sensory atmosphere, micro-expressions, action pacing, and subtext—never pad with filler or repetition."
            } else {
                "【输出长度要求】本轮纯叙事与对话正文不少于 ${target} 字（不包含 <rp_state>、<rp_choices>、<rp_ledger>、<rp_ending>、<rp_speaker> 等结构化标签及思考过程）。" +
                    "请着重刻画环境氛围、感官细节、角色微表情、动作节奏与潜台词，严禁空洞水字数或机械复述。"
            }

            ConversationMode.novel -> {
                val chapterTarget = novelConfig?.targetWordsPerChapter
                val remainingChapter = if (chapterTarget != null && activeChapter != null) {
                    (chapterTarget - activeChapter.wordCount).coerceAtLeast(0)
                } else {
                    null
                }
                if (isEnglish) {
                    val base = "[Output Length Requirement] Write at least $target words of pure literary prose in this turn " +
                        "(excluding internal tags such as <status_hud>, <lore_sync>, <chapter_end>, <choices>, and any thinking/reasoning). " +
                        "Focus on vivid imagery, narrative pacing, psychological depth, and dialogue tension—avoid filler or repetition."
                    if (chapterTarget != null && remainingChapter != null && remainingChapter > 0) {
                        "$base (Current chapter cumulative target is $chapterTarget words with ~$remainingChapter remaining; prioritize meeting this turn's minimum naturally without rushing the chapter end.)"
                    } else {
                        base
                    }
                } else {
                    val base = "【输出长度要求】本轮单次正文撰写不少于 ${target} 字（不包含 <status_hud>、<lore_sync>、<chapter_end>、<choices> 等系统标签及思考过程）。" +
                        "请注重画面意象、叙事节奏、人物心理层次与对话张力，禁止水字数或重复抒情。"
                    if (chapterTarget != null && remainingChapter != null && remainingChapter > 0) {
                        "${base}（当前章节累计目标 ${chapterTarget} 字，尚余约 ${remainingChapter} 字；本轮须先保证达到上述单次下限，自然推进情节即可。）"
                    } else {
                        base
                    }
                }
            }
        }
    }

    /**
     * Estimates the visible-output token floor required to comfortably hit [minOutputChars]
     * without mid-sentence truncation, accounting for model tokenizer prior and structured tags.
     */
    fun computeVisibleOutputTokenFloor(
        minOutputChars: Int?,
        mode: ConversationMode,
        modelId: String?,
        isEnglish: Boolean = false,
    ): Int? {
        val target = normalizeMinOutputChars(minOutputChars) ?: return null
        val prior = TokenEstimator.priorFor(modelId)
        val unitRate = if (isEnglish) {
            maxOf(prior.hanRatio, ENGLISH_WORD_TOKEN_RATE)
        } else {
            prior.hanRatio
        }
        val tagHeadroom = when (mode) {
            ConversationMode.rp -> 512
            ConversationMode.novel -> 384
            ConversationMode.chat -> 128
        }
        return ceil(target * unitRate * FINISH_SENTENCE_MULTIPLIER).toInt() + tagHeadroom
    }

    /**
     * Computes the total minimum `max_tokens` floor (visible output + reasoning headroom),
     * capped safely to `min(65_536, windowLimit / 2)`. Returns `null` if no minimum length is active.
     */
    fun computeRequiredMaxTokensFloor(
        params: ChatParams,
        mode: ConversationMode,
        modelId: String?,
        modelMeta: OnlineChatModelDto? = null,
        windowLimit: Int = 128_000,
        isEnglish: Boolean = false,
    ): Int? {
        val visibleFloor = computeVisibleOutputTokenFloor(
            minOutputChars = params.minOutputChars,
            mode = mode,
            modelId = modelId,
            isEnglish = isEnglish,
        ) ?: return null

        val reasoningHeadroom = if (params.enableReasoning) {
            val explicitBudget = DirectProtocolCodec.effortToBudgetTokens(params.reasoningEffort)
            val supportsReasoning = modelMeta?.supports_reasoning == true ||
                (!modelId.isNullOrBlank() && DirectProtocolCodec.inferChatModelMeta(modelId).supports_reasoning)
            when {
                explicitBudget != null -> explicitBudget
                supportsReasoning -> DEFAULT_REASONING_HEADROOM
                else -> 0
            }
        } else {
            0
        }

        val safeCap = minOf(MAX_EFFECTIVE_FLOOR_CAP, (windowLimit / 2).coerceAtLeast(1024))
        return (visibleFloor + reasoningHeadroom).coerceAtMost(safeCap)
    }

    /**
     * Computes the effective `max_tokens` to transmit on the wire:
     * - When `minOutputChars` is unset: returns `params.maxTokens` unchanged.
     * - When `params.maxTokens != null`: raises it to at least the required floor so output is never starved.
     * - When `params.maxTokens == null`:
     *   - For [ApiProtocol.ANTHROPIC] (which requires `max_tokens` and defaults to 4096): returns `max(4096, floor)`.
     *   - For OpenAI / Gemini: returns `null` so providers keep their full native max output limit.
     */
    fun computeEffectiveMaxTokens(
        params: ChatParams,
        mode: ConversationMode,
        modelId: String?,
        modelMeta: OnlineChatModelDto? = null,
        protocol: ApiProtocol = ApiProtocol.OPENAI_CHAT,
        windowLimit: Int = 128_000,
        isEnglish: Boolean = false,
    ): Int? {
        val floor = computeRequiredMaxTokensFloor(
            params = params,
            mode = mode,
            modelId = modelId,
            modelMeta = modelMeta,
            windowLimit = windowLimit,
            isEnglish = isEnglish,
        ) ?: return params.maxTokens

        val requested = params.maxTokens
        return when {
            requested != null -> maxOf(requested, floor)
            protocol == ApiProtocol.ANTHROPIC -> maxOf(ANTHROPIC_DEFAULT_MAX_TOKENS, floor)
            else -> null
        }
    }

    /**
     * Builds the continuation nudge prompt for silent auto-continue or manual short-reply continue.
     */
    fun buildContinueNudge(
        mode: ConversationMode,
        actual: Int,
        target: Int,
        isEnglish: Boolean = false,
    ): String {
        val safeActual = actual.coerceAtLeast(0)
        val safeTarget = target.coerceAtLeast(1)
        val remaining = (safeTarget - safeActual).coerceAtLeast(1)
        return when (mode) {
            ConversationMode.chat -> if (isEnglish) {
                "Continue seamlessly from where you left off without repeating earlier points. " +
                    "Current body length is ~$safeActual words (need ~$remaining more words to reach the $safeTarget-word minimum). " +
                    "Deepen the reasoning, concrete details, or examples, and conclude naturally."
            } else {
                "请紧接上文继续深入展开，不要重复已写过的内容。当前正文约 $safeActual 字，距最低目标 $safeTarget 字还差约 $remaining 字，请补充更有深度的分析、细节或示例并自然收尾。"
            }

            ConversationMode.rp -> if (isEnglish) {
                "Continue seamlessly from the last sentence with richer narrative prose, sensory atmosphere, and character interaction—do not repeat earlier text. " +
                    "Current body length is ~$safeActual words (need ~$remaining more words to reach the $safeTarget-word minimum). " +
                    "Only append structured RP tags at the very end once the prose length is met."
            } else {
                "请紧接上一句无缝续写剧情、感官细节与角色互动，不要重复前文。当前正文约 $safeActual 字，距最低目标 $safeTarget 字还差约 $remaining 字；待正文篇幅充实后再按规则在末尾输出状态与选项标签。"
            }

            ConversationMode.novel -> if (isEnglish) {
                "Continue the prose seamlessly from the last sentence, maintaining consistent voice, POV, and pacing without repeating earlier text. " +
                    "Current body length is ~$safeActual words (need ~$remaining more words to reach the $safeTarget-word minimum). " +
                    "Only append internal novel tags at the very end once the prose length is met."
            } else {
                "请紧接上一句无缝续写本章正文，保持文风、视角与叙事节奏一致，不要重复前文。当前正文约 $safeActual 字，距本轮最低目标 $safeTarget 字还差约 $remaining 字；待正文充分展开后再按规则在末尾输出系统标签。"
            }
        }
    }

    /**
     * Builds wire messages for manual "Continue" on a Standard Chat assistant reply.
     */
    fun buildStandardChatContinueMessages(
        conv: ChatConversation,
        activeMessages: List<ChatMessage>,
        instruction: String = "",
        isEnglish: Boolean = false,
    ): List<WireChatMessageDto> {
        val out = ArrayList<WireChatMessageDto>()
        val sysParts = buildList {
            if (conv.systemPrompt.isNotBlank()) add(conv.systemPrompt.trim())
            if (conv.archivedSummary.isNotBlank()) {
                val heading = if (isEnglish) "[Conversation Summary]" else "【前情摘要】"
                add("$heading\n${conv.archivedSummary.trim()}")
            }
        }
        if (sysParts.isNotEmpty()) {
            out += WireChatMessageDto.text("system", sysParts.joinToString("\n\n"))
        }

        val recent = activeMessages
            .filter { (it.role == "user" || it.role == "assistant") && it.content.isNotBlank() }
            .takeLast(12)
        for (msg in recent) {
            out += WireChatMessageDto.text(msg.role, msg.content)
        }

        val lastAssistant = recent.lastOrNull { it.role == "assistant" }
        val target = normalizeMinOutputChars(conv.params.minOutputChars)
        val cleanInstruction = instruction.trim()
        val nudge = when {
            cleanInstruction.isNotEmpty() -> if (isEnglish) {
                "Continue seamlessly from where your last reply ended without repeating previous text. Additional direction: $cleanInstruction"
            } else {
                "请紧接上一条回复的末尾继续展开，不要重复前文内容。补充方向：$cleanInstruction"
            }
            target != null && lastAssistant != null -> {
                val actual = countEffectiveLength(lastAssistant.content, ConversationMode.chat)
                if (actual < target) {
                    buildContinueNudge(ConversationMode.chat, actual, target, isEnglish)
                } else if (isEnglish) {
                    "Continue seamlessly from where your last reply ended with deeper details and no repetition."
                } else {
                    "请紧接上一条回复末尾继续深入补充，不要重复前文。"
                }
            }
            isEnglish -> "Continue seamlessly from where your last reply ended without repeating previous text."
            else -> "请紧接上一条回复末尾继续展开，不要重复前文内容。"
        }
        out += WireChatMessageDto.text("user", nudge)
        return out
    }
}
