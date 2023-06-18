package ru.yole.etymograph

import java.util.*

open class PhonemeClass(val name: String, val matchingPhonemes: List<String>) {
    open fun matchesCurrent(it: PhonemeIterator): Boolean {
        return it.current in matchingPhonemes
    }

    companion object {
        val diphthong = object : PhonemeClass("diphthong", emptyList()) {
            override fun matchesCurrent(it: PhonemeIterator): Boolean {
                val next = it.next
                return next != null && it.current + next in it.language.diphthongs
            }
        }

        val specialPhonemeClasses = listOf(diphthong)

        const val vowelClassName = "vowel"
    }
}

class Language(val name: String, val shortName: String) {
    var digraphs: List<String> = emptyList()
    var diphthongs: List<String> = emptyList()
    var phonemeClasses = mutableListOf<PhonemeClass>()
    var letterNormalization = mapOf<String, String>()
    var syllableStructures: List<String> = emptyList()
    var wordFinals: List<String> = emptyList()
    var stressRule: RuleRef? = null

    fun phonemeClassByName(name: String) =
        phonemeClasses.find { it.name == name } ?: PhonemeClass.specialPhonemeClasses.find { it.name == name }

    fun normalizeWord(text: String): String {
        return letterNormalization.entries.fold(text.lowercase(Locale.FRANCE)) { s, entry ->
            s.replace(entry.key, entry.value)
        }
    }

    fun isNormalizedEqual(ruleProducedWord: String, attestedWord: String): Boolean {
        return normalizeWord(ruleProducedWord) == normalizeWord(attestedWord)
    }
}
