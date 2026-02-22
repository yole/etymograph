package ru.yole.etymograph

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GraphRepositoryTest : QBaseTest() {
    lateinit var repo: InMemoryGraphRepository

    @Before
    fun setup() {
        repo = repoWithQ()
    }

    @Test
    fun links() {
        val abc = repo.addWord("abc")
        val def = repo.addWord("def")
        repo.addLink(abc, def, Link.Derived)
        assertEquals(1, repo.getLinksTo(def).count())
        assertTrue(repo.deleteLink(abc, def, Link.Derived))
        assertEquals(0, repo.getLinksTo(def).count())
    }

    @Test
    fun deleteWord() {
        val abc = repo.addWord("abc")
        repo.addWord("def")
        repo.deleteWord(abc)
        assertEquals(1, repo.dictionaryWords(q).size)
        assertTrue(repo.wordById(abc.id) == null)
        assertEquals(0, repo.wordsByText(q, "abc").size)
    }

    @Test
    fun classifyWordI() {
        val ek = repo.addWord("ek", "I")
        assertEquals(1, repo.dictionaryWords(q).size)
    }

    @Test
    fun wordByTextSyllabographic() {
        val ht = Language("Hittite", "Ht")
        ht.syllabographic = true
        repo.addLanguage(ht)
        repo.addWord("_A-NA", language = ht, gloss = "on", syllabographic = true)
        assertEquals(1, repo.wordsByText(ht, "_A-NA", true).size)
    }

    @Test
    fun wordByTextCaseInsensitive() {
        val pie = Language("Proto-Indo-European", "PIE")
        pie.phonemes += phoneme("H", "")
        repo.addLanguage(pie)
        repo.addWord("bheroH", language = pie, gloss = "carry.1SG")
        assertEquals(1, repo.wordsByText(pie, "bheroH").size)
        assertEquals(1, repo.wordsByText(pie, "bheroh").size)
    }
}
