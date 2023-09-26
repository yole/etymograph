package ru.yole.etymograph

import org.junit.Assert
import org.junit.Test
import ru.yole.etymograph.web.GraphService
import ru.yole.etymograph.web.WordController

class WordControllerTest {
    @Test
    fun testEmptyPOS() {
        val repo = InMemoryGraphRepository()
        val q = Language("Quenya", "q")
        repo.addLanguage(q)

        val graphService = object : GraphService() {
            override val graph: GraphRepository
                get() = repo
        }
        val wordController = WordController(graphService)
        val addWordParams = WordController.AddWordParameters("ea", "be", "", "", "", "")
        wordController.addWord("q", addWordParams)

        Assert.assertNull(repo.wordsByText(q, "ea").single().pos)
    }
}
