package ru.yole.etymograph.web

import org.junit.Assert
import org.junit.Test

class TranslationControllerTest {
    @Test
    fun testSimple() {
        val fixture = QTestFixture()

        val corpusController = CorpusController(fixture.graphService)
        val corpusParams = CorpusController.CorpusParams(text = "Elen sila...")
        val corpusTextViewModel = corpusController.newText("q", corpusParams)

        val translationController = TranslationController(fixture.graphService)
        val translationParams = TranslationController.TranslationParams(corpusTextViewModel.id, "The star shines...", "")
        translationController.addTranslation(translationParams)

        val corpusTextViewModel2 = corpusController.textJson(corpusTextViewModel.id)
        Assert.assertEquals(1, corpusTextViewModel2.translations.size)
    }
}
