package ru.yole.etymograph.web

import org.junit.Assert
import org.junit.Test
import ru.yole.etymograph.web.controllers.CorpusController
import ru.yole.etymograph.web.controllers.TranslationController

class TranslationControllerTest {
    @Test
    fun testSimple() {
        val fixture = QTestFixture()

        val corpusController = CorpusController()
        val corpusParams = CorpusController.CorpusTextParams(text = "Elen sila...")
        val corpusTextViewModel = corpusController.newText(fixture.graph, "q", corpusParams)

        val translationController = TranslationController(fixture.graphService)
        val translationParams = TranslationController.TranslationParams(corpusTextViewModel.id, "The star shines...", "")
        translationController.addTranslation("", translationParams)

        val corpusTextViewModel2 = corpusController.textJson(fixture.graph, corpusTextViewModel.id)
        Assert.assertEquals(1, corpusTextViewModel2.translations.size)
    }
}
