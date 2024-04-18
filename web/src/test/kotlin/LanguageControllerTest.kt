package ru.yole.etymograph.web

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.yole.etymograph.RuleLogic
import ru.yole.etymograph.web.controllers.LanguageController

class LanguageControllerTest {
    private lateinit var fixture: QTestFixture
    private lateinit var languageController: LanguageController

    @Before
    fun setup() {
        fixture = QTestFixture()
        languageController = LanguageController()
    }

    @Test
    fun grammaticalCategories() {
        val parameters = LanguageController.UpdateLanguageParameters(
            grammaticalCategories = "Number (N, ADJ): Singular (SG), Plural (PL), Dual (DU), Collective Plural (PPL)\nCase (N): Nominative (NOM), Genitive (GEN)"
        )
        languageController.updateLanguage(fixture.graph, "q", parameters)

        val categories = fixture.q.grammaticalCategories
        assertEquals(2, categories.size)
        val number = categories[0]
        assertEquals("Number", number.name)
        assertEquals("ADJ", number.pos[1])
        assertEquals(4, number.values.size)
        assertEquals("Singular", number.values[0].name)
        assertEquals("SG", number.values[0].abbreviation)

        val vm = languageController.language(fixture.graph, "q")
        assertEquals(vm.grammaticalCategories, parameters.grammaticalCategories)
    }

    @Test
    fun wordClassesOptionalAbbreviation() {
        val parameters = LanguageController.UpdateLanguageParameters(
            wordClasses = "Plural type (N): class-plural-rim"
        )
        languageController.updateLanguage(fixture.graph, "q", parameters)

        val categories = fixture.q.wordClasses
        assertEquals(1, categories.size)
        val pluralType = categories[0]
        assertEquals(1, pluralType.values.size)
        assertEquals("class-plural-rim", pluralType.values[0].name)
        assertEquals("class-plural-rim", pluralType.values[0].abbreviation)

        val vm = languageController.language(fixture.graph, "q")
        assertEquals(vm.wordClasses, parameters.wordClasses)
    }

    @Test
    fun diphthongs() {
        val parameters = LanguageController.UpdateLanguageParameters(
            diphthongs = ""
        )
        languageController.updateLanguage(fixture.graph, "q", parameters)

        assertEquals(0, fixture.q.diphthongs.size)
    }

    @Test
    fun phonotacticsRules() {
        val rule = fixture.graph.addRule("q-phono", fixture.q, fixture.q,
            RuleLogic(emptyList(), emptyList()))

        val parameters = LanguageController.UpdateLanguageParameters(
            phonotacticsRuleName = "q-phono"
        )
        languageController.updateLanguage(fixture.graph, "q", parameters)
        assertEquals(rule.id, fixture.q.phonotacticsRule?.resolve()?.id)

        val languageVM = languageController.language(fixture.graph, "q")
        assertEquals(rule.id, languageVM.phonotacticsRuleId)
        assertEquals("q-phono", languageVM.phonotacticsRuleName)
    }

    @Test
    fun copyPhonemes() {
        fixture.graph.addPhoneme(fixture.q, listOf("a"), null, setOf("open", "back", "vowel"))
        languageController.addLanguage(fixture.graph, LanguageController.UpdateLanguageParameters(
            "Sindarin", "S"
        ))
        languageController.copyPhonemes(fixture.graph, "S", LanguageController.CopyPhonemesParams("q"))
        val s = fixture.graph.languageByShortName("S")!!
        assertEquals(1, s.phonemes.size)
    }
}
