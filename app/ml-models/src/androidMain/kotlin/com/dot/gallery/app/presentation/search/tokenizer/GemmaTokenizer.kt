/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.search.tokenizer

import java.io.File
import java.io.InputStreamReader

/**
 * Minimal on-device port of the EmbeddingGemma / Gemma `tokenizer.json` (HF `tokenizers`
 * BPE model). The pipeline mirrors the upstream file exactly:
 *   normalizer  – Replace " " → the U+2581 word marker
 *   model       – BPE over Unicode characters with byte-fallback: chars missing from the
 *                 vocab are UTF-8 encoded and each byte maps to its `<0xNN>` piece
 *   post        – TemplateProcessing wraps the sequence as `<bos> … <eos>`
 * Added/special tokens (e.g. the media placeholder tokens) are matched greedily in the raw
 * text before BPE runs, mirroring `added_vocabulary` splitting.
 *
 * Merge tables are kept in sorted primitive arrays + binary search instead of boxed maps:
 * ~515k merges would otherwise cost ~100 MB of heap on-device.
 */
class GemmaTokenizer(tokenizerFile: File) {

    private class Tables(
        val vocab: Map<String, Int>,
        val mergeKeys: LongArray,      // sorted: first*VOCAB_STRIDE + second
        val mergeRanks: IntArray,      // parallel to mergeKeys
        val mergeResults: IntArray,    // parallel to mergeKeys: id of merged piece
        val addedTokens: Map<String, Int>,
    )

    private val tables: Tables = parse(tokenizerFile)

    /** Encode [text] into token ids including the `<bos>`/`<eos>` wrapper. */
    fun encode(text: String): IntArray {
        val pieces = mutableListOf<Int>()
        for (segment in splitAddedTokens(text)) {
            val (literal, isAdded) = segment
            if (isAdded) {
                pieces += tables.addedTokens.getValue(literal)
            } else if (literal.isNotEmpty()) {
                val normalized = literal.replace(' ', WORD_MARKER)
                bpeInto(normalized, pieces)
            }
        }
        val out = IntArray(pieces.size + 2)
        out[0] = BOS_ID
        pieces.forEachIndexed { i, id -> out[i + 1] = id }
        out[out.lastIndex] = EOS_ID
        return out
    }

    /**
     * Greedy longest-first scan for the added special tokens. Returns (literal, isAdded)
     * segments covering the whole input.
     */
    private fun splitAddedTokens(text: String): List<Pair<String, Boolean>> {
        if (addedTokenLiterals.isEmpty()) return listOf(text to false)
        val segments = mutableListOf<Pair<String, Boolean>>()
        var i = 0
        var plainStart = 0
        while (i < text.length) {
            var matched: String? = null
            for (token in addedTokenLiterals) {
                if (text.startsWith(token, i)) {
                    matched = token
                    break
                }
            }
            if (matched != null) {
                if (i > plainStart) segments += text.substring(plainStart, i) to false
                segments += matched to true
                i += matched.length
                plainStart = i
            } else {
                i++
            }
        }
        if (plainStart < text.length) segments += text.substring(plainStart) to false
        return segments
    }

    private val addedTokenLiterals: List<String> by lazy {
        tables.addedTokens.keys.sortedByDescending { it.length }
    }

    private fun bpeInto(text: String, out: MutableList<Int>) {
        // Initial units: one vocab id per Unicode code point (surrogate pairs must be
        // joined before UTF-8 encoding — a lone surrogate would byte-fallback to 0x3F).
        // Absent code points fall back to their UTF-8 bytes (up to 4 each).
        val ids = IntArray(text.length * 4)
        var count = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            val piece = String(Character.toChars(cp))
            val direct = tables.vocab[piece]
            if (direct != null) {
                ids[count++] = direct
            } else {
                for (byte in piece.toByteArray(Charsets.UTF_8)) {
                    ids[count++] = byteToken(byte)
                }
            }
        }
        var length = count
        // Merge loop: repeatedly apply the lowest-rank adjacent pair merge.
        while (length > 1) {
            var bestRank = Int.MAX_VALUE
            var bestIndex = -1
            var bestResult = -1
            for (i in 0 until length - 1) {
                val key = pairKey(ids[i], ids[i + 1])
                val pos = tables.mergeKeys.binarySearch(key)
                if (pos >= 0 && tables.mergeRanks[pos] < bestRank) {
                    bestRank = tables.mergeRanks[pos]
                    bestIndex = i
                    bestResult = tables.mergeResults[pos]
                }
            }
            if (bestIndex < 0) break
            ids[bestIndex] = bestResult
            System.arraycopy(ids, bestIndex + 2, ids, bestIndex + 1, length - bestIndex - 2)
            length--
        }
        for (i in 0 until length) out += ids[i]
    }

    private fun byteToken(byte: Byte): Int {
        val literal = "<0x%02X>".format(byte.toInt() and 0xFF)
        return tables.vocab[literal] ?: error("tokenizer.json missing byte piece $literal")
    }

    private fun LongArray.binarySearch(key: Long): Int {
        var lo = 0
        var hi = size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            when {
                this[mid] < key -> lo = mid + 1
                this[mid] > key -> hi = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    private fun pairKey(first: Int, second: Int): Long =
        first.toLong() * VOCAB_STRIDE + second.toLong()

    companion object {
        const val BOS_ID = 2
        const val EOS_ID = 1
        private const val WORD_MARKER = '▁'
        // Vocab has 262144 entries; keys use a stride big enough to hold the max id.
        private const val VOCAB_STRIDE = 1_000_000L

        private fun parse(file: File): Tables {
            file.inputStream().use { stream ->
                SimpleJsonReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    var vocab: Map<String, Int>? = null
                    var mergeKeys: LongArray? = null
                    var mergeRanks: IntArray? = null
                    var mergeResults: IntArray? = null
                    var addedTokens: Map<String, Int> = emptyMap()
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "model" -> {
                                val parsed = parseModel(reader)
                                vocab = parsed.first
                                mergeKeys = parsed.second
                                mergeRanks = parsed.third
                                mergeResults = parsed.fourth
                            }
                            "added_tokens" -> addedTokens = parseAddedTokens(reader)
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    return Tables(
                        vocab = requireNotNull(vocab),
                        mergeKeys = requireNotNull(mergeKeys),
                        mergeRanks = requireNotNull(mergeRanks),
                        mergeResults = requireNotNull(mergeResults),
                        addedTokens = addedTokens,
                    )
                }
            }
        }

        private class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

        private fun parseModel(reader: SimpleJsonReader): Quad<Map<String, Int>, LongArray, IntArray, IntArray> {
            var vocab: Map<String, Int>? = null
            var mergeKeys: LongArray? = null
            var mergeRanks: IntArray? = null
            var mergeResults: IntArray? = null
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "vocab" -> vocab = parseVocab(reader)
                    "merges" -> {
                        val tables = parseMerges(reader, requireNotNull(vocab))
                        mergeKeys = tables.first
                        mergeRanks = tables.second
                        mergeResults = tables.third
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            return Quad(
                requireNotNull(vocab),
                requireNotNull(mergeKeys),
                requireNotNull(mergeRanks),
                requireNotNull(mergeResults),
            )
        }

        private fun parseVocab(reader: SimpleJsonReader): Map<String, Int> {
            val vocab = HashMap<String, Int>(1 shl 19)
            reader.beginObject()
            while (reader.hasNext()) {
                vocab[reader.nextName()] = reader.nextInt()
            }
            reader.endObject()
            return vocab
        }

        private class MergeEntry(val key: Long, val rank: Int, val result: Int)

        /** Reads `"merges": [["a","b"],…]` into sorted parallel arrays. */
        private fun parseMerges(
            reader: SimpleJsonReader,
            vocab: Map<String, Int>
        ): Triple<LongArray, IntArray, IntArray> {
            val entries = ArrayList<MergeEntry>(1 shl 19)
            reader.beginArray()
            var rank = 0
            while (reader.hasNext()) {
                reader.beginArray()
                check(reader.hasNext()) { "merge pair missing first element" }
                val first = reader.nextString()
                check(reader.hasNext()) { "merge pair missing second element" }
                val second = reader.nextString()
                check(!reader.hasNext()) { "merge entry has more than two elements" }
                reader.endArray()
                val firstId = vocab.getValue(first)
                val secondId = vocab.getValue(second)
                entries += MergeEntry(
                    key = firstId.toLong() * VOCAB_STRIDE + secondId.toLong(),
                    rank = rank,
                    result = vocab.getValue(first + second),
                )
                rank++
            }
            reader.endArray()
            entries.sortBy { it.key }
            val size = entries.size
            val keys = LongArray(size)
            val ranks = IntArray(size)
            val results = IntArray(size)
            entries.forEachIndexed { i, entry ->
                keys[i] = entry.key
                ranks[i] = entry.rank
                results[i] = entry.result
            }
            return Triple(keys, ranks, results)
        }

        private fun parseAddedTokens(reader: SimpleJsonReader): Map<String, Int> {
            val tokens = HashMap<String, Int>(32)
            reader.beginArray()
            while (reader.hasNext()) {
                var id = -1
                var content: String? = null
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "id" -> id = reader.nextInt()
                        "content" -> content = reader.nextString()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                if (id >= 0 && content != null) tokens[content!!] = id
            }
            reader.endArray()
            return tokens
        }
    }
}
