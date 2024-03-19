package ru.yole.etymograph.web

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.yole.etymograph.RuleLogic

class LanguageControllerTest {
    @Test
    fun grammaticalCategories() {
        val fixture = QTestFixture()
        val languageController = LanguageController(fixture.graphService)

        val parameters = LanguageController.UpdateLanguageParameters(
            grammaticalCategories = "Number (N, ADJ): Singular (SG), Plural (PL), Dual (DU), Collective Plural (PPL)\nCase (N): Nominative (NOM), Genitive (GEN)"
        )
        languageController.updateLanguage("q", parameters)

        val categories = fixture.q.grammaticalCategories
        assertEquals(2, categories.size)
        val number = categories[0]
        assertEquals("Number", number.name)
        assertEquals("ADJ", number.pos[1])
        assertEquals(4, number.values.size)
        assertEquals("Singular", number.values[0].name)
        assertEquals("SG", number.values[0].abbreviation)

        val vm = languageController.language("q")
        assertEquals(vm.grammaticalCategories, parameters.grammaticalCategories)
    }

    @Test
    fun wordClassesOptionalAbbreviation() {
        val fixture = QTestFixture()
        val languageController = LanguageController(fixture.graphService)

        val parameters = LanguageController.UpdateLanguageParameters(
            wordClasses = "Plural type (N): class-plural-rim"
        )
        languageController.updateLanguage("q", parameters)

        val categories = fixture.q.wordClasses
        assertEquals(1, categories.size)
        val pluralType = categories[0]
        assertEquals(1, pluralType.values.size)
        assertEquals("class-plural-rim", pluralType.values[0].name)
        assertEquals("class-plural-rim", pluralType.values[0].abbreviation)

        val vm = languageController.language("q")
        assertEquals(vm.wordClasses, parameters.wordClasses)
    }

    @Test
    fun diphthongs() {
        val fixture = QTestFixture()
        val languageController = LanguageController(fixture.graphService)

        val parameters = LanguageController.UpdateLanguageParameters(
            diphthongs = ""
        )
        languageController.updateLanguage("q", parameters)

        assertEquals(0, fixture.q.diphthongs.size)
    }

    @Test
    fun phonotacticsRules() {
        val fixture = QTestFixture()
        val rule = fixture.graphService.graph.addRule("q-phono", fixture.q, fixture.q,
            RuleLogic(emptyList(), emptyList()))

        val languageController = LanguageController(fixture.graphService)
        val parameters = LanguageController.UpdateLanguageParameters(
            phonotacticsRuleName = "q-phono"
        )
        languageController.updateLanguage("q", parameters)
        assertEquals(rule.id, fixture.q.phonotacticsRule?.resolve()?.id)

        val languageVM = languageController.language("q")
        assertEquals(rule.id, languageVM.phonotacticsRuleId)
        assertEquals("q-phono", languageVM.phonotacticsRuleName)
    }
}
