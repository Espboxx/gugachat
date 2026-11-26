package com.gugachat.app.domain.chat

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

class BertWordPieceTokenizerTest {

    private fun buildSampleTokenizer(): BertWordPieceTokenizer {
        val tokens = listOf(
            "[PAD]",      // 0
            "[UNK]",      // 1
            "[CLS]",      // 2
            "[SEP]",      // 3
            "她",          // 4
            "悄",          // 5
            "把",          // 6
            "银",          // 7
            "针",          // 8
            "藏",          // 9
            "进",          // 10
            "袖",          // 11
            "口",          // 12
            "青",          // 13
            "云",          // 14
            "剑",          // 15
            "宗",          // 16
            "你",          // 17
            "好",          // 18
            ",",          // 19
            "!",          // 20
            "。",          // 21
            "-",          // 22
            "play",       // 23
            "##ing",      // 24
            "un",         // 25
            "##believ",   // 26
            "##able",     // 27
            "ai",         // 28
            "2026",       // 29
            "cafe",       // 30
            "naive",      // 31
            "abc",        // 32
            "##123",      // 33
            "が",          // 34
            "ぎ",          // 35
            "한",          // 36
            "글",          // 37
            "伏",          // 38
            "笔",          // 39
        )
        return BertWordPieceTokenizer.fromLines(tokens.asSequence())
    }

    @Test
    fun cjkPreTokenization_isolatesHanziAndPunctuationAroundLatinAndNumbers() {
        val tokenizer = buildSampleTokenizer()
        val chunk = tokenizer.encodeSingle("她悄悄把银针藏进袖口AI-2026。")

        val expectedIds = longArrayOf(
            2L, // [CLS]
            4L, // 她
            5L, // 悄
            5L, // 悄
            6L, // 把
            7L, // 银
            8L, // 针
            9L, // 藏
            10L, // 进
            11L, // 袖
            12L, // 口
            28L, // ai
            22L, // -
            29L, // 2026
            21L, // 。
            3L, // [SEP]
        )
        assertArrayEquals(expectedIds, chunk.inputIds)
        assertEquals(expectedIds.size - 2, chunk.contentTokenCount)
        assertArrayEquals(LongArray(expectedIds.size) { 1L }, chunk.attentionMask)
        assertArrayEquals(LongArray(expectedIds.size) { 0L }, chunk.tokenTypeIds)
    }

    @Test
    fun englishWordPiece_greedyLongestMatchSubwordsAndUnknownFallback() {
        val tokenizer = buildSampleTokenizer()

        val ids = tokenizer.tokenizeToIds("Playing UNBELIEVABLE xyzzy")
        assertArrayEquals(
            intArrayOf(
                23, // play
                24, // ##ing
                25, // un
                26, // ##believ
                27, // ##able
                1,  // [UNK]
            ),
            ids,
        )

        // Words exceeding 100 chars collapse to [UNK]
        val overlong = "a".repeat(105)
        assertArrayEquals(intArrayOf(1), tokenizer.tokenizeToIds(overlong))
    }

    @Test
    fun normalization_handlesNfkcFullwidthAccentsControlCharsAndPreservesKanaHangul() {
        val tokenizer = buildSampleTokenizer()

        // NFKC fullwidth Latin/digits/punctuation + Latin accents + control chars (\u0000, \u200B)
        val ids = tokenizer.tokenizeToIds("ＡＢＣ１２３，\u0000Café\u200B NAÏVE！你好")
        assertArrayEquals(
            intArrayOf(
                32, // abc
                33, // ##123
                19, // , (NFKC from ，)
                30, // cafe
                31, // naive
                20, // ! (NFKC from ！)
                17, // 你
                18, // 好
            ),
            ids,
        )

        // Japanese dakuten and Korean Hangul are NOT stripped by NFD accent removal
        val kanaHangul = tokenizer.tokenizeToIds("が ぎ 한 글")
        assertArrayEquals(intArrayOf(34, 35, 36, 37), kanaHangul)
    }

    @Test
    fun emptyAndWhitespaceText_returnsClsSepChunkWithZeroContent() {
        val tokenizer = buildSampleTokenizer()

        val single = tokenizer.encodeSingle("   \t\n  ")
        assertArrayEquals(longArrayOf(2L, 3L), single.inputIds)
        assertEquals(0, single.contentTokenCount)

        val chunks = tokenizer.encodeChunked("")
        assertEquals(1, chunks.size)
        assertArrayEquals(longArrayOf(2L, 3L), chunks[0].inputIds)
        assertEquals(0, chunks[0].contentTokenCount)
    }

    @Test
    fun encodeSingle_truncatesAtMaxSeqLenWithoutPaddingShortInputs() {
        val tokenizer = buildSampleTokenizer()

        val shortChunk = tokenizer.encodeSingle("青云剑宗", maxSeqLen = 512)
        assertEquals(6, shortChunk.inputIds.size) // [CLS] + 4 + [SEP], no wasteful padding to 512
        assertEquals(4, shortChunk.contentTokenCount)

        val truncated = tokenizer.encodeSingle("青云剑宗青云剑宗", maxSeqLen = 6)
        assertEquals(6, truncated.inputIds.size)
        assertEquals(4, truncated.contentTokenCount)
        assertArrayEquals(longArrayOf(2L, 13L, 14L, 15L, 16L, 3L), truncated.inputIds)
    }

    @Test
    fun encodeChunked_preservesLongWorldbookTailWithOverlappingStride() {
        val tokenizer = buildSampleTokenizer()

        // 1,350-token Chinese document with distinct tail clue ("伏笔") at the very end
        val body = "青云剑宗".repeat(337) // 1,348 Hanzi tokens
        val longDoc = body + "伏笔"     // 1,350 Hanzi tokens (> 512 single-chunk limit!)

        val chunks = tokenizer.encodeChunked(
            text = longDoc,
            maxSeqLen = 512,
            stride = 64,
            maxChunks = 3,
        )

        // Chunk 0: [0..510), Chunk 1: [446..956), Chunk 2: [892..1350) -> 458 content tokens
        assertEquals(3, chunks.size)
        assertEquals(510, chunks[0].contentTokenCount)
        assertEquals(510, chunks[1].contentTokenCount)
        assertEquals(458, chunks[2].contentTokenCount)

        // Every chunk is bounded by 512 tokens and wrapped in [CLS]..[SEP]
        for (c in chunks) {
            assertTrue(c.inputIds.size <= 512)
            assertEquals(2L, c.inputIds.first())
            assertEquals(3L, c.inputIds.last())
            assertEquals(c.contentTokenCount + 2, c.inputIds.size)
        }

        // Exact 64-token overlap between chunk 0 tail and chunk 1 head
        val c0Tail = chunks[0].inputIds.sliceArray((510 - 64 + 1)..510)
        val c1Head = chunks[1].inputIds.sliceArray(1..64)
        assertArrayEquals(c0Tail, c1Head)

        // Exact 64-token overlap between chunk 1 tail and chunk 2 head
        val c1Tail = chunks[1].inputIds.sliceArray((510 - 64 + 1)..510)
        val c2Head = chunks[2].inputIds.sliceArray(1..64)
        assertArrayEquals(c1Tail, c2Head)

        // Tail clue "伏笔" (38, 39) is preserved at the end of chunk 2!
        val c2Ids = chunks[2].inputIds
        assertEquals(38L, c2Ids[c2Ids.size - 3])
        assertEquals(39L, c2Ids[c2Ids.size - 2])
    }

    @Test
    fun encodeChunked_smallWindowBoundariesAndMaxChunksCap() {
        val tokenizer = buildSampleTokenizer()
        // 16 tokens: maxSeqLen=10 -> window=8, stride=2 -> step=6
        // Chunk 0: [0..8), Chunk 1: [6..14), Chunk 2: [12..16) (4 tokens)
        val text = "青云剑宗".repeat(4)
        val chunks = tokenizer.encodeChunked(text, maxSeqLen = 10, stride = 2, maxChunks = 3)
        assertEquals(3, chunks.size)
        assertEquals(8, chunks[0].contentTokenCount)
        assertEquals(8, chunks[1].contentTokenCount)
        assertEquals(4, chunks[2].contentTokenCount)

        // Capped at maxChunks=2 on longer input
        val capped = tokenizer.encodeChunked("青云剑宗".repeat(10), maxSeqLen = 10, stride = 2, maxChunks = 2)
        assertEquals(2, capped.size)
    }

    @Test
    fun fromFileAndFromStream_parseCrLfVocabTxtAccurately() {
        val vocabContent = "[PAD]\r\n[UNK]\r\n[CLS]\r\n[SEP]\r\n青\r\n云\r\n"
        val tempDir = Files.createTempDirectory("bert_vocab_test_").toFile()
        try {
            val vocabFile = File(tempDir, "vocab.txt")
            vocabFile.writeText(vocabContent, Charsets.UTF_8)

            val fromFile = BertWordPieceTokenizer.fromFile(vocabFile)
            val fromStream = BertWordPieceTokenizer.fromStream(ByteArrayInputStream(vocabContent.toByteArray(Charsets.UTF_8)))

            assertEquals(6, fromFile.vocabSize)
            assertEquals(6, fromStream.vocabSize)
            assertArrayEquals(fromFile.encodeSingle("青云").inputIds, fromStream.encodeSingle("青云").inputIds)
            assertArrayEquals(longArrayOf(2L, 4L, 5L, 3L), fromFile.encodeSingle("青云").inputIds)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
