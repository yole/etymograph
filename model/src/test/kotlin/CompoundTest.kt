package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Test

class CompoundTest : QBaseTest() {
    @Test
    fun segmentedText() {
        val repo = InMemoryGraphRepository().apply { addLanguage(q) }
        val fara = repo.addWord("fara")
        val mir = repo.addWord("mir")
        val faramir = repo.addWord("faramir")
        val compound = repo.createCompound(faramir, fara, emptyList(), null)
        compound.components.add(mir)
        val restored = repo.restoreSegments(faramir)
        assertEquals("fara-mir", restored.segmentedText())
    }

    @Test
    fun segmentedTextClitic() {
        val repo = InMemoryGraphRepository().apply { addLanguage(q) }
        val fara = repo.addWord("fara")
        fara.classes = listOf("clitic")
        val mir = repo.addWord("mir")
        val faramir = repo.addWord("faramir")
        val compound = repo.createCompound(faramir, fara, emptyList(), null)
        compound.components.add(mir)
        val restored = repo.restoreSegments(faramir)
        assertEquals("fara=mir", restored.segmentedText())
    }

    @Test
    fun compoundsInDictionary() {
        val repo = InMemoryGraphRepository().apply { addLanguage(q) }
        val fara = repo.addWord("fara", "fara.NOM")
        val mir = repo.addWord("mir")
        val faramir = repo.addWord("faramir")
        val compound = repo.createCompound(faramir, fara, emptyList(), null)
        compound.components.add(mir)
        assertEquals(1, repo.filteredWords(q, WordKind.COMPOUND).size)
    }

    @Test
    fun deleteWordFromCompound() {
        val repo = InMemoryGraphRepository().apply { addLanguage(q) }
        val fara = repo.addWord("fara")
        val mir = repo.addWord("mir")
        val faramir = repo.addWord("faramir")
        val compound = repo.createCompound(faramir, fara, emptyList(), null)
        compound.components.add(mir)

        repo.deleteWord(mir)
        assertEquals(1, repo.findComponentsByCompound(faramir).first().components.size)
    }

    @Test
    fun deleteWordOfCompound() {
        val repo = InMemoryGraphRepository().apply { addLanguage(q) }
        val fara = repo.addWord("fara")
        val mir = repo.addWord("mir")
        val faramir = repo.addWord("faramir")
        val compound = repo.createCompound(faramir, fara, emptyList(), null)
        compound.components.add(mir)

        repo.deleteWord(faramir)
        assertEquals(null, repo.langEntityById(compound.id))
    }

    @Test
    fun segmentedTextDeleteCompound() {
        val repo = InMemoryGraphRepository().apply { addLanguage(q) }
        val fara = repo.addWord("fara")
        val mir = repo.addWord("mir")
        val faramir = repo.addWord("faramir")
        val compound = repo.createCompound(faramir, fara, emptyList(), null)
        compound.components.add(mir)
        val restored = repo.restoreSegments(faramir)
        assertEquals("fara-mir", restored.segmentedText())
        repo.deleteCompound(compound)
        assertEquals("faramir", faramir.segmentedText())
    }

    @Test
    fun segmentedTextPartialMatch() {
        val repo = InMemoryGraphRepository().apply { addLanguage(q) }
        val fara = repo.addWord("anda")
        val mir = repo.addWord("aurenya")
        val faramir = repo.addWord("andaurenya")
        val compound = repo.createCompound(faramir, fara, emptyList(), null)
        compound.components.add(mir)
        val restored = repo.restoreSegments(faramir)
        assertEquals("andaurenya", restored.segmentedText())
    }

    @Test
    fun stressOnRootInCompound() {
        val repo = InMemoryGraphRepository().apply { addLanguage(q) }
        val stressRule = repo.rule("- stress is on first root syllable")
        assertEquals("stress is on first root syllable", stressRule.firstInstruction.toEditableText())
        q.stressRule = RuleRef.to(stressRule)

        val na = repo.addWord("na", pos = "PV")
        val pan = repo.addWord("pan-", pos = "V")
        val napan = repo.addWord("napan-")
        val compound = repo.createCompound(napan, na, emptyList(), null)
        compound.components.add(pan)

        assertEquals(3, napan.calculateStress(repo)!!.index)
    }
}
