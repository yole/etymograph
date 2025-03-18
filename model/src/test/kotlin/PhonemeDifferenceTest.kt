package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PhonemeDifferenceTest {
    private lateinit var oe: Language

    @Before
    fun setup() {
        oe = Language("Old English", "OE")
        oe.phonemes = listOf(
            phoneme(listOf("þ", "ð"))
        )
        oe.diphthongs = listOf("īe", "ēo")
    }

    @Test
    fun singlePhonemeDifference() {
        assertEquals("∅ -> d", getSinglePhonemeDifference(oe.word("wierdan"), oe.word("wierddan")).toString())
        assertEquals("d -> ∅", getSinglePhonemeDifference(oe.word("wierddan"), oe.word("wierdan")).toString())
        assertEquals("∅ -> e", getSinglePhonemeDifference(oe.word("aldor"), oe.word("ealdor")).toString())
        assertEquals("∅ -> n", getSinglePhonemeDifference(oe.word("haca"), oe.word("hacan")).toString())
    }

    @Test
    fun normalizeSpelling() {
        assertEquals("e -> a", getSinglePhonemeDifference(oe.word("wrīðen"), oe.word("wrīþan-")).toString())
    }

    @Test
    fun diphthong() {
        assertEquals("īe -> ēo", getSinglePhonemeDifference(oe.word("tīen"), oe.word("tēon")).toString())
    }
}
