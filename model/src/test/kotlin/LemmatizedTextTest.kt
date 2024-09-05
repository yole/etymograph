package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TestDictionary : Dictionary {
    override fun lookup(language: Language, word: String): List<Word> {
        return emptyList()
    }
}

class LemmatizedTextTest {
    lateinit var repo: GraphRepository
    lateinit var oe: Language

    @Before
    fun setup() {
        repo = InMemoryGraphRepository()
        oe = Language("Old English", "OE")
        repo.addLanguage(oe)
    }

    @Test
    fun importNoWordInDictionary() {
        val dictionary = TestDictionary()
        val lWord = LemmatizedWord("mæg", listOf(LemmatizedToken("mæg", "mæg", "verb", emptyList())))
        val lText = LemmatizedText("mæg", listOf(lWord))

        val text = importLemmatizedText(repo, oe, dictionary, "Test", lText)
        assertEquals("mæg", text.text)
        assertEquals(0, text.words.size)
    }
}
