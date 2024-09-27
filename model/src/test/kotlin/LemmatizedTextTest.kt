package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TestDictionary : Dictionary {
    override fun lookup(repo: GraphRepository, language: Language, word: String, disambiguation: String?): LookupResult {
        return LookupResult.empty
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
        val lWord = lemmatizedWord("mæg", "verb")
        val lText = LemmatizedText("mæg", listOf(lWord))

        val text = importLemmatizedText(repo, oe, dictionary, "Test", lText)
        assertEquals("mæg", text.text)
        assertEquals(0, text.words.size)
    }

    @Test
    fun importWordProperNoun() {
        val dictionary = TestDictionary()
        val lWord = lemmatizedWord("Maria", "proper noun")
        val lText = LemmatizedText("Maria", listOf(lWord))
        val text = importLemmatizedText(repo, oe, dictionary, "Test", lText)
        assertEquals("Maria", text.text)
        assertEquals(1, text.words.size)
        assertEquals("NP", text.words[0]!!.pos)
    }

    private fun lemmatizedWord(text: String, pos: String): LemmatizedWord {
        return  LemmatizedWord(text, listOf(LemmatizedToken(text, text, pos, emptyList())))
    }
}
