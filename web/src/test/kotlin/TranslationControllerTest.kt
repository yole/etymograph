package page.yole.etymograph.web

import org.junit.Assert
import org.junit.Test
import org.springframework.web.server.ResponseStatusException
import page.yole.etymograph.web.controllers.CorpusController
import page.yole.etymograph.web.controllers.TranslationController

class TranslationControllerTest {
    @Test
    fun simple() {
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
    fun anchoredTranslationRange() {
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

    @Test
    fun explicitAnchorRange() {
        val fixture = QTestFixture()
        val corpusController = CorpusController()
        val text = corpusController.newText(fixture.graph, "q",
            CorpusController.CorpusTextParams(text = "Ai laurie lantar lassi"))
        val controller = TranslationController()
        val later = controller.addTranslation(fixture.graph,
            TranslationController.TranslationParams(text.id, "Leaves", "", 3))

        val fragment = controller.addTranslation(fixture.graph,
            TranslationController.TranslationParams(text.id, "Golden", "", 1, 2))
        Assert.assertEquals(1, fragment.anchorStartIndex)
        Assert.assertEquals(2, fragment.anchorEndIndex)

        val spanning = controller.addTranslation(fixture.graph,
            TranslationController.TranslationParams(text.id, "Golden fall leaves", "", 1, 4))
        Assert.assertEquals(1, spanning.anchorStartIndex)
        Assert.assertEquals(4, spanning.anchorEndIndex)
        val updated = corpusController.textJson(fixture.graph, text.id)
        Assert.assertEquals(3, updated.translations.first { it.id == later.id }.anchorStartIndex)
        Assert.assertEquals(4, updated.translations.first { it.id == later.id }.anchorEndIndex)
    }

    @Test
    fun entireTextTranslationRemainsUnanchored() {
        val fixture = QTestFixture()
        val corpusController = CorpusController()
        val text = corpusController.newText(fixture.graph, "q",
            CorpusController.CorpusTextParams(text = "Ai laurie lantar"))
        val controller = TranslationController()
        controller.addTranslation(fixture.graph,
            TranslationController.TranslationParams(text.id, "Golden", "", 1, 2))
        val entire = controller.addTranslation(fixture.graph,
            TranslationController.TranslationParams(text.id, "Golden fall", ""))
        Assert.assertNull(entire.anchorStartIndex)
        Assert.assertNull(entire.anchorEndIndex)
        val fragment = corpusController.textJson(fixture.graph, text.id).translations.first()
        Assert.assertEquals(1, fragment.anchorStartIndex)
        Assert.assertEquals(2, fragment.anchorEndIndex)
    }

    @Test
    fun invalidAnchorRangeDoesNotCreateTranslation() {
        val fixture = QTestFixture()
        val corpusController = CorpusController()
        val text = corpusController.newText(fixture.graph, "q",
            CorpusController.CorpusTextParams(text = "Ai laurie lantar"))
        val controller = TranslationController()
        for ((start, end) in listOf(-1 to 1, 3 to 4, 1 to 1, 2 to 1, 1 to 4, null to 2)) {
            Assert.assertThrows(ResponseStatusException::class.java) {
                controller.addTranslation(fixture.graph,
                    TranslationController.TranslationParams(text.id, "Invalid", "", start, end))
            }
        }
        Assert.assertTrue(corpusController.textJson(fixture.graph, text.id).translations.isEmpty())
    }

    @Test
    fun deleteTranslation() {
        val fixture = QTestFixture()

        val corpusController = CorpusController()
        val corpusParams = CorpusController.CorpusTextParams(text = "Ai laurie lantar")
        val corpusTextViewModel = corpusController.newText(fixture.graph, "q", corpusParams)

        val translationController = TranslationController()
        val added = translationController.addTranslation(
            fixture.graph,
            TranslationController.TranslationParams(corpusTextViewModel.id, "Golden fall", "", 1)
        )
        translationController.deleteTranslation(fixture.graph, added.id)

        val updated = corpusController.textJson(fixture.graph, corpusTextViewModel.id)
        Assert.assertEquals(0, updated.translations.size)
    }
}
