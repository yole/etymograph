package ru.yole.etymograph

import java.util.*

class WordCategoryValue(val name: String, val abbreviation: String)

class WordCategory(var name: String, var pos: List<String>, var values: List<WordCategoryValue>)

object KnownPartsOfSpeech {
    val preverb = WordCategoryValue("Preverb", "PV")
    val properName = WordCategoryValue("Proper Name", "NP")
}

class Phoneme(
    id: Int, var graphemes: List<String>, var sound: String?, var classes: Set<String>, var historical: Boolean = false,
    source: List<SourceRef> = emptyList(), notes: String? = null
) : LangEntity(id, source, notes) {
    val effectiveSound: String
        get() = sound ?: graphemes.first()
}

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

class PhonemeTableCell {
    val phonemes = mutableListOf<Phoneme>()
}

class PhonemeTableRow(val title: String) {
    val columns = mutableListOf<PhonemeTableCell>()

    fun isEmpty(): Boolean = columns.isEmpty() || columns.all { c -> c.phonemes.isEmpty()}

    fun isColumnEmpty(i: Int): Boolean {
        val cell = columns.getOrNull(i)
        return cell == null || cell.phonemes.isEmpty()
    }
}

class PhonemeTable(val title: String, rowTitles: List<String>? = null, columnTitles: List<String>? = null) {
    val rows = mutableListOf<PhonemeTableRow>()
    val columnTitles = mutableListOf<String>()

    init {
        if (rowTitles != null) {
            for (rowTitle in rowTitles) {
                rows.add(PhonemeTableRow(rowTitle))
            }
        }
        if (columnTitles != null) {
            this.columnTitles.addAll(columnTitles)
        }
    }

    fun add(rowTitle: String, columnTitle: String, phoneme: Phoneme) {
        val row = rows.find { it.title == rowTitle } ?: PhonemeTableRow(title).also { rows.add(it) }
        var columnIndex = columnTitles.indexOf(columnTitle)
        if (columnIndex < 0) {
            columnTitles.add(columnTitle)
            columnIndex = columnTitles.size - 1
        }
        while (row.columns.size <= columnIndex) {
            row.columns.add(PhonemeTableCell())
        }
        row.columns[columnIndex].phonemes.add(phoneme)
    }

    fun add(phoneme: Phoneme) {
        if (rows.isEmpty()) {
            rows.add(PhonemeTableRow(""))
        }
        rows[0].columns.add(PhonemeTableCell().also { it.phonemes.add(phoneme)})
    }

    fun compact() {
        rows.removeIf { it.isEmpty() }
        for (i in columnTitles.size-1 downTo 0) {
            if (rows.all { it.isColumnEmpty(i) }) {
                columnTitles.removeAt(i)
                for (r in rows) {
                    if (i < r.columns.size) {
                        r.columns.removeAt(i)
                    }
                }
            }
        }
        for (row in rows) {
            while (row.columns.size < columnTitles.size) {
                row.columns.add(PhonemeTableCell())
            }
        }
    }

    fun isNotEmpty(): Boolean = rows.any { !it.isEmpty() }
}

class Language(val name: String, val shortName: String) {
    var reconstructed: Boolean = false
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

    var orthoPhonemeLookup = PhonemeLookup()
    var phonoPhonemeLookup = PhonemeLookup()

    var dictionarySettings: String? = null

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
        return phonemeClasses.findByName(name)
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

    fun buildPhonemeTables(): List<PhonemeTable> {
        val result = mutableListOf<PhonemeTable>()
        val consonants = phonemes.filter { "consonant" in it.classes && !it.historical }
        if (consonants.isNotEmpty()) {
            result.addAll(buildPhonemeTable(consonants, "Consonants", articulationManners, articulationPlaces))
        }
        val historicalConsonants = phonemes.filter { "consonant" in it.classes && it.historical }
        if (historicalConsonants.isNotEmpty()) {
            result.addAll(buildPhonemeTable(historicalConsonants, "Historical Consonants", articulationManners, articulationPlaces))
        }

        val vowels = phonemes.filter { PhonemeClass.vowelClassName in it.classes && !it.historical }
        if (vowels.isNotEmpty()) {
            result.addAll(buildPhonemeTable(vowels, "Vowels", vowelHeights, vowelBackness))
        }
        val historicalVowels = phonemes.filter { PhonemeClass.vowelClassName in it.classes && it.historical }
        if (historicalVowels.isNotEmpty()) {
            result.addAll(buildPhonemeTable(historicalVowels, "Historical Vowels", vowelHeights, vowelBackness))
        }

        val otherPhonemes = phonemes.filter { "consonant" !in it.classes && PhonemeClass.vowelClassName !in it.classes  }
        if (otherPhonemes.isNotEmpty()) {
            val otherPhonemeTable = PhonemeTable("Other Phonemes")
            for (phoneme in otherPhonemes) {
                otherPhonemeTable.add(phoneme)
            }
            result.add(otherPhonemeTable)
        }

        return result
    }

    private fun buildPhonemeTable(phonemes: List<Phoneme>, title: String, rows: List<String>, columns: List<String>): List<PhonemeTable> {
        val mainTable = PhonemeTable(title, rows, columns)
        val otherTable = PhonemeTable("Other $title")

        for (phoneme in phonemes) {
            val columnTitle = phoneme.classes.find { it in columns }
            val rowTitle = phoneme.classes.find { it in rows }
            if (columnTitle != null && rowTitle != null) {
                mainTable.add(rowTitle, columnTitle, phoneme)
            } else {
                otherTable.add(phoneme)
            }
        }

        val result = mutableListOf<PhonemeTable>()
        if (mainTable.isNotEmpty()) {
            mainTable.compact()
            result.add(mainTable)
        }
        if (otherTable.isNotEmpty()) {
            result.add(otherTable)
        }
        return result
    }

    companion object {
        val articulationPlaces = listOf(
            "labial", "bilabial", "labiodental", "dental", "alveolar", "postalveolar", "palatal", "velar", "labiovelar",
            "uvular", "glottal"
        )
        val articulationManners = listOf(
            "plosive", "stop", "fricative", "affricate", "nasal", "approximant", "trill", "lateral"
        )
        val vowelHeights = listOf("close", "near-close", "close-mid", "mid", "open-mid", "near-open", "open")
        val vowelBackness = listOf("front", "near-front", "central", "near-back", "back")
    }
}
