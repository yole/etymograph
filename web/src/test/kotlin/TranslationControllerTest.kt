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

        val translationController = TranslationController()
        val translationParams = TranslationController.TranslationParams(corpusTextViewModel.id, "The star shines...", "")
        translationController.addTranslation(fixture.graph, translationParams)

        val corpusTextViewModel2 = corpusController.textJson(fixture.graph, corpusTextViewModel.id)
        Assert.assertEquals(1, corpusTextViewModel2.translations.size)
    }

    @Test
    fun testAnchoredTranslationRange() {
        val fixture = QTestFixture()

        val corpusController = CorpusController()
        val corpusParams = CorpusController.CorpusTextParams(text = "Ai laurie lantar")
        val corpusTextViewModel = corpusController.newText(fixture.graph, "q", corpusParams)

        val translationController = TranslationController()
        translationController.addTranslation(
            fixture.graph,
            TranslationController.TranslationParams(corpusTextViewModel.id, "The golden fall", "", 0)
        )
        translationController.addTranslation(
            fixture.graph,
            TranslationController.TranslationParams(corpusTextViewModel.id, "Golden fall", "", 1)
        )

        val updated = corpusController.textJson(fixture.graph, corpusTextViewModel.id)
        Assert.assertEquals(2, updated.translations.size)
        val first = updated.translations.first { it.anchorStartIndex == 0 }
        val second = updated.translations.first { it.anchorStartIndex == 1 }
        Assert.assertEquals(1, first.anchorEndIndex)
        Assert.assertEquals(3, second.anchorEndIndex)
    }
}
