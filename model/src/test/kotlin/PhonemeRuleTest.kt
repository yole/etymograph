package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.yole.etymograph.JsonGraphRepository.Companion.toSerializedFormat

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
    fun nextSoundDisappears() {
        val rule = parseRule(ce, q, """
            sound is 'm' and next sound is 'b':
            - next sound disappears
            sound is 'b':
            - new sound is 'w'
        """.trimIndent())
        assertEquals("mawa", rule.apply(ce.word("mbaba"), emptyRepo).text)
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

    @Test
    fun diphtong() {
        val rule = parseRule(q, q, """
            sound is diphthong:
            - no change
            sound is 'a':
            - new sound is 'o'
        """.trimIndent())
        assertEquals("ainu", rule.apply(q.word("ainu"), emptyRepo).text)
        assertEquals("omo", rule.apply(q.word("ama"), emptyRepo).text)
    }

    @Test
    fun changePhonemeClass() {
        val rule = parseRule(q, q, """
            sound is voiceless stop and next sound is nasal:
            - voiceless becomes voiced
        """.trimIndent())
        assertEquals("utubnu", rule.apply(q.word("utupnu"), emptyRepo).text)

        val ruleInstruction = rule.logic.branches[0].instructions[0]
        assertEquals("voiceless becomes voiced", ruleInstruction.toEditableText())

        val data = ruleInstruction.toSerializedFormat()
        val deserialized = JsonGraphRepository.ruleInstructionFromSerializedFormat(emptyRepo, q, data)
        assertEquals("voiceless becomes voiced", deserialized.toEditableText())
    }

    @Test
    fun changePreviousPhonemeClass() {
        val rule = parseRule(q, q, """
            sound is nasal and previous sound is voiceless stop:
            - previous voiceless becomes voiced
        """.trimIndent())
        assertEquals("utubnu", rule.apply(q.word("utupnu"), emptyRepo).text)

        val ruleInstruction = rule.logic.branches[0].instructions[0]
        assertEquals("previous voiceless becomes voiced", ruleInstruction.toEditableText())

        val data = ruleInstruction.toSerializedFormat()
        val deserialized = JsonGraphRepository.ruleInstructionFromSerializedFormat(emptyRepo, q, data)
        assertEquals("previous voiceless becomes voiced", deserialized.toEditableText())
    }
}
