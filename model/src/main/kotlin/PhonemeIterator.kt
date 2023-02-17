package ru.yole.etymograph

import java.util.*

class PhonemeIterator(val word: Word) {
    private val phonemes = splitPhonemes(word.text.lowercase(Locale.getDefault()), word.language.digraphs)
    private val resultPhonemes = phonemes.toMutableList()
    private var phonemeIndex = 0
    private var resultPhonemeIndex = 0

    val current: String get() = phonemes[phonemeIndex]
    val previous: String? get() = phonemes.getOrNull(phonemeIndex - 1)
    val last: String get() = phonemes.last()

    fun advance(): Boolean {
        if (phonemeIndex < phonemes.size - 1) {
            phonemeIndex++
            resultPhonemeIndex++
            return true
        }
        return false
    }

    fun replace(s: String) {
        resultPhonemes[resultPhonemeIndex] = s
    }

    fun delete() {
        resultPhonemes.removeAt(resultPhonemeIndex)
        resultPhonemeIndex--
    }

    fun result(): String {
        return resultPhonemes.joinToString("")
    }

    private fun splitPhonemes(text: String, digraphs: List<String>): List<String> {
        val result = mutableListOf<String>()
        var offset = 0
        while (offset < text.length) {
            val digraph = digraphs.firstOrNull { text.startsWith(it, offset) }
            if (digraph != null) {
                result.add(digraph)
                offset += digraph.length
            }
            else {
                result.add(text.substring(offset, offset + 1))
                offset++
            }
        }
        return result
    }
}
