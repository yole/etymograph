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
}
