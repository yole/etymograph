package ru.yole.etymograph.web

import org.junit.Assert.*
import org.junit.Test
import org.springframework.web.server.ResponseStatusException
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

    @Test
    fun uniqueRuleName() {
        val fixture = QTestFixture()
        val ruleController = RuleController(fixture.graphService)

        ruleController.newRule(
            RuleController.UpdateRuleParameters(
                "q-pos",
                "q", "q",
                "- no change"
            ))

        assertThrows("Rule named 'q-pos' already exists", ResponseStatusException::class.java) {
            ruleController.newRule(
                RuleController.UpdateRuleParameters(
                    "q-pos",
                    "q", "q",
                    "- no change"
                ))
        }
    }

    @Test
    fun newSequence() {
        val fixture = QTestFixture()
        val rule = fixture.graphService.graph.addRule("q-gen", fixture.q, fixture.q,
            RuleLogic(emptyList(), emptyList()))

        val ruleController = RuleController(fixture.graphService)
        ruleController.newSequence(
            RuleController.UpdateSequenceParams(
                "ce-to-q",
                "ce",
                "q",
                rule.name
            )
        )

        val seq = fixture.graphService.graph.ruleSequencesForLanguage(fixture.q).single()

        val ruleList = ruleController.rules("q")
        assertEquals(1, ruleList.ruleGroups.size)
        assertEquals("Phonetics: ce-to-q", ruleList.ruleGroups[0].groupName)
        assertEquals(seq.id, ruleList.ruleGroups[0].sequenceId)
    }

    @Test
    fun editSequence() {
        val fixture = QTestFixture()
        val graph = fixture.graphService.graph
        val rule = graph.addRule("q-gen", fixture.q, fixture.q,
            RuleLogic(emptyList(), emptyList()), ".GEN")
        val seq = graph.addRuleSequence("ce-to-q", fixture.ce, fixture.q, listOf(rule))

        val rule2 = graph.addRule("q-acc", fixture.q, fixture.q,
            RuleLogic(emptyList(), emptyList()))

        val ruleController = RuleController(fixture.graphService)
        ruleController.updateSequence(
            seq.id,
            RuleController.UpdateSequenceParams(
                "ce-to-q",
                "ce",
                "q",
                "${rule.name}\n${rule2.name}"
            )
        )

        val ruleList = ruleController.rules("q")
        assertEquals(1, ruleList.ruleGroups.size)
        assertEquals(2, ruleList.ruleGroups[0].rules.size)
    }
}
