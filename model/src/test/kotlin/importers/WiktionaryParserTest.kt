package ru.yole.etymograph.importers

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.yole.etymograph.GraphRepository
import ru.yole.etymograph.InMemoryGraphRepository
import ru.yole.etymograph.Language

class TestWiktionary : Wiktionary() {
    override fun loadWiktionaryPageSource(language: Language, title: String): String? {
        WiktionaryParserTest::class.java.getResourceAsStream("/wiktionary/$title.txt").use {
            return it?.reader()?.readText()
        }
    }
}

class WiktionaryParserTest {
    lateinit var wiktionary: TestWiktionary
    lateinit var repo: GraphRepository

    @Before
    fun setUp() {
        wiktionary = TestWiktionary()
        repo = InMemoryGraphRepository()
    }

    @Test
    fun parseOrigin() {
        val oe = Language("Old English", "OE")
        repo.addLanguage(oe)
        val pgmc = Language("Proto-Germanic", "PGmc")
        repo.addLanguage(pgmc)

        pgmc.dictionarySettings = "wiktionary-id: gem-pro"

        val word = wiktionary.lookup(repo, oe, "bridel").single()
        assertEquals("bridle", word.gloss)
//        val origin = word.relatedWords.single()
    }
}
