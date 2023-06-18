package ru.yole.etymograph

import org.junit.Assert.*
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
        repo.addWord("def")
        repo.deleteWord(abc)
        assertEquals(1, repo.dictionaryWords(q).size)
        assertTrue(repo.wordById(abc.id) == null)
        assertEquals(0, repo.wordsByText(q, "abc").size)
    }

    @Test
    fun parseCandidates() {
        val repo = InMemoryGraphRepository()
        repo.addLanguage(q)
        val rule = parseRule(q, q, "- add suffix 'llo'", name = "q-abl")
        repo.addRule(rule)
        val candidates = repo.findParseCandidates(q.word("hrestallo"))
        assertEquals(1, candidates.size)
        assertEquals("hresta", candidates[0].text)
        assertNull(candidates[0].word)
        assertEquals(rule, candidates[0].rules.single())
    }

    @Test
    fun parseCandidatesWithWord() {
        val repo = InMemoryGraphRepository()
        repo.addLanguage(q)
        val rule = parseRule(q, q, "- add suffix 'llo'", name = "q-abl")
        val hresta = repo.addWord("hresta")
        repo.addRule(rule)

        val candidates = repo.findParseCandidates(q.word("hrestallo"))
        assertEquals(1, candidates.size)
        assertEquals("hresta", candidates[0].text)
        assertEquals(hresta, candidates[0].word)
        assertEquals(rule, candidates[0].rules.single())
    }
}
