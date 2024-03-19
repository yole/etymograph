package ru.yole.etymograph

import java.util.*

open class PhonemeClass(val name: String, var matchingPhonemes: List<String>) {
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

class Phoneme(
    id: Int, val graphemes: List<String>, val sound: String?, val classes: Set<String>,
    source: List<SourceRef> = emptyList(), notes: String? = null
) : LangEntity(id, source, notes)

class PhonemeLookup {
    private var digraphs = mutableMapOf<String, Phoneme>()
    private var singleGraphemes = mutableMapOf<Char, Phoneme>()

    fun clear() {
        digraphs.clear()
        singleGraphemes.clear()
    }

    fun add(key: String, phoneme: Phoneme) {
        if (key.length > 1) {
            digraphs[key] = phoneme
        }
        else {
            singleGraphemes[key[0]] = phoneme
        }
    }

    fun iteratePhonemes(text: String, callback: (String, Phoneme?) -> Unit) {
        var offset = 0
        while (offset < text.length) {
            val digraph = digraphs.keys.firstOrNull { text.startsWith(it, offset) }
            if (digraph != null) {
                callback(digraph, digraphs[digraph])
                offset += digraph.length
            }
            else {
                callback(text.substring(offset, offset + 1), singleGraphemes[text[offset]])
                offset++
            }
        }
    }
}

class Language(val name: String, val shortName: String) {
    var phonemes = listOf<Phoneme>()
        set(value) {
            field = value
            updatePhonemeClasses()
            updateGraphemes()
        }

    var diphthongs: List<String> = emptyList()
    var phonemeClasses = listOf<PhonemeClass>()
         private set

    var syllableStructures: List<String> = emptyList()
    var stressRule: RuleRef? = null
    var phonotacticsRule: RuleRef? = null
    var orthographyRule: RuleRef? = null
    var grammaticalCategories = mutableListOf<WordCategory>()
    var wordClasses = mutableListOf<WordCategory>()

    var orthoPhonemeLookup = PhonemeLookup()
    var phonoPhonemeLookup = PhonemeLookup()

    private fun updatePhonemeClasses() {
        val phonemeClassMap = mutableMapOf<String, MutableList<String>>()
        for (phoneme in phonemes) {
            for (cls in phoneme.classes) {
                phonemeClassMap.getOrPut(cls) { mutableListOf() }.add(phoneme.sound ?: phoneme.graphemes[0])
            }
        }
        val oldPhonemeClasses = phonemeClasses
        phonemeClasses = phonemeClassMap.map { (name, phonemes) ->
            oldPhonemeClasses.find { it.name == name }?.also { it.matchingPhonemes = phonemes }
                ?: PhonemeClass(name, phonemes)
        }
    }

    private fun updateGraphemes() {
        orthoPhonemeLookup.clear()
        phonoPhonemeLookup.clear()
        for (phoneme in phonemes) {
            for (g in phoneme.graphemes) {
                orthoPhonemeLookup.add(g, phoneme)
            }
            phonoPhonemeLookup.add(phoneme.sound ?: phoneme.graphemes[0], phoneme)
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
        return buildString {
            orthoPhonemeLookup.iteratePhonemes(text.lowercase(Locale.FRANCE)) { s, phoneme ->
                append(phoneme?.graphemes?.get(0) ?: s)
            }
        }.removeSuffix("-")
    }

    fun isNormalizedEqual(ruleProducedWord: Word, attestedWord: Word): Boolean {
        return normalizeWord(ruleProducedWord.asOrthographic().text) == normalizeWord(attestedWord.asOrthographic().text)
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
