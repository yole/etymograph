package ru.yole.etymograph

import org.junit.Assert
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
        Assert.assertEquals("fara-mir", restored.segmentedText())
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
        Assert.assertEquals("fara=mir", restored.segmentedText())
    }

    @Test
    fun compoundsInDictionary() {
        val repo = InMemoryGraphRepository().apply { addLanguage(q) }
        val fara = repo.addWord("fara", "fara.NOM")
        val mir = repo.addWord("mir")
        val faramir = repo.addWord("faramir")
        val compound = repo.createCompound(faramir, fara, emptyList(), null)
        compound.components.add(mir)
        Assert.assertEquals(1, repo.filteredWords(q, WordKind.COMPOUND).size)
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
        Assert.assertEquals(1, repo.findComponentsByCompound(faramir).first().components.size)
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
        Assert.assertEquals(null, repo.langEntityById(compound.id))
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
        Assert.assertEquals("fara-mir", restored.segmentedText())
        repo.deleteCompound(compound)
        Assert.assertEquals("faramir", faramir.segmentedText())
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
        Assert.assertEquals("andaurenya", restored.segmentedText())
    }
}
