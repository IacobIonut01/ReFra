/**
 * Copyright 2023 Viacheslav Barkov
 * Copyright 2020 The HuggingFace Team
 *
 * The following code is a derivative work of the code from the TensorFlow Lite Transformers
 * with Android project, which is licensed Apache 2.0
 */

package com.dot.gallery.feature_node.presentation.search.tokenizer

import android.content.Context
import android.util.JsonReader
import com.dot.gallery.R
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.io.useLines
import kotlin.use

class ClipTokenizer(
    private val appContext: Context,
) {

    private val encoder: Map<String, Int> = getVocab()

    private fun getVocab(): Map<String, Int> {
        val vocab = hashMapOf<String, Int>().apply {
            appContext.resources.openRawResource(R.raw.vocab).use {
                val vocabReader = JsonReader(InputStreamReader(it, "UTF-8"))
                vocabReader.beginObject()
                while (vocabReader.hasNext()) {
                    val key = vocabReader.nextName().replace("</w>", " ")
                    val value = vocabReader.nextInt()
                    put(key, value)
                }
                vocabReader.close()
            }
        }
        return vocab
    }


    private val bpeRanks: Map<Pair<String, String>, Int> = getMerges()

    private fun getMerges(): HashMap<Pair<String, String>, Int> {
        val merges = hashMapOf<Pair<String, String>, Int>().apply {
            appContext.resources.openRawResource(R.raw.merges).use {
                val mergesReader = BufferedReader(InputStreamReader(it))
                mergesReader.useLines { seq ->
                    seq.drop(1).forEachIndexed { i, s ->
                        val list = s.split(" ")
                        val keyTuple = list[0] to list[1].replace("</w>", " ")
                        put(keyTuple, i)
                    }
                }
            }
        }
        return merges
    }

    private val encodeRegex =
        Regex("""<\|startoftext\|>|<\|endoftext\|>|'s|'t|'re|'ve|'m|'ll|'d|[\p{L}]+|[\p{N}]|[^\s\p{L}\p{N}]+""")

    fun encode(text: String): MutableList<Int> {
        val tokens = encodeRegex.findAll(text).map { result ->
            result.value.codePoints().boxed().map { byteEncoder[it]!! }.toArray().joinToString("")
        }
        return tokens.map { bpe(it) }.flatten().map { encoder[it]!! }.toMutableList()
    }

    private fun bpe(token: String): List<String> {
        if (token.length <= 1) return listOf("$token ")

        val wordWithBreak = token.map { it.toString() }.toMutableList()
        wordWithBreak[wordWithBreak.size - 1] = "${wordWithBreak[wordWithBreak.size - 1]} "
        var word = wordWithBreak.toList()
        var pairs = getPairs(word)

        while (true) {
            if (!pairs.any { bpeRanks.containsKey(it) }) break
            val (first, second) = pairs.minBy { bpeRanks.getOrDefault(it, Int.MAX_VALUE) }

            var i = 0
            val newWord = mutableListOf<String>()
            while (i < word.size) {
                val j = word.withIndex().indexOfFirst { it.index >= i && it.value == first }
                if (j != -1) {
                    newWord.addAll(word.subList(i, j))
                    i = j
                } else {
                    newWord.addAll(word.subList(i, word.size))
                    break
                }

                if (word[i] == first && i < word.size - 1 && word[i + 1] == second) {
                    newWord.add(first + second)
                    i += 2
                } else {
                    newWord.add(word[i])
                    i += 1
                }
            }

            word = newWord
            if (word.size == 1) {
                break
            } else {
                pairs = getPairs(word)
            }
        }
        return word
    }

    private fun getPairs(word: List<String>): Set<Pair<String, String>> {
        return mutableSetOf<Pair<String, String>>().apply {
            for (i in 0 until word.size - 1) {
                add(word[i] to word[i + 1])
            }
        }
    }
}