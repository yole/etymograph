package page.yole.etymograph

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
        fara = q.word("fara", "fara")
        mir = q.word("mir", "mir")
        faramir = q.word("faramir", "faramir")
    }

    @Test
    fun segmentedText() {
        graph.createCompound(faramir, listOf(fara, mir))
        assertEquals("fara-mir", faramir.segmentedText())
    }

    @Test
    fun segmentedTextClitic() {
        fara.classes = listOf("clitic")
        graph.createCompound(faramir, listOf(fara, mir))
        assertEquals("fara=mir", faramir.segmentedText())
    }

    @Test
    fun compoundsInDictionary() {
        val fara = q.word("fara", "fara.NOM")
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
        assertEquals("fara-mir", faramir.segmentedText())
        graph.deleteCompound(compound)
        assertEquals("faramir", faramir.segmentedText())
    }

    @Test
    fun segmentedTextPartialMatch() {
        val fara = q.word("anda", "anda")
        val mir = q.word("aurenya", "aurenya")
        val faramir = q.word("andaurenya", "andaurenya")
        graph.createCompound(faramir, listOf(fara, mir))
        assertEquals("andaurenya", faramir.segmentedText())
    }

    @Test
    fun stressOnRootInCompound() {
        val stressRule = q.rule("- stress is on first root syllable")
        assertEquals("stress is on first root syllable", stressRule.firstInstruction.toEditableText(graph))
        q.stressRule = RuleRef.to(stressRule)

        val na = q.word("na", "na", pos = "PV")
        val pan = q.word("pan-", "pan-", pos = "V")
        val napan = q.word("napan-", "napan-")
        graph.createCompound(napan, listOf(na, pan))

        assertEquals(3, napan.calculateStress()!!.index)
    }

    @Test
    fun stressOnRootInCompoundWithRule() {
        val stressRule = q.rule("- stress is on first root syllable")
        assertEquals("stress is on first root syllable", stressRule.firstInstruction.toEditableText(graph))
        q.stressRule = RuleRef.to(stressRule)

        val na = q.word("na", "na", pos = "PV")
        val pan = q.word("pan", "pan", pos = "V")
        val napan = q.word("napan", "napan")
        graph.createCompound(napan, listOf(na, pan))

        val soundRule = q.rule("* a > e if sound is stressed")
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
        val faramirDash = q.word("fara-mir", "fara-mir")
        val suggestions = graph.suggestCompound(faramirDash)
        assertEquals(fara, suggestions.single())
        val compound = graph.createCompound(faramirDash, listOf(fara))
        val suggestions2 = graph.suggestCompound(faramirDash, compound)
        assertEquals(mir, suggestions2.single())
    }

    @Test
    fun suggestCompoundExcludeInflectedForm() {
        val onAcc = q.rule("word ends with 'r':\n- change ending to ''", name = "on-acc")
        val stadr = q.word("stadr", "city")
        val stad = q.word("stad", "stad")
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
