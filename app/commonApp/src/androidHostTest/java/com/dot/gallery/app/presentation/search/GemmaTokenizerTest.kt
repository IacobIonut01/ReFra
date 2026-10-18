/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.search

import com.dot.gallery.app.presentation.search.tokenizer.GemmaTokenizer
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Golden-vector tests for [GemmaTokenizer], checked against the upstream `tokenizers`
 * library output for `onnx-community/embeddinggemma-2-ONNX` tokenizer.json, plus
 * always-on synthetic-fixture tests that exercise BPE / byte-fallback / added tokens.
 */
class GemmaTokenizerTest {

    private companion object {
        // Written via concatenation so the literal can't be mistaken for a tag.
        val IMG_TOKEN = "<" + "|image|" + ">"
        val X_TOKEN = "<" + "|x|" + ">"
    }

    /**
     * The shipped tokenizer.json lives in the asset pack. Tests run from the
     * commonApp module dir, so the pack is one level up. Skip when the checkout
     * doesn't carry model assets (e.g. a docs-only CI lane).
     */
    private fun realTokenizerFile(): File? = listOf(
        "../ml-models-pack/src/main/assets/tokenizer.json",
        "app/ml-models-pack/src/main/assets/tokenizer.json",
    ).map(::File).firstOrNull { it.exists() }

    private data class Case(val text: String, val ids: IntArray)

    @Test
    fun `encode matches upstream golden vectors`() {
        val file = realTokenizerFile()
        assumeTrue("tokenizer.json asset not present", file != null)
        val tokenizer = GemmaTokenizer(file!!)

        val cases = listOf(
            Case(
                "task: search result | query: a dog on the beach",
                intArrayOf(2, 8071, 236787, 3927, 1354, 1109, 7609, 236787, 496, 4799, 580, 506, 7642, 1)
            ),
            Case("hello world", intArrayOf(2, 23391, 1902, 1)),
            Case(
                "Ünïcödé café 日本語 🎉",
                intArrayOf(2, 238194, 236749, 238527, 236755, 55515, 236859, 33443, 33375, 238582, 204906, 1)
            ),
            Case(
                "title: none | text: sunset over mountains",
                intArrayOf(2, 3250, 236787, 7293, 1109, 1816, 236787, 14711, 1024, 13254, 1)
            ),
            // Empty query: the trailing space collapses into a lone ▁ token.
            Case(
                "task: search result | query: ",
                intArrayOf(2, 8071, 236787, 3927, 1354, 1109, 7609, 236787, 236743, 1)
            ),
            // Media placeholder is an added token matched verbatim mid-string; the
            // space before it survives as its own ▁ piece.
            Case(
                "a $IMG_TOKEN dog",
                intArrayOf(2, 236746, 236743, 258880, 4799, 1)
            ),
            Case(IMG_TOKEN, intArrayOf(2, 258880, 1)),
            Case(
                "spaces  doubled   tripled",
                intArrayOf(2, 35220, 138, 61899, 16658, 139, 12233, 39564, 1)
            ),
            Case("", intArrayOf(2, 1)),
        )
        cases.forEach { case ->
            assertArrayEquals("mismatch for ${case.text}", case.ids, tokenizer.encode(case.text))
        }
    }

    /**
     * A 20-line synthetic tokenizer.json exercising every code path the real one
     * needs: merge ordering, byte-fallback for absent chars (incl. a surrogate
     * pair), and added-token splitting.
     */
    private fun syntheticTokenizerFile(): File {
        val marker = '▁'
        val json = """
        {
          "model": {
            "vocab": {
              "a": 10, "b": 11, "$marker": 12, "ab": 13, "${marker}a": 14,
              "<0xC3>": 20, "<0xA9>": 21, "<0xF0>": 22, "<0x9F>": 23,
              "<0x8E>": 24, "<0x89>": 25
            },
            "merges": [["a", "b"], ["$marker", "a"]]
          },
          "added_tokens": [{"id": 100, "content": "$X_TOKEN"}]
        }
        """.trimIndent()
        val file = File.createTempFile("synthetic-tokenizer", ".json")
        file.writeText(json)
        file.deleteOnExit()
        return file
    }

    @Test
    fun `synthetic fixture exercises merge ordering and byte fallback`() {
        val tokenizer = GemmaTokenizer(syntheticTokenizerFile())

        // "ab" merges (a,b) -> ab.
        assertArrayEquals(intArrayOf(2, 13, 1), tokenizer.encode("ab"))
        // "aab": only the trailing (a,b) pair merges.
        assertArrayEquals(intArrayOf(2, 10, 13, 1), tokenizer.encode("aab"))
        // "aba": first merge produces (ab,a) — not a merge entry — then (▁? no) stays.
        assertArrayEquals(intArrayOf(2, 13, 10, 1), tokenizer.encode("aba"))
        // "a a" -> "a▁a": pairs (a,▁) no; then (▁,a) merges -> ▁a.
        assertArrayEquals(intArrayOf(2, 10, 14, 1), tokenizer.encode("a a"))
        // é is absent from vocab -> UTF-8 bytes <0xC3><0xA9>.
        assertArrayEquals(intArrayOf(2, 20, 21, 1), tokenizer.encode("é"))
        // 🎎 is a surrogate pair: must byte-fallback the JOINED UTF-8 (F0 9F 8E 8E),
        // never two lone-surrogate 0x3F encodings.
        assertArrayEquals(intArrayOf(2, 22, 23, 24, 24, 1), tokenizer.encode("🎎"))
        // Added token splits the raw text; surrounding pieces still BPE normally.
        assertArrayEquals(intArrayOf(2, 10, 12, 100, 13, 1), tokenizer.encode("a $X_TOKEN" + "ab"))
        // Empty input still gets the bos/eos template.
        assertArrayEquals(intArrayOf(2, 1), tokenizer.encode(""))
    }
}
