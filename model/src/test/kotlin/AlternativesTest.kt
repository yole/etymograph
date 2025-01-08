package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AlternativesTest : QBaseTest() {
    lateinit var repo: InMemoryGraphRepository

    @Before
    fun setup() {
        repo = InMemoryGraphRepository()
    }

    @Test
    fun simple() {
        val ct1 = repo.addCorpusText("elen sila", null, q)
        val word = repo.addWord("elen", "star", "N")

        val accRule = setupNoChangeRule(repo)

        val alts = Alternatives.requestAlternatives(repo, word)
        assertEquals(1, alts.size)
        assertEquals(accRule, alts[0].rules[0])
    }

    @Test
    fun compound() {
        val ct1 = repo.addCorpusText("elentari sila", null, q)
        val elen = repo.addWord("elen", "star", "N")
        val tari = repo.addWord("tari", "queen", "N")
        val elentari = repo.addWord("elentari")
        val compound = repo.createCompound(elentari, elen)
        compound.components.add(tari)
        compound.headIndex = 1

        val accRule = setupNoChangeRule(repo)

        val alts = Alternatives.requestAlternatives(repo, elentari)
        assertEquals(1, alts.size)
        assertEquals(accRule, alts[0].rules[0])
    }

    private fun setupNoChangeRule(repo: InMemoryGraphRepository): Rule {
        val accRule = repo.rule("otherwise:\n - no change", name = "q-acc", fromLanguage = q)

        val paradigm = repo.addParadigm("Noun", q, listOf("N"))
        paradigm.addRow("Nom")
        paradigm.addRow("Acc")
        paradigm.addColumn("Sg")
        paradigm.setRule(1, 0, listOf(accRule))
        return accRule
    }
}
