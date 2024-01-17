package ru.yole.etymograph

import org.junit.Assert
import org.junit.Test

class ParseCandidatesTest : QBaseTest() {

    @Test
    fun parseCandidates() {
        val repo = repoWithQ()
        val rule = parseRule(q, q, "- append 'llo'", name = "q-abl")
        repo.addRule(rule)
        val candidates = repo.findParseCandidates(q.word("hrestallo"))
        Assert.assertEquals(1, candidates.size)
        Assert.assertEquals("hresta", candidates[0].text)
        Assert.assertNull(candidates[0].word)
        Assert.assertEquals(rule, candidates[0].rules.single())
    }

    @Test
    fun parseCandidatesWithWord() {
        val repo = repoWithQ()
        val rule = parseRule(q, q, "- append 'llo'", name = "q-abl")
        val hresta = repo.addWord("hresta")
        repo.addRule(rule)

        val candidates = repo.findParseCandidates(q.word("hrestallo"))
        Assert.assertEquals(1, candidates.size)
        Assert.assertEquals("hresta", candidates[0].text)
        Assert.assertEquals(hresta, candidates[0].word)
        Assert.assertEquals(rule, candidates[0].rules.single())
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
        Assert.assertEquals(1, candidates.size)
        Assert.assertEquals("oi", candidates[0].text)
    }

    @Test
    fun parseCandidatesExistingWordPOSMismatch() {
        val repo = repoWithQ()
        val presRule = parseRule(q, q, "- append 'a'", name = "q-pres", addedCategories = ".PRES", fromPOS = "V", toPOS = "V")
        repo.addRule(presRule)
        val hresta = repo.addWord("hrest", pos = "N")

        val candidates = repo.findParseCandidates(q.word("hresta"))
        Assert.assertEquals(1, candidates.size)
        Assert.assertNull(candidates[0].word)
    }

    @Test
    fun parseCandidatesExistingWordPOSMismatch2() {
        val repo = repoWithQ()
        val presRule = parseRule(q, q, "- append 'a'", name = "q-pres", addedCategories = ".PRES", fromPOS = "V")
        repo.addRule(presRule)
        val hresta = repo.addWord("hrest", pos = "N")

        val candidates = repo.findParseCandidates(q.word("hresta"))
        Assert.assertEquals(1, candidates.size)
        Assert.assertNull(candidates[0].word)
    }

    @Test
    fun parseCandidatesPOSMismatch() {
        val repo = repoWithQ()
        val presRule = parseRule(q, q, "- append 'a'", name = "q-pres", addedCategories = ".PRES", fromPOS = "V")
        repo.addRule(presRule)

        val candidates = repo.findParseCandidates(q.word("hresta", pos = "N"))
        Assert.assertEquals(0, candidates.size)
    }

    @Test
    fun parseCandidatesNotEmpty() {
        val repo = repoWithQ()
        val rule = parseRule(q, q, "- append 'llo'", name = "q-abl")
        repo.addRule(rule)
        val candidates = repo.findParseCandidates(q.word("llo"))
        Assert.assertEquals(0, candidates.size)
    }

    @Test
    fun normalizeCase() {
        val repo = repoWithQ()
        val rule = parseRule(q, q, "- append 'llo'", name = "q-abl")
        repo.addRule(rule)
        val candidates = repo.findParseCandidates(q.word("Hrestallo"))
        Assert.assertEquals(1, candidates.size)
        Assert.assertEquals("Hresta", candidates[0].text)
    }
}
