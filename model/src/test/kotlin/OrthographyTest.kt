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

    @Test
    fun orthographyRuleSPE() {
        val rule = parseRule(ce, ce, "* j > i / #_")
        ce.orthographyRule = RuleRef.to(rule)
        ce.phonemes = listOf(phoneme(listOf("y"), "j", "semivowel"))
        val iayn = ce.word("jajn").apply { isPhonemic = true }
        assertEquals("iayn", iayn.asOrthographic().text)
    }

    @Test
    fun orthographyRuleRemapSegments() {
        val rule = parseRule(ce, ce, "* ts > z")
        ce.orthographyRule = RuleRef.to(rule)
        val ats = ce.word("ats").apply {
            isPhonemic = true
            segments = listOf(WordSegment(1, 2, null, null, null, false))
        }
        val ortho = ats.asOrthographic()
        assertEquals("az", ortho.text)
        assertEquals(1, ortho.segments!![0].length)
    }

    @Test
    fun orthographyRuleReference() {
        val rule = parseRule(ce, ce, "* ks > x\n* ts > z")
        ce.orthographyRule = RuleRef.to(rule)
        val oksum = ce.word("oksum").apply { isPhonemic = true }
        val oks = ce.word("oks")
        val ox = ce.word("ox")
        assertEquals("oksum", oksum.asOrthographic(oks).text)
        assertEquals("oxum", oksum.asOrthographic(ox).text)

        val voxtr = ce.word("voxtr")
        val voxts = ce.word("voksts").apply { isPhonemic = true }
        assertEquals("voxts", voxts.asOrthographic(voxtr).text)
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

    @Test
    fun pronunciationRuleRemapSegments() {
        val rule = parseRule(ce, ce, "* ts > z")
        ce.pronunciationRule = RuleRef.to(rule)
        val ats = ce.word("ats").apply {
            segments = listOf(WordSegment(1, 2, null, null, null, false))
        }
        val phono = ats.asPhonemic()
        assertEquals("az", phono.text)
        assertEquals(1, phono.segments!![0].length)
    }
}
