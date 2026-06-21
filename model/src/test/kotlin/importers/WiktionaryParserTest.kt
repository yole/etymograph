package page.yole.etymograph.importers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import page.yole.etymograph.Graph
import page.yole.etymograph.InMemoryGraph
import page.yole.etymograph.Language
import page.yole.etymograph.WordCategoryValue

class WiktionaryParserTest {
    lateinit var wiktionary: TestWiktionary
    lateinit var graph: Graph
    lateinit var oe: Language
    lateinit var on: Language

    @Before
    fun setUp() {
        wiktionary = TestWiktionary(WiktionaryParserTest::class.java)
        graph = InMemoryGraph()
        oe = graph.addLanguage("Old English", "OE").withPartsOfSpeech()
        on = graph.addLanguage("Old Norse", "ON").withPartsOfSpeech()
        val pgmc = graph.addLanguage("Proto-Germanic", "PGmc")

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
        val word = wiktionary.lookup(oe, "bridel").result.single()
        assertEquals("bridle", word.gloss)
        val origin = word.relatedWords.single()
        assertEquals("brigdilaz", origin.relatedWord.text)
        assertEquals("strap", origin.relatedWord.gloss)
    }

    @Test
    fun parseAlternativeForm() {
        oe.dictionarySettings = "ang-decl-noun-o-f: fem, o-stem"

        val word = wiktionary.lookup(oe, "nytwyrþnes").result.single()
        val variation = word.relatedWords.single()
        assertEquals("nytwierþnes", variation.relatedWord.text)
        assertEquals(2, variation.relatedWord.classes.size)
    }

    @Test
    fun parseCompound() {
        val word = wiktionary.lookup(oe, "æþelboren").result.single()
        assertEquals(2, word.compoundComponents.size)
    }

    @Test
    fun parseInflectionOf() {
        val word = wiktionary.lookup(oe, "byþ").result.single()
        val baseWord = word.relatedWords.single()
        assertEquals(listOf("3", "sg", "pres"), baseWord.linkDetails)
        assertEquals("inflection of beon", word.gloss)
    }

    @Test
    fun parseInflectionOfMatchPOS() {
        val word = wiktionary.lookup(oe, "iserne").result
        val baseNoun = word[0].relatedWords.single()
        assertEquals("N", baseNoun.relatedWord.pos)
        val baseAdj = word[1].relatedWords.single()
        assertEquals("ADJ", baseAdj.relatedWord.pos)
    }

    @Test
    fun parseMultipleEtymologies() {
        val words = wiktionary.lookup(oe, "wesan").result
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
        val word = wiktionary.lookup(oe, "werig").result.single()
        assertEquals("weary", word.gloss)
        assertEquals("weary, tired, exhausted, fatigued", word.fullGloss)
    }

    @Test
    fun languageLink() {
        val word = wiktionary.lookup(oe, "oft").result.single()
        assertEquals("often, oft", word.fullGloss)
    }

    @Test
    fun alternativeAndInflection() {
        val result = wiktionary.lookup(oe, "frecne").result
        assertEquals(3, result.size)
        assertEquals("variant of frēcn", result[1].gloss)
    }

    @Test
    fun lTag() {
        val result = wiktionary.lookup(oe, "hand").result.single()
        assertEquals("hand", result.gloss)
    }

    @Test
    fun fleira() {
        val result = wiktionary.lookup(on, "fleira").result.single()
        assertEquals(2, result.relatedWords.size)
        assertNull(result.fullGloss)
    }

    @Test
    fun ikorni() {
        on.dictionarySettings = "non-decl-m-a: m, strong\nnon-decl-m-an: m, weak"
        val result = wiktionary.lookup(on, "ikorni").result.single()
        assertEquals(listOf("m", "weak"), result.classes)
    }
}
