package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import ru.yole.etymograph.importers.TestWiktionary

class AugmentWordTest {
    lateinit var wiktionary: TestWiktionary
    lateinit var repo: GraphRepository
    lateinit var oe: Language

    @Before
    fun setUp() {
        wiktionary = TestWiktionary(AugmentWordTest::class.java)
        repo = InMemoryGraphRepository()
        oe = Language("Old English", "OE")
        oe.pos.addAll(
            listOf(
                WordCategoryValue("Noun", "N"),
                WordCategoryValue("Adjective", "ADJ"),
                WordCategoryValue("Verb", "V")
            )
        )
        oe.grammaticalCategories.add(WordCategory(
            "Case", listOf("N"), listOf(WordCategoryValue("Dative", "DAT"))
        ))
        oe.grammaticalCategories.add(WordCategory(
            "Number", listOf("N"), listOf(
                WordCategoryValue("Singular", "SG"),
                WordCategoryValue("Plural", "PL")
            )
        ))
        repo.addLanguage(oe)
    }

    @Test
    fun augmentInflection() {
        val bille = repo.findOrAddWord("bille", oe, null)
        val rule = repo.rule("- no change", oe, name = "oe-dat", addedCategories = ".DAT")
        augmentWordWithDictionary(repo, wiktionary, bille)
        assertNull(bille.gloss)
        val link = bille.baseWordLink(repo)
        assertEquals("bil", (link!!.toEntity as Word).text)
        assertEquals(rule, link.rules.single())
    }

    @Test
    fun augmentInflectionAmbiguous() {
        val wyrtum = repo.findOrAddWord("wyrtum", oe, null)
        val rule = repo.rule("- no change", oe, name = "oe-dat-pl", addedCategories = ".DAT.PL")
        val result = augmentWordWithDictionary(repo, wiktionary, wyrtum)
        assertEquals(2, result.variants.size)

        augmentWordWithDictionary(repo, wiktionary, wyrtum, result.variants[0].disambiguation)
        val link = wyrtum.baseWordLink(repo)
        assertEquals("wyrt", (link!!.toEntity as Word).text)
        assertEquals("plant", link.toEntity.gloss)
    }
}
