package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Test

class AlternativesTest : QBaseTest() {
    @Test
    fun simple() {
        val repo = InMemoryGraphRepository()
        val ct1 = repo.addCorpusText("elen sila", null, q)
        val word = repo.addWord("elen", "star", "N")

        val accRule = repo.addRule(
            "q-acc", q, q,
            RuleLogic(
                emptyList(),
                listOf(RuleBranch(OtherwiseCondition, listOf(RuleInstruction(InstructionType.NoChange, ""))))
            )
        )

        val paradigm = repo.addParadigm("Noun", q, "N")
        paradigm.addRow("Nom")
        paradigm.addRow("Acc")
        paradigm.addColumn("Sg")
        paradigm.setRule(1, 0, listOf(accRule))

        val alts = repo.requestAlternatives(word)
        assertEquals(1, alts.size)
        assertEquals(accRule, alts[0].rules[0])
    }
}
