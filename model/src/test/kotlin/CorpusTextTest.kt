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
}
