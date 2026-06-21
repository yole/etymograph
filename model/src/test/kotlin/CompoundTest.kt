package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class CompoundTest : QBaseTest() {
    private lateinit var fara: Word
    private lateinit var mir: Word
    private lateinit var faramir: Word

    @Before
    fun setup() {
        fara = graph.addWord("fara")
        mir = graph.addWord("mir")
        faramir = graph.addWord("faramir")
    }

    @Test
    fun segmentedText() {
        graph.createCompound(faramir, listOf(fara, mir))
        val restored = graph.restoreSegments(faramir)
        assertEquals("fara-mir", restored.segmentedText())
    }

    @Test
    fun segmentedTextClitic() {
        fara.classes = listOf("clitic")
        graph.createCompound(faramir, listOf(fara, mir))
        val restored = graph.restoreSegments(faramir)
        assertEquals("fara=mir", restored.segmentedText())
    }

    @Test
    fun compoundsInDictionary() {
        val fara = graph.addWord("fara", "fara.NOM")
        graph.createCompound(faramir, listOf(fara, mir))
        assertEquals(1, graph.filteredWords(q, WordKind.COMPOUND).size)
    }

    @Test
    fun deleteWordFromCompound() {
        graph.createCompound(faramir, listOf(fara, mir))

        graph.deleteWord(mir)
        assertEquals(1, graph.findCompoundsByCompoundWord(faramir).first().components.size)
    }

    @Test
    fun deleteWordOfCompound() {
        val compound = graph.createCompound(faramir, listOf(fara, mir))

        graph.deleteWord(faramir)
        assertEquals(null, graph.langEntityById(compound.id))
    }

    @Test
    fun segmentedTextDeleteCompound() {
        val compound = graph.createCompound(faramir, listOf(fara, mir))
        val restored = graph.restoreSegments(faramir)
        assertEquals("fara-mir", restored.segmentedText())
        graph.deleteCompound(compound)
        assertEquals("faramir", faramir.segmentedText())
    }

    @Test
    fun segmentedTextPartialMatch() {
        val fara = graph.addWord("anda")
        val mir = graph.addWord("aurenya")
        val faramir = graph.addWord("andaurenya")
        graph.createCompound(faramir, listOf(fara, mir))
        val restored = graph.restoreSegments(faramir)
        assertEquals("andaurenya", restored.segmentedText())
    }

    @Test
    fun stressOnRootInCompound() {
        val stressRule = graph.rule("- stress is on first root syllable")
        assertEquals("stress is on first root syllable", stressRule.firstInstruction.toEditableText(graph))
        q.stressRule = RuleRef.to(stressRule)

        val na = graph.addWord("na", pos = "PV")
        val pan = graph.addWord("pan-", pos = "V")
        val napan = graph.addWord("napan-")
        graph.createCompound(napan, listOf(na, pan))

        assertEquals(3, napan.calculateStress()!!.index)
    }

    @Test
    fun stressOnRootInCompoundWithRule() {
        val stressRule = graph.rule("- stress is on first root syllable")
        assertEquals("stress is on first root syllable", stressRule.firstInstruction.toEditableText(graph))
        q.stressRule = RuleRef.to(stressRule)

        val na = graph.addWord("na", pos = "PV")
        val pan = graph.addWord("pan", pos = "V")
        val napan = graph.addWord("napan")
        graph.createCompound(napan, listOf(na, pan))

        val soundRule = graph.rule("* a > e if sound is stressed")
        val newWord = soundRule.apply(napan)
        assertEquals("napen", newWord.text)
    }

    @Test
    fun suggestCompound() {
        val suggestions = graph.suggestCompound(faramir)
        assertEquals(fara, suggestions.single())
        val compound = graph.createCompound(faramir, listOf(fara))
        val suggestions2 = graph.suggestCompound(faramir, compound)
        assertEquals(mir, suggestions2.single())
    }

    @Test
    fun suggestCompoundDash() {
        val faramirDash = graph.addWord("fara-mir")
        val suggestions = graph.suggestCompound(faramirDash)
        assertEquals(fara, suggestions.single())
        val compound = graph.createCompound(faramirDash, listOf(fara))
        val suggestions2 = graph.suggestCompound(faramirDash, compound)
        assertEquals(mir, suggestions2.single())
    }

    @Test
    fun suggestCompoundExcludeInflectedForm() {
        val onAcc = graph.rule("word ends with 'r':\n- change ending to ''", name = "on-acc")
        val stadr = graph.addWord("stadr", "city")
        val stad = graph.addWord("stad")
        graph.addLink(stad, stadr, Link.Derived, listOf(onAcc))
        val suggestions = graph.suggestCompound(stadr)
        assertEquals(0, suggestions.size)
    }

    @Test
    fun createCompoundRejectsSelfReference() {
        assertThrows(IllegalArgumentException::class.java) {
            graph.createCompound(faramir, listOf(faramir))
        }
    }

    @Test
    fun addToCompoundRejectsSelfReference() {
        val compound = graph.createCompound(faramir, listOf(fara))

        assertThrows(IllegalArgumentException::class.java) {
            graph.addToCompound(compound, faramir)
        }
    }
}
