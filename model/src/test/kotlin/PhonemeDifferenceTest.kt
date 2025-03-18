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
    }

    @Test
    fun singlePhonemeDifference() {
        assertEquals("∅ -> d", getSinglePhonemeDifference(oe.word("wierdan"), oe.word("wierddan")))
        assertEquals("d -> ∅", getSinglePhonemeDifference(oe.word("wierddan"), oe.word("wierdan")))
        assertEquals("∅ -> e", getSinglePhonemeDifference(oe.word("aldor"), oe.word("ealdor")))
        assertEquals("∅ -> n", getSinglePhonemeDifference(oe.word("haca"), oe.word("hacan")))
    }

    @Test
    fun normalizeSpelling() {
        assertEquals("e -> a", getSinglePhonemeDifference(oe.word("wrīðen"), oe.word("wrīþan-")))
    }
}
