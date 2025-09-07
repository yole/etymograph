package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CompoundTest : QBaseTest() {
    private lateinit var repo: GraphRepository
    private lateinit var fara: Word
    private lateinit var mir: Word
    private lateinit var faramir: Word

    @Before
    fun setup() {
        repo = repoWithQ()
        fara = repo.addWord("fara")
        mir = repo.addWord("mir")
        faramir = repo.addWord("faramir")
    }

    @Test
    fun segmentedText() {
        repo.createCompound(faramir, listOf(fara, mir))
        val restored = repo.restoreSegments(faramir)
        assertEquals("fara-mir", restored.segmentedText())
    }

    @Test
    fun segmentedTextClitic() {
        fara.classes = listOf("clitic")
        repo.createCompound(faramir, listOf(fara, mir))
        val restored = repo.restoreSegments(faramir)
        assertEquals("fara=mir", restored.segmentedText())
    }

    @Test
    fun compoundsInDictionary() {
        val fara = repo.addWord("fara", "fara.NOM")
        repo.createCompound(faramir, listOf(fara, mir))
        assertEquals(1, repo.filteredWords(q, WordKind.COMPOUND).size)
    }

    @Test
    fun deleteWordFromCompound() {
        repo.createCompound(faramir, listOf(fara, mir))

        repo.deleteWord(mir)
        assertEquals(1, repo.findCompoundsByCompoundWord(faramir).first().components.size)
    }

    @Test
    fun deleteWordOfCompound() {
        val compound = repo.createCompound(faramir, listOf(fara, mir))

        repo.deleteWord(faramir)
        assertEquals(null, repo.langEntityById(compound.id))
    }

    @Test
    fun segmentedTextDeleteCompound() {
        val compound = repo.createCompound(faramir, listOf(fara, mir))
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
        repo.createCompound(faramir, listOf(fara, mir))
        val restored = repo.restoreSegments(faramir)
        assertEquals("andaurenya", restored.segmentedText())
    }

    @Test
    fun stressOnRootInCompound() {
        val stressRule = repo.rule("- stress is on first root syllable")
        assertEquals("stress is on first root syllable", stressRule.firstInstruction.toEditableText(repo))
        q.stressRule = RuleRef.to(stressRule)

        val na = repo.addWord("na", pos = "PV")
        val pan = repo.addWord("pan-", pos = "V")
        val napan = repo.addWord("napan-")
        repo.createCompound(napan, listOf(na, pan))

        assertEquals(3, napan.calculateStress(repo)!!.index)
    }

    @Test
    fun stressOnRootInCompoundWithRule() {
        val stressRule = repo.rule("- stress is on first root syllable")
        assertEquals("stress is on first root syllable", stressRule.firstInstruction.toEditableText(repo))
        q.stressRule = RuleRef.to(stressRule)

        val na = repo.addWord("na", pos = "PV")
        val pan = repo.addWord("pan", pos = "V")
        val napan = repo.addWord("napan")
        repo.createCompound(napan, listOf(na, pan))

        val soundRule = repo.rule("* a > e if sound is stressed")
        val newWord = soundRule.apply(napan, repo)
        assertEquals("napen", newWord.text)
    }

    @Test
    fun suggestCompound() {
        val suggestions = repo.suggestCompound(faramir)
        assertEquals(fara, suggestions.single())
        val compound = repo.createCompound(faramir, listOf(fara))
        val suggestions2 = repo.suggestCompound(faramir, compound)
        assertEquals(mir, suggestions2.single())
    }

    @Test
    fun suggestCompoundExcludeInflectedForm() {
        val onAcc = repo.rule("word ends with 'r':\n- change ending to ''", name = "on-acc")
        val stadr = repo.addWord("stadr", "city")
        val stad = repo.addWord("stad")
        repo.addLink(stad, stadr, Link.Derived, listOf(onAcc))
        val suggestions = repo.suggestCompound(stadr)
        assertEquals(0, suggestions.size)
    }
}
