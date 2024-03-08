package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Test

class RuleSequenceTest : QBaseTest() {
    @Test
    fun simpleSequence() {
        val repo = repoWithQ().apply {
            addLanguage(ce)
        }
        val qAiE = repo.rule("sound is 'a' and next sound is 'i':\n- new sound is 'e'", name = "q-ai-e")
        val qSfF = repo.rule("sound is 's' and next sound is 'f':\n- sound disappears") // inapplicable for this test
        val qWV = repo.rule("beginning of word and sound is 'w':\n- new sound is 'v'", name = "q-w-v")
        val seq = repo.addRuleSequence("ce-q", ce, q, listOf(qAiE, qSfF, qWV))
        val ceWord = repo.addWord("waiwai", language = ce)
        val qWord = repo.addWord("vaiwe", language = q)
        val link = repo.addLink(qWord, ceWord, Link.Derived, emptyList(), emptyList(), null)
        repo.applyRuleSequence(link, seq)
        assertEquals(2, link.rules.size)
    }

    @Test
    fun normalize() {
        val repo = repoWithQ().apply {
            addLanguage(ce)
        }
        ce.phonemes += Phoneme(listOf("c", "k"), setOf("voiceless", "velar", "stop", "consonant"))
        ce.phonemes += Phoneme(listOf("g"), setOf("voiced", "velar", "stop", "consonant"))
        q.phonemes = q.phonemes.filter { "c" !in it.graphemes && "k" !in it.graphemes } +
                Phoneme(listOf("c", "k"), setOf("voiceless", "velar", "stop", "consonant"))
        val qVoiceless = repo.rule("sound is voiceless stop:\n- voiceless becomes voiced", name = "q-voiceless")
        val seq = repo.addRuleSequence("ce-q", ce, q, listOf(qVoiceless))
        val ceWord = repo.addWord("aklar", language = ce)
        val qWord = repo.addWord("aglar", language = q)
        val link = repo.addLink(qWord, ceWord, Link.Derived, emptyList(), emptyList(), null)
        repo.applyRuleSequence(link, seq)
        assertEquals(1, link.rules.size)
    }
}
