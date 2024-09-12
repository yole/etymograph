package ru.yole.etymograph.importers

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.yole.etymograph.GraphRepository
import ru.yole.etymograph.InMemoryGraphRepository
import ru.yole.etymograph.Language
import ru.yole.etymograph.WordCategory
import ru.yole.etymograph.WordCategoryValue
import ru.yole.etymograph.rule

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
    lateinit var oe: Language

    @Before
    fun setUp() {
        wiktionary = TestWiktionary()
        repo = InMemoryGraphRepository()
        oe = Language("Old English", "OE")
        repo.addLanguage(oe)
    }

    @Test
    fun parseOrigin() {
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

        /*
        oe.grammaticalCategories.apply {
            add(WordCategory("Tense", listOf("V"), listOf(WordCategoryValue("Present", "PRES"))))
            add(WordCategory("Person", listOf("V"), listOf(WordCategoryValue("Third", "3"))))
            add(WordCategory("Number", listOf("V"), listOf(WordCategoryValue("Singular", "SG"))))
        }

        val rule = repo.rule("- no change", oe, addedCategories = ".PRES.3SG")
         */
    }
}
