package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CompoundTest : QBaseTest() {
    lateinit var repo: GraphRepository
    lateinit var fara: Word
    lateinit var mir: Word
    lateinit var faramir: Word

    @Before
    fun setup() {
        repo = repoWithQ()
        fara = repo.addWord("fara")
        mir = repo.addWord("mir")
        faramir = repo.addWord("faramir")
    }

    @Test
    fun segmentedText() {
        val compound = repo.createCompound(faramir, fara)
        compound.components.add(mir)
        val restored = repo.restoreSegments(faramir)
        assertEquals("fara-mir", restored.segmentedText())
    }

    @Test
    fun segmentedTextClitic() {
        fara.classes = listOf("clitic")
        val compound = repo.createCompound(faramir, fara)
        compound.components.add(mir)
        val restored = repo.restoreSegments(faramir)
        assertEquals("fara=mir", restored.segmentedText())
    }

    @Test
    fun compoundsInDictionary() {
        val fara = repo.addWord("fara", "fara.NOM")
        val compound = repo.createCompound(faramir, fara)
        compound.components.add(mir)
        assertEquals(1, repo.filteredWords(q, WordKind.COMPOUND).size)
    }

    @Test
    fun deleteWordFromCompound() {
        val compound = repo.createCompound(faramir, fara)
        compound.components.add(mir)

        repo.deleteWord(mir)
        assertEquals(1, repo.findCompoundsByCompoundWord(faramir).first().components.size)
    }

    @Test
    fun deleteWordOfCompound() {
        val compound = repo.createCompound(faramir, fara)
        compound.components.add(mir)

        repo.deleteWord(faramir)
        assertEquals(null, repo.langEntityById(compound.id))
    }

    @Test
    fun segmentedTextDeleteCompound() {
        val compound = repo.createCompound(faramir, fara)
        compound.components.add(mir)
        val restored = repo.restoreSegments(faramir)
        assertEquals("fara-mir", restored.segmentedText())
        repo.deleteCompound(compound)
        assertEquals("faramir", faramir.segmentedText())
    }

    @Test
    fun segmentedTextPartialMatch() {
        val fara = repo.addWord("anda")
        val mir = repo.addWord("aurenya")
        val faramir = repo.addWord("andaurenya")
        val compound = repo.createCompound(faramir, fara)
        compound.components.add(mir)
        val restored = repo.restoreSegments(faramir)
        assertEquals("andaurenya", restored.segmentedText())
    }

    @Test
    fun stressOnRootInCompound() {
        val stressRule = repo.rule("- stress is on first root syllable")
        assertEquals("stress is on first root syllable", stressRule.firstInstruction.toEditableText())
        q.stressRule = RuleRef.to(stressRule)

        val na = repo.addWord("na", pos = "PV")
        val pan = repo.addWord("pan-", pos = "V")
        val napan = repo.addWord("napan-")
        val compound = repo.createCompound(napan, na)
        compound.components.add(pan)

        assertEquals(3, napan.calculateStress(repo)!!.index)
    }

    @Test
    fun suggestCompound() {
        val suggestions = repo.suggestCompound(faramir)
        assertEquals(fara, suggestions.single())
        val compound = repo.createCompound(faramir, fara)
        val suggestions2 = repo.suggestCompound(faramir, compound)
        assertEquals(mir, suggestions2.single())
    }
}
