package page.yole.etymograph.web

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import page.yole.etymograph.WordCategory
import page.yole.etymograph.WordCategoryValue
import page.yole.etymograph.web.controllers.ParadigmController

class ParadigmControllerTest {
    private lateinit var fixture: QTestFixture
    private lateinit var paradigmController: ParadigmController

    @Before
    fun setup() {
        fixture = QTestFixture()
        paradigmController = ParadigmController()

        fixture.q.grammaticalCategories.add(
            WordCategory(
                "Case", listOf("N", "ADJ"),
                listOf(WordCategoryValue("Nominative", "NOM"))
            )
        )
        fixture.q.grammaticalCategories.add(
            WordCategory(
                "Number", listOf("N", "ADJ"),
                listOf(WordCategoryValue("Singular", "SG"))
            )
        )
    }

    @Test
    fun generateParadigmWithMultiplePos() {
        val paradigm = paradigmController.generateParadigm(
            fixture.graph,
            ParadigmController.GenerateParadigmParameters(
                name = "Noun and adjective",
                lang = "q",
                pos = "N, ADJ",
                addedCategories = null,
                prefix = "q-",
                rows = "Case",
                columns = "Number",
                endings = null,
                source = null
            )
        )

        assertEquals(listOf("N", "ADJ"), paradigm.pos)
    }
}