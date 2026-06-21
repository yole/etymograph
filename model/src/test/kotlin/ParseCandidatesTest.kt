package page.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParseCandidatesTest : QBaseTest() {

    @Test
    fun parseCandidates() {
        val rule = graph.rule("- append 'llo'", name = "q-abl", addedCategories = ".ABL")
        val candidates = graph.findParseCandidates(q.word("hrestallo"))
        assertEquals(1, candidates.size)
        assertEquals("hresta", candidates[0].text)
        assertNull(candidates[0].word)
        assertEquals(rule, candidates[0].rules.single())
    }

    @Test
    fun parseCandidatesWithWord() {
        val rule = graph.rule("- append 'llo'", name = "q-abl", addedCategories = ".ABL")
        val hresta = graph.addWord("hresta")

        val candidates = graph.findParseCandidates(q.word("hrestallo"))
        assertEquals(1, candidates.size)
        assertEquals("hresta", candidates[0].text)
        assertEquals(hresta, candidates[0].word)
        assertEquals(rule, candidates[0].rules.single())
    }

    @Test
    fun parseCandidatesSameCategory() {
        q.withGrammaticalCategory("Tense", "V", "Present" to "PRES", "Aorist" to  "AOR")
        graph.rule("- append 'a'", name = "q-pres", addedCategories = ".PRES")
        graph.rule("- append 'i'", name = "q-aor", addedCategories = ".AOR")

        val candidates = graph.findParseCandidates(q.word("oia"))
        assertEquals(1, candidates.size)
        assertEquals("oi", candidates[0].text)
    }

    @Test
    fun sameCategoryExistingWord() {
        q.withGrammaticalCategory("Tense", "V", "Present" to "PRES", "Aorist" to  "AOR")
        graph.rule("- append 'a'", name = "q-pres", addedCategories = ".PRES")
        graph.rule("- append 'i'", name = "q-aor", addedCategories = ".AOR")

        graph.addWord("oi", "be.PRES")

        val candidates = graph.findParseCandidates(q.word("oia"))
        assertEquals(1, candidates.size)
        assertEquals("oi", candidates[0].text)
        assertNull(candidates[0].word)
    }

    @Test
    fun parseCandidatesExistingWordPOSMismatch() {
        val presRule = parseRule(q, q, "- append 'a'", name = "q-pres", addedCategories = ".PRES", fromPOS = listOf("V"), toPOS = "V")
        graph.addRule(presRule)
        val hresta = graph.addWord("hrest", pos = "N")

        val candidates = graph.findParseCandidates(q.word("hresta"))
        assertEquals(1, candidates.size)
        assertNull(candidates[0].word)
    }

    @Test
    fun parseCandidatesExistingWordPOSMismatch2() {
        val presRule = parseRule(q, q, "- append 'a'", name = "q-pres", addedCategories = ".PRES", fromPOS = listOf("V"))
        graph.addRule(presRule)
        val hresta = graph.addWord("hrest", pos = "N")

        val candidates = graph.findParseCandidates(q.word("hresta"))
        assertEquals(1, candidates.size)
        assertNull(candidates[0].word)
    }

    @Test
    fun parseCandidatesPOSMismatch() {
        val presRule = parseRule(q, q, "- append 'a'", name = "q-pres", addedCategories = ".PRES", fromPOS = listOf("V"))
        graph.addRule(presRule)

        val candidates = graph.findParseCandidates(q.word("hresta", pos = "N"))
        assertEquals(0, candidates.size)
    }

    @Test
    fun parseCandidatesNotEmpty() {
        val rule = parseRule(q, q, "- append 'llo'", name = "q-abl")
        graph.addRule(rule)
        val candidates = graph.findParseCandidates(q.word("llo"))
        assertEquals(0, candidates.size)
    }

    @Test
    fun normalizeCase() {
        graph.rule("- append 'llo'", name = "q-abl", addedCategories = ".ABL")
        val candidates = graph.findParseCandidates(q.word("Hrestallo"))
        assertEquals(1, candidates.size)
        assertEquals("Hresta", candidates[0].text)
    }
}
