package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhonemeRuleTest : QBaseTest() {

    @Test
    fun phonemeCondition() {
        val it = PhonemeIterator(ce.word("khith"))
        val cond = LeafRuleCondition(ConditionType.PhonemeMatches, null, "kh", false)
        assertTrue(cond.matches(it))
    }

    @Test
    fun phonemeConditionParse() {
        val it = PhonemeIterator(ce.word("khith"))
        val cond = RuleCondition.parse(ParseBuffer("sound is 'kh'"), ce)
        assertTrue(cond.matches(it))
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
    }

    @Test
    fun previousSound() {
        val rule = parseRule(ce, q, """
            sound is 'i' and previous sound is 'kh':
            - sound disappears
        """.trimIndent())
        assertEquals("khthi", rule.apply(ce.word("khithi"), emptyRepo).text)
    }

    @Test
    fun previousSoundPhonemeClass() {
        val rule = parseRule(q, q, """
            sound is 'i' and previous sound is not vowel:
            - sound disappears
        """.trimIndent())
        assertEquals("khai", rule.apply(ce.word("khiai"), emptyRepo).text)
    }

    @Test
    fun previousSoundPhonemeClassFirst() {
        val rule = parseRule(q, q, """
            sound is 'i' and previous sound is not vowel:
            - sound disappears
        """.trimIndent())
        assertEquals("da", rule.apply(ce.word("ida"), emptyRepo).text)
    }

    @Test
    fun previousSoundNegated() {
        val rule = parseRule(ce, q, """
            sound is 'i' and previous sound is not 'kh':
            - sound disappears
        """.trimIndent())
        assertEquals("khith", rule.apply(ce.word("khithi"), emptyRepo).text)
        assertEquals(
            "sound is 'i' and previous sound is not 'kh'",
            rule.logic.branches[0].condition.toEditableText()
        )
    }

    @Test
    fun nextSound() {
        val rule = parseRule(ce, q, """
            sound is 'i' and next sound is 'kh':
            - sound disappears
        """.trimIndent())
        assertEquals("khthis", rule.apply(ce.word("ikhthis"), emptyRepo).text)
    }

    @Test
    fun nextPhonemeClass() {
        val rule = parseRule(q, q, """
            sound is 'i' and next vowel is 'a':
            - sound disappears
        """.trimIndent())
        assertEquals("khtha", rule.apply(ce.word("khitha"), emptyRepo).text)
        assertEquals("sound is 'i' and next vowel is 'a'", rule.logic.branches[0].condition.toEditableText())
    }
}
