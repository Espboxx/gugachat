package com.gugachat.app.domain.chat

import java.io.File
import java.io.InputStream
import java.text.Normalizer
import java.util.Locale

/**
 * Encoded BERT input tensors for a single sequence or overlapping window chunk.
 * Uses dynamic sequence length (`contentTokenCount + 2`, bounded by `maxSeqLen`)
 * so short queries avoid quadratic self-attention overhead over padding tokens.
 */
data class EncodedChunk(
    val inputIds: LongArray,
    val attentionMask: LongArray,
    val tokenTypeIds: LongArray,
    val contentTokenCount: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncodedChunk) return false
        return contentTokenCount == other.contentTokenCount &&
            inputIds.contentEquals(other.inputIds) &&
            attentionMask.contentEquals(other.attentionMask) &&
            tokenTypeIds.contentEquals(other.tokenTypeIds)
    }

    override fun hashCode(): Int {
        var result = inputIds.contentHashCode()
        result = 31 * result + attentionMask.contentHashCode()
        result = 31 * result + tokenTypeIds.contentHashCode()
        result = 31 * result + contentTokenCount
        return result
    }
}

/**
 * Pure-Kotlin BERT WordPiece tokenizer optimized for Chinese/English/CJK retrieval models
 * (`thenlper/gte-small-zh`, `BAAI/bge-small-zh-v1.5`, standard `bert-base-chinese` vocabularies).
 *
 * Key optimizations:
 * - Zero regex allocation in the hot path (single-pass code-point scanning).
 * - Full CJK pre-tokenization: NFKC normalization, lowercase, accent stripping on Latin scripts
 *   (preserving Japanese Kana dakuten & Korean Hangul), control-char removal, standalone Han/CJK
 *   and punctuation isolation, and greedy longest-match `##subword` WordPiece splitting.
 * - Overlapping chunk encoding (`encodeChunked`) so 800-1,400 token worldbook entries and RP
 *   episodes preserve tail clues instead of silently truncating at token 512.
 */
class BertWordPieceTokenizer private constructor(
    private val vocab: HashMap<String, Int>,
    val padId: Int,
    val unkId: Int,
    val clsId: Int,
    val sepId: Int,
    private val maxInputCharsPerWord: Int = DEFAULT_MAX_INPUT_CHARS_PER_WORD,
) {
    val vocabSize: Int
        get() = vocab.size

    fun tokenToId(token: String): Int = vocab[token] ?: unkId

    /**
     * Tokenizes [text] into raw content WordPiece token IDs (excluding `[CLS]` and `[SEP]`).
     * Stops early once [maxContentTokens] IDs are collected when [maxContentTokens] > 0.
     */
    fun tokenizeToIds(text: String, maxContentTokens: Int = Int.MAX_VALUE): IntArray {
        if (text.isEmpty() || maxContentTokens <= 0) return IntArray(0)

        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val out = ArrayList<Int>(minOf(normalized.length, maxContentTokens.coerceAtMost(1024)))
        val wordBuf = StringBuilder(32)
        val charBuf = StringBuilder(2)

        fun flushWord() {
            if (wordBuf.isEmpty()) return
            if (out.size < maxContentTokens) {
                appendWordPieceIds(wordBuf.toString(), out, maxContentTokens)
            }
            wordBuf.setLength(0)
        }

        var i = 0
        val len = normalized.length
        while (i < len && out.size < maxContentTokens) {
            val cp = normalized.codePointAt(i)
            i += Character.charCount(cp)

            if (isControlOrInvalid(cp)) {
                continue
            }
            if (isWhitespace(cp)) {
                flushWord()
                continue
            }
            if (isChineseChar(cp) || isPunctuation(cp)) {
                flushWord()
                if (out.size >= maxContentTokens) break
                charBuf.setLength(0)
                charBuf.appendCodePoint(cp)
                val token = charBuf.toString()
                out.add(vocab[token] ?: vocab[token.lowercase(Locale.ROOT)] ?: unkId)
                continue
            }

            appendNormalizedCodePoint(cp, wordBuf)
        }
        flushWord()

        val result = IntArray(out.size)
        for (idx in out.indices) {
            result[idx] = out[idx]
        }
        return result
    }

    /**
     * Fast-path single-sequence encoding for short queries and snippets (<1ms, zero regex churn).
     */
    fun encodeSingle(text: String, maxSeqLen: Int = DEFAULT_MAX_SEQ_LEN): EncodedChunk {
        val safeMaxSeq = maxSeqLen.coerceAtLeast(2)
        val maxContent = safeMaxSeq - 2
        val contentIds = tokenizeToIds(text, maxContentTokens = maxContent)
        return buildChunk(contentIds, start = 0, end = contentIds.size)
    }

    /**
     * Encodes arbitrarily long text into up to [maxChunks] overlapping windows of up to
     * [maxSeqLen] tokens (including `[CLS]` and `[SEP]`) with [stride] overlapping content tokens
     * between adjacent windows.
     */
    fun encodeChunked(
        text: String,
        maxSeqLen: Int = DEFAULT_MAX_SEQ_LEN,
        stride: Int = DEFAULT_CHUNK_STRIDE,
        maxChunks: Int = DEFAULT_MAX_CHUNKS,
    ): List<EncodedChunk> {
        val safeMaxSeq = maxSeqLen.coerceAtLeast(2)
        val windowSize = safeMaxSeq - 2
        val safeMaxChunks = maxChunks.coerceAtLeast(1)
        val safeStride = if (windowSize <= 1) 0 else stride.coerceIn(0, windowSize - 1)
        val step = (windowSize - safeStride).coerceAtLeast(1)

        val maxNeededTokens = if (windowSize == 0) {
            0
        } else {
            val extra = (safeMaxChunks - 1).toLong() * step.toLong()
            (windowSize.toLong() + extra).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        val contentIds = tokenizeToIds(text, maxContentTokens = maxNeededTokens)

        if (contentIds.isEmpty() || windowSize == 0) {
            return listOf(buildChunk(IntArray(0), start = 0, end = 0))
        }
        if (contentIds.size <= windowSize || safeMaxChunks == 1) {
            return listOf(buildChunk(contentIds, start = 0, end = minOf(contentIds.size, windowSize)))
        }

        val chunks = ArrayList<EncodedChunk>(safeMaxChunks)
        var start = 0
        while (start < contentIds.size && chunks.size < safeMaxChunks) {
            val end = minOf(start + windowSize, contentIds.size)
            chunks.add(buildChunk(contentIds, start = start, end = end))
            if (end >= contentIds.size) break
            start += step
        }
        return chunks
    }

    private fun buildChunk(contentIds: IntArray, start: Int, end: Int): EncodedChunk {
        val count = (end - start).coerceAtLeast(0)
        val seqLen = count + 2
        val inputIds = LongArray(seqLen)
        val attentionMask = LongArray(seqLen) { 1L }
        val tokenTypeIds = LongArray(seqLen)
        inputIds[0] = clsId.toLong()
        for (i in 0 until count) {
            inputIds[i + 1] = contentIds[start + i].toLong()
        }
        inputIds[seqLen - 1] = sepId.toLong()
        return EncodedChunk(
            inputIds = inputIds,
            attentionMask = attentionMask,
            tokenTypeIds = tokenTypeIds,
            contentTokenCount = count,
        )
    }

    private fun appendWordPieceIds(word: String, out: MutableList<Int>, maxContentTokens: Int) {
        if (word.isEmpty() || out.size >= maxContentTokens) return
        if (word.length > maxInputCharsPerWord) {
            out.add(unkId)
            return
        }
        // Fast path: whole word is in vocabulary (covers most ASCII words & numbers in O(1))
        val directId = vocab[word]
        if (directId != null) {
            out.add(directId)
            return
        }

        val subIds = ArrayList<Int>(4)
        var start = 0
        val len = word.length
        var isBad = false
        val probe = StringBuilder(len + 2)

        while (start < len) {
            var end = len
            var foundId = -1
            while (start < end) {
                probe.setLength(0)
                if (start > 0) {
                    probe.append("##")
                }
                probe.append(word, start, end)
                val id = vocab[probe.toString()]
                if (id != null) {
                    foundId = id
                    break
                }
                end--
            }
            if (foundId == -1) {
                isBad = true
                break
            }
            subIds.add(foundId)
            start = end
        }

        if (isBad || subIds.isEmpty()) {
            out.add(unkId)
        } else {
            for (i in subIds.indices) {
                if (out.size >= maxContentTokens) break
                out.add(subIds[i])
            }
        }
    }

    private fun appendNormalizedCodePoint(cp: Int, dst: StringBuilder) {
        // Fast ASCII lowercase path (zero allocations)
        if (cp < 0x80) {
            val lower = if (cp in 'A'.code..'Z'.code) cp + 32 else cp
            dst.append(lower.toChar())
            return
        }

        val raw = String(Character.toChars(cp)).lowercase(Locale.ROOT)
        // Strip combining accents (e.g. café -> cafe, naïve -> naive) on non-CJK / non-Kana / non-Hangul scripts
        if (shouldStripAccents(cp)) {
            val decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD)
            var idx = 0
            while (idx < decomposed.length) {
                val dcp = decomposed.codePointAt(idx)
                idx += Character.charCount(dcp)
                if (Character.getType(dcp) != Character.NON_SPACING_MARK.toInt()) {
                    dst.appendCodePoint(dcp)
                }
            }
        } else {
            dst.append(raw)
        }
    }

    companion object {
        const val TOKEN_PAD = "[PAD]"
        const val TOKEN_UNK = "[UNK]"
        const val TOKEN_CLS = "[CLS]"
        const val TOKEN_SEP = "[SEP]"

        const val DEFAULT_MAX_SEQ_LEN = 512
        const val DEFAULT_CHUNK_STRIDE = 64
        const val DEFAULT_MAX_CHUNKS = 3
        const val DEFAULT_MAX_INPUT_CHARS_PER_WORD = 100

        fun fromFile(vocabFile: File): BertWordPieceTokenizer {
            require(vocabFile.isFile) { "Vocab file does not exist: ${vocabFile.absolutePath}" }
            return vocabFile.bufferedReader(Charsets.UTF_8).use { reader ->
                fromLines(reader.lineSequence())
            }
        }

        fun fromStream(inputStream: InputStream): BertWordPieceTokenizer =
            inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                fromLines(reader.lineSequence())
            }

        fun fromLines(lines: Sequence<String>): BertWordPieceTokenizer {
            val vocab = HashMap<String, Int>(24_576)
            var index = 0
            for (rawLine in lines) {
                val token = rawLine.trimEnd('\r')
                if (token.isNotEmpty() && !vocab.containsKey(token)) {
                    vocab[token] = index
                }
                index++
            }
            return fromVocabMap(vocab)
        }

        fun fromVocabMap(vocabMap: Map<String, Int>): BertWordPieceTokenizer {
            val map = if (vocabMap is HashMap<String, Int>) {
                HashMap(vocabMap)
            } else {
                HashMap<String, Int>(vocabMap.size).apply { putAll(vocabMap) }
            }
            val padId = map[TOKEN_PAD] ?: 0
            val unkId = map[TOKEN_UNK] ?: 100
            val clsId = map[TOKEN_CLS] ?: 101
            val sepId = map[TOKEN_SEP] ?: 102
            return BertWordPieceTokenizer(
                vocab = map,
                padId = padId,
                unkId = unkId,
                clsId = clsId,
                sepId = sepId,
            )
        }

        internal fun isWhitespace(cp: Int): Boolean {
            if (cp == ' '.code || cp == '\t'.code || cp == '\n'.code || cp == '\r'.code) return true
            return Character.getType(cp) == Character.SPACE_SEPARATOR.toInt()
        }

        internal fun isControlOrInvalid(cp: Int): Boolean {
            if (cp == 0 || cp == 0xFFFD) return true
            if (cp == '\t'.code || cp == '\n'.code || cp == '\r'.code) return false
            val type = Character.getType(cp)
            return type == Character.CONTROL.toInt() ||
                type == Character.FORMAT.toInt() ||
                type == Character.PRIVATE_USE.toInt() ||
                type == Character.SURROGATE.toInt()
        }

        internal fun isPunctuation(cp: Int): Boolean {
            if ((cp in 33..47) || (cp in 58..64) || (cp in 91..96) || (cp in 123..126)) {
                return true
            }
            return when (Character.getType(cp).toByte()) {
                Character.CONNECTOR_PUNCTUATION,
                Character.DASH_PUNCTUATION,
                Character.START_PUNCTUATION,
                Character.END_PUNCTUATION,
                Character.INITIAL_QUOTE_PUNCTUATION,
                Character.FINAL_QUOTE_PUNCTUATION,
                Character.OTHER_PUNCTUATION,
                -> true
                else -> false
            }
        }

        internal fun isChineseChar(cp: Int): Boolean =
            (cp in 0x4E00..0x9FFF) ||
                (cp in 0x3400..0x4DBF) ||
                (cp in 0x20000..0x2A6DF) ||
                (cp in 0x2A700..0x2B73F) ||
                (cp in 0x2B740..0x2B81F) ||
                (cp in 0x2B820..0x2CEAF) ||
                (cp in 0xF900..0xFAFF) ||
                (cp in 0x2F800..0x2FA1F)

        private fun shouldStripAccents(cp: Int): Boolean {
            // Preserve Japanese Hiragana/Katakana dakuten/handakuten and Korean Hangul syllables
            if (cp in 0x3040..0x30FF || cp in 0x31F0..0x31FF || cp in 0xAC00..0xD7AF) return false
            return true
        }
    }
}
