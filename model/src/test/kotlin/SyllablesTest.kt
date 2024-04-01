package ru.yole.etymograph

import org.junit.Test
import org.junit.Assert.*

class SyllablesTest : QBaseTest() {
    private fun assertSyllables(word: Word, vararg expectedSyllables: Pair<Int, Int>) {
        val syllables = breakIntoSyllables(word)
        assertEquals(expectedSyllables.size, syllables.size)
        for ((index, expectedSyllable) in expectedSyllables.withIndex()) {
            assertEquals(expectedSyllable.first, syllables[index].startIndex)
            assertEquals(expectedSyllable.second, syllables[index].endIndex)
        }
    }

    @Test
    fun openSyllables() {
        assertSyllables(q.word("mana"), 0 to 2, 2 to 4)

    }

    @Test
    fun consonantCluster() {
        val word = q.word("lasse")
        assertSyllables(word, 0 to 3, 3 to 5)
        val syllables = breakIntoSyllables(word)
        assertTrue(syllables[0].closed)
        assertFalse(syllables[1].closed)
    }

    @Test
    fun finalConsonant() {
        assertSyllables(q.word("atan"), 0 to 1, 1 to 4)
    }

    @Test
    fun diphthong() {
        assertSyllables(q.word("caita"), 0 to 3, 3 to 5)
    }

    @Test
    fun longConsonantCluster() {
        assertSyllables(q.word("monster"), 0 to 3, 3 to 7)
    }

    @Test
    fun oromardi() {
        assertSyllables(q.word("oromardi"), 0 to 1, 1 to 3, 3 to 6, 6 to 8)
    }

    @Test
    fun leadingConsonantCluster() {
        assertSyllables(q.word("stop"), 0 to 4)
    }
}
