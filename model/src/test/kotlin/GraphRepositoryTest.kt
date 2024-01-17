package ru.yole.etymograph

import org.junit.Assert.*
import org.junit.Test

class GraphRepositoryTest : QBaseTest() {
    @Test
    fun links() {
        val repo = repoWithQ()
        val abc = repo.addWord("abc")
        val def = repo.addWord("def")
        repo.addLink(abc, def, Link.Derived, emptyList(), emptyList(), null)
        assertEquals(1, repo.getLinksTo(def).count())
        assertTrue(repo.deleteLink(abc, def, Link.Derived))
        assertEquals(0, repo.getLinksTo(def).count())
    }

    @Test
    fun deleteWord() {
        val repo = repoWithQ()

        val abc = repo.addWord("abc")
        repo.addWord("def")
        repo.deleteWord(abc)
        assertEquals(1, repo.dictionaryWords(q).size)
        assertTrue(repo.wordById(abc.id) == null)
        assertEquals(0, repo.wordsByText(q, "abc").size)
    }

    @Test
    fun classifyWordI() {
        val repo = repoWithQ()
        val ek = repo.addWord("ek", "I")
        assertEquals(1, repo.dictionaryWords(q).size)
    }
}
