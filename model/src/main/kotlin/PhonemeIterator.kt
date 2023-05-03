package ru.yole.etymograph

object Ordinals {
    private val ordinals = listOf(
        "last" to -1,
        "second to last" to -2,
        "third to last" to -3,
        "first" to 1,
        "second" to 2,
        "third" to 3,
    )

    fun toString(i: Int): String? {
        return ordinals.find { it.second == i }?.first
    }

    fun parse(s: String): Pair<Int, String>? {
        for (ordinal in ordinals) {
            if (s.startsWith(ordinal.first)) {
                return ordinal.second to s.removePrefix(ordinal.first).trim()
            }
        }
        return null
    }

    fun <T> at(items: List<T>, index: Int): T? {
        return items.getOrNull(if (index < 0) items.size + index else index - 1)
    }
}

class SeekTarget(val index: Int, val phonemeClass: PhonemeClass) {
    fun toEditableText(): String {
        return Ordinals.toString(index)?.let { "$it ${phonemeClass.name}"} ?: "$index ${phonemeClass.name}"
    }

    companion object {
        fun parse(s: String, language: Language): SeekTarget {
            val (index, phonemeClassName) = Ordinals.parse(s) ?: throw RuleParseException("Cannot parse seek target $s")
            val phonemeClass = language.phonemeClassByName(phonemeClassName)
                ?: throw RuleParseException("Unknown phoneme class '$phonemeClassName'")
            return SeekTarget(index, phonemeClass)
        }
    }
}

class PhonemeIterator(text: String, val language: Language) {
    private val phonemes = splitPhonemes(text, language.digraphs)
    private val resultPhonemes = phonemes.toMutableList()
    private var phonemeIndex = 0
    private var resultPhonemeIndex = 0

    constructor(word: Word) : this(word.normalizedText.trimEnd('-'), word.language)

    val current: String get() = phonemes[phonemeIndex]
    val previous: String? get() = phonemes.getOrNull(phonemeIndex - 1)
    val next: String? get() = phonemes.getOrNull(phonemeIndex + 1)
    val last: String get() = phonemes.last()
    val size: Int get() = phonemes.size

    operator fun get(index: Int): String = phonemes[index]

    fun advanceTo(index: Int) {
        phonemeIndex = index
        resultPhonemeIndex = index
    }

    fun advance(): Boolean {
        if (phonemeIndex < phonemes.size - 1) {
            phonemeIndex++
            resultPhonemeIndex++
            return true
        }
        return false
    }

    fun seek(seekTarget: SeekTarget): Boolean {
        val matchingIndexes = mutableListOf<Int>()
        for (targetPhonemeIndex in phonemes.indices) {
            if (phonemes[targetPhonemeIndex] in seekTarget.phonemeClass.matchingPhonemes) {
                matchingIndexes.add(targetPhonemeIndex)
            }
        }
        val result = Ordinals.at(matchingIndexes, seekTarget.index) ?: return false
        phonemeIndex = result
        resultPhonemeIndex = result
        return true
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

    fun atBeginning(): Boolean = phonemeIndex == 0

    fun findMatchInRange(start: Int, end: Int, phonemeClass: PhonemeClass): Int? {
        for (i in start until end) {
            advanceTo(i)
            if (phonemeClass.matchesCurrent(this)) {
                return i
            }
        }
        return null
    }

    fun phonemeToCharacterIndex(phonemeIndex: Int): Int {
        return phonemes.subList(0, phonemeIndex).sumOf { it.length }
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
