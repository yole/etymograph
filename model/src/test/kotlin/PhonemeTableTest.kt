package ru.yole.etymograph

import org.junit.Assert.*
import org.junit.Test

class PhonemeTableTest : QBaseTest() {
    @Test
    fun qTables() {
        val tables = q.buildPhonemeTables()
        val consonants = tables.first()
        assertEquals("Consonants", consonants.title)
        val nasalRow = consonants.rows.find { it.title == "nasal" }!!
        val alveolarColumnIndex = consonants.columnTitles.indexOf("alveolar")
        val cell = nasalRow.columns[alveolarColumnIndex]
        assertEquals("n", cell.phonemes.first().graphemes.first())

        assertNull(consonants.rows.find { it.title == "lateral" })
        assertEquals(-1, consonants.columnTitles.indexOf("labial"))

        val otherConsonants = tables[1]
        assertEquals("Other Consonants", otherConsonants.title)
        assertNotNull(otherConsonants.rows[0].columns.find { it.phonemes.any { p -> "q" in p.graphemes }})

        val vowels = tables[2]
        assertEquals("Other Vowels", vowels.title)
    }
}
