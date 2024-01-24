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
        val accRule = fixture.repo.addRule("q-acc", fixture.q, fixture.q,
            RuleLogic(emptyList(), listOf(RuleBranch(OtherwiseCondition, listOf(RuleInstruction(InstructionType.NoChange, ""))))),
            ".ACC"
        )

        val paradigm = fixture.repo.addParadigm("Noun", fixture.q, listOf("N"))
        paradigm.addRow("Nom")
        paradigm.addRow("Acc")
        paradigm.addColumn("Sg")
        paradigm.setRule(1, 0, listOf(accRule))

        val alternatives = corpusController.requestAlternatives(corpusTextViewModel.id, 0)
        assertEquals(1, alternatives.size)
        assertEquals("star.ACC", alternatives[0].gloss)
        assertEquals(accRule.id, alternatives[0].ruleId)

        corpusController.acceptAlternative(corpusTextViewModel.id,
            CorpusController.AcceptAlternativeParameters(0, alternatives[0].wordId, alternatives[0].ruleId))
        val word = fixture.repo.corpusTextById(corpusTextViewModel.id)!!.words[0]!!
        assertEquals("star.ACC", word.getOrComputeGloss(fixture.repo))
    }
}
