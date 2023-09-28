package ru.yole.etymograph.web

import org.junit.Assert
import org.junit.Test

class WordControllerTest {
    @Test
    fun testEmptyPOS() {
        val fixture = QTestFixture()
        val wordController = WordController(fixture.graphService)
        val addWordParams = WordController.AddWordParameters("ea", "be", "", "", "", "")
        val wordViewModel = wordController.addWord("q", addWordParams)

        Assert.assertNull(fixture.repo.wordsByText(fixture.q, "ea").single().pos)

        wordController.updateWord(wordViewModel.id, addWordParams)
        Assert.assertNull(fixture.repo.wordsByText(fixture.q, "ea").single().pos)
    }
}
