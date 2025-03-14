package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Test

class PhonemeDifferenceTest {
    @Test
    fun singlePhonemeDifference() {
        assertEquals("∅ -> d", getSinglePhonemeDifference("wierdan", "wierddan"))
        assertEquals("d -> ∅", getSinglePhonemeDifference("wierddan", "wierdan"))
        assertEquals("∅ -> e", getSinglePhonemeDifference("aldor", "ealdor"))
    }
}