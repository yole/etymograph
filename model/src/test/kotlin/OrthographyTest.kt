package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Test

class OrthographyTest : QBaseTest() {
    @Test
    fun simpleRoundtrip() {
        ce.phonemes = listOf(phoneme(listOf("y"), "j", "semivowel"))
        val yulma = ce.word("yulma")
        val phonemic = yulma.asPhonemic()
        assertEquals("julma", phonemic.text)

        val ortho = phonemic.asOrthographic()
        assertEquals("yulma", ortho.text)
    }

    @Test
    fun normalize() {
        ce.phonemes = listOf(phoneme(listOf("á", "ā"), null, "vowel"))
        val yulma = ce.word("yulmā")
        val phonemic = yulma.asPhonemic()
        assertEquals("yulmá", phonemic.text)
    }

    @Test
    fun orthographyRule() {
        val rule = parseRule(ce, ce, "sound is word-initial 'j':\n- new sound is 'i'")
        ce.orthographyRule = RuleRef.to(rule)
        ce.phonemes = listOf(phoneme(listOf("y"), "j", "semivowel"))
        val iayn = ce.word("jajn").apply { isPhonemic = true }
        assertEquals("iayn", iayn.asOrthographic().text)
    }

    @Test fun pronunciationRule() {
        ce.phonemes = listOf(
            phoneme(listOf("y"), "j", "semivowel"),
            phoneme("a", "vowel")
        )
        val rule = parseRule(ce, ce, "sound is word-initial 'i' and next sound is vowel:\n- new sound is 'j'")
        ce.pronunciationRule = RuleRef.to(rule)
        val iayn = ce.word("iayn")
        assertEquals("jajn", iayn.asPhonemic().text)
    }
}
