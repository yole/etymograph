package ru.yole.etymograph

class Syllable(val startIndex: Int, val endIndex: Int) {

}

data class MutableSyllable(var startIndex: Int, var endIndex: Int)

fun breakIntoSyllables(word: Word): List<Syllable> {
    val vowels = word.language.phonemeClassByName("vowel")
        ?: return emptyList()

    val result = mutableListOf<MutableSyllable>()
    val it = PhonemeIterator(word)

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
                result.add(MutableSyllable(currentSyllableStart, index + 1))
                currentSyllableStart = index + 1
                consonants = 0
            }
        }
        else {
            consonants++
            if (consonants > 1) {
                result.last().endIndex++
                currentSyllableStart++
            }
        }
        index++
        if (!it.advance()) break
    }
    result.last().endIndex = index

    return result.map { Syllable(it.startIndex, it.endIndex) }
}