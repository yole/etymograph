package ru.yole.etymograph

class Syllable(val startIndex: Int, val endIndex: Int, val closed: Boolean)

data class MutableSyllable(var startIndex: Int, var endIndex: Int, var closed: Boolean)

fun breakIntoSyllables(word: Word): List<Syllable> {
    val vowels = word.language.phonemeClassByName(PhonemeClass.vowelClassName)
        ?: return emptyList()

    val result = mutableListOf<MutableSyllable>()
    val it = PhonemeIterator(word, null)
    if (it.size == 0) return emptyList()

    var currentSyllableStart = 0
    var index = 0
    var consonants = 0
    var lastVowel: String? = null
    while (true) {
        if (it.current in vowels.matchingPhonemes) {
            if (lastVowel != null && lastVowel + it.current in word.language.diphthongs) {
                lastVowel = null
                result.last().endIndex++
                currentSyllableStart++
            }
            else {
                lastVowel = it.current
                result.add(MutableSyllable(currentSyllableStart, index + 1, false))
                currentSyllableStart = index + 1
                consonants = 0
            }
        }
        else {
            lastVowel = null
            consonants++
            if (consonants == 2 && result.isNotEmpty()) {
                result.last().endIndex++
                result.last().closed = true
                currentSyllableStart++
            }
        }
        index++
        if (!it.advance()) break
    }
    if (result.isNotEmpty()) {
        result.last().endIndex = index
    }

    return result.map { Syllable(it.startIndex, it.endIndex, it.closed) }
}

fun findSyllable(syllables: List<Syllable>, phonemeIndex: Int): Int {
    return syllables.indexOfFirst { phonemeIndex >= it.startIndex && phonemeIndex < it.endIndex }
}

fun analyzeSyllableStructure(vowels: PhonemeClass, phonemes: PhonemeIterator, syllable: Syllable): String {
    return buildString {
        for (index in syllable.startIndex until syllable.endIndex) {
            phonemes.advanceTo(index)
            if (vowels.matchesCurrent(phonemes)) {
                append('V')
            }
            else {
                append('C')
            }
        }
    }
}
