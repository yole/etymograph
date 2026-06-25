package page.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AlternativesTest : QBaseTest() {
    lateinit var accRule: Rule

    @Before
    fun setup() {
        accRule = setupNoChangeRule()
    }

    @Test
    fun simple() {
        val ct1 = graph.addCorpusText("elen sila", null, q)
        val word = q.word("elen", "star", pos = "N")

        val alts = Alternatives.request(q, "elen", null)
        assertEquals(1, alts.size)
        assertEquals(accRule, alts[0].rule)
    }

    @Test
    fun compound() {
        val corpusText = graph.addCorpusText("elentari ortanen", null, q)

        val elentari = q.word("elentari", pos = "N")
        val elen = q.word("elen", "star", pos = "N")
        val tari = q.word("tari", "queen", pos = "N")
        graph.createCompound(elentari, listOf(elen, tari))

        val alternatives = Alternatives.request(q, "elentari", null)
        assertEquals(1, alternatives.size)
        assertEquals("star-queen.ACC", alternatives[0].gloss)
        assertEquals(accRule, alternatives[0].rule)

        Alternatives.accept(corpusText, 0, alternatives[0].word, alternatives[0].rule)
        val word = corpusText.wordByIndex(0)!!
        assertEquals("star-queen.ACC", word.getOrComputeGloss())
    }

    @Test
    fun compoundNoPOS() {
        graph.addCorpusText("elentari sila", null, q)
        val elen = q.word("elen", "star", pos = "N")
        val tari = q.word("tari", "queen", pos = "N")
        val elentari = q.word("elentari", "elentari")
        graph.createCompound(elentari, listOf(elen, tari), headIndex = 1)

        val alts = Alternatives.request(q, "elentari", null)
        assertEquals(1, alts.size)
        assertEquals(accRule, alts[0].rule)
    }

    @Test
    fun homonym() {
        q.word("elen", "star")
        q.word("elen", "scar")

        val ct1 = graph.addCorpusText("elen sila", null, q)

        val alternatives = Alternatives.request(q, "elen", null)
        assertEquals(1, alternatives.size)
        assertEquals("scar", alternatives[0].gloss)
        assertNull(alternatives[0].rule)

        Alternatives.accept(ct1, 0, alternatives[0].word, alternatives[0].rule)
        val word = ct1.wordByIndex(0)!!
        assertEquals("scar", word.getOrComputeGloss())
    }

    @Test
    fun np() {
        val corpusText = graph.addCorpusText("Elen sila...", null, q)

        q.word("elen", null, pos = "NP")

        val alternatives = Alternatives.request(q, "elen", null)
        assertEquals(1, alternatives.size)
        assertEquals("Elen.ACC", alternatives[0].gloss)
        assertEquals(accRule, alternatives[0].rule)

        Alternatives.accept(corpusText, 0, alternatives[0].word, alternatives[0].rule)
        val word = corpusText.wordByIndex(0)!!
        assertEquals("Elen.ACC", word.getOrComputeGloss())
    }

    @Test
    fun existingLink() {
        val corpusText = graph.addCorpusText("Elen sila...", null, q)

        val elen = q.word("elen", "star", pos = "N")
        val elenAcc = q.word("elen", "star.ACC", pos = "N")
        elenAcc.gloss = null
        graph.addLink(elenAcc, elen, Link.Derived, listOf(accRule))

        val alternatives = Alternatives.request(q, "elen", null)
        assertEquals(1, alternatives.size)
        assertEquals("star.ACC", alternatives[0].gloss)

        Alternatives.accept(corpusText, 0, alternatives[0].word, alternatives[0].rule)

        val word = corpusText.wordByIndex(0)!!
        assertEquals(elenAcc.id, word.id)
        assertEquals(1, graph.getLinksFrom(elenAcc).count())
    }

    @Test
    fun variantRule() {
        val byggva = q.word("byggva", "settle", pos = "V")
        val byggjaInf = q.word("byggja", null)
        graph.addLink(byggjaInf, byggva, Link.Variation)

        val byggjaAcc = q.word("byggja", "settle.ACC")
        byggjaAcc.gloss = null
        graph.addLink(byggjaAcc, byggjaInf, Link.Derived, listOf(accRule))

        val alternatives = Alternatives.request(q, "byggja", byggjaInf)
        assertEquals(1, alternatives.size)
        assertEquals(byggjaAcc, alternatives[0].word)
    }

    private fun setupNoChangeRule(): Rule {
        val accRule = q.rule("otherwise:\n - no change", name = "q-acc", addedCategories = ".ACC")

        val paradigm = graph.addParadigm("Noun", q, listOf("N", "NP"))
        paradigm.addRow("Nom")
        paradigm.addRow("Acc")
        paradigm.addColumn("Sg")
        paradigm.setRule(1, 0, listOf(accRule))
        return accRule
    }
}
