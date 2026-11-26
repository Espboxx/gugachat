package com.gugachat.app.domain.chat

import android.icu.text.BreakIterator
import android.icu.util.ULocale

/**
 * Pluggable CJK word segmenter abstraction so Android runtime can leverage native
 * `android.icu.text.BreakIterator` while pure-JVM unit tests fall back deterministically
 * to [PureKotlinClauseSegmenter].
 */
interface CjkWordSegmenter {
    fun segment(text: String): List<WeightedTerm>
}

/**
 * Pure-Kotlin stopword-as-delimiter clause segmenter with a legitimate-compound whitelist.
 *
 * - Protects legitimate multi-char compounds containing grammatical particles (e.g. `目的`, `的确`,
 *   `了解`, `存在`, `太古`, `但是`, `自己`, `世界`, `没有`, `所有`) before clause splitting.
 * - Splits Han runs on hard grammatical stopwords so boundary bigrams like `的何`/`半的`/`的顶`/`的林`
 *   are never generated.
 * - Emits single-char Han unigrams ONLY for standalone 1-char non-stopword clauses (never side-emits
 *   every char of multi-char runs).
 */
object PureKotlinClauseSegmenter : CjkWordSegmenter {

    internal val HARD_CJK_STOPWORDS: Set<Char> = setOf(
        '的', '了', '着', '过', '地', '得', '和', '与', '或', '把', '被', '给', '让',
        '从', '向', '往', '于', '而', '但', '却', '之', '以', '这', '那', '其', '我',
        '你', '他', '她', '它', '们', '是', '在', '有', '没', '不', '也', '都', '就',
        '还', '又', '很', '太', '吗', '呢', '吧', '啊', '哦', '呀', '嘛', '哇', '嗯',
        '哎', '个',
    )

    internal val EN_STOPWORDS: Set<String> = setOf(
        "the", "a", "an", "is", "are", "was", "were", "be", "been", "to", "of",
        "in", "on", "at", "for", "with", "and", "or", "but", "not", "this", "that",
        "it", "he", "she", "they", "them", "his", "her", "their", "you", "your",
    )

    /**
     * ~100 legitimate Chinese compounds containing chars from [HARD_CJK_STOPWORDS] that should stay
     * intact rather than being split on particle boundaries.
     */
    internal val COMPOUND_WHITELIST: Set<String> = setOf(
        // 的
        "目的", "的确", "标的", "的士", "目的地",
        // 了
        "了解", "了结", "了然", "了断", "明了",
        // 着
        "着落", "着手", "着迷", "着魔", "着火", "着急", "着陆", "执着", "沉着", "显著",
        // 过
        "过去", "过程", "经过", "超过", "错过", "通过", "过错", "过分", "过渡", "过滤", "难过", "不过",
        // 地
        "地方", "地点", "地图", "地位", "地区", "地球", "地道", "地下", "地面", "地址",
        "土地", "阵地", "领地", "场地", "基地", "境地", "天地", "墓地", "地下室",
        // 得
        "得到", "得以", "得意", "得罪", "得失", "值得", "获得", "取得", "心得", "记得", "觉得", "懂得",
        // 和 / 与 / 或
        "和平", "和谐", "温和", "缓和", "和解", "和尚", "柔和", "参与", "赋予", "给予", "或者", "或许",
        // 把 / 被 / 给 / 让
        "把握", "把手", "把柄", "火把", "被动", "被告", "被子", "供给", "补给", "退让", "礼让", "转让",
        // 从 / 向 / 往 / 于 / 而 / 但 / 却
        "从来", "从前", "从此", "随从", "服从", "方向", "意向", "倾向", "动向", "向往", "向导",
        "往事", "往常", "往往", "交往", "前往", "关于", "属于", "处于", "位于", "终于", "基于", "由于",
        "而且", "然而", "反而", "但是", "但愿", "退却", "忘却", "冷却",
        // 之 / 以 / 其 / 自 / 他
        "之后", "之前", "之间", "之中", "之外", "以后", "以前", "以上", "以下", "可以", "所以", "以为",
        "其他", "其实", "其中", "尤其", "自己", "自我", "他人", "他乡", "世界",
        // 是 / 在 / 有 / 没 / 不 / 个 / 都 / 就 / 还 / 太
        "存在", "现在", "所在", "所有", "没有", "有效", "有限", "有趣", "有利", "有害", "有毒",
        "拥有", "持有", "占有", "稀有", "唯有", "是非", "是否", "不断", "不同", "不安", "不满",
        "个人", "个性", "个体", "整个", "都市", "首都", "京都", "成就", "就业", "归还", "还原",
        "太阳", "太空", "太古", "太子", "太后", "太守", "太平", "太极",
    )

    /**
     * Pure discourse/function compounds in [COMPOUND_WHITELIST]: protected during splitting so their
     * stopword chars never glue onto adjacent content words, but downweighted and excluded from
     * display-layer `matchedTerms` and minimum-content-term gating.
     */
    internal val FUNCTION_COMPOUNDS: Set<String> = setOf(
        "但是", "不过", "而且", "然而", "反而", "或者", "或许", "所以", "可以", "以为",
        "其实", "其中", "其他", "尤其", "之后", "之前", "之间", "之中", "之外",
        "以后", "以前", "以上", "以下", "没有", "所有", "自己", "现在", "关于", "由于", "终于",
    )

    private const val MAX_COMPOUND_LEN = 3

    private val HAN_RUN_RE = Regex("\\p{IsHan}+")
    private val LATIN_WORD_RE = Regex("[a-z0-9][a-z0-9_-]*")

    fun isCleanContentTerm(term: String): Boolean {
        val clean = term.trim()
        if (clean.isEmpty()) return false
        if (clean in FUNCTION_COMPOUNDS) return false
        val first = clean[0]
        if (TokenEstimator.isHanChar(first)) {
            if (clean.length < 2) return false
            if (clean in COMPOUND_WHITELIST) return true
            for (ch in clean) {
                if (ch in HARD_CJK_STOPWORDS) return false
            }
            return true
        }
        val lower = clean.lowercase()
        return lower.length >= 2 && lower !in EN_STOPWORDS
    }

    override fun segment(text: String): List<WeightedTerm> {
        val norm = CjkSearch.normalizeForSearch(text)
        if (norm.isEmpty()) return emptyList()
        val result = ArrayList<WeightedTerm>()

        for (match in HAN_RUN_RE.findAll(norm)) {
            segmentHanRun(match.value, result)
        }

        for (match in LATIN_WORD_RE.findAll(norm)) {
            val w = match.value
            if (w.length >= 2 && w !in EN_STOPWORDS) {
                result.add(WeightedTerm(w, 1.0))
            }
        }
        return result
    }

    internal fun segmentHanRun(run: String, out: MutableList<WeightedTerm>) {
        val clause = StringBuilder()

        fun flushClause() {
            val len = clause.length
            if (len == 0) return
            if (len == 1) {
                out.add(WeightedTerm(clause.toString(), 0.65))
            } else {
                val s = clause.toString()
                for (i in 0 until len - 1) {
                    out.add(WeightedTerm(s.substring(i, i + 2), 1.0))
                }
            }
            clause.setLength(0)
        }

        var idx = 0
        val n = run.length
        while (idx < n) {
            val matchedCompound = matchCompoundAt(run, idx)
            if (matchedCompound != null) {
                flushClause()
                val weight = if (matchedCompound in FUNCTION_COMPOUNDS) 0.30 else 1.0
                out.add(WeightedTerm(matchedCompound, weight))
                idx += matchedCompound.length
                continue
            }

            val ch = run[idx]
            if (ch in HARD_CJK_STOPWORDS) {
                flushClause()
            } else {
                clause.append(ch)
            }
            idx++
        }
        flushClause()
    }

    private fun matchCompoundAt(run: String, start: Int): String? {
        val remaining = run.length - start
        val maxLen = minOf(MAX_COMPOUND_LEN, remaining)
        for (len in maxLen downTo 2) {
            val sub = run.substring(start, start + len)
            if (sub in COMPOUND_WHITELIST) return sub
        }
        return null
    }
}

/**
 * Android native ICU word segmenter backed by OS `libicu` (`BreakIterator.getWordInstance`)
 * with:
 * - `ThreadLocal` reuse of `BreakIterator` instances (thread-safe, avoids repeated `.brk` loads)
 * - Null-safe `runCatching` fallback to [PureKotlinClauseSegmenter] so `:app:testDebugUnitTest`
 *   with `isReturnDefaultValues = true` works deterministically without Robolectric
 * - Consecutive 1-char non-stopword OOV bridge so fictional RP character/sect/item names
 *   (e.g. `林|晚|棠`) emit sliding bigrams + OOV compound cleanly without cross-word noise (`层晚`).
 */
class AndroidIcuCjkSegmenter(
    private val fallback: CjkWordSegmenter = PureKotlinClauseSegmenter,
) : CjkWordSegmenter {

    private val latinWordRe = Regex("[a-z0-9][a-z0-9_-]*")

    private val iteratorLocal = object : ThreadLocal<BreakIterator?>() {
        override fun initialValue(): BreakIterator? = runCatching {
            val locale = ULocale.SIMPLIFIED_CHINESE ?: return null
            BreakIterator.getWordInstance(locale)
        }.getOrNull()
    }

    private val cacheLock = Any()
    private val tokenCache = object : LinkedHashMap<String, List<WeightedTerm>>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<WeightedTerm>>?): Boolean =
            size > MAX_CACHE_ENTRIES
    }

    override fun segment(text: String): List<WeightedTerm> {
        val norm = CjkSearch.normalizeForSearch(text)
        if (norm.isEmpty()) return emptyList()

        if (norm.length <= MAX_CACHED_TEXT_LEN) {
            synchronized(cacheLock) {
                tokenCache[norm]?.let { return it }
            }
        }

        val computed = segmentWithIcuOrFallback(norm)
        if (norm.length <= MAX_CACHED_TEXT_LEN) {
            synchronized(cacheLock) {
                tokenCache[norm] = computed
            }
        }
        return computed
    }

    private fun segmentWithIcuOrFallback(norm: String): List<WeightedTerm> {
        val iterator = iteratorLocal.get() ?: return fallback.segment(norm)
        val icuResult: List<WeightedTerm>? = runCatching<List<WeightedTerm>?> {
            iterator.setText(norm)
            var start = iterator.first()
            var end = iterator.next()
            // Guard against Android stub JAR (`isReturnDefaultValues = true`) returning 0
            if (end == BreakIterator.DONE || end <= start) return@runCatching null

            val out = ArrayList<WeightedTerm>()
            val oovSingleChars = StringBuilder()

            fun flushOovSingles() {
                val len = oovSingleChars.length
                if (len == 0) return
                if (len == 1) {
                    out.add(WeightedTerm(oovSingleChars.toString(), 0.65))
                } else {
                    val s = oovSingleChars.toString()
                    for (i in 0 until len - 1) {
                        out.add(WeightedTerm(s.substring(i, i + 2), 1.0))
                    }
                    if (len in 3..4) {
                        out.add(WeightedTerm(s, 1.0))
                    }
                }
                oovSingleChars.setLength(0)
            }

            var steps = 0
            val maxSteps = norm.length * 2 + 16
            while (end != BreakIterator.DONE && end > start && steps < maxSteps) {
                steps++
                val token = norm.substring(start, end)
                val first = token[0]
                if (TokenEstimator.isHanChar(first)) {
                    if (token.length == 1) {
                        if (first in PureKotlinClauseSegmenter.HARD_CJK_STOPWORDS) {
                            flushOovSingles()
                        } else {
                            oovSingleChars.append(first)
                        }
                    } else {
                        flushOovSingles()
                        if (token in PureKotlinClauseSegmenter.FUNCTION_COMPOUNDS) {
                            out.add(WeightedTerm(token, 0.30))
                        } else if (!hasDisallowedEdgeStopword(token)) {
                            out.add(WeightedTerm(token, 1.0))
                        } else {
                            PureKotlinClauseSegmenter.segmentHanRun(token, out)
                        }
                    }
                } else {
                    flushOovSingles()
                    if (first.isLetterOrDigit()) {
                        for (m in latinWordRe.findAll(token)) {
                            val w = m.value
                            if (w.length >= 2 && w !in PureKotlinClauseSegmenter.EN_STOPWORDS) {
                                out.add(WeightedTerm(w, 1.0))
                            }
                        }
                    }
                }
                start = end
                end = iterator.next()
            }
            flushOovSingles()
            out
        }.getOrNull()

        if (icuResult == null) return fallback.segment(norm)
        if (icuResult.isEmpty() && norm.any { TokenEstimator.isHanChar(it) || it.isLetterOrDigit() }) {
            return fallback.segment(norm)
        }
        return icuResult
    }

    private fun hasDisallowedEdgeStopword(token: String): Boolean {
        if (token in PureKotlinClauseSegmenter.COMPOUND_WHITELIST) return false
        val first = token.first()
        val last = token.last()
        return first in EDGE_PARTICLE_STOPWORDS || last in EDGE_PARTICLE_STOPWORDS
    }

    companion object {
        private const val MAX_CACHE_ENTRIES = 512
        private const val MAX_CACHED_TEXT_LEN = 2500
        private val EDGE_PARTICLE_STOPWORDS = setOf('的', '了', '着', '过', '吗', '呢', '吧', '啊', '哦', '呀')
    }
}
