package ru.yole.etymograph.importers

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.yole.etymograph.GraphRepository
import ru.yole.etymograph.InMemoryGraphRepository
import ru.yole.etymograph.Language

class TestWiktionary : Wiktionary() {
    override fun loadWiktionaryPageSource(language: Language, title: String): String? {
        WiktionaryParserTest::class.java.getResourceAsStream("/wiktionary/${language.shortName.lowercase()}/$title.txt").use {
            return it?.reader()?.readText()
        }
    }
}

class WiktionaryParserTest {
    lateinit var wiktionary: TestWiktionary
    lateinit var repo: GraphRepository
    lateinit var oe: Language

    @Before
    fun setUp() {
        wiktionary = TestWiktionary()
        repo = InMemoryGraphRepository()
        oe = Language("Old English", "OE")
        repo.addLanguage(oe)

        val pgmc = Language("Proto-Germanic", "PGmc")
        repo.addLanguage(pgmc)

        pgmc.dictionarySettings = "wiktionary-id: gem-pro"
    }

    @Test
    fun parseOrigin() {
        val word = wiktionary.lookup(repo, oe, "bridel").result.single()
        assertEquals("bridle", word.gloss)
        val origin = word.relatedWords.single()
        assertEquals("brigdilaz", origin.relatedWord.text)
        assertEquals("strap, rein", origin.relatedWord.gloss)
    }

    @Test
    fun parseAlternativeForm() {
        oe.dictionarySettings = "ang-decl-noun-o-f: fem, o-stem"

        val word = wiktionary.lookup(repo, oe, "nytwyrþnes").result.single()
        val variation = word.relatedWords.single()
        assertEquals("nytwierþnes", variation.relatedWord.text)
        assertEquals(2, variation.relatedWord.classes.size)
    }

    @Test
    fun parseCompound() {
        val word = wiktionary.lookup(repo, oe, "æþelboren").result.single()
        assertEquals(2, word.compoundComponents.size)
    }

    @Test
    fun parseInflectionOf() {
        val word = wiktionary.lookup(repo, oe, "byþ").result.single()
        val baseWord = word.relatedWords.single()
        assertEquals(listOf("3", "sg", "pres"), baseWord.linkDetails)
        assertEquals("inflection of bēon", word.gloss)
    }

    @Test
    fun parseMultipleEtymologies() {
        val words = wiktionary.lookup(repo, oe, "wesan").result
        assertEquals(3, words.size)
        val etymology1 = words[0].relatedWords
        assertEquals(1, etymology1.size)
        assertEquals("to be", etymology1.single().relatedWord.gloss)
        val etymology3 = words[2].relatedWords
        assertEquals(1, etymology3.size)
        assertEquals("juice, moisture", etymology3.single().relatedWord.gloss)
    }
}
