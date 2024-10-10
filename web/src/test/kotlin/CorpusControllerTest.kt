package ru.yole.etymograph.web

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.yole.etymograph.*
import ru.yole.etymograph.web.controllers.CorpusController
import ru.yole.etymograph.web.controllers.RuleController

class CorpusControllerTest {
    @Test
    fun alternatives() {
        val fixture = QTestFixture()
        val corpusController = CorpusController()
        val corpusParams = CorpusController.CorpusTextParams(text = "Elen sila...")
        val corpusTextViewModel = corpusController.newText(fixture.graph, "q", corpusParams)

        fixture.graph.findOrAddWord("elen", fixture.q, "star", pos = "N")
        val accRule = fixture.setupParadigm()

        val alternatives = corpusController.requestAlternatives(fixture.graph, corpusTextViewModel.id, 0)
        assertEquals(1, alternatives.size)
        assertEquals("star.ACC", alternatives[0].gloss)
        assertEquals(accRule.id, alternatives[0].ruleId)

        corpusController.acceptAlternative(fixture.graph, corpusTextViewModel.id,
            CorpusController.AcceptAlternativeParameters(0, alternatives[0].wordId, alternatives[0].ruleId))
        val word = fixture.graph.corpusTextById(corpusTextViewModel.id)!!.wordByIndex(0)!!
        assertEquals("star.ACC", word.getOrComputeGloss(fixture.graph))
    }

    @Test
    fun alternativesExistingLink() {
        val fixture = QTestFixture()
        val corpusController = CorpusController()
        val corpusParams = CorpusController.CorpusTextParams(text = "Elen sila...")
        val corpusTextViewModel = corpusController.newText(fixture.graph, "q", corpusParams)

        val accRule = fixture.setupParadigm()
        val elen = fixture.graph.findOrAddWord("elen", fixture.q, "star", pos = "N")
        val elenAcc =  fixture.graph.findOrAddWord("elen", fixture.q, "star.ACC", pos = "N")
        elenAcc.gloss = null
        fixture.graph.addLink(elenAcc, elen, Link.Derived, listOf(accRule), emptyList(), null)

        val alternatives = corpusController.requestAlternatives(fixture.graph, corpusTextViewModel.id, 0)
        assertEquals(1, alternatives.size)
        assertEquals("star.ACC", alternatives[0].gloss)

        corpusController.acceptAlternative(fixture.graph, corpusTextViewModel.id,
            CorpusController.AcceptAlternativeParameters(0, alternatives[0].wordId, alternatives[0].ruleId))

        val word = fixture.graph.corpusTextById(corpusTextViewModel.id)!!.wordByIndex(0)!!
        assertEquals(elenAcc.id, word.id)
        assertEquals(1, fixture.graph.getLinksFrom(elenAcc).count())
    }

    @Test
    fun alternativesNP() {
        val fixture = QTestFixture()
        val corpusController = CorpusController()
        val corpusParams = CorpusController.CorpusTextParams(text = "Elen sila...")
        val corpusTextViewModel = corpusController.newText(fixture.graph, "q", corpusParams)

        fixture.graph.findOrAddWord("elen", fixture.q, null, pos = "NP")
        val accRule = fixture.setupParadigm()

        val alternatives = corpusController.requestAlternatives(fixture.graph, corpusTextViewModel.id, 0)
        assertEquals(1, alternatives.size)
        assertEquals("Elen.ACC", alternatives[0].gloss)
        assertEquals(accRule.id, alternatives[0].ruleId)

        corpusController.acceptAlternative(fixture.graph, corpusTextViewModel.id,
            CorpusController.AcceptAlternativeParameters(0, alternatives[0].wordId, alternatives[0].ruleId))
        val word = fixture.graph.corpusTextById(corpusTextViewModel.id)!!.wordByIndex(0)!!
        assertEquals("Elen.ACC", word.getOrComputeGloss(fixture.graph))
    }

    @Test
    fun alternativesHomonym() {
        val fixture = QTestFixture()
        val corpusController = CorpusController()
        val corpusParams = CorpusController.CorpusTextParams(text = "Elen sila...")
        val corpusTextViewModel = corpusController.newText(fixture.graph, "q", corpusParams)

        fixture.graph.findOrAddWord("elen", fixture.q, "star", pos = "N")
        fixture.graph.findOrAddWord("elen", fixture.q, "scar", pos = "N")

        val alternatives = corpusController.requestAlternatives(fixture.graph, corpusTextViewModel.id, 0)
        assertEquals(1, alternatives.size)
        assertEquals("scar", alternatives[0].gloss)
        assertEquals(-1, alternatives[0].ruleId)

        corpusController.acceptAlternative(fixture.graph, corpusTextViewModel.id,
            CorpusController.AcceptAlternativeParameters(0, alternatives[0].wordId, alternatives[0].ruleId))
        val word = fixture.graph.corpusTextById(corpusTextViewModel.id)!!.wordByIndex(0)!!
        assertEquals("scar", word.getOrComputeGloss(fixture.graph))
    }

    @Test
    fun stress() {
        val fixture = QTestFixture()
        fixture.q.phonemes = listOf(
            Phoneme(-1, listOf("a"), null, setOf("vowel")),
            Phoneme(-1, listOf("i"), null, setOf("vowel")),
            Phoneme(-1, listOf("e"), null, setOf("vowel"))
        )

        val ruleController = RuleController()
        ruleController.newRule(
            fixture.graph,
            RuleController.UpdateRuleParameters(
                "q-stress",
                "q", "q",
                "- stress is on first syllable",
            ))

        fixture.q.stressRule = RuleRef.to(fixture.graph.ruleByName("q-stress")!!)

        val elen = fixture.graph.findOrAddWord("elen", fixture.q, "star")
        val sila = fixture.graph.findOrAddWord("sila", fixture.q, "shines")

        val corpusController = CorpusController()
        val corpusParams = CorpusController.CorpusTextParams(text = "Elen sila...")
        val corpusTextViewModel = corpusController.newText(fixture.graph, "q", corpusParams)
        val id = corpusTextViewModel.id

        corpusController.associateWord(fixture.graph, id, CorpusController.AssociateWordParameters(0, elen.id))
        corpusController.associateWord(fixture.graph, id, CorpusController.AssociateWordParameters(1, sila.id))

        val corpusTextViewModel2 = corpusController.textJson(fixture.graph, id)
        val line = corpusTextViewModel2.lines.single()
        assertEquals(0, line.words[0].stressIndex)
        assertEquals(1, line.words[0].stressLength)
    }
}
