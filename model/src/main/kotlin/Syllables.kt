package ru.yole.etymograph

class Syllable(val startIndex: Int, val endIndex: Int, val vowelIndex: Int, val closed: Boolean) {
    val length = endIndex - startIndex
}

data class MutableSyllable(var startIndex: Int, var endIndex: Int, val vowelIndex: Int, var closed: Boolean)

abstract class SyllableClass(val name: String) {
    abstract fun matches(word: Word?, syllable: Syllable, repo: GraphRepository? = null): Boolean

    override fun toString(): String = name

    companion object {
        val syllable = object : SyllableClass("syllable") {
            override fun matches(word: Word?, syllable: Syllable, repo: GraphRepository?) = true
        }

        val stressedSyllable = object : SyllableClass("stressed syllable") {
            override fun matches(word: Word?, syllable: Syllable, repo: GraphRepository?): Boolean {
                val stress = word?.calcStressedPhonemeIndex(null) ?: return false
                return stress in syllable.startIndex..<syllable.endIndex
            }
        }

        val rootSyllable = object : SyllableClass("root syllable") {
            override fun matches(word: Word?, syllable: Syllable, repo: GraphRepository?): Boolean {
                if (word == null) return false
                val orthoWord = word.asOrthographic()
                val segments = (repo?.restoreSegments(orthoWord) ?: orthoWord).segments
                val rootSegment = segments?.firstOrNull {
                    it.sourceRule == null &&
                            (it.sourceWord == null ||
                                    (it.sourceWord.pos != KnownPartsOfSpeech.preverb.abbreviation &&
                                            it.sourceWord.pos != KnownPartsOfSpeech.affix.abbreviation))
                } ?: return true
                return syllable.startIndex >= rootSegment.firstCharacter
            }
        }

        val allClasses = listOf(syllable, stressedSyllable, rootSyllable)

        fun find(name: String) = allClasses.find { it.name == name }
    }
}

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
                result.add(MutableSyllable(currentSyllableStart, index + 1, index,false))
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

    return result.map { Syllable(it.startIndex, it.endIndex, it.vowelIndex, it.closed) }
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
