package ru.yole.etymograph.web

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
