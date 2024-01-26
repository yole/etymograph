package ru.yole.etymograph.web

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.yole.etymograph.*

class CorpusControllerTest {
    @Test
    fun alternatives() {
        val fixture = QTestFixture()
        val corpusController = CorpusController(fixture.graphService)
        val corpusParams = CorpusController.CorpusTextParams(text = "Elen sila...")
        val corpusTextViewModel = corpusController.newText("q", corpusParams)

        fixture.repo.findOrAddWord("elen", fixture.q, "star", pos = "N")
        val accRule = fixture.setupParadigm()

        val alternatives = corpusController.requestAlternatives(corpusTextViewModel.id, 0)
        assertEquals(1, alternatives.size)
        assertEquals("star.ACC", alternatives[0].gloss)
        assertEquals(accRule.id, alternatives[0].ruleId)

        corpusController.acceptAlternative(corpusTextViewModel.id,
            CorpusController.AcceptAlternativeParameters(0, alternatives[0].wordId, alternatives[0].ruleId))
        val word = fixture.repo.corpusTextById(corpusTextViewModel.id)!!.words[0]!!
        assertEquals("star.ACC", word.getOrComputeGloss(fixture.repo))
    }

    @Test
    fun alternativesNP() {
        val fixture = QTestFixture()
        val corpusController = CorpusController(fixture.graphService)
        val corpusParams = CorpusController.CorpusTextParams(text = "Elen sila...")
        val corpusTextViewModel = corpusController.newText("q", corpusParams)

        fixture.repo.findOrAddWord("elen", fixture.q, null, pos = "NP")
        val accRule = fixture.setupParadigm()

        val alternatives = corpusController.requestAlternatives(corpusTextViewModel.id, 0)
        assertEquals(1, alternatives.size)
        assertEquals("Elen.ACC", alternatives[0].gloss)
        assertEquals(accRule.id, alternatives[0].ruleId)

        corpusController.acceptAlternative(corpusTextViewModel.id,
            CorpusController.AcceptAlternativeParameters(0, alternatives[0].wordId, alternatives[0].ruleId))
        val word = fixture.repo.corpusTextById(corpusTextViewModel.id)!!.words[0]!!
        assertEquals("Elen.ACC", word.getOrComputeGloss(fixture.repo))
    }

    @Test
    fun alternativesHomonym() {
        val fixture = QTestFixture()
        val corpusController = CorpusController(fixture.graphService)
        val corpusParams = CorpusController.CorpusTextParams(text = "Elen sila...")
        val corpusTextViewModel = corpusController.newText("q", corpusParams)

        fixture.repo.findOrAddWord("elen", fixture.q, "star", pos = "N")
        fixture.repo.findOrAddWord("elen", fixture.q, "scar", pos = "N")

        val alternatives = corpusController.requestAlternatives(corpusTextViewModel.id, 0)
        assertEquals(1, alternatives.size)
        assertEquals("scar", alternatives[0].gloss)
        assertEquals(-1, alternatives[0].ruleId)

        corpusController.acceptAlternative(corpusTextViewModel.id,
            CorpusController.AcceptAlternativeParameters(0, alternatives[0].wordId, alternatives[0].ruleId))
        val word = fixture.repo.corpusTextById(corpusTextViewModel.id)!!.words[0]!!
        assertEquals("scar", word.getOrComputeGloss(fixture.repo))
    }
}
