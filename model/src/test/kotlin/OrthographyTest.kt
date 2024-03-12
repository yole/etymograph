package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Test

class OrthographyTest : QBaseTest() {
    @Test
    fun simpleRoundtrip() {
        ce.phonemes = listOf(Phoneme(listOf("y"), "j", setOf("semivowel")))
        val yulma = ce.word("yulma")
        val phonemic = yulma.asPhonemic()
        assertEquals("julma", phonemic.text)

        val ortho = phonemic.asOrthographic()
        assertEquals("yulma", ortho.text)
    }
}
