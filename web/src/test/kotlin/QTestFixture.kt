package page.yole.etymograph.web

import page.yole.etymograph.*
import page.yole.etymograph.web.controllers.RuleController

class QTestFixture {
    val graph = InMemoryGraph()
    val ce = graph.addLanguage("Common Eldarin", "ce")
    val q = graph.addLanguage("Quenya", "q")

    fun setupParadigm(): Rule {
        val accRule = graph.addRule(
            "q-acc", q, q,
            MorphoRuleLogic(
                emptyList(),
                listOf(RuleBranch(OtherwiseCondition, listOf(RuleInstruction(InstructionType.NoChange, "", null)))),
                emptyList()
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
        RuleController().newRule(
            graph,
            RuleController.UpdateRuleParameters(
                "q-final-consonant",
                "q", "q",
                "* m > n / _#"
            )
        )
        return graph.addRuleSequence(
            "ce-to-q", ce, q,
            listOf(graph.ruleByName("q-final-consonant")!!.step())
        )
    }

}
