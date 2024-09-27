package ru.yole.etymograph

import org.junit.Assert.assertEquals
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
            "Number", listOf("N"), listOf(WordCategoryValue("Singular", "SG"))
        ))
        repo.addLanguage(oe)
    }

    @Test
    fun augmentInflection() {
        val bille = repo.findOrAddWord("bille", oe, null)
        val rule = repo.rule("- no change", oe, name = "oe-dat", addedCategories = ".DAT")
        augmentWordWithDictionary(repo, wiktionary, bille)
        val link = bille.baseWordLink(repo)
        assertEquals("bil", (link!!.toEntity as Word).text)
        assertEquals(rule, link.rules.single())
    }
}
