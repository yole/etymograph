package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AlternativesTest : QBaseTest() {
    lateinit var repo: InMemoryGraphRepository
    lateinit var accRule: Rule

    @Before
    fun setup() {
        repo = InMemoryGraphRepository()
        accRule = setupNoChangeRule()
    }

    @Test
    fun simple() {
        val ct1 = repo.addCorpusText("elen sila", null, q)
        val word = repo.addWord("elen", "star", "N")

        val alts = Alternatives.request(repo, q, "elen", null)
        assertEquals(1, alts.size)
        assertEquals(accRule, alts[0].rule)
    }

    @Test
    fun compound() {
        val corpusText = repo.addCorpusText("elentari ortanen", null, q)

        val elentari = repo.addWord("elentari", null, pos = "N")
        val elen = repo.addWord("elen", "star", pos = "N")
        val tari = repo.addWord("tari", "queen", pos = "N")
        repo.createCompound(elentari, listOf(elen, tari))

        val alternatives = Alternatives.request(repo, q, "elentari", null)
        assertEquals(1, alternatives.size)
        assertEquals("star-queen.ACC", alternatives[0].gloss)
        assertEquals(accRule, alternatives[0].rule)

        Alternatives.accept(repo, corpusText, 0, alternatives[0].word, alternatives[0].rule)
        val word = corpusText.wordByIndex(0)!!
        assertEquals("star-queen.ACC", word.getOrComputeGloss(repo))
    }

    @Test
    fun compoundNoPOS() {
        repo.addCorpusText("elentari sila", null, q)
        val elen = repo.addWord("elen", "star", "N")
        val tari = repo.addWord("tari", "queen", "N")
        val elentari = repo.addWord("elentari")
        repo.createCompound(elentari, listOf(elen, tari), headIndex = 1)

        val alts = Alternatives.request(repo, q, "elentari", null)
        assertEquals(1, alts.size)
        assertEquals(accRule, alts[0].rule)
    }

    @Test
    fun homonym() {
        repo.addWord("elen", "star")
        repo.addWord("elen", "scar")

        val ct1 = repo.addCorpusText("elen sila", null, q)

        val alternatives = Alternatives.request(repo, q, "elen", null)
        assertEquals(1, alternatives.size)
        assertEquals("scar", alternatives[0].gloss)
        assertNull(alternatives[0].rule)

        Alternatives.accept(repo, ct1, 0, alternatives[0].word, alternatives[0].rule)
        val word = ct1.wordByIndex(0)!!
        assertEquals("scar", word.getOrComputeGloss(repo))
    }

    @Test
    fun np() {
        val corpusText = repo.addCorpusText("Elen sila...", null, q)

        repo.addWord("elen", null, pos = "NP")

        val alternatives = Alternatives.request(repo, q, "elen", null)
        assertEquals(1, alternatives.size)
        assertEquals("Elen.ACC", alternatives[0].gloss)
        assertEquals(accRule, alternatives[0].rule)

        Alternatives.accept(repo, corpusText, 0, alternatives[0].word, alternatives[0].rule)
        val word = corpusText.wordByIndex(0)!!
        assertEquals("Elen.ACC", word.getOrComputeGloss(repo))
    }

    @Test
    fun existingLink() {
        val corpusText = repo.addCorpusText("Elen sila...", null, q)

        val elen = repo.addWord("elen","star", pos = "N")
        val elenAcc =  repo.addWord("elen", "star.ACC", pos = "N")
        elenAcc.gloss = null
        repo.addLink(elenAcc, elen, Link.Derived, listOf(accRule))

        val alternatives = Alternatives.request(repo, q, "elen", null)
        assertEquals(1, alternatives.size)
        assertEquals("star.ACC", alternatives[0].gloss)

        Alternatives.accept(repo, corpusText, 0, alternatives[0].word, alternatives[0].rule)

        val word = corpusText.wordByIndex(0)!!
        assertEquals(elenAcc.id, word.id)
        assertEquals(1, repo.getLinksFrom(elenAcc).count())
    }

    @Test
    fun variantRule() {
        val byggva = repo.addWord("byggva","settle", pos = "V")
        val byggjaInf = repo.addWord("byggja", null)
        repo.addLink(byggjaInf, byggva, Link.Variation, emptyList())

        val byggjaAcc = repo.addWord("byggja", "settle.ACC")
        byggjaAcc.gloss = null
        repo.addLink(byggjaAcc, byggjaInf, Link.Derived, listOf(accRule))

        val alternatives = Alternatives.request(repo, q, "byggja", byggjaInf)
        assertEquals(1, alternatives.size)
        assertEquals(byggjaAcc, alternatives[0].word)
    }

    private fun setupNoChangeRule(): Rule {
        val accRule = repo.rule("otherwise:\n - no change", name = "q-acc", addedCategories = ".ACC", fromLanguage = q)

        val paradigm = repo.addParadigm("Noun", q, listOf("N", "NP"))
        paradigm.addRow("Nom")
        paradigm.addRow("Acc")
        paradigm.addColumn("Sg")
        paradigm.setRule(1, 0, listOf(accRule))
        return accRule
    }
}
