package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RuleSequenceTest : QBaseTest() {
    private lateinit var aq: Language

    @Before
    fun setup() {
        aq = graph.addLanguage("Ancient Quenya", "AQ")
    }

    @Test
    fun simpleSequence() {
        val qAiE = graph.rule("* a > e / _i", name = "q-ai-e")
        val qSfF = graph.rule("* s > 0 / _f") // inapplicable for this test
        val qWV = graph.rule("* w > v / #_", name = "q-w-v")
        val seq = graph.addRuleSequence("ce-q", ce, q, listOf(qAiE.step(), qSfF.step(), qWV.step()))
        val ceWord = graph.addWord("waiwai", language = ce)
        val qWord = graph.addWord("vaiwe", language = q)
        val link = graph.addLink(qWord, ceWord, Link.Origin)
        graph.applyRuleSequence(link, seq)
        assertEquals(2, link.rules.size)
        assertEquals(seq, link.sequence)
    }

    @Test
    fun simpleSequenceSPE() {
        val qAiE = graph.rule("* a > e / _i", name = "q-ai-e")
        val qSfF = graph.rule("* s > 0 / _f") // inapplicable for this test
        val qWV = graph.rule("* w > v / #_", name = "q-w-v")
        val seq = graph.addRuleSequence("ce-q", ce, q, listOf(qAiE.step(), qSfF.step(), qWV.step()))
        val ceWord = graph.addWord("waiwai", language = ce)
        val qWord = graph.addWord("vaiwe", language = q)
        val link = graph.addLink(qWord, ceWord, Link.Origin)
        graph.applyRuleSequence(link, seq)
        assertEquals(2, link.rules.size)
    }

    @Test
    fun simpleSequencePhonemic() {
        val vowelLengthening = graph.rule("* a > ā / _[+voice]",
            name = "q-vowel-lengthening")
        val rule1 = graph.rule("* a > e / _i", name = "q-ai-e")
        val rule2 = graph.rule("* z -> r")
        val rule3 = graph.rule("- apply sound rule 'q-vowel-lengthening' to first vowel", name = "q-w-v")
        val seq = graph.addRuleSequence("ce-q", ce, q, listOf(rule1.step(), rule2.step(), rule3.step()))
        val ceWord = graph.addWord("suzja", language = ce)
        val qWord = graph.addWord("surya", language = q)
        val link = graph.addLink(qWord, ceWord, Link.Origin)
        graph.applyRuleSequence(link, seq)
        assertEquals(1, link.rules.size)
    }

    @Test
    fun optionalSteps() {
        val qAiE = graph.rule("* a > e / _i", name = "q-ai-e")
        val qSfF = graph.rule("* s > 0 / _f") // inapplicable for this test
        val qWV = graph.rule("* w > v / #_", name = "q-w-v")
        val seq = graph.addRuleSequence("ce-q", ce, q, listOf(qAiE.step(), qSfF.step(), qWV.step(optional = true)))
        val ceWord = graph.addWord("waiwai", language = ce)
        val qWord = graph.addWord("weiwei", language = q)
        val link = graph.addLink(qWord, ceWord, Link.Origin)
        graph.applyRuleSequence(link, seq)
        assertEquals(1, link.rules.size)
    }

    @Test
    fun alternativeRules() {
        val qAO = graph.rule("* a > o", name = "q-a-o")
        val qAI = graph.rule("* a > i", name = "q-a-i")
        val seq = graph.addRuleSequence("ce-q", ce, q, listOf(qAO.step(alternative = qAI)))
        val ceWordA = graph.addWord("wawa", language = ce)
        val qWordO = graph.addWord("wowo", language = q)
        val link = graph.addLink(qWordO, ceWordA, Link.Origin)
        graph.applyRuleSequence(link, seq)
        assertEquals("q-a-o", link.rules[0].name)

        val ceWordI = graph.addWord("lala", language = ce)
        val qWordI = graph.addWord("lili", language = q)
        val linkI = graph.addLink(qWordI, ceWordI, Link.Origin)
        graph.applyRuleSequence(linkI, seq)
        assertEquals("q-a-i", linkI.rules[0].name)
    }

    @Test
    fun normalize() {
        ce.phonemes += Phoneme(-1, listOf("c", "k"), null, setOf("voiceless", "velar", "stop", "consonant"))
        ce.phonemes += Phoneme(-1, listOf("g"), null, setOf("voiced", "velar", "stop", "consonant"))
        q.phonemes = q.phonemes.filter { "c" !in it.graphemes && "k" !in it.graphemes } +
                Phoneme(-1, listOf("c", "k"), null, setOf("voiceless", "velar", "stop", "consonant"))
        val qVoiceless = graph.rule("* [-sonorant,-continuant] > [+voice]", name = "q-voiceless")
        val seq = graph.addRuleSequence("ce-q", ce, q, listOf(qVoiceless.step()))
        val ceWord = graph.addWord("aklar", language = ce)
        val qWord = graph.addWord("aglar", language = q)
        val link = graph.addLink(qWord, ceWord, Link.Origin)
        graph.applyRuleSequence(link, seq)
        assertEquals(1, link.rules.size)
    }

    @Test
    fun deleteRule() {
        val qVoiceless = graph.rule("* [-sonorant,-continuant] > [+voice]", name = "q-voiceless")
        val seq = graph.addRuleSequence("ce-q", ce, q, listOf(qVoiceless.step()))
        graph.deleteRule(qVoiceless)
        assertTrue(seq.steps.isEmpty())
    }

    @Test
    fun chainedSequence() {
        val qAiE = graph.rule("* a > e / _i", name = "q-ai-e")
        val aqSeq = graph.addRuleSequence("ce-aq", ce, aq, listOf(qAiE.step()))
        val qWV = graph.rule("* w > v / #_", name = "q-w-v")
        val qSeq = graph.addRuleSequence("aq-q", aq, q, listOf(qWV.step()))

        val ceSeq = graph.addRuleSequence("ce-q", ce, q, listOf(aqSeq.step(), qSeq.step()))

        val ceWord = graph.addWord("waiwai", language = ce)
        val aqWord = graph.addWord("weiwei", language = aq)
        val aqLink = graph.addLink(aqWord, ceWord, Link.Origin)
        graph.applyRuleSequence(aqLink, aqSeq)

        val qWord = graph.addWord("veiwei", language = q)
        val qLink = graph.addLink(qWord, aqWord, Link.Origin)
        graph.applyRuleSequence(qLink, qSeq)

        val links = graph.findDerivationsWithSequence(ceSeq)
        assertEquals(1, links.size)
        assertEquals(2, links.first().size)
        assertEquals(aqLink, links.first().first())
        assertEquals(qLink, links.first()[1])
    }

    @Test
    fun findDerivationsWithEmptySequence() {
        val emptySeq = graph.addRuleSequence("ce-q-empty", ce, q, emptyList())
        val links = graph.findDerivationsWithSequence(emptySeq)
        assertTrue(links.isEmpty())
    }
}
