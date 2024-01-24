package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentTest : QBaseTest() {
    @Test
    fun segment() {
        val rule = parseRule(q, q, "- append 'llo'")
        val result = rule.apply(q.word("hresta"), emptyRepo)
        assertEquals(1, result.segments!!.size)
        val segment = result.segments!![0]
        assertEquals(6, segment.firstCharacter)
        assertEquals(3, segment.length)
        assertEquals(rule, segment.sourceRule)
    }

    @Test
    fun segmentAppend() {
        val rule = parseRule(q, q, "- append 'llo'")
        val result = rule.apply(q.word("hresta"), emptyRepo)
        assertEquals(1, result.segments!!.size)
        val segment = result.segments!![0]
        assertEquals(6, segment.firstCharacter)
        assertEquals(3, segment.length)
        assertEquals(rule, segment.sourceRule)
    }

    @Test
    fun multipleSegments() {
        val rule1 = parseRule(q, q, "- append 'llo'")
        val rule2 = parseRule(q, q, "- append 's'")
        val result = rule2.apply(rule1.apply(q.word("hresta"), emptyRepo), emptyRepo)
        assertEquals(2, result.segments!!.size)
        /*
        val segment = result.segments!![0]
        assertEquals(6, segment.firstCharacter)
        assertEquals(3, segment.length)
        assertEquals(rule, segment.sourceRule)
         */
    }

    @Test
    fun restoreSegments() {
        val repo = InMemoryGraphRepository()
        val hresta = repo.addWord("hresta")
        val hrestallo = repo.addWord("hrestallo", gloss = null)
        val rule = parseRule(q, q, "- append 'llo'", addedCategories = ".ABL")
        val link = repo.addLink(hrestallo, hresta, Link.Derived, listOf(rule), emptyList(), null)
        val restored = repo.restoreSegments(hrestallo)
        assertEquals(1, restored.segments!!.size)
        assertEquals("hresta-llo", restored.segmentedText())
        assertEquals("hresta-ABL", restored.getOrComputeGloss(repo))
    }

    @Test
    fun restoreSegmentsNoChange() {
        val repo = InMemoryGraphRepository()
        val hresta2 = repo.addWord("hresta", gloss = null)
        val hresta = repo.addWord("hresta", gloss = "hresta")
        val rule = parseRule(q, q, "word ends with 'a':\n- no change", addedCategories = ".ABL")
        val link = repo.addLink(hresta2, hresta, Link.Derived, listOf(rule), emptyList(), null)
        val restored = repo.restoreSegments(hresta2)
        assertEquals("hresta.ABL", restored.getOrComputeGloss(repo))
    }

    @Test
    fun restoreSegmentsNoChangeNP() {
        val repo = InMemoryGraphRepository()
        val hresta2 = repo.addWord("hresta", gloss = "hresta.ABL")
        val hresta = repo.addWord("hresta", gloss = "hresta", pos = "NP")
        val rule = parseRule(q, q, "word ends with 'a':\n- no change", addedCategories = ".ABL")
        val link = repo.addLink(hresta2, hresta, Link.Derived, listOf(rule), emptyList(), null)
        hresta2.gloss = null
        hresta.gloss = null
        val restored = repo.restoreSegments(hresta2)
        assertEquals("Hresta.ABL", restored.getOrComputeGloss(repo))
    }

    @Test
    fun restoreSegmentsNoChangeNoRule() {
        val repo = InMemoryGraphRepository()
        val hresta2 = repo.addWord("hresta", gloss = "hresta.ABL")
        val hresta = repo.addWord("hresta", gloss = "hresta")
        val rule = parseRule(q, q, "word ends with 'i':\n- no change", addedCategories = ".ABL")
        val link = repo.addLink(hresta2, hresta, Link.Derived, emptyList(), emptyList(), null)
        val restored = repo.restoreSegments(hresta2)
        assertEquals("hresta.ABL", restored.getOrComputeGloss(repo))
    }

    @Test
    fun restoreSegmentsEmptyEnding() {
        val repo = InMemoryGraphRepository()
        val hresta = repo.addWord("hresta")
        val hrestallo = repo.addWord("hrestallo", gloss = null)
        val rule = parseRule(q, q, "word ends with 'llo':\n- change ending to ''", addedCategories = ".ABL")
        val link = repo.addLink(hresta, hrestallo, Link.Derived, listOf(rule), emptyList(), null)
        val restored = repo.restoreSegments(hresta)
        assertEquals("hresta", restored.segmentedText())
    }

    @Test
    fun chainedSegments() {
        val qNomPl = parseRule(q, q, """
            word ends with a vowel:
            - append 'r'
            otherwise:
            - append 'i'
        """.trimIndent(), "q-nom-pl")
        val repo = InMemoryGraphRepository()
        repo.addRule(qNomPl)

        val qGenPl = parseRule(q, q, """
            - apply rule 'q-nom-pl'
            - append 'on'
            """.trimIndent(), repo = repo)
        val result = qGenPl.apply(q.word("alda"), repo)
        assertEquals(1, result.segments!!.size)
        val segment = result.segments!![0]
        assertEquals(4, segment.firstCharacter)
        assertEquals(3, segment.length)
    }

    @Test
    fun testChainTwoSegments() {
        val repo = InMemoryGraphRepository()
        val qPpl = parseRule(q, q, "- append 'li'", "q-ppl")
        repo.addRule(qPpl)
        val qAll = parseRule(q, q, "- append 'nna'", "q-all")
        repo.addRule(qAll)

        val qAllPpl = parseRule(q, q, """
            - apply rule 'q-ppl'
            - apply rule 'q-all'
            - append 'r'
        """.trimIndent(), repo = repo)
        val result = qAllPpl.apply(q.word("falma"), repo)
        assertEquals(1, result.segments!!.size)
    }
}