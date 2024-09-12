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

        val word = wiktionary.lookup(repo, oe, "bridel").result.single()
        assertEquals("bridle", word.gloss)
        val origin = word.relatedWords.single()
        assertEquals("brigdilaz", origin.relatedWord.text)
        assertEquals("strap, rein", origin.relatedWord.gloss)
    }

    @Test
    fun parseAlternativeForm() {
        val oe = Language("Old English", "OE")
        oe.dictionarySettings = "ang-decl-noun-o-f: fem, o-stem"
        repo.addLanguage(oe)

        val word = wiktionary.lookup(repo, oe, "nytwyrþnes").result.single()
        val variation = word.relatedWords.single()
        assertEquals("nytwierþnes", variation.relatedWord.text)
        assertEquals(2, variation.relatedWord.classes.size)
    }

    @Test
    fun parseCompound() {
        val oe = Language("Old English", "OE")
        repo.addLanguage(oe)

        val word = wiktionary.lookup(repo, oe, "æþelboren").result.single()
        assertEquals(2, word.compoundComponents.size)
    }
}
