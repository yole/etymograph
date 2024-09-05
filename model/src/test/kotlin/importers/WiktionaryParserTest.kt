package ru.yole.etymograph.importers

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
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

    @Before
    fun setUp() {
        wiktionary = TestWiktionary()
    }

    @Test
    fun parseOrigin() {
        val oe = Language("Old English", "OE")
        val word = wiktionary.lookup(oe, "bridel").single()
        assertEquals("bridle", word.gloss)
//        val origin = word.relatedWords.single()
    }
}
