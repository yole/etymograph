package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SegmentTest : QBaseTest() {
    lateinit var repo: InMemoryGraphRepository

    @Before
    fun setup() {
        repo = InMemoryGraphRepository()
    }

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
        val hresta2 = repo.addWord("hresta", gloss = null)
        val hresta = repo.addWord("hresta", gloss = "hresta")
        val rule = parseRule(q, q, "word ends with 'a':\n- no change", addedCategories = ".ABL")
        val link = repo.addLink(hresta2, hresta, Link.Derived, listOf(rule), emptyList(), null)
        val restored = repo.restoreSegments(hresta2)
        assertEquals("hresta.ABL", restored.getOrComputeGloss(repo))
    }

    @Test
    fun restoreSegmentsNoChangeNP() {
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
        val hresta2 = repo.addWord("hresta", gloss = "hresta.ABL")
        val hresta = repo.addWord("hresta", gloss = "hresta")
        val rule = parseRule(q, q, "word ends with 'i':\n- no change", addedCategories = ".ABL")
        val link = repo.addLink(hresta2, hresta, Link.Derived, emptyList(), emptyList(), null)
        val restored = repo.restoreSegments(hresta2)
        assertEquals("hresta.ABL", restored.getOrComputeGloss(repo))
    }

    @Test
    fun restoreSegmentsEmptyEnding() {
        val hresta = repo.addWord("hresta")
        val hrestallo = repo.addWord("hrestallo", gloss = null)
        val rule = parseRule(q, q, "word ends with 'llo':\n- change ending to ''", addedCategories = ".ABL")
        val link = repo.addLink(hresta, hrestallo, Link.Derived, listOf(rule), emptyList(), null)
        val restored = repo.restoreSegments(hresta)
        assertEquals("hresta", restored.segmentedText())
    }

    @Test
    fun chainedSegments() {
        repo.rule("""
            word ends with a vowel:
            - append 'r'
            otherwise:
            - append 'i'
        """.trimIndent(), name = "q-nom-pl")

        val qGenPl = repo.rule("""
            - apply rule 'q-nom-pl'
            - append 'on'
            """.trimIndent())
        val result = qGenPl.apply(q.word("alda"), repo)
        assertEquals(1, result.segments!!.size)
        val segment = result.segments!![0]
        assertEquals(4, segment.firstCharacter)
        assertEquals(3, segment.length)
    }

    @Test
    fun testChainTwoSegments() {
        repo.rule("- append 'li'", name = "q-ppl")
        repo.rule("- append 'nna'", name = "q-all")

        val qAllPpl = repo.rule("""
            - apply rule 'q-ppl'
            - apply rule 'q-all'
            - append 'r'
        """.trimIndent())
        val result = qAllPpl.apply(q.word("falma"), repo)
        assertEquals("falmalinnar", result.text)
        assertEquals(1, result.segments!!.size)
    }

    @Test
    fun deleteCharacterAdjustSegments() {
        repo.rule("sound is 'e':\n- sound disappears", name = "oe-syncope")
        val oeAcc = repo.rule("- append 'a'\n- apply rule 'oe-syncope'\n")
        val result = oeAcc.apply(q.word("swingel"), repo)
        assertEquals("swingla", result.text)
        assertEquals(1, result.segments!!.size)
        assertEquals(6, result.segments!![0].firstCharacter)
        assertEquals(1, result.segments!![0].length)
    }

    @Test
    fun deleteCharacterAdjustSegmentsWithApplySoundRule() {
        repo.rule("sound is 'ć':\n- new sound is 'c'", name = "oe-depalatalize")
        repo.rule("sound is 'e':\n- sound disappears\n- apply sound rule 'oe-depalatalize' to previous sound", name = "oe-syncope")
        val oeAcc = repo.rule("- append 'a'\n- apply rule 'oe-syncope'\n")
        val result = oeAcc.apply(q.word("swingel"), repo)
        assertEquals("swingla", result.text)
        assertEquals(1, result.segments!!.size)
        assertEquals(6, result.segments!![0].firstCharacter)
        assertEquals(1, result.segments!![0].length)
    }

    @Test
    fun normalizeSegmentsIntersecting() {
        repo.rule("word ends with 'a':\n- change ending to 'r'", name = "on-3sg")
        val onMid = repo.rule("- apply rule 'on-3sg'\nword ends with 'r':\n- change ending to 'sk'")
        val result = onMid.apply(q.word("dreifa"), repo)
        assertEquals(1, result.segments!!.size)
    }

    @Test
    fun segmentsAfterDeleteVowel() {
        repo.rule("sound is morpheme-initial vowel and previous sound is vowel:\n-sound disappears",
            name = "on-article-vowel-deletion")
        val onDefDatDg = repo.rule("- append 'inu'\n= apply rule 'on-article-vowel-deletion'")
        val result = onDefDatDg.apply(q.word("horni"), repo)
        assertEquals(1, result.segments!!.size)
    }
}
