package ru.yole.etymograph

import java.util.*

open class PhonemeClass(val name: String, val matchingPhonemes: List<String>) {
    open fun matchesCurrent(it: PhonemeIterator): Boolean {
        return it.current in matchingPhonemes
    }

    companion object {
        val diphthong = object : PhonemeClass("diphthong", emptyList()) {
            override fun matchesCurrent(it: PhonemeIterator): Boolean {
                val next = it.atRelative(1)
                return next != null && it.current + next in it.language.diphthongs
            }
        }

        val specialPhonemeClasses = listOf(diphthong)

        const val vowelClassName = "vowel"
    }
}

class IntersectionPhonemeClass(name: String, val classList: List<PhonemeClass>)
    : PhonemeClass(name, emptyList()) {
    override fun matchesCurrent(it: PhonemeIterator): Boolean {
        return classList.all { cls -> cls.matchesCurrent(it) }
    }
}

class WordCategoryValue(val name: String, val abbreviation: String)

class WordCategory(var name: String, var pos: List<String>, var values: List<WordCategoryValue>)

class Phoneme(val graphemes: List<String>, val classes: Set<String>)

class Language(val name: String, val shortName: String) {
    var phonemes = listOf<Phoneme>()
        set(value) {
            field = value
            updatePhonemeClasses()
        }
    var diphthongs: List<String> = emptyList()
    var phonemeClasses = listOf<PhonemeClass>()
         private set

    var syllableStructures: List<String> = emptyList()
    var stressRule: RuleRef? = null
    var phonotacticsRule: RuleRef? = null
    var grammaticalCategories = mutableListOf<WordCategory>()
    var wordClasses = mutableListOf<WordCategory>()

    val digraphs: List<String>
        get() = phonemes.flatMap { it.graphemes }.filter { it.length > 1 }

    private fun updatePhonemeClasses() {
        val phonemeClassMap = mutableMapOf<String, MutableList<String>>()
        for (phoneme in phonemes) {
            for (cls in phoneme.classes) {
                phonemeClassMap.getOrPut(cls) { mutableListOf() }.add(phoneme.graphemes[0])
            }
        }
        phonemeClasses = phonemeClassMap.map { (name, phonemes) ->
            PhonemeClass(name, phonemes)
        }
    }

    fun phonemeClassByName(name: String): PhonemeClass? {
        if (' ' in name) {
            val subclassNames = name.split(' ')
            val subclasses = subclassNames.map { phonemeClassByName(it) ?: return null }
            return IntersectionPhonemeClass(name, subclasses)
        }
        return phonemeClasses.find { it.name == name } ?: PhonemeClass.specialPhonemeClasses.find { it.name == name }
    }

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

    fun findGrammaticalCategory(abbreviation: String): Pair<WordCategory, WordCategoryValue>? {
        return findWordCategory(abbreviation, grammaticalCategories)
    }

    fun findWordClass(abbreviation: String): Pair<WordCategory, WordCategoryValue>? {
        return findWordCategory(abbreviation, wordClasses)
    }

    private fun findWordCategory(abbreviation: String, wordCategories: List<WordCategory>): Pair<WordCategory, WordCategoryValue>? {
        for (category in wordCategories) {
            val gcValue = category.values.find { it.abbreviation == abbreviation }
            if (gcValue != null) {
                return category to gcValue
            }
        }
        return null
    }

    fun findNumberPersonCategories(abbreviation: String): List<Pair<WordCategory, WordCategoryValue?>>? {
        if (abbreviation.first().isDigit()) {
            val personCatValue = findGrammaticalCategory(abbreviation.take(1)) ?: return null
            val numberCatValue = findGrammaticalCategory(abbreviation.drop(1)) ?: return null
            return listOf(personCatValue, numberCatValue)
        }
        return null
    }
}
