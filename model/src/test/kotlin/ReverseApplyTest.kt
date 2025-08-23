package ru.yole.etymograph

import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Test

class ReverseApplyTest : QBaseTest() {

    @Test
    fun reverseApply() {
        val rule = parseRule(q, q, "- append 'llo'")
        val candidates = rule.reverseApply(q.word("hrestallo"), emptyRepo)
        assertEquals(1, candidates.size)
        assertEquals("hresta", candidates[0])
    }

    @Test
    fun reverseApplyAppend() {
        val rule = parseRule(q, q, "- append 'llo'")
        val candidates = rule.reverseApply(q.word("hrestallo"), emptyRepo)
        assertEquals(1, candidates.size)
        assertEquals("hresta", candidates[0])
    }

    @Test
    fun reverseApplyNormalize() {
        val rule = parseRule(q, q, "- append 'sse'")
        val candidates = rule.reverseApply(q.word("auressë"), emptyRepo)
        assertEquals(1, candidates.size)
        assertEquals("aure", candidates[0])
    }

    @Test
    fun reverseApplyMatch() {
        val rule = parseRule(q, q, "word ends with a consonant:\n- append 'i'")
        val candidate = rule.reverseApply(q.word("nai"), emptyRepo)
        assertEquals(0, candidate.size)
    }

    @Test
    fun reverseApplyChangeEnding() {
        val rule = parseRule(q, q, "word ends with 'ea':\n- change ending to 'ie'")
        val candidate = rule.reverseApply(q.word("yaimie"), emptyRepo)
        assertEquals("yaimea", candidate.single())
    }

    @Test
    fun reverseApplyIgnoreClass() {
        q.wordClasses.add(WordCategory("gender", listOf("N"), listOf(WordCategoryValue("female", "f"))))
        val rule = parseRule(q, q, "word ends with 'ea' and word is f:\n- change ending to 'ie'")
        val candidate = rule.reverseApply(q.word("yaimie"), emptyRepo)
        assertEquals("yaimea", candidate.single())
    }

    @Test
    fun reverseApplyIgnoreClassNegated() {
        q.wordClasses.add(WordCategory("gender", listOf("N"), listOf(WordCategoryValue("female", "f"))))
        val rule = parseRule(q, q, "word ends with 'ea' and word is not f:\n- change ending to 'ie'")
        val candidate = rule.reverseApply(q.word("yaimie"), emptyRepo)
        assertEquals("yaimea", candidate.single())
    }

    @Test
    fun reverseApplyChangeEndingOr() {
        val rule = parseRule(q, q, "word ends with 'ea' or word ends with 'ao':\n- change ending to 'ie'")
        val candidates = rule.reverseApply(q.word("yaimie"), emptyRepo)
        assertEquals(2, candidates.size)
        Assert.assertTrue("yaimao" in candidates)
    }

    @Test
    fun reverseApplyToPhoneme() {
        val rule = parseRule(q, q, "sound is 'i':\n- new sound is 'í'")
        val phonemes = PhonemeIterator(q.word("círa"), null)
        phonemes.advanceTo(1)
        assertEquals(listOf("cira"), rule.reverseApplyToPhoneme(phonemes))

        val applySoundRuleInstruction = ApplySoundRuleInstruction(q, RuleRef.to(rule), "first vowel", null)
        assertEquals("cira", applySoundRuleInstruction.reverseApply(rule, "círa", q, emptyRepo).single())
    }

    @Test
    fun reverseApplyToPhonemeNoChange() {
        val rule = parseRule(q, q, "sound is 'e':\n- no change\nsound is 'i':\n- new sound is 'í'")
        val phonemes = PhonemeIterator(q.word("círa"), null)
        phonemes.advanceTo(1)
        assertEquals(listOf("cira"), rule.reverseApplyToPhoneme(phonemes))

        val applySoundRuleInstruction = ApplySoundRuleInstruction(q, RuleRef.to(rule), "first vowel", null)
        assertEquals("cira", applySoundRuleInstruction.reverseApply(rule, "círa", q, emptyRepo).single())
    }

    @Test
    fun reverseApplyMultiple() {
        val rule = parseRule(q, q, "word ends with a consonant:\n- append 'ala'\notherwise:\n- append 'la'")
        val candidates = rule.reverseApply(q.word("picala"), emptyRepo)
        assertEquals(2, candidates.size)
    }

    @Test
    fun reverseApplyPreInstructions() {
        val rule = parseRule(q, q, "- prepend 'a'\nword ends with vowel:\n- append 'e'")
        val candidates = rule.reverseApply(q.word("acirae"), emptyRepo)
        assertEquals("cira", candidates.single())
    }

    @Test
    fun reverseApplyNoChange() {
        val rule = parseRule(q, q, "word ends with vowel:\n- no change")
        val candidates = rule.reverseApply(q.word("cira"), emptyRepo)
        assertEquals("cira", candidates.single())
    }
}
