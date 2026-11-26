package com.gugachat.app.domain.chat

import com.gugachat.app.data.api.AppJson
import com.gugachat.app.domain.chat.RpCardCodec.arrOrNull
import com.gugachat.app.domain.chat.RpCardCodec.boolOrNull
import com.gugachat.app.domain.chat.RpCardCodec.intOrNull
import com.gugachat.app.domain.chat.RpCardCodec.objOrNull
import com.gugachat.app.domain.chat.RpCardCodec.strListOrEmpty
import com.gugachat.app.domain.chat.RpCardCodec.strOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Pure-JVM codec for UUGame (`format: "uu-game-save"`, v1+) interactive text-game / life-sim saves.
 *
 * Maps the rich save snapshot cleanly into GuGuChat's native RP architecture:
 * - Main story turns (`messages`, `narrator` -> `assistant`) + interactive `choices` -> `RpChoice` / `pendingChoices`
 * - NPC roster (`connections`) + latest named relationship stats (`connectionChanges`) + inner `opinion` -> `RpCharacter.secret` + remote `avatarCutoutUrl` -> `RpCharacter.avatar`
 * - Player persona (`playerName`, `setupFieldAnswers`, string attributes), named RPG `stats`, `gold`, `inventory`, `quests`, `quickActions`
 * - Structured long-term memory: `sideEventFacts` -> `MemoryLedgerItem`, `mainlineSummaryHistory` & `sideEventChronicles` -> `MemoryEpisode`
 * - Secondary world context (`assets` details, `events`, `contactChats`, `contactMomentPosts`, `forumPosts`, `diaryEntries`) -> keyword-triggered `NovelLoreEntry`
 */
object UuSaveCodec {

    private val GOLD_KEYS = setOf("现金", "金钱", "资金", "银两", "金币", "余额", "cash", "gold", "money")
    private val PLACEHOLDER_PREFIXES = listOf("新属性", "attr-", "attr_")
    private val IGNORED_SETUP_VALUES = setOf("无", "暂无", "none", "n/a", "-")
    private val GENERIC_LORE_KEYWORDS = setOf("消息", "动态", "微信", "朋友圈", "论坛", "热帖", "日记")
    private const val FRIEND_VERIFY_BOILERPLATE = "我通过了你的朋友验证请求"
    private const val STAGE0_HUGE_TURN_THRESHOLD = 12
    private const val STAGE0_HUGE_TOKEN_THRESHOLD = 40_000
    private const val STAGE0_DEFAULT_EFFECTIVE_LIMIT = 100_352 // 128K * 0.80 - 2048
    private const val STAGE0_TARGET_WINDOW_RATIO = 0.55

    fun isUuGameSave(root: JsonElement): Boolean {
        val obj = root as? JsonObject ?: return false
        val format = obj.strOrNull("format")?.trim()?.lowercase()
        if (format == "uu-game-save" && obj.objOrNull("gameState") != null) return true
        // Also tolerate unwrapped `gameState` objects exported directly
        val hasMsgs = obj["messages"] is JsonArray
        if (!hasMsgs) return false
        if (!obj.strOrNull("playthroughId").isNullOrBlank()) return true
        val hasPlayer = !obj.strOrNull("playerName").isNullOrBlank()
        val hasGameFields = obj["connections"] is JsonArray ||
            obj["events"] is JsonArray ||
            obj["sideEventFacts"] is JsonObject ||
            obj["mainlineSummary"] is JsonPrimitive
        return hasPlayer && hasGameFields
    }

    fun parseJsonText(rawJson: String, fileName: String? = null): ExternalImportBundle {
        val cleaned = rawJson.removePrefix("\uFEFF").trim()
        val root = try {
            AppJson.parseToJsonElement(cleaned) as? JsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("无效的 UU 存档 JSON 格式：${e.message}")
        } ?: throw IllegalArgumentException("UU 存档 JSON 根节点必须为对象")
        return parseJsonElement(root, fileName)
    }

    fun parseJsonElement(root: JsonObject, fileName: String? = null): ExternalImportBundle {
        val gs = root.objOrNull("gameState") ?: root
        val now = System.currentTimeMillis()
        val exportedAt = parseTimestamp(root["exportedAt"]) ?: now

        val scriptId = gs.strOrNull("scriptId")?.takeIf { it.isNotBlank() }
            ?: root.strOrNull("scriptId")?.takeIf { it.isNotBlank() }
            ?: ""
        val playthroughId = gs.strOrNull("playthroughId")?.takeIf { it.isNotBlank() }
            ?: scriptId.ifBlank { fileName ?: "uu_save" }
        val baseId = playthroughId.hashCode().toUInt().toString(16)
        val convId = "uu_$baseId"

        val playerName = gs.strOrNull("playerName")?.trim()?.takeIf { it.isNotEmpty() } ?: "主角"
        val round = gs.intOrNull("round") ?: root.intOrNull("round") ?: 1
        val timeLabel = gs.strOrNull("timeLabel").orEmpty().trim()
        val location = gs.strOrNull("location").orEmpty().trim()
        val activeSideEventId = gs.strOrNull("activeSideEventId")?.trim()
        val actionsRemaining = gs.intOrNull("actionsRemainingInPeriod")
        val slotName = root.strOrNull("slotName")?.trim()?.takeIf { it.isNotEmpty() }

        // 1. Parse Events metadata early (needed for title, quests, fallback choices, and chronicle titles)
        val rawEvents = gs.arrOrNull("events").orEmpty().mapNotNull { it as? JsonObject }
        val eventTitleById = LinkedHashMap<String, String>()
        rawEvents.forEach { ev ->
            val eid = ev.strOrNull("id")?.trim().orEmpty()
            val title = ev.strOrNull("title")?.trim().orEmpty()
            if (eid.isNotEmpty() && title.isNotEmpty()) {
                eventTitleById[eid] = title
            }
        }
        val activeEventObj = activeSideEventId?.let { aid ->
            rawEvents.firstOrNull { it.strOrNull("id") == aid }
        }
        val activeEventTitle = activeEventObj?.strOrNull("title")?.trim().orEmpty()

        val convTitle = slotName ?: when {
            activeEventTitle.isNotEmpty() && activeEventTitle.length <= 16 ->
                "$playerName · $activeEventTitle"
            else -> "$playerName · 第${round}回合"
        }

        // 2. Parse Messages, tracking latest named NPC attributes, on-stage cast, choices, and chapter spans
        val rawMsgs = gs.arrOrNull("messages").orEmpty().mapNotNull { it as? JsonObject }
        val latestNpcNamedAttrs = LinkedHashMap<String, LinkedHashMap<String, String>>()
        var lastOnStageIds = emptySet<String>()
        var lastNarratorChoices: List<RpChoice> = emptyList()
        val extraSystemNotes = ArrayList<String>()

        val seedStartTs = exportedAt - (rawMsgs.size.coerceAtLeast(1) * 2_000L)
        var lastMsgTs = seedStartTs
        val messages = ArrayList<ChatMessage>(rawMsgs.size)

        // Chapter grouping for optional Novel mode conversion
        val chapterKeyOrder = ArrayList<String>()
        val chapterTitles = LinkedHashMap<String, String>()
        val chapterWordCounts = LinkedHashMap<String, Int>()
        val chapterFirstSeq = LinkedHashMap<String, Int>()

        rawMsgs.forEachIndexed { idx, mObj ->
            val rawRole = mObj.strOrNull("role")?.trim()?.lowercase().orEmpty()
            val content = mObj.strOrNull("content").orEmpty().trim()

            // Always harvest metadata from narrator/assistant turns even if content is blank
            mObj.arrOrNull("connectionChanges").orEmpty().mapNotNull { it as? JsonObject }.forEach { cc ->
                val npcName = cc.strOrNull("name")?.trim().orEmpty()
                val attrs = cc.objOrNull("npcAttributes")
                if (npcName.isNotEmpty() && attrs != null) {
                    val bucket = latestNpcNamedAttrs.getOrPut(npcName) { LinkedHashMap() }
                    attrs.forEach { (k, v) ->
                        val valStr = primitiveDisplayValue(v)
                        if (k.isNotBlank() && !isPlaceholderAttrKey(k) && valStr.isNotEmpty()) {
                            bucket[k.trim()] = valStr
                        }
                    }
                }
            }
            val stageIds = mObj.strListOrEmpty("onStageConnectionIds").toSet()
            if (stageIds.isNotEmpty()) {
                lastOnStageIds = stageIds
            }

            if (rawRole == "system") {
                if (content.isNotEmpty()) extraSystemNotes.add(content)
                return@forEachIndexed
            }
            val mappedRole = when (rawRole) {
                "user", "human", "player" -> "user"
                "narrator", "assistant", "ai", "model", "gm", "bot" -> "assistant"
                else -> return@forEachIndexed
            }
            if (content.isEmpty()) return@forEachIndexed

            val parsedTs = parseTimestamp(mObj["timestamp"])
            val msgTs = if (parsedTs != null) {
                if (parsedTs > lastMsgTs) parsedTs else lastMsgTs + 1_000L
            } else {
                lastMsgTs + 1_000L
            }
            lastMsgTs = msgTs

            val msgId = mObj.strOrNull("id")?.trim()?.takeIf { it.isNotEmpty() } ?: "m$idx"
            val choices = if (mappedRole == "assistant") {
                parseUuChoices(mObj.arrOrNull("choices"), "${convId}_${msgId}")
            } else {
                emptyList()
            }
            if (mappedRole == "assistant" && choices.isNotEmpty()) {
                lastNarratorChoices = choices
            }

            val seq = messages.size + 1
            val modelLabel = if (mappedRole == "assistant") {
                mObj.strOrNull("aiEngineLabel")?.trim()?.takeIf { it.isNotEmpty() }
            } else {
                null
            }

            messages.add(
                ChatMessage(
                    id = "${convId}_$msgId",
                    conversationId = convId,
                    role = mappedRole,
                    content = content,
                    model = modelLabel,
                    createdAt = msgTs,
                    seq = seq,
                    rpChoices = choices,
                    status = "done",
                    tokenEstimate = ContextBuilder.estimateMessageTokens(content),
                ),
            )

            // Group chapter milestones by sideEventId or timeLabel
            val evId = mObj.strOrNull("sideEventId")?.trim().orEmpty()
            val mTimeLabel = mObj.strOrNull("timeLabel")?.trim().orEmpty()
            val chKey = evId.ifBlank { mTimeLabel.ifBlank { "main" } }
            if (chKey !in chapterTitles) {
                chapterKeyOrder.add(chKey)
                chapterTitles[chKey] = eventTitleById[evId]?.takeIf { it.isNotBlank() }
                    ?: mTimeLabel.ifBlank { "主线剧情" }
                chapterFirstSeq[chKey] = seq
            }
            if (mappedRole == "assistant") {
                chapterWordCounts[chKey] = (chapterWordCounts[chKey] ?: 0) + content.length
            }
        }

        // 3. Parse Attributes -> gold, named numeric stats, meaningful text flags
        val attrsObj = gs.objOrNull("attributes")
        var gold = 0
        val stats = LinkedHashMap<String, Int>()
        val flags = LinkedHashMap<String, String>()
        val meaningfulTextAttrs = ArrayList<String>()

        attrsObj?.forEach { (key, elem) ->
            val k = key.trim()
            val prim = elem as? JsonPrimitive ?: return@forEach
            if (prim is JsonNull) return@forEach
            val lowerK = k.lowercase()
            if (lowerK in GOLD_KEYS) {
                val num = prim.intOrNull ?: prim.doubleOrNull?.toInt()
                if (num != null) {
                    gold = num
                    return@forEach
                }
            }
            if (prim.isString) {
                val v = prim.content.trim()
                if (v.isNotEmpty() && v.lowercase() !in IGNORED_SETUP_VALUES) {
                    meaningfulTextAttrs.add(v)
                    if (!isPlaceholderAttrKey(k)) {
                        flags[k] = v
                    }
                }
            } else {
                val num = prim.intOrNull ?: prim.doubleOrNull?.toInt()
                if (num != null && !isPlaceholderAttrKey(k)) {
                    stats[k] = num
                }
            }
        }
        if (scriptId.isNotEmpty()) flags["UU剧本"] = scriptId
        if (actionsRemaining != null) flags["剩余行动"] = actionsRemaining.toString()

        // 4. Assemble Player Persona from `setupFieldAnswers` + meaningful text attributes + age
        val setupAnswers = gs.objOrNull("setupFieldAnswers")
            ?: gs.objOrNull("characterSetupSnapshot")?.objOrNull("fieldAnswers")
        val personaParts = LinkedHashSet<String>()
        gs.intOrNull("playerAgeAnchorAge")?.let { age ->
            if (age > 0) personaParts.add("年龄：${age}岁")
        }
        setupAnswers?.values?.forEach { elem ->
            when (elem) {
                is JsonPrimitive -> {
                    if (elem !is JsonNull) {
                        val s = elem.content.trim()
                        if (s.isNotEmpty() && s.lowercase() !in IGNORED_SETUP_VALUES) {
                            personaParts.add(s)
                        }
                    }
                }
                is JsonArray -> {
                    val joined = elem.mapNotNull { (it as? JsonPrimitive)?.takeUnless { p -> p is JsonNull }?.contentOrNull?.trim() }
                        .filter { it.isNotEmpty() && it.lowercase() !in IGNORED_SETUP_VALUES }
                        .joinToString("、")
                    if (joined.isNotEmpty()) personaParts.add(joined)
                }
                else -> Unit
            }
        }
        meaningfulTextAttrs.forEach { v ->
            if (personaParts.none { it.contains(v) }) {
                personaParts.add(v)
            }
        }
        val playerPersona = personaParts.joinToString("\n").take(1800)

        // 5. Parse NPC Roster (`connections[]`)
        val rawConnections = gs.arrOrNull("connections").orEmpty().mapNotNull { it as? JsonObject }
        val npcIdToName = LinkedHashMap<String, String>()
        val cast = rawConnections.mapIndexedNotNull { idx, cObj ->
            val name = cObj.strOrNull("name")?.trim().orEmpty()
            if (name.isEmpty()) return@mapIndexedNotNull null
            val npcId = cObj.strOrNull("id")?.trim()?.takeIf { it.isNotEmpty() } ?: "${convId}_npc_$idx"
            npcIdToName[npcId] = name

            val rel = cObj.strOrNull("relationship").orEmpty().trim()
            val rawAffinity = cObj.intOrNull("affinity") ?: 0
            val rawFriendliness = cObj.intOrNull("friendliness") ?: 0
            val effectiveAffinity = (if (rawAffinity != 0) rawAffinity else rawFriendliness).coerceIn(-100, 100)
            val romanceable = cObj.boolOrNull("romanceable")
            val desc = cObj.strOrNull("description").orEmpty().trim()
            val bg = cObj.strOrNull("background").orEmpty().trim()
            val opinion = cObj.strOrNull("opinion").orEmpty().trim()
            val avatarUrl = cObj.strOrNull("avatarCutoutUrl")?.trim()?.takeIf { it.startsWith("http") }
                ?: cObj.strOrNull("avatar")?.trim()?.takeIf { it.startsWith("http") }

            val npcAttrs = cObj.objOrNull("npcAttributes")
            val inferredGender = inferGenderFromNpcAttrs(npcAttrs)

            val role = when {
                romanceable == true || rawAffinity >= 25 ||
                    rel.contains("恋人") || rel.contains("伴侣") || rel.contains("包养") -> "romance"
                rawFriendliness < 0 || rawAffinity < 0 || rel.contains("敌") || rel.contains("情敌") -> "rival"
                rawFriendliness >= 60 -> "ally"
                else -> "companion"
            }

            val statusParts = ArrayList<String>()
            if (rawAffinity != 0 && rawFriendliness != 0) {
                statusParts.add("友好 $rawFriendliness")
            }
            latestNpcNamedAttrs[name]?.forEach { (k, v) ->
                statusParts.add("$k $v")
            }
            val statusText = statusParts.take(5).joinToString(" · ")
            val inScene = npcId in lastOnStageIds || cObj.boolOrNull("companionsPlayer") == true

            RpCharacter(
                id = npcId,
                name = name,
                role = role,
                gender = inferredGender,
                appearance = desc,
                personality = desc.take(260),
                background = bg,
                relationship = rel,
                affinity = effectiveAffinity,
                mood = if (effectiveAffinity >= 40) "亲近" else if (effectiveAffinity < 0) "戒备" else "平静",
                status = statusText,
                avatar = avatarUrl,
                secret = opinion,
                inScene = inScene,
            )
        }.ifEmpty {
            listOf(RpCharacter(id = "${convId}_gm", name = convTitle, role = "narrator", affinity = 20))
        }

        // 6. Inventory (`assets[]`) & Quests (`events[]`)
        val rawAssets = gs.arrOrNull("assets").orEmpty().mapNotNull { it as? JsonObject }
        val inventory = rawAssets.mapNotNull { a ->
            val name = a.strOrNull("name")?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            val icon = a.strOrNull("icon")?.trim().orEmpty()
            val qty = a.intOrNull("quantity") ?: 1
            buildString {
                if (icon.isNotEmpty()) append(icon).append(' ')
                append(name)
                if (qty > 1) append(" ×").append(qty)
            }
        }

        val quests = rawEvents
            .filter { ev ->
                val resolved = ev.boolOrNull("resolved") == true
                val abandoned = ev.boolOrNull("abandoned") == true
                !resolved && !abandoned
            }
            .sortedByDescending { it.strOrNull("id") == activeSideEventId }
            .mapNotNull { ev ->
                val title = ev.strOrNull("title")?.trim().orEmpty()
                if (title.isEmpty()) return@mapNotNull null
                val prefix = if (ev.strOrNull("id") == activeSideEventId) "★ " else ""
                val period = ev.strOrNull("periodKey")?.trim().orEmpty()
                if (period.isNotEmpty()) "$prefix$title（$period）" else "$prefix$title"
            }

        val quickActions = (
            gs.strListOrEmpty("nextPeriodQuickActionSlots") +
                gs.strListOrEmpty("customNextPeriodQuickActions")
            ).distinct()

        // Fallback pendingChoices from active event entryChoices if latest narrator message had none
        val pendingChoices = lastNarratorChoices.ifEmpty {
            parseUuChoices(activeEventObj?.arrOrNull("entryChoices"), "${convId}_evt")
        }

        // 7. Worldview from `mainlineSummary` or latest `mainlineSummaryHistory`
        val summaryHistory = gs.arrOrNull("mainlineSummaryHistory").orEmpty().mapNotNull { it as? JsonObject }
        val worldview = gs.strOrNull("mainlineSummary")?.trim()?.takeIf { it.isNotEmpty() }
            ?: summaryHistory.lastOrNull()?.strOrNull("summary")?.trim()?.takeIf { it.isNotEmpty() }
            ?: activeEventObj?.strOrNull("description")?.trim().orEmpty()

        val systemRules = buildList {
            add("本局由 UU 互动文游存档导入（当前时间：${timeLabel.ifBlank { "第${round}回合" }}，地点：${location.ifBlank { "未知" }}）。请严格延续已发生的剧情事实、人物性格口吻与数值状态继续推进。")
            if (extraSystemNotes.isNotEmpty()) addAll(extraSystemNotes)
        }.joinToString("\n\n")

        // 8. Long-Term Memory Ledger (`sideEventFacts` -> `MemoryLedgerItem`)
        val memoryLedger = ArrayList<MemoryLedgerItem>()
        gs.objOrNull("sideEventFacts")?.forEach { (eventId, factsElem) ->
            val evTitle = eventTitleById[eventId] ?: eventId.removePrefix("evt-").substringBeforeLast('-')
            val arr = factsElem as? JsonArray ?: return@forEach
            arr.forEachIndexed { fIdx, fElem ->
                val fObj = fElem as? JsonObject ?: return@forEachIndexed
                val content = fObj.strOrNull("content")?.trim().orEmpty()
                if (content.isEmpty()) return@forEachIndexed
                val factId = fObj.strOrNull("id")?.trim()?.takeIf { it.isNotEmpty() } ?: "${eventId}_f$fIdx"
                val tags = fObj.strListOrEmpty("tags")
                val periodKey = fObj.strOrNull("periodKey")?.trim().orEmpty()
                val created = parseTimestamp(fObj["createdAt"]) ?: exportedAt
                val fromSeq = (fObj.objOrNull("sourceRef")?.intOrNull("fromIndex") ?: 0) + 1
                val titlePrefix = if (periodKey.isNotEmpty()) "[$periodKey] " else ""
                val titleCore = tags.firstOrNull()?.takeIf { it.isNotBlank() } ?: evTitle.ifBlank { content.take(18) }
                val item = MemoryLedgerItem(
                    id = "${convId}_fact_$factId",
                    conversationId = convId,
                    kind = "fact",
                    title = "$titlePrefix$titleCore".take(48),
                    content = content,
                    keyEntities = (tags + listOfNotNull(evTitle.takeIf { it.isNotBlank() })).distinct(),
                    origin = "system",
                    validFromSeq = fromSeq,
                    createdAt = created,
                    updatedAt = created,
                    estimatedTokens = ContextBuilder.estimateMessageTokens(content),
                )
                memoryLedger.add(item)
            }
        }

        // 9. Long-Term Memory Episodes (`mainlineSummaryHistory` + `sideEventChronicles`)
        val memoryEpisodes = ArrayList<MemoryEpisode>()
        val maxSeq = messages.size.coerceAtLeast(1)
        summaryHistory.forEachIndexed { idx, hObj ->
            val summary = hObj.strOrNull("summary")?.trim().orEmpty()
            if (summary.isEmpty()) return@forEachIndexed
            val tLabel = hObj.strOrNull("timeLabel")?.trim().orEmpty().ifBlank { "第${idx + 1}阶段" }
            val atRound = hObj.intOrNull("atRound") ?: (idx + 1)
            memoryEpisodes.add(
                MemoryEpisode(
                    id = "${convId}_ep_main_$idx",
                    conversationId = convId,
                    level = 2,
                    title = "主线阶段 · $tLabel（第${atRound}回合）",
                    summary = summary,
                    keyEntities = listOf(playerName),
                    fromSeq = 1,
                    toSeq = maxSeq,
                    locked = false,
                    createdAt = exportedAt,
                    updatedAt = exportedAt,
                    estimatedTokens = EpisodeConsolidator.estimateEpisodeTokens("主线阶段 · $tLabel", summary),
                ),
            )
        }
        gs.objOrNull("sideEventChronicles")?.forEach { (eventId, chElem) ->
            val chObj = chElem as? JsonObject ?: return@forEach
            val body = chObj.strOrNull("body")?.trim().orEmpty()
            if (body.isEmpty()) return@forEach
            val evTitle = eventTitleById[eventId] ?: eventId.removePrefix("evt-").substringBeforeLast('-')
            val tLabel = chObj.strOrNull("timeLabel")?.trim().orEmpty()
            val created = parseTimestamp(chObj["createdAt"]) ?: exportedAt
            val epTitle = if (tLabel.isNotEmpty()) "编年史 · $evTitle（$tLabel）" else "编年史 · $evTitle"
            memoryEpisodes.add(
                MemoryEpisode(
                    id = "${convId}_ep_side_${eventId.hashCode().toUInt().toString(16)}",
                    conversationId = convId,
                    level = 3,
                    title = epTitle,
                    summary = body,
                    keyEntities = listOf(playerName, evTitle).filter { it.isNotBlank() }.distinct(),
                    fromSeq = 1,
                    toSeq = maxSeq,
                    locked = false,
                    createdAt = created,
                    updatedAt = created,
                    estimatedTokens = EpisodeConsolidator.estimateEpisodeTokens(epTitle, body),
                ),
            )
        }

        // 10. Keyword-Triggered Worldbook (`assets`, `events`, `contactChats`, `contactMomentPosts`, `forumPosts`, `diaryEntries`)
        val loreEntries = ArrayList<NovelLoreEntry>()
        val allNpcNames = cast.map { it.name }.filter { it.isNotBlank() }

        fun sanitizeLoreKeywords(rawKeywords: List<String>, fallbackTitle: String): List<String> {
            val cleaned = rawKeywords
                .map { it.trim() }
                .filter { kw ->
                    kw.length >= 2 &&
                        kw !in GENERIC_LORE_KEYWORDS &&
                        !kw.equals(playerName, ignoreCase = true)
                }
                .distinct()
            if (cleaned.isNotEmpty()) return cleaned
            val safeTitle = fallbackTitle.trim()
            return if (safeTitle.length >= 2 && safeTitle !in GENERIC_LORE_KEYWORDS && !safeTitle.equals(playerName, ignoreCase = true)) {
                listOf(safeTitle)
            } else {
                emptyList()
            }
        }

        fun addLore(
            idSuffix: String,
            category: String,
            title: String,
            content: String,
            keywords: List<String>,
            priority: Int = 55,
            alwaysActive: Boolean = false,
        ) {
            val cleanBody = content.trim()
            if (cleanBody.isEmpty()) return
            val entry = NovelLoreEntry(
                id = "${convId}_lore_$idSuffix",
                conversationId = convId,
                category = category,
                title = title.trim().take(60),
                content = cleanBody,
                keywords = sanitizeLoreKeywords(keywords, title),
                alwaysActive = alwaysActive,
                priority = priority,
                enabled = true,
                validFromSeq = 0,
                createdAt = exportedAt,
                updatedAt = exportedAt,
            )
            loreEntries.add(entry.copy(estimatedTokens = NovelEngine.estimateLoreTokens(entry)))
        }

        // 10a. Assets details
        rawAssets.forEachIndexed { idx, a ->
            val name = a.strOrNull("name")?.trim().orEmpty()
            val desc = a.strOrNull("description")?.trim().orEmpty()
            if (name.isEmpty() || desc.isEmpty()) return@forEachIndexed
            val qty = a.intOrNull("quantity") ?: 1
            val feedback = a.strOrNull("useFeedback")?.trim().orEmpty()
            val body = buildString {
                append("物品：$name")
                if (qty > 1) append("（数量：$qty）")
                append("\n").append(desc)
                if (feedback.isNotEmpty()) append("\n使用效果：").append(feedback)
            }
            addLore(
                idSuffix = "item_$idx",
                category = "item",
                title = "物品 · $name",
                content = body,
                keywords = listOf(name),
                priority = 60,
            )
        }

        // 10b. Events overview
        rawEvents.forEachIndexed { idx, ev ->
            val title = ev.strOrNull("title")?.trim().orEmpty()
            val desc = ev.strOrNull("description")?.trim().orEmpty()
            if (title.isEmpty() || desc.isEmpty()) return@forEachIndexed
            val resolved = ev.boolOrNull("resolved") == true
            val abandoned = ev.boolOrNull("abandoned") == true
            val period = ev.strOrNull("periodKey")?.trim().orEmpty()
            val statusStr = when {
                ev.strOrNull("id") == activeSideEventId -> "当前进行中"
                resolved -> "已完结"
                abandoned -> "已跳过"
                else -> "待触发/进行中"
            }
            val relNames = ev.arrOrNull("relatedConnections").orEmpty()
                .mapNotNull { (it as? JsonObject)?.strOrNull("name")?.trim()?.takeIf { n -> n.isNotEmpty() } }
            val body = buildString {
                append("事件：$title（$statusStr")
                if (period.isNotEmpty()) append(" · $period")
                append("）\n").append(desc)
                val summary = ev.strOrNull("summary")?.trim().orEmpty()
                if (summary.isNotEmpty()) {
                    append("\n\n【事件经过摘要】\n").append(summary.take(900))
                }
            }
            addLore(
                idSuffix = "evt_$idx",
                category = "event",
                title = "事件 · $title",
                content = body,
                keywords = listOf(title) + relNames.take(3),
                priority = if (ev.strOrNull("id") == activeSideEventId) 80 else 55,
            )
        }

        // 10c. NPC Private Chats (`contactChats`) -> keyword-triggered per NPC
        gs.objOrNull("contactChats")?.forEach { (npcId, msgsElem) ->
            val chatArr = msgsElem as? JsonArray ?: return@forEach
            val npcName = npcIdToName[npcId] ?: npcId
            val meaningfulLines = chatArr.mapNotNull { el ->
                val m = el as? JsonObject ?: return@mapNotNull null
                val text = m.strOrNull("content")?.trim().orEmpty()
                if (text.isEmpty() || text.contains(FRIEND_VERIFY_BOILERPLATE)) return@mapNotNull null
                val sender = if (m.strOrNull("role").equals("user", ignoreCase = true) ||
                    m.strOrNull("role").equals("player", ignoreCase = true)
                ) {
                    playerName
                } else {
                    npcName
                }
                "$sender：$text"
            }
            if (meaningfulLines.isNotEmpty()) {
                val content = "【与 $npcName 的手机私聊记录】\n" + meaningfulLines.takeLast(30).joinToString("\n")
                addLore(
                    idSuffix = "dm_${npcId.hashCode().toUInt().toString(16)}",
                    category = "concept",
                    title = "私聊记录 · $npcName",
                    content = content.take(2000),
                    keywords = listOf(npcName, "$npcName&私聊"),
                    priority = 65,
                )
            }
        }

        // 10d. Social Moments (`contactMomentPosts`) -> grouped cleanly by author or chronological feed
        val rawMoments = gs.arrOrNull("contactMomentPosts").orEmpty().mapNotNull { it as? JsonObject }
        if (rawMoments.isNotEmpty()) {
            val momentAuthors = LinkedHashSet<String>()
            val lines = rawMoments.mapNotNull { post ->
                val author = post.strOrNull("authorName")?.trim().orEmpty().ifBlank { "好友" }
                val body = post.strOrNull("content")?.trim().orEmpty()
                if (body.isEmpty()) return@mapNotNull null
                momentAuthors.add(author)
                val tLabel = post.strOrNull("timeLabel")?.trim().orEmpty()
                val header = if (tLabel.isNotEmpty()) "[$tLabel] $author" else author
                val comments = post.arrOrNull("commentItems").orEmpty().mapNotNull { c ->
                    val co = c as? JsonObject ?: return@mapNotNull null
                    val cAuthor = co.strOrNull("authorName") ?: co.strOrNull("author") ?: ""
                    val cBody = co.strOrNull("content") ?: co.strOrNull("text") ?: ""
                    if (cBody.isNotBlank()) "  └ $cAuthor：$cBody" else null
                }
                if (comments.isEmpty()) "$header：$body" else "$header：$body\n${comments.joinToString("\n")}"
            }
            if (lines.isNotEmpty()) {
                addLore(
                    idSuffix = "moments",
                    category = "concept",
                    title = "朋友圈动态（${lines.size} 条）",
                    content = lines.joinToString("\n\n").take(2400),
                    keywords = momentAuthors.filter { it != "好友" }.toList(),
                    priority = 50,
                )
            }
        }

        // 10e. Forum Posts (`forumPosts`) -> one keyword-triggered lorebook entry per thread
        gs.arrOrNull("forumPosts").orEmpty().mapNotNull { it as? JsonObject }.forEachIndexed { idx, fp ->
            val title = fp.strOrNull("title")?.trim().orEmpty()
            val author = fp.strOrNull("author")?.trim().orEmpty().ifBlank { "匿名" }
            val body = fp.strOrNull("content")?.trim().orEmpty()
            if (title.isEmpty() && body.isEmpty()) return@forEachIndexed
            val replies = fp.arrOrNull("replies").orEmpty().mapNotNull { r ->
                val ro = r as? JsonObject ?: return@mapNotNull null
                val rAuthor = ro.strOrNull("author")?.trim().orEmpty().ifBlank { "路人" }
                val rContent = ro.strOrNull("content")?.trim().orEmpty()
                if (rContent.isNotEmpty()) "• $rAuthor：$rContent" else null
            }
            val full = buildString {
                append("【社区热帖】$title\n发帖人：$author\n\n").append(body)
                if (replies.isNotEmpty()) {
                    append("\n\n【热评精选】\n").append(replies.take(8).joinToString("\n"))
                }
            }
            val mentionedNpcs = allNpcNames.filter { n -> title.contains(n) || full.contains(n) }
            addLore(
                idSuffix = "forum_$idx",
                category = "concept",
                title = "论坛 · ${title.ifBlank { "帖子 #${idx + 1}" }}",
                content = full.take(2200),
                keywords = (listOf(title.take(18)) + mentionedNpcs).distinct(),
                priority = 45,
            )
        }

        // 10f. Diary entries (`diaryEntries`)
        gs.arrOrNull("diaryEntries").orEmpty().mapNotNull { it as? JsonObject }.forEachIndexed { idx, d ->
            val body = d.strOrNull("content")?.trim() ?: d.strOrNull("text")?.trim().orEmpty()
            if (body.isEmpty()) return@forEachIndexed
            val dTitle = d.strOrNull("title")?.trim()?.takeIf { it.isNotEmpty() }
                ?: d.strOrNull("timeLabel")?.trim()?.takeIf { it.isNotEmpty() }
                ?: "日记 #${idx + 1}"
            val mentionedNpcs = allNpcNames.filter { n -> dTitle.contains(n) || body.contains(n) }
            addLore(
                idSuffix = "diary_$idx",
                category = "concept",
                title = "日记 · $dTitle",
                content = body.take(1800),
                keywords = (listOf(dTitle) + mentionedNpcs).distinct(),
                priority = 50,
            )
        }

        // 11. Build RpConfig, NovelChapters, and ChatConversation
        val rpState = RpGameState(
            scene = location.ifBlank { "当前场景" },
            time = timeLabel,
            gold = gold,
            inventory = inventory,
            quests = quests,
            flags = flags,
            stats = stats,
            chapterTitle = activeEventTitle.ifBlank { timeLabel },
            actNumber = round.coerceAtLeast(1),
        )

        val rpConfig = RpConfig(
            title = convTitle,
            worldview = worldview,
            systemRules = systemRules,
            playerName = playerName,
            playerPersona = playerPersona,
            cast = cast,
            state = rpState,
            greeting = messages.firstOrNull { it.role == "assistant" }?.content.orEmpty(),
            pendingChoices = pendingChoices,
            cardSource = "uu_game_save",
            playStyle = RpPlayStyle.chengguang,
            theaterMode = false,
            quickActions = quickActions,
        )

        // Stage-0 Cold-Start: for huge saves (>12 turns or >40K est tokens), set activeWindowStartSeq
        // so the conversation opens cleanly inside ~55% of the default context window with zero API cost.
        val totalEstMsgTokens = messages.sumOf { it.tokenEstimate }
        val stage0WindowStartSeq: Int? = if (messages.size > STAGE0_HUGE_TURN_THRESHOLD || totalEstMsgTokens > STAGE0_HUGE_TOKEN_THRESHOLD) {
            val prefixEst = TokenEstimator.estimateText(worldview) +
                TokenEstimator.estimateText(systemRules) +
                TokenEstimator.estimateText(playerPersona) +
                cast.size * 60 +
                minOf(8_000, memoryEpisodes.sumOf { it.estimatedTokens }) +
                rpConfig.loreTokenBudget +
                1_200
            val targetTotal = (STAGE0_DEFAULT_EFFECTIVE_LIMIT * STAGE0_TARGET_WINDOW_RATIO).toInt()
            val historyBudget = (targetTotal - prefixEst).coerceIn(8_000, 50_000)
            var keptTokens = 0
            var keptCount = 0
            var startSeq = messages.last().seq
            val maxInitialTurns = if (totalEstMsgTokens > STAGE0_HUGE_TOKEN_THRESHOLD) messages.size else STAGE0_HUGE_TURN_THRESHOLD
            for (i in messages.indices.reversed()) {
                val msg = messages[i]
                if (keptCount >= maxInitialTurns) break
                if (keptCount >= 2 && keptTokens + msg.tokenEstimate > historyBudget) break
                keptTokens += msg.tokenEstimate
                keptCount++
                startSeq = msg.seq
            }
            startSeq.takeIf { it > messages.first().seq }
        } else {
            null
        }

        val createdAt = messages.firstOrNull()?.createdAt ?: exportedAt
        val updatedAt = maxOf(exportedAt, messages.maxOfOrNull { it.createdAt } ?: exportedAt)
        val conversation = ChatConversation(
            id = convId,
            title = convTitle,
            mode = ConversationMode.rp,
            params = ChatParams(activeWindowStartSeq = stage0WindowStartSeq),
            rp = rpConfig,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

        val novelChapters = chapterKeyOrder.mapIndexed { idx, key ->
            NovelChapter(
                id = "${convId}_ch_${idx + 1}",
                conversationId = convId,
                order = idx + 1,
                title = chapterTitles[key] ?: "第 ${idx + 1} 章",
                startSeq = chapterFirstSeq[key] ?: 1,
                wordCount = chapterWordCounts[key] ?: 0,
                status = if (idx == chapterKeyOrder.lastIndex) "writing" else "finished",
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }

        if (messages.isEmpty() && cast.isEmpty() && loreEntries.isEmpty()) {
            throw IllegalArgumentException("UU 存档中未解析到有效剧情消息或角色数据")
        }

        return ExternalImportBundle(
            source = ExternalImportSource.UU_GAME,
            sourceFormat = "uu_game_save_v${root.intOrNull("version") ?: 1}",
            items = listOf(
                ExternalImportedItem(
                    source = ExternalImportSource.UU_GAME,
                    sourceId = baseId,
                    conversation = conversation,
                    messages = messages,
                    loreEntries = loreEntries,
                    novelChapters = novelChapters,
                    memoryLedger = memoryLedger,
                    memoryEpisodes = memoryEpisodes,
                    hasReasoning = false,
                ),
            ),
        )
    }

    /**
     * Distills a large `uu-game-save` JSON (often 1MB+ due to raw location IDs and per-turn state snapshots)
     * into a clean, compact Markdown briefing when attached directly as a chat document.
     */
    fun formatAsAttachmentMarkdown(rawJson: String, fileName: String? = null): String? {
        val cleaned = rawJson.removePrefix("\uFEFF").trim()
        if (!cleaned.startsWith("{")) return null
        val root = runCatching { AppJson.parseToJsonElement(cleaned) as? JsonObject }.getOrNull() ?: return null
        if (!isUuGameSave(root)) return null
        val item = runCatching { parseJsonElement(root, fileName).items.firstOrNull() }.getOrNull() ?: return null
        val rp = item.conversation.rp ?: return null

        return buildString {
            appendLine("# UU 互动文游存档：${item.conversation.title}")
            appendLine("- 主角：${rp.playerName}")
            if (rp.state.time.isNotBlank() || rp.state.scene.isNotBlank()) {
                appendLine("- 当前进度：第 ${rp.state.actNumber} 回合 · ${rp.state.time} · 地点：${rp.state.scene}")
            }
            if (rp.state.gold != 0 || rp.state.stats.isNotEmpty()) {
                val statStr = rp.state.stats.entries.joinToString(" / ") { "${it.key}:${it.value}" }
                appendLine("- 数值状态：现金 ${rp.state.gold}${if (statStr.isNotEmpty()) " | $statStr" else ""}")
            }
            if (rp.state.quests.isNotEmpty()) {
                appendLine("- 进行中事件：${rp.state.quests.joinToString("；")}")
            }
            if (rp.state.inventory.isNotEmpty()) {
                appendLine("- 持有物品：${rp.state.inventory.joinToString("、")}")
            }
            if (rp.playerPersona.isNotBlank()) {
                appendLine("\n## 主角人设")
                appendLine(rp.playerPersona)
            }
            if (rp.worldview.isNotBlank()) {
                appendLine("\n## 当前局势摘要")
                appendLine(rp.worldview)
            }
            if (rp.cast.isNotEmpty()) {
                appendLine("\n## 核心人物关系（${rp.cast.size} 位）")
                rp.cast.forEach { npc ->
                    val meta = buildList {
                        if (npc.relationship.isNotBlank()) add(npc.relationship)
                        add("好感 ${npc.affinity}")
                        if (npc.status.isNotBlank()) add(npc.status)
                        if (npc.inScene) add("当前在场")
                    }.joinToString(" · ")
                    appendLine("### ${npc.name}（$meta）")
                    if (npc.appearance.isNotBlank()) appendLine("- 外貌性格：${npc.appearance}")
                    if (npc.background.isNotBlank()) appendLine("- 背景：${npc.background}")
                    if (npc.secret.isNotBlank()) appendLine("- 内心想法：${npc.secret}")
                }
            }
            if (item.memoryEpisodes.isNotEmpty()) {
                appendLine("\n## 阶段回顾与编年史")
                item.memoryEpisodes.forEach { ep ->
                    appendLine("### ${ep.title}")
                    appendLine(ep.summary)
                }
            }
            if (item.memoryLedger.isNotEmpty()) {
                appendLine("\n## 关键剧情事实（${item.memoryLedger.size} 条）")
                item.memoryLedger.forEach { fact ->
                    appendLine("- ${fact.title}：${fact.content}")
                }
            }
            val socialLore = item.loreEntries.filter { it.category == "concept" }
            if (socialLore.isNotEmpty()) {
                appendLine("\n## 社交与世界动态（私聊 / 朋友圈 / 论坛）")
                socialLore.forEach { l ->
                    appendLine("### ${l.title}")
                    appendLine(l.content)
                }
            }
            if (item.messages.isNotEmpty()) {
                appendLine("\n## 完整剧情对话（${item.messages.size} 条）")
                item.messages.forEachIndexed { idx, msg ->
                    val speaker = if (msg.role == "user") rp.playerName else "旁白/剧情"
                    appendLine("### #${idx + 1} $speaker")
                    appendLine(msg.content)
                }
            }
            if (rp.pendingChoices.isNotEmpty()) {
                appendLine("\n## 当前可选分支")
                rp.pendingChoices.forEachIndexed { idx, ch ->
                    appendLine("${idx + 1}. ${ch.actionText}")
                }
            }
        }.trim()
    }

    private fun parseUuChoices(arr: JsonArray?, idPrefix: String): List<RpChoice> {
        if (arr == null || arr.isEmpty()) return emptyList()
        return arr.mapIndexedNotNull { idx, elem ->
            val obj = elem as? JsonObject ?: return@mapIndexedNotNull null
            val text = obj.strOrNull("text")?.trim()
                ?: obj.strOrNull("label")?.trim()
                ?: return@mapIndexedNotNull null
            if (text.isEmpty()) return@mapIndexedNotNull null
            val cid = obj.strOrNull("id")?.trim()?.takeIf { it.isNotEmpty() } ?: "${idPrefix}_c$idx"
            val style = obj.strOrNull("style")?.trim()?.takeIf { it.isNotEmpty() }
            val staminaCost = obj.intOrNull("staminaCost")
            RpChoice(
                id = cid,
                label = shortenChoiceLabel(text),
                actionText = text,
                inputKind = RpInputKind.action,
                tone = style,
                conditionText = staminaCost?.let { "行动 -$it" },
            )
        }
    }

    private fun shortenChoiceLabel(text: String): String {
        val clean = text.trim()
        if (clean.length <= 24) return clean
        val commaBreak = clean.indexOfFirst { it == '，' || it == '。' || it == '；' }
        if (commaBreak in 6..24) return clean.substring(0, commaBreak)
        return clean.take(22) + "…"
    }

    private fun isPlaceholderAttrKey(key: String): Boolean {
        val low = key.trim().lowercase()
        return PLACEHOLDER_PREFIXES.any { low.startsWith(it) }
    }

    private fun primitiveDisplayValue(elem: JsonElement?): String {
        val prim = elem as? JsonPrimitive ?: return ""
        if (prim is JsonNull) return ""
        return prim.content.trim()
    }

    private fun inferGenderFromNpcAttrs(npcAttrs: JsonObject?): String? {
        if (npcAttrs == null) return null
        for (v in npcAttrs.values) {
            val s = primitiveDisplayValue(v)
            when (s) {
                "男", "男性", "male", "Male" -> return "male"
                "女", "女性", "female", "Female" -> return "female"
            }
        }
        return null
    }

    private fun parseTimestamp(elem: JsonElement?): Long? {
        val prim = elem as? JsonPrimitive ?: return null
        if (prim is JsonNull) return null
        if (prim.isString) {
            val s = prim.content.trim()
            if (s.isEmpty()) return null
            s.toLongOrNull()?.let { return if (it < 10_000_000_000L) it * 1000L else it }
            runCatching { return OffsetDateTime.parse(s).toInstant().toEpochMilli() }
            runCatching { return Instant.parse(s).toEpochMilli() }
            runCatching {
                return LocalDateTime.parse(s.replace(' ', 'T'), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }
            return null
        }
        prim.longOrNull?.let { return if (it < 10_000_000_000L) it * 1000L else it }
        return null
    }
}
