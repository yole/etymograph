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

class GrammaticalCategoryValue(val name: String, val abbreviation: String)

class GrammaticalCategory(var name: String, var pos: List<String>, var values: List<GrammaticalCategoryValue>)

class Phoneme(val graphemes: List<String>, val classes: List<String>)

class Language(val name: String, val shortName: String) {
    var phonemes = mutableListOf<Phoneme>()
    var diphthongs: List<String> = emptyList()
    var phonemeClasses = mutableListOf<PhonemeClass>()
    var syllableStructures: List<String> = emptyList()
    var wordFinals: List<String> = emptyList()
    var stressRule: RuleRef? = null
    var grammaticalCategories = mutableListOf<GrammaticalCategory>()

    val digraphs: List<String>
        get() = phonemes.flatMap { it.graphemes }.filter { it.length > 1 }

    fun phonemeClassByName(name: String) =
        phonemeClasses.find { it.name == name } ?: PhonemeClass.specialPhonemeClasses.find { it.name == name }

    fun normalizeWord(text: String): String {
        return phonemes.filter { it.graphemes.size > 1 }.fold(text.lowercase(Locale.FRANCE)) { s, phoneme ->
            normalizeGrapheme(s, phoneme)
        }.removeSuffix("-")
    }

    private fun normalizeGrapheme(text: String, phoneme: Phoneme) =
        phoneme.graphemes.drop(1).fold(text) { s, grapheme -> s.replace(grapheme, phoneme.graphemes.first()) }

    fun isNormalizedEqual(ruleProducedWord: String, attestedWord: String): Boolean {
        return normalizeWord(ruleProducedWord) == normalizeWord(attestedWord)
    }

    fun findGrammaticalCategory(abbreviation: String): Pair<GrammaticalCategory, GrammaticalCategoryValue>? {
        for (category in grammaticalCategories) {
            val gcValue = category.values.find { it.abbreviation == abbreviation }
            if (gcValue != null) {
                return category to gcValue
            }
        }
        return null
    }

    fun findNumberPersonCategories(abbreviation: String): List<Pair<GrammaticalCategory, GrammaticalCategoryValue?>>? {
        if (abbreviation.first().isDigit()) {
            val personCatValue = findGrammaticalCategory(abbreviation.take(1)) ?: return null
            val numberCatValue = findGrammaticalCategory(abbreviation.drop(1)) ?: return null
            return listOf(personCatValue, numberCatValue)
        }
        return null
    }
}
