package ru.yole.etymograph

import org.junit.Assert.*
import org.junit.Test

class GraphRepositoryTest : QBaseTest() {
    @Test
    fun links() {
        val repo = setupRepo()
        val abc = repo.addWord("abc")
        val def = repo.addWord("def")
        repo.addLink(abc, def, Link.Derived, emptyList(), emptyList(), null)
        assertEquals(1, repo.getLinksTo(def).count())
        assertTrue(repo.deleteLink(abc, def, Link.Derived))
        assertEquals(0, repo.getLinksTo(def).count())
    }

    @Test
    fun deleteWord() {
        val repo = setupRepo()

        val abc = repo.addWord("abc")
        repo.addWord("def")
        repo.deleteWord(abc)
        assertEquals(1, repo.dictionaryWords(q).size)
        assertTrue(repo.wordById(abc.id) == null)
        assertEquals(0, repo.wordsByText(q, "abc").size)
    }

    @Test
    fun parseCandidates() {
        val repo = setupRepo()
        val rule = parseRule(q, q, "- append 'llo'", name = "q-abl")
        repo.addRule(rule)
        val candidates = repo.findParseCandidates(q.word("hrestallo"))
        assertEquals(1, candidates.size)
        assertEquals("hresta", candidates[0].text)
        assertNull(candidates[0].word)
        assertEquals(rule, candidates[0].rules.single())
    }

    @Test
    fun parseCandidatesWithWord() {
        val repo = setupRepo()
        val rule = parseRule(q, q, "- append 'llo'", name = "q-abl")
        val hresta = repo.addWord("hresta")
        repo.addRule(rule)

        val candidates = repo.findParseCandidates(q.word("hrestallo"))
        assertEquals(1, candidates.size)
        assertEquals("hresta", candidates[0].text)
        assertEquals(hresta, candidates[0].word)
        assertEquals(rule, candidates[0].rules.single())
    }

    @Test
    fun parseCandidatesSameCategory() {
        val repo = setupRepo()
        q.grammaticalCategories.add(WordCategory("Tense", listOf("V"),
            listOf(WordCategoryValue("Present", "PRES"), WordCategoryValue("Aorist", "AOR"))))
        val presRule = parseRule(q, q, "- append 'a'", name = "q-pres", addedCategories = ".PRES")
        val aorRule = parseRule(q, q, "- append 'i'", name = "q-aor", addedCategories = ".AOR")
        repo.addRule(presRule)
        repo.addRule(aorRule)

        val candidates = repo.findParseCandidates(q.word("oia"))
        assertEquals(1, candidates.size)
        assertEquals("oi", candidates[0].text)
    }

    @Test
    fun parseCandidatesExistingWordPOSMismatch() {
        val repo = setupRepo()
        val presRule = parseRule(q, q, "- append 'a'", name = "q-pres", addedCategories = ".PRES", fromPOS = "V", toPOS = "V")
        repo.addRule(presRule)
        val hresta = repo.addWord("hrest", pos = "N")

        val candidates = repo.findParseCandidates(q.word("hresta"))
        assertEquals(1, candidates.size)
        assertNull(candidates[0].word)
    }

    @Test
    fun parseCandidatesNotEmpty() {
        val repo = setupRepo()
        val rule = parseRule(q, q, "- append 'llo'", name = "q-abl")
        repo.addRule(rule)
        val candidates = repo.findParseCandidates(q.word("llo"))
        assertEquals(0, candidates.size)
    }

    @Test
    fun classifyWordI() {
        val repo = setupRepo()
        val ek = repo.addWord("ek", "I")
        assertEquals(1, repo.dictionaryWords(q).size)
    }

    private fun setupRepo(): InMemoryGraphRepository {
        return InMemoryGraphRepository().apply {
            addLanguage(q)
        }
    }
}
