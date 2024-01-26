package ru.yole.etymograph.web

import ru.yole.etymograph.*

class QTestFixture {
    val q = Language("Quenya", "q")
    val repo = InMemoryGraphRepository().apply {
        addLanguage(q)
    }

    val graphService = object : GraphService() {
        override val graph: GraphRepository
            get() = repo
    }

    fun setupParadigm(): Rule {
        val accRule = repo.addRule(
            "q-acc", q, q,
            RuleLogic(
                emptyList(),
                listOf(RuleBranch(OtherwiseCondition, listOf(RuleInstruction(InstructionType.NoChange, ""))))
            ),
            ".ACC"
        )

        val paradigm = repo.addParadigm("Noun", q, listOf("N", "NP"))
        paradigm.addRow("Nom")
        paradigm.addRow("Acc")
        paradigm.addColumn("Sg")
        paradigm.setRule(1, 0, listOf(accRule))
        return accRule
    }
}
