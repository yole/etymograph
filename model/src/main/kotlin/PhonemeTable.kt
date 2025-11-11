package ru.yole.etymograph

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

    companion object {
        fun build(phonemes: List<Phoneme>): List<PhonemeTable> {
            val result = mutableListOf<PhonemeTable>()
            val consonants = phonemes.filter { PhonemeClass.consonantClassName in withImplicit(it.classes) && !it.historical }
            if (consonants.isNotEmpty()) {
                result.addAll(buildPhonemeTable(consonants, "Consonants", articulationManners, articulationPlaces))
            }
            val historicalConsonants = phonemes.filter { PhonemeClass.consonantClassName in withImplicit(it.classes) && it.historical }
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

            val otherPhonemes = phonemes.filter {
                PhonemeClass.consonantClassName !in withImplicit(it.classes) &&
                        PhonemeClass.vowelClassName !in withImplicit(it.classes)
            }
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

        val articulationPlaces = listOf(
            "labial", "bilabial", "labiodental", "dental", "alveolar", "postalveolar", "retroflex",
            "alveolo-palatal", "palatal", "velar", "labiovelar", "uvular", "pharyngeal", "glottal"
        )
        val articulationManners = listOf(
            "plosive", "stop", "fricative", "affricate", "nasal", "approximant", "trill", "lateral"
        )
        val vowelHeights = listOf("close", "near-close", "close-mid", "mid", "open-mid", "near-open", "open")
        val vowelBackness = listOf("front", "near-front", "central", "near-back", "back")
    }
}
