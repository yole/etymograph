package page.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleExamplesTest : QBaseTest() {
    @Test
    fun collectExampleForLinkUsingRule() {
        val rule = q.rule("* a > o", name = "q-a-o")
        val base = ce.word("kha", "old")
        val derived = q.word("kho", "new")
        val link = graph.addLink(derived, base, Link.Origin, listOf(rule))

        val examples = collectRuleExamples(rule)
        assertEquals(1, examples.size)
        val example = examples.single()
        assertSame(link, example.link)
        assertEquals(1, example.steps.size)
        val step = example.steps.single()
        assertSame(rule, step.rule)
        assertEquals("kho", step.result)
    }

    @Test
    fun includesAllIntermediateStepsForChainedRules() {
        val firstRule = q.rule("* a > o", name = "q-a-o")
        val secondRule = q.rule("* o > u", name = "q-o-u")

        val base = ce.word("kha", "old")
        val derived = q.word("khu", "new")
        graph.addLink(derived, base, Link.Origin, listOf(firstRule, secondRule))

        val example = collectRuleExamples(secondRule).single()
        assertEquals(2, example.steps.size)
        assertEquals("kho", example.steps[0].result)
        assertEquals("khu", example.steps[1].result)
        assertSame(firstRule, example.steps[0].rule)
        assertSame(secondRule, example.steps[1].rule)
    }

    @Test
    fun reportsMatchedBranchOfExample() {
        val rule = q.rule(
            "word ends with 'a':\n- change ending to 'o'\notherwise:\n- no change",
            name = "q-a-o-morpho"
        )
        val logic = rule.logic as MorphoRuleLogic

        val base = q.word("kala", "base")
        val derived = q.word("kalo", "derived")
        graph.addLink(derived, base, Link.Derived, listOf(rule))

        val example = collectRuleExamples(rule).single()
        assertEquals("kalo", example.steps.single().result)

        assertTrue(example.branches.contains(logic.branches[0]))
        assertFalse(example.branches.contains(logic.branches[1]))
    }

    @Test
    fun reportsMatchedInstructionOfExample() {
        val rule = q.rule("* a > o", name = "q-a-o")
        val logic = rule.logic as SpeRuleLogic

        val base = ce.word("kha", "old")
        val derived = q.word("kho", "new")
        graph.addLink(derived, base, Link.Origin, listOf(rule))

        val example = collectRuleExamples(rule).single()
        assertTrue(example.instructions.contains(logic.instructions.single()))
    }
}
