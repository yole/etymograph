package ru.yole.etymograph.web

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.yole.etymograph.GrammaticalCategory
import ru.yole.etymograph.GrammaticalCategoryValue
import ru.yole.etymograph.RuleLogic

class RuleControllerTest {
    @Test
    fun testGrammaticalCategories() {
        val fixture = QTestFixture()
        val ruleController = RuleController(fixture.graphService)

        fixture.q.grammaticalCategories.add(
            GrammaticalCategory("Case", listOf("N"),
                listOf(GrammaticalCategoryValue("Genitive", "GEN"))))

        val rule = fixture.graphService.graph.addRule("q-gen", fixture.q, fixture.q,
            RuleLogic(emptyList(), emptyList()), ".GEN")

        val ruleVM = ruleController.rule(rule.id)
        assertEquals("Case: Genitive", ruleVM.addedCategoryDisplayNames)
    }
}
