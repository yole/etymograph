package ru.yole.etymograph.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.yole.etymograph.WordCategory
import ru.yole.etymograph.WordCategoryValue
import ru.yole.etymograph.RuleLogic

class RuleControllerTest {
    @Test
    fun testGrammaticalCategories() {
        val fixture = QTestFixture()
        val ruleController = RuleController(fixture.graphService)

        fixture.q.grammaticalCategories.add(
            WordCategory("Case", listOf("N"),
                listOf(WordCategoryValue("Genitive", "GEN"))))

        val rule = fixture.graphService.graph.addRule("q-gen", fixture.q, fixture.q,
            RuleLogic(emptyList(), emptyList()), ".GEN")

        val ruleVM = ruleController.rule(rule.id)
        assertEquals("Case: Genitive", ruleVM.addedCategoryDisplayNames)
    }

    @Test
    fun testGrammaticalCategoriesExtractNumber() {
        val fixture = QTestFixture()
        val ruleController = RuleController(fixture.graphService)

        fixture.q.grammaticalCategories.add(
            WordCategory("Person", listOf("V"),
                listOf(WordCategoryValue("1st person", "1"))))
        fixture.q.grammaticalCategories.add(
            WordCategory("Number", listOf("V"),
                listOf(WordCategoryValue("Singular", "SG"))))

        val rule = fixture.graphService.graph.addRule("q-1sg", fixture.q, fixture.q,
            RuleLogic(emptyList(), emptyList()), ".1SG")

        val ruleVM = ruleController.rule(rule.id)
        assertEquals("Person: 1st person, Number: Singular", ruleVM.addedCategoryDisplayNames)
    }

    @Test
    fun testEmptyToPOS() {
        val fixture = QTestFixture()
        val ruleController = RuleController(fixture.graphService)

        ruleController.newRule(
            RuleController.UpdateRuleParameters(
            "q-pos",
            "q", "q",
            "- no change",
            toPOS = ""
        ))

        val rule = fixture.graphService.graph.ruleByName("q-pos")
        assertNull(rule!!.toPOS)
    }
}
