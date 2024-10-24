package ru.yole.etymograph.web

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.yole.etymograph.*
import ru.yole.etymograph.web.controllers.CorpusController
import ru.yole.etymograph.web.controllers.RuleController

class CorpusControllerTest {
    private lateinit var fixture: QTestFixture
    private lateinit var corpusController: CorpusController
    private lateinit var graph: InMemoryGraphRepository

    @Before
    fun setup() {
        fixture = QTestFixture()
        graph = fixture.graph
        corpusController = CorpusController()
    }

    @Test
    fun alternatives() {
        val corpusParams = CorpusController.CorpusTextParams(text = "Elen sila...")
        val corpusTextViewModel = corpusController.newText(graph, "q", corpusParams)

        graph.findOrAddWord("elen", fixture.q, "star", pos = "N")
        val accRule = fixture.setupParadigm()

        val alternatives = corpusController.requestAlternatives(graph, corpusTextViewModel.id, 0)
        assertEquals(1, alternatives.size)
        assertEquals("star.ACC", alternatives[0].gloss)
        assertEquals(accRule.id, alternatives[0].ruleId)

        corpusController.acceptAlternative(graph, corpusTextViewModel.id,
            CorpusController.AcceptAlternativeParameters(0, alternatives[0].wordId, alternatives[0].ruleId))
        val word = graph.corpusTextById(corpusTextViewModel.id)!!.wordByIndex(0)!!
        assertEquals("star.ACC", word.getOrComputeGloss(graph))
    }

    @Test
    fun alternativesExistingLink() {
        val corpusParams = CorpusController.CorpusTextParams(text = "Elen sila...")
        val corpusTextViewModel = corpusController.newText(graph, "q", corpusParams)

        val accRule = fixture.setupParadigm()
        val elen = graph.findOrAddWord("elen", fixture.q, "star", pos = "N")
        val elenAcc =  graph.findOrAddWord("elen", fixture.q, "star.ACC", pos = "N")
        elenAcc.gloss = null
        graph.addLink(elenAcc, elen, Link.Derived, listOf(accRule))

        val alternatives = corpusController.requestAlternatives(graph, corpusTextViewModel.id, 0)
        assertEquals(1, alternatives.size)
        assertEquals("star.ACC", alternatives[0].gloss)

        corpusController.acceptAlternative(graph, corpusTextViewModel.id,
            CorpusController.AcceptAlternativeParameters(0, alternatives[0].wordId, alternatives[0].ruleId))

        val word = graph.corpusTextById(corpusTextViewModel.id)!!.wordByIndex(0)!!
        assertEquals(elenAcc.id, word.id)
        assertEquals(1, graph.getLinksFrom(elenAcc).count())
    }

    @Test
    fun alternativesNP() {
        val corpusParams = CorpusController.CorpusTextParams(text = "Elen sila...")
        val corpusTextViewModel = corpusController.newText(graph, "q", corpusParams)

        graph.findOrAddWord("elen", fixture.q, null, pos = "NP")
        val accRule = fixture.setupParadigm()

        val alternatives = corpusController.requestAlternatives(graph, corpusTextViewModel.id, 0)
        assertEquals(1, alternatives.size)
        assertEquals("Elen.ACC", alternatives[0].gloss)
        assertEquals(accRule.id, alternatives[0].ruleId)

        corpusController.acceptAlternative(graph, corpusTextViewModel.id,
            CorpusController.AcceptAlternativeParameters(0, alternatives[0].wordId, alternatives[0].ruleId))
        val word = graph.corpusTextById(corpusTextViewModel.id)!!.wordByIndex(0)!!
        assertEquals("Elen.ACC", word.getOrComputeGloss(graph))
    }

    @Test
    fun alternativesHomonym() {
        val corpusParams = CorpusController.CorpusTextParams(text = "Elen sila...")
        val corpusTextViewModel = corpusController.newText(graph, "q", corpusParams)

        graph.findOrAddWord("elen", fixture.q, "star", pos = "N")
        graph.findOrAddWord("elen", fixture.q, "scar", pos = "N")

        val alternatives = corpusController.requestAlternatives(graph, corpusTextViewModel.id, 0)
        assertEquals(1, alternatives.size)
        assertEquals("scar", alternatives[0].gloss)
        assertEquals(-1, alternatives[0].ruleId)

        corpusController.acceptAlternative(graph, corpusTextViewModel.id,
            CorpusController.AcceptAlternativeParameters(0, alternatives[0].wordId, alternatives[0].ruleId))
        val word = graph.corpusTextById(corpusTextViewModel.id)!!.wordByIndex(0)!!
        assertEquals("scar", word.getOrComputeGloss(graph))
    }

    @Test
    fun stress() {
        fixture.q.phonemes = listOf(
            Phoneme(-1, listOf("a"), null, setOf("vowel")),
            Phoneme(-1, listOf("i"), null, setOf("vowel")),
            Phoneme(-1, listOf("e"), null, setOf("vowel"))
        )

        val ruleController = RuleController()
        ruleController.newRule(
            graph,
            RuleController.UpdateRuleParameters(
                "q-stress",
                "q", "q",
                "- stress is on first syllable",
            ))

        fixture.q.stressRule = RuleRef.to(graph.ruleByName("q-stress")!!)

        val elen = graph.findOrAddWord("elen", fixture.q, "star")
        val sila = graph.findOrAddWord("sila", fixture.q, "shines")

        val corpusParams = CorpusController.CorpusTextParams(text = "Elen sila...")
        val corpusTextViewModel = corpusController.newText(graph, "q", corpusParams)
        val id = corpusTextViewModel.id

        corpusController.associateWord(graph, id, CorpusController.AssociateWordParameters(0, elen.id))
        corpusController.associateWord(graph, id, CorpusController.AssociateWordParameters(1, sila.id))

        val corpusTextViewModel2 = corpusController.textJson(graph, id)
        val line = corpusTextViewModel2.lines.single()
        assertEquals(0, line.words[0].stressIndex)
        assertEquals(1, line.words[0].stressLength)
    }

    @Test
    fun contextGloss() {
        val corpusParams = CorpusController.CorpusTextParams(text = "Ai laurie lantar")
        val corpusTextViewModel = corpusController.newText(graph, "q", corpusParams)

        val word = graph.findOrAddWord("laurie", fixture.q, "golden")
        corpusController.associateWord(graph, corpusTextViewModel.id, CorpusController.AssociateWordParameters(
            1, word.id, "goldenly"
        ))

        val textJson = corpusController.textJson(graph, corpusTextViewModel.id)
        assertEquals("goldenly", textJson.lines.single().words[1].contextGloss)
    }
}
