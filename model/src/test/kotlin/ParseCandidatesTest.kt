package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParseCandidatesTest : QBaseTest() {

    @Test
    fun parseCandidates() {
        val repo = repoWithQ()
        val rule = parseRule(q, q, "- append 'llo'", name = "q-abl", addedCategories = ".ABL")
        repo.addRule(rule)
        val candidates = repo.findParseCandidates(q.word("hrestallo"))
        assertEquals(1, candidates.size)
        assertEquals("hresta", candidates[0].text)
        assertNull(candidates[0].word)
        assertEquals(rule, candidates[0].rules.single())
    }

    @Test
    fun parseCandidatesWithWord() {
        val repo = repoWithQ()
        val rule = parseRule(q, q, "- append 'llo'", name = "q-abl", addedCategories = ".ABL")
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
        val repo = repoWithQ()
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
    fun sameCategoryExistingWord() {
        val repo = repoWithQ()
        q.grammaticalCategories.add(WordCategory("Tense", listOf("V"),
            listOf(WordCategoryValue("Present", "PRES"), WordCategoryValue("Aorist", "AOR"))))
        val presRule = parseRule(q, q, "- append 'a'", name = "q-pres", addedCategories = ".PRES")
        val aorRule = parseRule(q, q, "- append 'i'", name = "q-aor", addedCategories = ".AOR")
        repo.addRule(presRule)
        repo.addRule(aorRule)

        repo.addWord("oi", "be.PRES")

        val candidates = repo.findParseCandidates(q.word("oia"))
        assertEquals(1, candidates.size)
        assertEquals("oi", candidates[0].text)
        assertNull(candidates[0].word)
    }

    @Test
    fun parseCandidatesExistingWordPOSMismatch() {
        val repo = repoWithQ()
        val presRule = parseRule(q, q, "- append 'a'", name = "q-pres", addedCategories = ".PRES", fromPOS = "V", toPOS = "V")
        repo.addRule(presRule)
        val hresta = repo.addWord("hrest", pos = "N")

        val candidates = repo.findParseCandidates(q.word("hresta"))
        assertEquals(1, candidates.size)
        assertNull(candidates[0].word)
    }

    @Test
    fun parseCandidatesExistingWordPOSMismatch2() {
        val repo = repoWithQ()
        val presRule = parseRule(q, q, "- append 'a'", name = "q-pres", addedCategories = ".PRES", fromPOS = "V")
        repo.addRule(presRule)
        val hresta = repo.addWord("hrest", pos = "N")

        val candidates = repo.findParseCandidates(q.word("hresta"))
        assertEquals(1, candidates.size)
        assertNull(candidates[0].word)
    }

    @Test
    fun parseCandidatesPOSMismatch() {
        val repo = repoWithQ()
        val presRule = parseRule(q, q, "- append 'a'", name = "q-pres", addedCategories = ".PRES", fromPOS = "V")
        repo.addRule(presRule)

        val candidates = repo.findParseCandidates(q.word("hresta", pos = "N"))
        assertEquals(0, candidates.size)
    }

    @Test
    fun parseCandidatesNotEmpty() {
        val repo = repoWithQ()
        val rule = parseRule(q, q, "- append 'llo'", name = "q-abl")
        repo.addRule(rule)
        val candidates = repo.findParseCandidates(q.word("llo"))
        assertEquals(0, candidates.size)
    }

    @Test
    fun normalizeCase() {
        val repo = repoWithQ()
        val rule = parseRule(q, q, "- append 'llo'", name = "q-abl", addedCategories = ".ABL")
        repo.addRule(rule)
        val candidates = repo.findParseCandidates(q.word("Hrestallo"))
        assertEquals(1, candidates.size)
        assertEquals("Hresta", candidates[0].text)
    }
}
