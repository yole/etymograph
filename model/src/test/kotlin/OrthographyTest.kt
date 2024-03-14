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

    @Test
    fun normalize() {
        ce.phonemes = listOf(Phoneme(listOf("á", "ā"), null, setOf("vowel")))
        val yulma = ce.word("yulmā")
        val phonemic = yulma.asPhonemic()
        assertEquals("yulmá", phonemic.text)
    }

    @Test
    fun orthographyRule() {
        val rule = parseRule(ce, ce, "beginning of word and sound is 'j':\n- new sound is 'i'")
        ce.orthographyRule = RuleRef.to(rule)
        ce.phonemes = listOf(Phoneme(listOf("y"), "j", setOf("semivowel")))
        val iayn = ce.word("jajn").apply { isPhonemic = true }
        assertEquals("iayn", iayn.asOrthographic().text)
    }
}
