package page.yole.etymograph

import org.junit.Assert.*
import org.junit.Test

class PhonemeRuleTest : QBaseTest() {
    @Test
    fun phonemeCondition() {
        val word = ce.word("khith")
        val it = PhonemeIterator(word, null)
        val cond = RelativePhonemeRuleCondition(false, null, PhonemePattern(null, "kh"), null)
        assertTrue(cond.matches(word, it))
    }

    @Test
    fun phonemeConditionParse() {
        val word = ce.word("khith")
        val it = PhonemeIterator(word, null)
        val cond = RuleCondition.parse(ParseBuffer("sound is 'kh'"), ce)
        assertTrue(cond.matches(word, it))
    }

    @Test
    fun previousSound() {
        val rule = parseRule(ce, q, "* i > 0 if previous sound is 'kh'")
        assertEquals("khthi", rule("khithi"))
    }

    @Test
    fun previousSoundPhonemeClass() {
        val rule = parseRule(q, q, "* i > 0 if previous sound is not vowel")
        assertEquals("khai", rule("khiai"))
    }

    @Test
    fun previousSoundPhonemeClassFirst() {
        val rule = parseRule(q, q, "* i > 0 if previous sound is not vowel")
        assertEquals("da", rule("ida"))
    }

    @Test
    fun previousSoundNegated() {
        val rule = parseRule(ce, q, "* i > 0 if previous sound is not 'kh'", assertTextRoundtrip = true)
        assertEquals("khith", rule("khithi"))
    }

    @Test
    fun nextSound() {
        val rule = parseRule(ce, q, "* i > 0 if next sound is 'kh'")
        assertEquals("khthis", rule("ikhthis"))
    }

    @Test
    fun nextPhonemeClass() {
        val rule = parseRule(q, q, "* i > 0 if next vowel is 'a'", assertTextRoundtrip = true)
        assertEquals("khtha", rule("khitha"))
    }

    @Test
    fun nextPhonemeClassComplex() {
        val rule = parseRule(q, q, "* i > 0 if next short vowel is 'a'", assertTextRoundtrip = true)
        assertEquals("khtha", rule("khitha"))
    }

    @Test
    fun nextSoundIs() {
        val rule = parseRule(q, q, " * s > z if next sound is 'p'".trimIndent())
        assertEquals("zpin", rule("spin"))
    }

    @Test
    fun nextSoundIsClassParameter() {
        val rule = parseRule(q, q, "* s > z if next sound is non-word-final 'p'", assertTextRoundtrip = true)
        assertEquals("zpisvosp", rule("spisvosp"))
    }

    @Test
    fun syllableIs() {
        val rule = parseRule(q, q, "* i > e if syllable is second to last and next vowel is 'a'", assertTextRoundtrip = true)
        assertEquals("findela", rule("findila"))
    }

    @Test
    fun syllableIsNegated() {
        val rule = parseRule(q, q, """
            * o > e if syllable is not last 
            * o > y if syllable is last
        """.trimIndent())
        assertEquals("yrch", rule("orch"))
        assertEquals("o > e if syllable is not last", rule.firstInstruction.toEditableText(graph))
    }

    @Test
    fun syllableIsOpen() {
        val rule = parseRule(q, q, "* i > e if syllable is open")
        assertEquals("wilwe", rule("wilwi"))
    }

    @Test
    fun prevSyllableIsClosed() {
        val rule = parseRule(q, q, "* a > e if previous syllable is closed")
        assertEquals("wilwena", rule("wilwana"))
    }

    @Test
    fun phonemeRulesWorkWithSoundValues() {
        val rule = parseRule(ce, q, "* ju > y")
        ce.phonemes = listOf(phoneme(listOf("y"), "j", "semivowel"))
        assertEquals("ylma", rule.apply(ce.word("yulma")).asOrthographic().text)
    }

    @Test
    fun phonemeClassesWithSoundValues() {
        val rule = parseRule(ce, ce, "* i > y if next sound is consonant")
        ce.phonemes = listOf(phoneme(listOf("c", "k"), "k", "consonant"))
        assertEquals("ycra", rule.apply(ce.word("ikra")).asOrthographic().text)
    }

    @Test
    fun applySoundRulePhonemic() {
        q.rule("* a > á", name = "q-lengthen")
        val rule = q.rule("""
            * V > V
            = apply sound rule 'q-lengthen'
        """.trimIndent())
        assertEquals("silá", rule("sila"))
    }

    @Test
    fun applySoundRulePhonemicNext() {
        q.rule("* a > á", name = "q-lengthen")
        val rule = q.rule("""
            * l > l
            = apply sound rule 'q-lengthen' to next vowel
        """.trimIndent())
        assertEquals("silá", rule("sila"))
    }

    @Test
    fun applySoundRulePhonemicNextComplexClass() {
        q.rule("* a > á", name = "q-lengthen")
        val rule = q.rule("""
            * s > s
             = apply sound rule 'q-lengthen' to next short vowel
        """.trimIndent())
        assertEquals("sílá", rule("síla"))
    }

    @Test
    fun applySoundRuleSpecialClass() {
        q.rule("* V > x", name = "q-lengthen")
        val rule = q.rule("""
            - apply sound rule 'q-lengthen' to second to last nucleus vowel
        """.trimIndent())
        assertEquals("lxita", rule("laita"))
    }

    @Test
    fun applySoundRuleSeesResultOfPreviousInstructions() {
        q.rule("* t > d / _C", name = "q-voicing")
        val rule = q.rule("""
            * a > 0
            = apply sound rule 'q-voicing' to previous sound
        """.trimIndent())
        assertEquals("dmo", rule("tamo"))
    }

    @Test
    fun phonemeEquality() {
        val rule = parseRule(q, q, "* a > 0 if sound is same as previous vowel", assertTextRoundtrip = true)
        assertEquals("glawre", rule("glaware"))
    }

    @Test
    fun phonemeEqualityNegated() {
        val rule = parseRule(q, q, "* a > 0 if sound is not same as previous vowel", assertTextRoundtrip = true)
        assertEquals("glaware", rule("glaware"))
        assertEquals("glewre", rule("gleware"))
    }

    @Test
    fun phonemeEqualityRelative() {
        val rule = parseRule(q, q, "* a > o if next sound is same as second next sound", assertTextRoundtrip = true)
        assertEquals("ottale", rule("attale"))
    }
}
