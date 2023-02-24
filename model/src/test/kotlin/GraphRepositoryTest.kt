package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphRepositoryTest : QBaseTest() {
    @Test
    fun links() {
        val repo = InMemoryGraphRepository()
        repo.addLanguage(q)
        val abc = repo.addWord("abc")
        val def = repo.addWord("def")
        repo.addLink(abc, def, Link.Derived, emptyList(), null, null)
        assertEquals(1, repo.getLinksTo(def).count())
        assertTrue(repo.deleteLink(abc, def, Link.Derived))
        assertEquals(0, repo.getLinksTo(def).count())
    }

    @Test
    fun deleteWord() {
        val repo = InMemoryGraphRepository()
        repo.addLanguage(q)

        val abc = repo.addWord("abc")
        val def = repo.addWord("def")
        repo.deleteWord(abc)
        assertEquals(1, repo.dictionaryWords(q).size)
        assertTrue(repo.wordById(abc.id) == null)
        assertEquals(0, repo.wordsByText(q, "abc").size)
    }

    fun GraphRepository.addWord(text: String) = findOrAddWord(text, q, text)
}
