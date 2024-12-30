package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleSequenceTest : QBaseTest() {
    @Test
    fun simpleSequence() {
        val repo = repoWithQ().with(ce)
        val qAiE = repo.rule("sound is 'a' and next sound is 'i':\n- new sound is 'e'", name = "q-ai-e")
        val qSfF = repo.rule("sound is 's' and next sound is 'f':\n- sound disappears") // inapplicable for this test
        val qWV = repo.rule("beginning of word and sound is 'w':\n- new sound is 'v'", name = "q-w-v")
        val seq = repo.addRuleSequence("ce-q", ce, q, listOf(qAiE.step(), qSfF.step(), qWV.step()))
        val ceWord = repo.addWord("waiwai", language = ce)
        val qWord = repo.addWord("vaiwe", language = q)
        val link = repo.addLink(qWord, ceWord, Link.Derived, emptyList())
        repo.applyRuleSequence(link, seq)
        assertEquals(2, link.rules.size)
    }

    @Test
    fun simpleSequenceSPE() {
        val repo = repoWithQ().with(ce)
        val qAiE = repo.rule("sound is 'a' and next sound is 'i':\n- new sound is 'e'", name = "q-ai-e")
        val qSfF = repo.rule("* s -> 0 / _f") // inapplicable for this test
        val qWV = repo.rule("beginning of word and sound is 'w':\n- new sound is 'v'", name = "q-w-v")
        val seq = repo.addRuleSequence("ce-q", ce, q, listOf(qAiE.step(), qSfF.step(), qWV.step()))
        val ceWord = repo.addWord("waiwai", language = ce)
        val qWord = repo.addWord("vaiwe", language = q)
        val link = repo.addLink(qWord, ceWord, Link.Derived, emptyList())
        repo.applyRuleSequence(link, seq)
        assertEquals(2, link.rules.size)
    }

    @Test
    fun simpleSequencePhonemic() {
        val repo = repoWithQ().with(ce)
        val vowelLengthening = repo.rule("sound is 'a' and next sound is voiced:\n- short becomes long",
            name = "q-vowel-lengthening")
        val rule1 = repo.rule("sound is 'a' and next sound is 'i':\n- new sound is 'e'", name = "q-ai-e")
        val rule2 = repo.rule("* z -> r")
        val rule3 = repo.rule("- apply sound rule 'q-vowel-lengthening' to first vowel", name = "q-w-v")
        val seq = repo.addRuleSequence("ce-q", ce, q, listOf(rule1.step(), rule2.step(), rule3.step()))
        val ceWord = repo.addWord("suzja", language = ce)
        val qWord = repo.addWord("surya", language = q)
        val link = repo.addLink(qWord, ceWord, Link.Derived, emptyList())
        repo.applyRuleSequence(link, seq)
        assertEquals(1, link.rules.size)
    }

    @Test
    fun optionalSteps() {
        val repo = repoWithQ().with(ce)
        val qAiE = repo.rule("sound is 'a' and next sound is 'i':\n- new sound is 'e'", name = "q-ai-e")
        val qSfF = repo.rule("sound is 's' and next sound is 'f':\n- sound disappears") // inapplicable for this test
        val qWV = repo.rule("beginning of word and sound is 'w':\n- new sound is 'v'", name = "q-w-v")
        val seq = repo.addRuleSequence("ce-q", ce, q, listOf(qAiE.step(), qSfF.step(), qWV.step(true)))
        val ceWord = repo.addWord("waiwai", language = ce)
        val qWord = repo.addWord("weiwei", language = q)
        val link = repo.addLink(qWord, ceWord, Link.Derived, emptyList())
        repo.applyRuleSequence(link, seq)
        assertEquals(1, link.rules.size)
    }

    @Test
    fun normalize() {
        val repo = repoWithQ().with(ce)
        ce.phonemes += Phoneme(-1, listOf("c", "k"), null, setOf("voiceless", "velar", "stop", "consonant"))
        ce.phonemes += Phoneme(-1, listOf("g"), null, setOf("voiced", "velar", "stop", "consonant"))
        q.phonemes = q.phonemes.filter { "c" !in it.graphemes && "k" !in it.graphemes } +
                Phoneme(-1, listOf("c", "k"), null, setOf("voiceless", "velar", "stop", "consonant"))
        val qVoiceless = repo.rule("sound is voiceless stop:\n- voiceless becomes voiced", name = "q-voiceless")
        val seq = repo.addRuleSequence("ce-q", ce, q, listOf(qVoiceless.step()))
        val ceWord = repo.addWord("aklar", language = ce)
        val qWord = repo.addWord("aglar", language = q)
        val link = repo.addLink(qWord, ceWord, Link.Derived, emptyList())
        repo.applyRuleSequence(link, seq)
        assertEquals(1, link.rules.size)
    }

    @Test
    fun deleteRule() {
        val repo = repoWithQ().with(ce)
        val qVoiceless = repo.rule("sound is voiceless stop:\n- voiceless becomes voiced", name = "q-voiceless")
        val seq = repo.addRuleSequence("ce-q", ce, q, listOf(qVoiceless.step()))
        repo.deleteRule(qVoiceless)
        assertTrue(seq.steps.isEmpty())
    }
}

private fun Rule.step(optional: Boolean = false) = RuleSequenceStep(this, optional)
