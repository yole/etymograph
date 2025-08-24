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
    fun soundCorrespondence() {
        val rule = parseRule(
            ce, q,
            """
            sound is 'th':
            - new sound is 's'
            sound is 'kh':
            - new sound is 'h'
            """.trimIndent()
        )
        assertEquals("his", rule.apply(ce.word("khith"), emptyRepo).text)
        assertEquals("'th' > 's', 'kh' > 'h'", rule.toSummaryText(repo))
    }

    @Test
    fun soundDisappears() {
        val rule = parseRule(ce, q, """
            sound is 'i':
            - sound disappears
            sound is 'th':
            - new sound is 's'
        """.trimIndent())
        assertEquals("khs", rule.apply(ce.word("khithi"), emptyRepo).text)
        assertEquals("'i' > Ø, 'th' > 's'", rule.toSummaryText(repo))
        assertTrue(rule.refersToPhoneme(ce.phonemes.find { "th" in it.graphemes }!!))
        assertFalse(rule.refersToPhoneme(q.phonemes.find { "t" in it.graphemes }!!))
    }

    @Test
    fun soundDisappearsPreserveStress() {
        val rule = parseRule(ce, q, """
            sound is 'i':
            - sound disappears
        """)
        val word = ce.word("miena")
        word.stressedPhonemeIndex = 2
        word.explicitStress = true
        val newWord = rule.apply(word, emptyRepo)
        assertEquals("mena", newWord.text)
        assertEquals(1, newWord.stressedPhonemeIndex)
    }

    @Test
    fun nextSoundDisappears() {
        val rule = parseRule(ce, q, """
            sound is 'm' and next sound is 'b':
            - next sound disappears
            sound is 'b':
            - new sound is 'w'
        """.trimIndent())
        assertEquals("mawa", rule.apply(ce.word("mbaba"), emptyRepo).text)
        assertEquals("'mb' > 'm', 'b' > 'w'", rule.toSummaryText(repo))
    }

    @Test
    fun previousSoundDisappears() {
        val rule = parseRule(q, q, """
            sound is 'z' and previous sound is 'i' and second previous sound is 'a':
            - previous sound disappears
            - sound disappears
        """.trimIndent())
        assertEquals("ma", applyRule(rule, q.word("maiz")))
        assertEquals("previous sound disappears", rule.logic.branches[0].instructions[0].toEditableText(repo))
    }

    @Test
    fun previousSoundDisappearsSummary() {
        val rule = parseRule(q, q, """
            sound is 'z' and previous sound is 'i' and second previous sound is vowel:
            - sound disappears
        """.trimIndent())
        assertEquals("'z' > Ø after 'i' preceded by vowel", rule.toSummaryText(repo))
    }

    @Test
    fun secondNextSoundDisappears() {
        val rule = parseRule(q, q, """
            second next sound is 'z':
            - next sound disappears
            - second next sound disappears
        """.trimIndent())
        assertEquals("ma", applyRule(rule, q.word("maiz")))
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
        assertEquals("'o' > 'e' in not last syllable", rule.logic.branches[0].toSummaryText(repo, true))
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
    fun summaryWithOr() {
        val rule = parseRule(q, q, """
            sound is 'i' and (previous sound is 'a' or previous sound is 'o'):
            - new sound is 'e'
        """.trimIndent())
        assertEquals("'i' > 'e' after 'a' or 'o'", rule.toSummaryText(repo))
    }

    @Test
    fun summaryBeforeAfter() {
        val rule = parseRule(q, q, """
            sound is 'kh' and previous sound is vowel and next sound is 'th':
            - new sound is 'i'
        """.trimIndent())
        assertEquals("'kh' > 'i' after vowel before 'th'", rule.toSummaryText(repo))
    }

    @Test
    fun phonemeRulesWorkWithSoundValues() {
        val rule = parseRule(ce, q, """
            sound is 'j' and next sound is 'u':
            - new sound is 'y'
            - next sound disappears
        """.trimIndent())
        ce.phonemes = listOf(phoneme(listOf("y"), "j", "semivowel"))
        assertEquals("ylma", rule.apply(ce.word("yulma"), emptyRepo).asOrthographic().text)
    }

    @Test
    fun phonemeClassesWithSoundValues() {
        val rule = parseRule(ce, ce, """
            sound is 'i' and next sound is consonant:
            - new sound is 'y'
        """.trimIndent())
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
        val soundRule = parseRule(q, q, """
            sound is 't' and next sound is consonant:
            - new sound is 'd'
        """.trimIndent(), name = "q-voicing")
        val parseContext = q.parseContext(null, soundRule)
        val rule = parseRule(q, q, """
            sound is 'a':
            - sound disappears
            - apply sound rule 'q-voicing' to previous sound
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

    @Test
    fun postInstructions() {
        val rule = parseRule(q, q, """
            sound is syllable-final 'n':
            - new sound is 'm'
            = previous sound disappears
        """.trimIndent())
        assertEquals("inmdm", applyRule(rule, q.word("inonden")))
    }

    @Test
    fun applyRulePhonemic() {
        val rule1 = repo.rule("sound is 'u' and previous sound is 'v':\n- previous sound disappears",
            name = "q-v-removal")
        val rule2 = repo.rule("sound is 'i':\n- new sound is 'u'\n - apply rule 'q-v-removal'",
            name = "q-umlaut")
        val rule3 = repo.rule("- apply sound rule 'q-umlaut' to first vowel")
        assertEquals("unna", applyRule(rule3, q.word("vinna")))
    }
}
