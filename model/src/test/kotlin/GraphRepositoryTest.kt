package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphRepositoryTest {
    val q = Language("Quenya", "Q")

    @Test
    fun links() {
        val repo = InMemoryGraphRepository()
        repo.addLanguage(q)
        val abc = repo.addWord("abc", q, null, null, null, null)
        val def = repo.addWord("def", q, null, null, null, null)
        repo.addLink(abc, def, Link.Derived, null, null, null)
        assertEquals(1, repo.getLinksTo(def).count())
        assertTrue(repo.deleteLink(abc, def, Link.Derived))
        assertEquals(0, repo.getLinksTo(def).count())
    }
}