package ru.yole.etymograph.importers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import ru.yole.etymograph.Graph
import ru.yole.etymograph.InMemoryGraph
import ru.yole.etymograph.Language
import ru.yole.etymograph.WordCategoryValue

class WiktionaryParserTest {
    lateinit var wiktionary: TestWiktionary
    lateinit var graph: Graph
    lateinit var oe: Language
    lateinit var on: Language

    @Before
    fun setUp() {
        wiktionary = TestWiktionary(WiktionaryParserTest::class.java)
        graph = InMemoryGraph()
        oe = Language(graph, "Old English", "OE").withPartsOfSpeech()
        graph.addLanguage(oe)

        on = Language(graph, "Old Norse", "ON").withPartsOfSpeech()
        graph.addLanguage(on)

        val pgmc = Language(graph, "Proto-Germanic", "PGmc")
        graph.addLanguage(pgmc)

        pgmc.dictionarySettings = "wiktionary-id: gem-pro"
    }

    private fun Language.withPartsOfSpeech(): Language {
        pos.addAll(
            listOf(
                WordCategoryValue("Noun", "N"),
                WordCategoryValue("Adjective", "ADJ"),
                WordCategoryValue("Verb", "V")
            )
        )
        return this
    }

    @Test
    fun parseOrigin() {
        val word = wiktionary.lookup(graph, oe, "bridel").result.single()
        assertEquals("bridle", word.gloss)
        val origin = word.relatedWords.single()
        assertEquals("brigdilaz", origin.relatedWord.text)
        assertEquals("strap", origin.relatedWord.gloss)
    }

    @Test
    fun parseAlternativeForm() {
        oe.dictionarySettings = "ang-decl-noun-o-f: fem, o-stem"

        val word = wiktionary.lookup(graph, oe, "nytwyrþnes").result.single()
        val variation = word.relatedWords.single()
        assertEquals("nytwierþnes", variation.relatedWord.text)
        assertEquals(2, variation.relatedWord.classes.size)
    }

    @Test
    fun parseCompound() {
        val word = wiktionary.lookup(graph, oe, "æþelboren").result.single()
        assertEquals(2, word.compoundComponents.size)
    }

    @Test
    fun parseInflectionOf() {
        val word = wiktionary.lookup(graph, oe, "byþ").result.single()
        val baseWord = word.relatedWords.single()
        assertEquals(listOf("3", "sg", "pres"), baseWord.linkDetails)
        assertEquals("inflection of beon", word.gloss)
    }

    @Test
    fun parseInflectionOfMatchPOS() {
        val word = wiktionary.lookup(graph, oe, "iserne").result
        val baseNoun = word[0].relatedWords.single()
        assertEquals("N", baseNoun.relatedWord.pos)
        val baseAdj = word[1].relatedWords.single()
        assertEquals("ADJ", baseAdj.relatedWord.pos)
    }

    @Test
    fun parseMultipleEtymologies() {
        val words = wiktionary.lookup(graph, oe, "wesan").result
        assertEquals(3, words.size)
        val etymology1 = words[0].relatedWords
        assertEquals(1, etymology1.size)
        assertEquals("to be", etymology1.single().relatedWord.gloss)
        val etymology3 = words[2].relatedWords
        assertEquals(1, etymology3.size)
        assertEquals("juice", etymology3.single().relatedWord.gloss)
    }

    @Test
    fun commaSeparatedGloss() {
        val word = wiktionary.lookup(graph, oe, "werig").result.single()
        assertEquals("weary", word.gloss)
        assertEquals("weary, tired, exhausted, fatigued", word.fullGloss)
    }

    @Test
    fun languageLink() {
        val word = wiktionary.lookup(graph, oe, "oft").result.single()
        assertEquals("often, oft", word.fullGloss)
    }

    @Test
    fun alternativeAndInflection() {
        val result = wiktionary.lookup(graph, oe, "frecne").result
        assertEquals(3, result.size)
        assertEquals("variant of frēcn", result[1].gloss)
    }

    @Test
    fun lTag() {
        val result = wiktionary.lookup(graph, oe, "hand").result.single()
        assertEquals("hand", result.gloss)
    }

    @Test
    fun fleira() {
        val result = wiktionary.lookup(graph, on, "fleira").result.single()
        assertEquals(2, result.relatedWords.size)
        assertNull(result.fullGloss)
    }

    @Test
    fun ikorni() {
        on.dictionarySettings = "non-decl-m-a: m, strong\nnon-decl-m-an: m, weak"
        val result = wiktionary.lookup(graph, on, "ikorni").result.single()
        assertEquals(listOf("m", "weak"), result.classes)
    }
}
