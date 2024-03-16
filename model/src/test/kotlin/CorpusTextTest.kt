package ru.yole.etymograph

import org.junit.Test
import org.junit.Assert.*

class CorpusTextTest : QBaseTest() {
    @Test
    fun testWordIndex() {
        val corpusText = CorpusText(-1, "ai laurie lantar\nyeni unotime", null, q, mutableListOf(), emptyList(), null)
        val lines = corpusText.mapToLines(emptyRepo)
        assertEquals(0, lines[0].corpusWords[0].index)
        assertEquals(3, lines[1].corpusWords[0].index)
    }

    @Test
    fun testAssociateWord() {
        val corpusText = CorpusText(-1, "ai laurie lantar", null, q, mutableListOf(), emptyList(), null)
        val repo = InMemoryGraphRepository()
        val laurie = q.word("laurie")
        corpusText.associateWord(1, laurie)
        val line = corpusText.mapToLines(repo).single()
        assertEquals(laurie, line.corpusWords[1].word)
    }

    @Test
    fun testEditText() {
        val corpusText = CorpusText(-1, "ai laurie lantar", null, q, mutableListOf(), emptyList(), null)
        val repo = InMemoryGraphRepository()
        val laurie = q.word("laurie")
        corpusText.associateWord(1, laurie)

        corpusText.text = "laurie lantar"
        val line = corpusText.mapToLines(repo).single()
        assertEquals(laurie, line.corpusWords[0].word)
    }

    @Test
    fun testNormalizedText() {
        val corpusText = CorpusText(-1, "ai lau[rie] lantar", null, q, mutableListOf(), emptyList(), null)

        val repo = InMemoryGraphRepository()
        val laurie = q.word("laurie")

        val lines = corpusText.mapToLines(repo)
        assertEquals("laurie", lines[0].corpusWords[1].normalizedText)
    }

    @Test
    fun testNormalizedTextDecapitalized() {
        val corpusText = CorpusText(-1, "Ai Laurie. Lantar", null, q, mutableListOf(), emptyList(), null)

        val repo = InMemoryGraphRepository()
        val lines = corpusText.mapToLines(repo)
        assertEquals("ai", lines[0].corpusWords[0].normalizedText)
        assertEquals("Laurie", lines[0].corpusWords[1].normalizedText)
        assertEquals("lantar", lines[0].corpusWords[2].normalizedText)
    }

    @Test
    fun testNormalizedTextRemoveQuotes() {
        val corpusText = CorpusText(-1, "quet \"Ai laurie lantar,\"", null, q, mutableListOf(), emptyList(), null)

        val repo = InMemoryGraphRepository()
        val lines = corpusText.mapToLines(repo)
        assertEquals("ai", lines[0].corpusWords[1].normalizedText)
        assertEquals("lantar", lines[0].corpusWords[3].normalizedText)
    }

    @Test
    fun testNormalizedTextRemoveQuotesAssociate() {
        val corpusText = CorpusText(-1, "quet \"Ai laurie lantar,\"", null, q, mutableListOf(), emptyList(), null)

        val repo = repoWithQ()
        val ai = repo.addWord("ai")
        corpusText.associateWord(1, ai)
        val lines = corpusText.mapToLines(repo)
        assertEquals("ai", lines[0].corpusWords[1].normalizedText)
        assertEquals("\"Ai", lines[0].corpusWords[1].segmentedText)
        assertEquals("lantar", lines[0].corpusWords[3].normalizedText)
    }

    @Test
    fun testNormalizedTextRemoveParenthees() {
        val corpusText = CorpusText(-1, "Perhael (i sennui Panthael)", null, q, mutableListOf(), emptyList(), null)

        val repo = repoWithQ()
        val stressRule = repo.rule("- stress is on first syllable")
        q.stressRule = RuleRef.to(stressRule)

        val i = repo.addWord("i")
        corpusText.associateWord(1, i)

        val lines = corpusText.mapToLines(repo)
        assertEquals("i", lines[0].corpusWords[1].normalizedText)
        assertEquals("(i", lines[0].corpusWords[1].segmentedText)
        assertEquals(1, lines[0].corpusWords[1].stressIndex)
        assertEquals("Panthael", lines[0].corpusWords[3].normalizedText)
    }

    @Test
    fun testAttestations() {
        val repo = InMemoryGraphRepository()
        val eaV = q.word("ea", "be")
        val eaN = q.word("ea", "being")
        val ct1 = repo.addCorpusText("ea", null, q, listOf(eaV))
        val ct2 = repo.addCorpusText("ea", null, q, listOf(eaN))

        val attestations = repo.findAttestations(eaV)
        assertEquals(1, attestations.size)
    }

    @Test
    fun testAttestationsNotAssigned() {
        val repo = InMemoryGraphRepository()
        val eaV = q.word("lantar", "fall")
        val ct1 = repo.addCorpusText("ai laurie lantar", null, q)

        val attestations = repo.findAttestations(eaV)
        assertEquals(1, attestations.size)
    }
}
