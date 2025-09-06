package ru.yole.etymograph

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PhonemeRuleTest : QBaseTest() {
    lateinit var repo: GraphRepository

    @Before
    fun setup() {
        repo = repoWithQ()
    }

    @Test
    fun phonemeCondition() {
        val word = ce.word("khith")
        val it = PhonemeIterator(word, null)
        val cond = RelativePhonemeRuleCondition(false, null, PhonemePattern(null, "kh"), null)
        assertTrue(cond.matches(word, it, emptyRepo))
    }

    @Test
    fun phonemeConditionParse() {
        val word = ce.word("khith")
        val it = PhonemeIterator(word, null)
        val cond = RuleCondition.parse(ParseBuffer("sound is 'kh'"), ce)
        assertTrue(cond.matches(word, it, emptyRepo))
    }

    @Test
    fun previousSound() {
        val rule = parseRule(ce, q, "* i > 0 if previous sound is 'kh'")
        assertEquals("khthi", rule.apply(ce.word("khithi"), emptyRepo).text)
    }

    @Test
    fun previousSoundPhonemeClass() {
        val rule = parseRule(q, q, "* i > 0 if previous sound is not vowel")
        assertEquals("khai", rule.apply(ce.word("khiai"), emptyRepo).text)
    }

    @Test
    fun previousSoundPhonemeClassFirst() {
        val rule = parseRule(q, q, "* i > 0 if previous sound is not vowel")
        assertEquals("da", rule.apply(ce.word("ida"), emptyRepo).text)
    }

    @Test
    fun previousSoundNegated() {
        val text = "* i > 0 if previous sound is not 'kh'"
        val rule = parseRule(ce, q, text)
        assertEquals("khith", rule.apply(ce.word("khithi"), emptyRepo).text)
        assertEquals(text, rule.toEditableText(repo))
    }

    @Test
    fun nextSound() {
        val rule = parseRule(ce, q, "* i > 0 if next sound is 'kh'")
        assertEquals("khthis", rule.apply(ce.word("ikhthis"), emptyRepo).text)
    }

    @Test
    fun nextPhonemeClass() {
        val text = "* i > 0 if next vowel is 'a'"
        val rule = parseRule(q, q, text)
        assertEquals("khtha", rule.apply(ce.word("khitha"), emptyRepo).text)
        assertEquals(text, rule.toEditableText(repo))
    }

    @Test
    fun nextPhonemeClassComplex() {
        val text = "* i > 0 if next short vowel is 'a'"
        val rule = parseRule(q, q, text)
        assertEquals("khtha", rule.apply(ce.word("khitha"), emptyRepo).text)
        assertEquals(text, rule.toEditableText(repo))
    }

    @Test
    fun nextSoundIs() {
        val rule = parseRule(q, q, " * s > z if next sound is 'p'".trimIndent())
        assertEquals("zpin", rule.apply(q.word("spin"), emptyRepo).text)
    }

    @Test
    fun nextSoundIsClassParameter() {
        val text = "* s > z if next sound is non-word-final 'p'"
        val rule = parseRule(q, q, text)
        assertEquals("zpisvosp", rule.apply(q.word("spisvosp"), emptyRepo).text)
        assertEquals(text, rule.toEditableText(repo))
    }

    @Test
    fun syllableIs() {
        val text = "* i > e if syllable is second to last and next vowel is 'a'"
        val rule = parseRule(q, q, text)
        assertEquals("findela", rule.apply(q.word("findila"), emptyRepo).text)
        assertEquals(text, rule.toEditableText(repo))
    }

    @Test
    fun syllableIsNegated() {
        val rule = parseRule(q, q, """
            syllable is not last and sound is 'o':
            - new sound is 'e'
            syllable is last and sound is 'o':
            - new sound is 'y'
        """.trimIndent())
        assertEquals("yrch", rule.apply(q.word("orch"), emptyRepo).text)
        val ruleCondition = rule.logic.branches[0].condition
        assertEquals("syllable is not last and sound is 'o'", ruleCondition.toEditableText())
    }

    @Test
    fun syllableIsOpen() {
        val rule = parseRule(q, q, "* i > e if syllable is open")
        assertEquals("wilwe", applyRule(rule, q.word("wilwi")))
    }

    @Test
    fun prevSyllableIsClosed() {
        val rule = parseRule(q, q, "* a > e if previous syllable is closed")
        assertEquals("wilwena", applyRule(rule, q.word("wilwana")))
    }

    @Test
    fun phonemeRulesWorkWithSoundValues() {
        val rule = parseRule(ce, q, "* ju > y")
        ce.phonemes = listOf(phoneme(listOf("y"), "j", "semivowel"))
        assertEquals("ylma", rule.apply(ce.word("yulma"), emptyRepo).asOrthographic().text)
    }

    @Test
    fun phonemeClassesWithSoundValues() {
        val rule = parseRule(ce, ce, "* i > y if next sound is consonant")
        ce.phonemes = listOf(phoneme(listOf("c", "k"), "k", "consonant"))
        assertEquals("ycra", rule.apply(ce.word("ikra"), emptyRepo).asOrthographic().text)
    }

    @Test
    fun applySoundRulePhonemic() {
        val soundRule = parseRule(q, q, """
            sound is 'a':
            - new sound is 'á'
        """.trimIndent(), name = "q-lengthen")
        val parseContext = q.parseContext(null, soundRule)
        val rule = parseRule(q, q, """
            sound is vowel:
            - apply sound rule 'q-lengthen'
        """.trimIndent(), context = parseContext)
        assertEquals("silá", rule.apply(q.word("sila"), emptyRepo).text)
    }

    @Test
    fun applySoundRulePhonemicNext() {
        val soundRule = parseRule(q, q, """
            sound is 'a':
            - new sound is 'á'
        """.trimIndent(), name = "q-lengthen")
        val parseContext = q.parseContext(null, soundRule)
        val rule = parseRule(q, q, """
            sound is 'l':
            - apply sound rule 'q-lengthen' to next vowel
        """.trimIndent(), context = parseContext)
        assertEquals("silá", rule.apply(q.word("sila"), emptyRepo).text)
    }

    @Test
    fun applySoundRulePhonemicNextComplexClass() {
        val soundRule = parseRule(q, q, """
            sound is 'a':
            - new sound is 'á'
        """.trimIndent(), name = "q-lengthen")
        val parseContext = q.parseContext(null, soundRule)
        val rule = parseRule(q, q, """
            sound is 's':
            - apply sound rule 'q-lengthen' to next short vowel
        """.trimIndent(), context = parseContext)
        assertEquals("sílá", rule.apply(q.word("síla"), emptyRepo).text)
    }

    @Test
    fun applySoundRuleSpecialClass() {
        val soundRule = parseRule(q, q, "* V > x", name = "q-lengthen")
        val parseContext = q.parseContext(null, soundRule)
        val rule = parseRule(q, q, """
            - apply sound rule 'q-lengthen' to second to last nucleus vowel
        """.trimIndent(), context = parseContext)
        assertEquals("lxita", rule.apply(q.word("laita"), emptyRepo).text)
    }

    @Test
    fun applySoundRuleSeesResultOfPreviousInstructions() {
        val soundRule = parseRule(q, q, "* t > d / _C", name = "q-voicing")
        val parseContext = q.parseContext(null, soundRule)
        val rule = parseRule(q, q, """
            * a > 0
            = apply sound rule 'q-voicing' to previous sound
        """.trimIndent(), context = parseContext)
        assertEquals("dmo", applyRule(rule, q.word("tamo")))
    }

    @Test
    fun phonemeEquality() {
        val text = "* a > 0 if sound is same as previous vowel"
        val rule = parseRule(q, q, text)
        assertEquals("glawre", rule.apply(q.word("glaware"), emptyRepo).text)
        assertEquals(text, rule.toEditableText(repo))
    }

    @Test
    fun phonemeEqualityNegated() {
        val text = "* a > 0 if sound is not same as previous vowel"
        val rule = parseRule(q, q, text)
        assertEquals("glaware", applyRule(rule, q.word("glaware")))
        assertEquals("glewre", applyRule(rule, q.word("gleware")))
        assertEquals(text, rule.toEditableText(repo))
    }

    @Test
    fun phonemeEqualityRelative() {
        val text = "* a > o if next sound is same as second next sound"
        val rule = parseRule(q, q, text)
        assertEquals("ottale", rule.apply(q.word("attale"), emptyRepo).text)
        assertEquals(text, rule.toEditableText(repo))
    }
}
