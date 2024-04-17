package ru.yole.etymograph.web

import ru.yole.etymograph.*
import ru.yole.etymograph.web.controllers.RuleController

class QTestFixture {
    val ce = Language("Common Eldarin", "ce")
    val q = Language("Quenya", "q")
    val graph = InMemoryGraphRepository().apply {
        addLanguage(ce)
        addLanguage(q)
    }

    val graphService = object : GraphService() {
        override val graph: GraphRepository
            get() = this@QTestFixture.graph
    }

    fun setupParadigm(): Rule {
        val accRule = graph.addRule(
            "q-acc", q, q,
            RuleLogic(
                emptyList(),
                listOf(RuleBranch(OtherwiseCondition, listOf(RuleInstruction(InstructionType.NoChange, ""))))
            ),
            ".ACC"
        )

        val paradigm = graph.addParadigm("Noun", q, listOf("N", "NP"))
        paradigm.addRow("Nom")
        paradigm.addRow("Acc")
        paradigm.addColumn("Sg")
        paradigm.setRule(1, 0, listOf(accRule))
        return accRule
    }

    fun setupRuleSequence(): RuleSequence {
        RuleController(graphService).newRule(
            "",
            RuleController.UpdateRuleParameters(
                "q-final-consonant",
                "q", "q",
                "end of word and sound is 'm':\n- new sound is 'n'"
            )
        )
        return graph.addRuleSequence(
            "ce-to-q", ce, q,
            listOf(graph.ruleByName("q-final-consonant")!!)
        )
    }

}
