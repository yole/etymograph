package page.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import page.yole.etymograph.importers.TestWiktionary

class AugmentWordTest {
    lateinit var wiktionary: TestWiktionary
    lateinit var graph: Graph
    lateinit var oe: Language

    @Before
    fun setUp() {
        wiktionary = TestWiktionary(AugmentWordTest::class.java)
        graph = InMemoryGraph()
        oe = graph.addLanguage("Old English", "OE")
        oe.pos.addAll(
            listOf(
                WordCategoryValue("Noun", "N"),
                WordCategoryValue("Adjective", "ADJ"),
                WordCategoryValue("Verb", "V")
            )
        )
        oe
            .withGrammaticalCategory("Case", "N", "Dative" to "DAT")
            .withGrammaticalCategory("Number", "N", "Singular" to "SG", "Plural" to  "PL")
    }

    @Test
    fun augmentInflection() {
        val bille = graph.findOrAddWord("bille", oe, null)
        val rule = oe.rule("- no change", name = "oe-dat", addedCategories = ".DAT")
        augmentWordWithDictionary(wiktionary, bille)
        assertNull(bille.gloss)
        val link = bille.baseWordLink()
        assertEquals("bil", (link!!.toEntity as Word).text)
        assertEquals(rule, link.rules.single())
    }

    @Test
    fun augmentInflectionAmbiguous() {
        val wyrtum = graph.findOrAddWord("wyrtum", oe, null)
        val rule = oe.rule("- no change", name = "oe-dat-pl", addedCategories = ".DAT.PL")
        val result = augmentWordWithDictionary(wiktionary, wyrtum)
        assertEquals(2, result.variants.size)

        augmentWordWithDictionary(wiktionary, wyrtum, result.variants[0].disambiguation)
        val link = wyrtum.baseWordLink()
        assertEquals("wyrt", (link!!.toEntity as Word).text)
        assertEquals("plant", link.toEntity.gloss)
    }
}
