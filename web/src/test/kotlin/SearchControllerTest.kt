package ru.yole.etymograph.web

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.yole.etymograph.GraphRepository
import ru.yole.etymograph.Language
import ru.yole.etymograph.web.controllers.SearchController

class SearchControllerTest {
    private lateinit var fixture: QTestFixture
    private lateinit var graph: GraphRepository
    private lateinit var q: Language
    private lateinit var ce: Language
    private lateinit var oe: Language
    private lateinit var controller: SearchController

    @Before
    fun setup() {
        fixture = QTestFixture()
        graph = fixture.graph
        q = fixture.q
        ce = fixture.ce
        oe = Language("Old English", "OE")
        graph.addLanguage(oe)
        controller = SearchController()

        // Seed some words across languages
        graph.findOrAddWord("élan", q, "vigor") // composed accent
        graph.findOrAddWord("elan", q, "spear") // to test homonym vs different gloss, but different text here
        graph.findOrAddWord("Elan", q, "proper") // case variant

        graph.findOrAddWord("lan", q, "substring target")
        graph.findOrAddWord("elanor", q, "prefix target")

        // Homonyms in same language and text
        graph.findOrAddWord("an", q, "first")
        graph.findOrAddWord("an", q, "second")

        // Other language words
        graph.findOrAddWord("elan", ce, "proto root")
        graph.findOrAddWord("æppel", oe, "apple")
    }

    @Test
    fun normalizationDiacritics() {
        val res1 = controller.search(graph, q = "elan", limit = 50, offset = 0, mode = SearchController.SearchMode.auto)
        assertEquals("exact", res1.usedMode)
        assertEquals(4, res1.totalExact)
        assertEquals(4, res1.matches.size)
        assertTrue(res1.matches.any { it.text == "élan" })

        // case-insensitive: uppercase query finds the same 4 entries
        val res2 = controller.search(graph, q = "ELAN", limit = 50, offset = 0, mode = SearchController.SearchMode.auto)
        assertEquals("exact", res2.usedMode)
        assertEquals(4, res2.totalExact)
        assertEquals(4, res2.matches.size)
    }

    @Test
    fun matchExactAndPrefix() {
        // Exact normalized match (query 'élan' matches 4 entries in q: élan/elan/Elan/elanor)
        val exact = controller.search(graph, q = "élan", limit = 50, offset = 0, mode = SearchController.SearchMode.auto)
        assertEquals("exact", exact.usedMode)
        assertEquals(4, exact.totalExact)
        assertEquals(4, exact.matches.size)
        assertTrue(exact.matches.any { it.text == "élan" })

        // Query "ela" in auto mode also yields substring matches (4 entries)
        val ela = controller.search(graph, q = "ela", limit = 50, offset = 0, mode = SearchController.SearchMode.auto)
        assertEquals("prefix", ela.usedMode)
        assertEquals(0, ela.totalExact)
        assertEquals(5, ela.totalPrefix)
        assertEquals(5, ela.matches.size)
    }

    @Test
    fun paginationAndOffset() {
        // Create many words starting with "test" to test pagination in auto (substring) mode
        for (i in 1..10) {
            graph.findOrAddWord("test$i", q, "g$i")
        }
        val res1 = controller.search(graph, q = "test", limit = 3, offset = 0, mode = SearchController.SearchMode.auto)
        assertEquals("prefix", res1.usedMode)
        assertEquals(10, res1.totalPrefix)
        assertEquals(3, res1.matches.size)

        val res2 = controller.search(graph, q = "test", limit = 3, offset = 3, mode = SearchController.SearchMode.auto)
        assertEquals("prefix", res2.usedMode)
        assertEquals(10, res2.totalPrefix)
        assertEquals(3, res2.matches.size)
        // Ensure no overlap between pages 1 and 2
        val ids1 = res1.matches.map { it.id }.toSet()
        val ids2 = res2.matches.map { it.id }.toSet()
        assertTrue(ids1.intersect(ids2).isEmpty())
    }
}
