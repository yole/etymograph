package ru.yole.etymograph

import java.util.*

class WordCategoryValue(val name: String, val abbreviation: String)

class WordCategory(var name: String, var pos: List<String>, var values: List<WordCategoryValue>)

data class WordCategoryWithValue(val category: WordCategory, val value: WordCategoryValue)

object KnownPartsOfSpeech {
    val preverb = WordCategoryValue("Preverb", "PV")
    val affix = WordCategoryValue("Affix", "AFF")
    val properName = WordCategoryValue("Proper Name", "NP")
}

class Phoneme(
    id: Int, var graphemes: List<String>, var sound: String?, var classes: Set<String>, var historical: Boolean = false,
    source: List<SourceRef> = emptyList(), notes: String? = null
) : LangEntity(id, source, notes) {
    val effectiveSound: String
        get() = sound ?: graphemes.first()
}

class PhonemeLookup(val accentTypes: Set<AccentType>) {
    private val digraphLookup = arrayOfNulls<MutableMap<String, Phoneme>>(Char.MAX_VALUE.code)
    private var digraphs = mutableMapOf<String, Phoneme>()
    private var singleGraphemes = arrayOfNulls<Pair<Phoneme?, AccentType?>>(Char.MAX_VALUE.code)

    init {
        for (type in accentTypes) {
            singleGraphemes[type.combiningMark.code] = null to type
        }
    }

    fun add(key: String, phoneme: Phoneme) {
        if (key.length > 1) {
            digraphs[key] = phoneme
            val c = key.first().code
            if (digraphLookup[c] == null) {
                digraphLookup[c] = mutableMapOf()
            }
            digraphLookup[c]!![key] = phoneme
        }
        else {
            singleGraphemes[key[0].code] = phoneme to null
            for (type in accentTypes) {
                val accented = type.combine(key)
                if (accented.length == 1) {
                    singleGraphemes[accented[0].code] = phoneme to type
                }
            }
        }
    }

    fun iteratePhonemes(text: String, callback: (Int, Int, Phoneme?, AccentType?) -> Unit) {
        var offset = 0
        while (offset < text.length) {
            val digraph = digraphLookup[text[offset].code]?.keys?.firstOrNull { text.startsWith(it, offset) }
            if (digraph != null) {
                callback(offset, offset + digraph.length, digraphs[digraph], null)
                offset += digraph.length
            }

            else {
                val graphemeData = singleGraphemes[text[offset].code]
                if (offset + 1 < text.length) {
                    val nextGraphemeData = singleGraphemes[text[offset+1].code]
                    if (nextGraphemeData?.first == null && nextGraphemeData?.second != null) {
                        callback(offset, offset + 1, graphemeData?.first, nextGraphemeData.second)
                        offset += 2
                        continue
                    }
                }
                callback(offset, offset + 1, graphemeData?.first, graphemeData?.second)
                offset++
            }
        }
    }

    fun nextPhoneme(text: String, offset: Int): String {
        val digraph = digraphLookup[text[offset].code]?.keys?.firstOrNull { text.startsWith(it, offset) }
        return digraph ?: text.substring(offset, offset + 1)
    }
}

class Language(val name: String, val shortName: String) {
    var reconstructed: Boolean = false
    var protoLanguage: Language? = null
    var phonemes = listOf<Phoneme>()
        set(value) {
            field = value
            phonemeClasses.update(value)
            updateGraphemes()
        }

    var diphthongs: List<String> = emptyList()
    private val phonemeClasses = PhonemeClassList()

    var syllableStructures: List<String> = emptyList()
    var stressRule: RuleRef? = null
    var phonotacticsRule: RuleRef? = null
    var pronunciationRule: RuleRef? = null
    var orthographyRule: RuleRef? = null
    var pos = mutableListOf<WordCategoryValue>()
    var grammaticalCategories = mutableListOf<WordCategory>()
    var wordClasses = mutableListOf<WordCategory>()
    var accentTypes = setOf<AccentType>()
        set(value) {
            field = value
            updateGraphemes()
        }

    var orthoPhonemeLookup = PhonemeLookup(accentTypes)
    var phonoPhonemeLookup = PhonemeLookup(accentTypes)

    var dictionarySettings: String? = null

    private val normalizeCache = mutableMapOf<String, String>()
    private val phonemicCache = mutableMapOf<String, String>()

    private fun updateGraphemes() {
        orthoPhonemeLookup = PhonemeLookup(accentTypes)
        phonoPhonemeLookup = PhonemeLookup(accentTypes)
        for (phoneme in phonemes) {
            for (g in phoneme.graphemes) {
                orthoPhonemeLookup.add(g, phoneme)
            }
            phonoPhonemeLookup.add(phoneme.sound ?: phoneme.graphemes[0], phoneme)
        }
        normalizeCache.clear()
        phonemicCache.clear()
    }

    fun phonemeClassByName(name: String): PhonemeClass? {
        return phonemeClasses.findByName(name)
    }

    fun phonemeFeatures(phoneme: Phoneme): Set<String> {
        return (phoneme.classes + implicitPhonemeClasses(phoneme.classes)).filterTo(mutableSetOf()) {
            it.startsWith('+') ||
                    it.startsWith('-') ||
                    it in placeFeatures ||
                    it in unaryFeatures ||
                    (phonemeClassByName("-$it") != null && phonemeClassByName("+$it") == null)
        }
    }

    fun comparePhonemes(oldPhoneme: Phoneme, newPhoneme: Phoneme): Collection<String> {
        val oldFeatures = phonemeFeatures(oldPhoneme)
        val newFeatures = phonemeFeatures(newPhoneme)

        return (oldFeatures - newFeatures).mapNotNull { invertFeature(it) }.toSet() + (newFeatures - oldFeatures)
    }

    private fun invertFeature(name: String): String? {
        if (name.startsWith("+")) {
            return "-" + name.substring(1)
        }
        if (name.startsWith("-")) {
            val plus = "+${name.substring(1)}"
            if (phonemeClassByName(plus) != null) {
                return plus
            }
            return name.substring(1)
        }
        val negatedClass = phonemeClassByName("-$name")
        if (negatedClass != null) {
            return negatedClass.name
        }
        return null
    }

    fun contradictingFeatures(name: String): Collection<String> {
        if (name in placeFeatures) {
            return placeFeatures.filter { it != name }
        }
        val inverted = invertFeature(name)
        return inverted?.let { listOf(it) } ?: emptyList()
    }

    fun normalizeWord(text: String): String {
        return normalizeCache.getOrPut(text) {
            buildString {
                val lowerText = text.lowercase(Locale.FRANCE)
                orthoPhonemeLookup.iteratePhonemes(lowerText) { startIndex, endIndex, phoneme, accentType ->
                    append(phoneme?.graphemes?.get(0)?.let { accentType?.combine(it) ?: it }
                        ?: lowerText.substring(startIndex, endIndex))
                }
            }.removeSuffix("-")
        }
    }

    fun cachePhonemicText(text: String, segments: List<WordSegment>?,
                          callback: () -> Pair<String, List<WordSegment>?>): Pair<String, List<WordSegment>?> {
        if (segments == null) {
            return phonemicCache.getOrPut(text) { callback().first } to null
        }
        return callback()
    }

    fun isNormalizedEqual(ruleProducedWord: Word, attestedWord: Word): Boolean {
        return normalizeWord(ruleProducedWord.asOrthographic().text) == normalizeWord(attestedWord.asOrthographic().text)
    }

    fun findGrammaticalCategory(abbreviation: String): WordCategoryWithValue? {
        return findWordCategory(abbreviation, grammaticalCategories)
    }

    fun findWordClass(abbreviation: String): WordCategoryWithValue? {
        return findWordCategory(abbreviation, wordClasses)
    }

    private fun findWordCategory(abbreviation: String, wordCategories: List<WordCategory>): WordCategoryWithValue? {
        for (category in wordCategories) {
            val gcValue = category.values.find { it.abbreviation == abbreviation }
            if (gcValue != null) {
                return WordCategoryWithValue(category, gcValue)
            }
        }
        return null
    }

    fun findNumberPersonCategories(abbreviation: String): List<WordCategoryWithValue>? {
        if (abbreviation.first().isDigit()) {
            val personCatValue = findGrammaticalCategory(abbreviation.take(1)) ?: return null
            val numberCatValue = findGrammaticalCategory(abbreviation.drop(1)) ?: return null
            return listOf(personCatValue, numberCatValue)
        }
        return null
    }

    companion object {
        val placeFeatures = listOf("labial", "coronal", "dorsal", "pharyngeal", "laryngeal")
        val unaryFeatures = listOf("nasal")
    }
}
