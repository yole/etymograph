package ru.yole.etymograph

class SeekTarget(val index: Int, val phonemeClass: PhonemeClass) {
    fun toEditableText(): String {
        for (ordinal in ordinals) {
            if (index == ordinal.value) {
                return "${ordinal.key} ${phonemeClass.name}"
            }
        }
        return "$index ${phonemeClass.name}"
    }

    companion object {
        val ordinals = mapOf(
            "first" to 1,
            "second" to 2,
            "third" to 3
        )

        fun parse(s: String, language: Language): SeekTarget {
            for (ordinal in ordinals) {
                if (s.startsWith(ordinal.key)) {
                    val phonemeClassName = s.removePrefix(ordinal.key).trim()
                    val phonemeClass = language.phonemeClassByName(phonemeClassName)
                        ?: throw RuleParseException("Unknown phoneme class '$phonemeClassName'")
                    return SeekTarget(ordinal.value, phonemeClass)
                }
            }
            throw RuleParseException("Cannot parse seek target $s")
        }
    }
}

class PhonemeIterator(text: String, language: Language) {
    private val phonemes = splitPhonemes(text, language.digraphs)
    private val resultPhonemes = phonemes.toMutableList()
    private var phonemeIndex = 0
    private var resultPhonemeIndex = 0

    constructor(word: Word) : this(word.normalizedText.trimEnd('-'), word.language)

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

    fun seek(seekTarget: SeekTarget): Boolean {
        var foundCount = 0
        for (targetPhonemeIndex in phonemes.indices) {
            if (phonemes[targetPhonemeIndex] in seekTarget.phonemeClass.matchingPhonemes) {
                foundCount++
                if (foundCount == seekTarget.index) {
                    phonemeIndex = targetPhonemeIndex
                    resultPhonemeIndex = targetPhonemeIndex
                    return true
                }
            }
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
