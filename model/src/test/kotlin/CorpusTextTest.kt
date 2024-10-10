package ru.yole.etymograph

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

class CorpusTextTest : QBaseTest() {
    lateinit var repo: InMemoryGraphRepository

    @Before
    fun setup() {
        repo = repoWithQ()
    }

    @Test
    fun testWordIndex() {
        val corpusText = q.corpusText("ai laurie lantar\nyeni unotime")
        val lines = corpusText.mapToLines(emptyRepo)
        assertEquals(0, lines[0].corpusWords[0].index)
        assertEquals(3, lines[1].corpusWords[0].index)
    }

    @Test
    fun testAssociateWord() {
        val corpusText = q.corpusText("ai laurie lantar")
        val laurie = q.word("laurie")
        corpusText.associateWord(1, laurie)
        val line = corpusText.mapToLines(repo).single()
        assertEquals(laurie, line.corpusWords[1].word)
    }

    @Test
    fun testEditText() {
        val corpusText = q.corpusText("ai laurie lantar")
        val laurie = q.word("laurie")
        corpusText.associateWord(1, laurie)

        corpusText.text = "laurie lantar"
        val line = corpusText.mapToLines(repo).single()
        assertEquals(laurie, line.corpusWords[0].word)
    }

    @Test
    fun testNormalizedText() {
        val corpusText = q.corpusText("ai lau[rie] lantar")

        val laurie = q.word("laurie")

        val lines = corpusText.mapToLines(repo)
        assertEquals("laurie", lines[0].corpusWords[1].normalizedText)
    }

    @Test
    fun testNormalizedTextDecapitalized() {
        val corpusText = q.corpusText("Ai Laurie. Lantar")

        val lines = corpusText.mapToLines(repo)
        assertEquals("ai", lines[0].corpusWords[0].normalizedText)
        assertEquals("Laurie", lines[0].corpusWords[1].normalizedText)
        assertEquals("lantar", lines[0].corpusWords[2].normalizedText)
    }

    @Test
    fun testNormalizedTextRemoveQuotes() {
        val corpusText = q.corpusText("quet \"Ai laurie lantar,\"")

        val lines = corpusText.mapToLines(repo)
        assertEquals("ai", lines[0].corpusWords[1].normalizedText)
        assertEquals("lantar", lines[0].corpusWords[3].normalizedText)
    }

    @Test
    fun testNormalizedTextRemoveApostrophes() {
        val corpusText = q.corpusText("quet 'Ai laurie lantar,'")

        val lines = corpusText.mapToLines(repo)
        assertEquals("ai", lines[0].corpusWords[1].normalizedText)
        assertEquals("lantar", lines[0].corpusWords[3].normalizedText)
    }

    @Test
    fun testNormalizedTextRemoveQuotesAssociate() {
        val corpusText = q.corpusText("quet \"Ai laurie lantar,\"")

        val ai = repo.addWord("ai")
        corpusText.associateWord(1, ai)
        val lines = corpusText.mapToLines(repo)
        assertEquals("ai", lines[0].corpusWords[1].normalizedText)
        assertEquals("\"Ai", lines[0].corpusWords[1].segmentedText)
        assertEquals("lantar", lines[0].corpusWords[3].normalizedText)
    }

    @Test
    fun testNormalizedTextRemoveParentheses() {
        val corpusText = q.corpusText("Perhael (i sennui Panthael)")

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
        val eaV = q.word("ea", "be")
        val eaN = q.word("ea", "being")
        val ct1 = repo.addCorpusText("ea", null, q)
        ct1.associateWord(0, eaV)
        val ct2 = repo.addCorpusText("ea", null, q)
        ct2.associateWord(0, eaN)

        val attestations = repo.findAttestations(eaV)
        assertEquals(1, attestations.size)
    }

    @Test
    fun testAttestationsNotAssigned() {
        val eaV = q.word("lantar", "fall")
        val ct1 = repo.addCorpusText("ai laurie lantar", null, q)

        val attestations = repo.findAttestations(eaV)
        assertEquals(1, attestations.size)
    }
}

fun Language.corpusText(text: String) = CorpusText(-1, text, null, this)
