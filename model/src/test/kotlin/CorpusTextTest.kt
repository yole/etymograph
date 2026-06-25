package page.yole.etymograph

import org.junit.Test
import org.junit.Assert.*

class CorpusTextTest : QBaseTest() {
    @Test
    fun testWordIndex() {
        val corpusText = q.corpusText("ai laurie lantar\nyeni unotime")
        val lines = corpusText.mapToLines()
        assertEquals(0, lines[0].corpusWords[0].index)
        assertEquals(3, lines[1].corpusWords[0].index)
    }

    @Test
    fun testWordIndexTrailingSpace() {
        val corpusText = q.corpusText("ai laurie lantar \nyeni unotime")
        val lines = corpusText.mapToLines()
        assertEquals(0, lines[0].corpusWords[0].index)
        assertEquals(3, lines[1].corpusWords[0].index)
    }

    @Test
    fun testAssociateWord() {
        val corpusText = q.corpusText("ai laurie lantar")
        val laurie = q.word("laurie")
        corpusText.associateWord(1, laurie)
        val line = corpusText.mapToLines().single()
        assertEquals(laurie, line.corpusWords[1].word)
    }

    @Test
    fun testEditText() {
        val corpusText = q.corpusText("ai laurie lantar")
        val laurie = q.word("laurie")
        corpusText.associateWord(1, laurie, "goldenly")

        corpusText.text = "laurie lantar"
        val line = corpusText.mapToLines().single()
        assertEquals(laurie, line.corpusWords[0].word)
        assertEquals("goldenly", line.corpusWords[0].contextGloss)
    }

    @Test
    fun testNormalizedText() {
        val corpusText = q.corpusText("ai lau[rie] lant⸢a⸣r")

        val lines = corpusText.mapToLines()
        assertEquals("laurie", lines[0].corpusWords[1].normalizedText)
        assertEquals("lantar", lines[0].corpusWords[2].normalizedText)
    }

    @Test
    fun testNormalizedTextDecapitalized() {
        val corpusText = q.corpusText("Ai Laurie. Lantar")

        val lines = corpusText.mapToLines()
        assertEquals("ai", lines[0].corpusWords[0].normalizedText)
        assertEquals("Laurie", lines[0].corpusWords[1].normalizedText)
        assertEquals("lantar", lines[0].corpusWords[2].normalizedText)
    }

    @Test
    fun testNormalizedTextRemoveQuotes() {
        val corpusText = q.corpusText("quet \"Ai laurie lantar,\"")

        val lines = corpusText.mapToLines()
        assertEquals("ai", lines[0].corpusWords[1].normalizedText)
        assertEquals("lantar", lines[0].corpusWords[3].normalizedText)
    }

    @Test
    fun testNormalizedTextRemoveApostrophes() {
        val corpusText = q.corpusText("quet 'Ai laurie lantar,'")

        val lines = corpusText.mapToLines()
        assertEquals("ai", lines[0].corpusWords[1].normalizedText)
        assertEquals("lantar", lines[0].corpusWords[3].normalizedText)
    }

    @Test
    fun testNormalizedTextRemoveQuotesAssociate() {
        val corpusText = q.corpusText("quet \"Ai laurie lantar,\"")

        val ai = q.word("ai", "ai")
        corpusText.associateWord(1, ai)
        val lines = corpusText.mapToLines()
        assertEquals("ai", lines[0].corpusWords[1].normalizedText)
        assertEquals("\"Ai", lines[0].corpusWords[1].segmentedText)
        assertEquals("lantar", lines[0].corpusWords[3].normalizedText)
    }

    @Test
    fun testNormalizedTextRemoveParentheses() {
        val corpusText = q.corpusText("Perhael (i sennui Panthael)")

        val stressRule = q.rule("- stress is on first syllable")
        q.stressRule = RuleRef.to(stressRule)

        val i = q.word("i", "i")
        corpusText.associateWord(1, i)

        val lines = corpusText.mapToLines()
        assertEquals("i", lines[0].corpusWords[1].normalizedText)
        assertEquals("(i", lines[0].corpusWords[1].segmentedText)
        assertEquals(1, lines[0].corpusWords[1].stressIndex)
        assertEquals("Panthael", lines[0].corpusWords[3].normalizedText)
    }

    @Test
    fun testAttestations() {
        val eaV = q.word("ea", "be")
        val eaN = q.word("ea", "being")
        val ct1 = graph.addCorpusText("ea", null, q)
        ct1.associateWord(0, eaV)
        val ct2 = graph.addCorpusText("ea", null, q)
        ct2.associateWord(0, eaN)

        val attestations = graph.findAttestations(eaV)
        assertEquals(1, attestations.size)
    }

    @Test
    fun testAttestationsNotAssigned() {
        val eaV = q.word("lantar", "fall")
        val ct1 = graph.addCorpusText("ai laurie lantar", null, q)

        val attestations = graph.findAttestations(eaV)
        assertEquals(1, attestations.size)
    }

    @Test
    fun testAssociateContextGloss() {
        val ct1 = graph.addCorpusText("ai laurie lantar", null, q)
        val laurie = q.word("laurie", "golden")
        ct1.associateWord(1, laurie, "goldenly")
        val lines = ct1.mapToLines()
        assertEquals("goldenly", lines[0].corpusWords[1].contextGloss)
    }

    @Test
    fun testLockWordAssociations() {
        val ct1 = graph.addCorpusText("ai laurie lantar", null, q)
        val laurie = q.word("laurie", "golden")
        ct1.lockWordAssociations()
        assertEquals(laurie, ct1.words.single().word)
    }

    @Test
    fun syllabographicCandidate() {
        val hittite = graph.addLanguage("Hittite", "Hitt").also { it.syllabographic = true }
        val nu = hittite.word("nu", "and", syllabographic = true)
        val text = hittite.corpusText("nu")
        val lines = text.mapToLines()
        val word = lines[0].corpusWords[0]
        assertEquals(nu, word.wordCandidates!!.single())
    }
}

fun Language.corpusText(text: String) = CorpusText(-1, text, null, this)
