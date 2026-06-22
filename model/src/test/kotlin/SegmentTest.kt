package page.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentTest : QBaseTest() {
    @Test
    fun segment() {
        val rule = parseRule(q, q, "- append 'llo'")
        val result = rule.apply(q.word("hresta"))
        assertEquals(1, result.segments!!.size)
        val segment = result.segments!![0]
        assertEquals(6, segment.firstCharacter)
        assertEquals(3, segment.length)
        assertEquals(rule, segment.sourceRule)
    }

    @Test
    fun segmentAppend() {
        val rule = parseRule(q, q, "- append 'llo'")
        val result = rule.apply(q.word("hresta"))
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
        val result = rule2.apply(rule1.apply(q.word("hresta")))
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
        val hresta = graph.addWord("hresta")
        val hrestallo = graph.addWord("hrestallo", gloss = null)
        val rule = parseRule(q, q, "- append 'llo'", addedCategories = ".ABL")
        graph.addLink(hrestallo, hresta, Link.Derived, listOf(rule))
        val restored = graph.restoreSegments(hrestallo)
        assertEquals(1, restored.segments!!.size)
        assertEquals("hresta-llo", restored.segmentedText())
        assertEquals("hresta-ABL", restored.getOrComputeGloss())
    }

    @Test
    fun restoreSegmentsNoChange() {
        val hresta2 = graph.addWord("hresta", gloss = null)
        val hresta = graph.addWord("hresta", gloss = "hresta")
        val rule = parseRule(q, q, "word ends with 'a':\n- no change", addedCategories = ".ABL")
        graph.addLink(hresta2, hresta, Link.Derived, listOf(rule))
        val restored = graph.restoreSegments(hresta2)
        assertEquals("hresta.ABL", restored.getOrComputeGloss())
    }

    @Test
    fun restoreSegmentsNoChangeNP() {
        val hresta2 = graph.addWord("hresta", gloss = "hresta.ABL")
        val hresta = graph.addWord("hresta", gloss = "hresta", pos = "NP")
        val rule = parseRule(q, q, "word ends with 'a':\n- no change", addedCategories = ".ABL")
        graph.addLink(hresta2, hresta, Link.Derived, listOf(rule))
        hresta2.gloss = null
        hresta.gloss = null
        val restored = graph.restoreSegments(hresta2)
        assertEquals("Hresta.ABL", restored.getOrComputeGloss())
    }

    @Test
    fun restoreSegmentsNoChangeNoRule() {
        val hresta2 = graph.addWord("hresta", gloss = "hresta.ABL")
        val hresta = graph.addWord("hresta", gloss = "hresta")
        val rule = parseRule(q, q, "word ends with 'i':\n- no change", addedCategories = ".ABL")
        graph.addLink(hresta2, hresta, Link.Derived)
        val restored = graph.restoreSegments(hresta2)
        assertEquals("hresta.ABL", restored.getOrComputeGloss())
    }

    @Test
    fun restoreSegmentsEmptyEnding() {
        val hresta = graph.addWord("hresta")
        val hrestallo = graph.addWord("hrestallo", gloss = null)
        val rule = parseRule(q, q, "word ends with 'llo':\n- change ending to ''", addedCategories = ".ABL")
        graph.addLink(hresta, hrestallo, Link.Derived, listOf(rule))
        val restored = graph.restoreSegments(hresta)
        assertEquals("hresta", restored.segmentedText())
    }

    @Test
    fun chainedSegments() {
        q.rule("""
            word ends with a vowel:
            - append 'r'
            otherwise:
            - append 'i'
        """.trimIndent(), name = "q-nom-pl")

        val qGenPl = q.rule("""
            - apply rule 'q-nom-pl'
            - append 'on'
            """.trimIndent())
        val result = qGenPl.apply(q.word("alda"))
        assertEquals(1, result.segments!!.size)
        val segment = result.segments!![0]
        assertEquals(4, segment.firstCharacter)
        assertEquals(3, segment.length)
    }

    @Test
    fun testChainTwoSegments() {
        q.rule("- append 'li'", name = "q-ppl")
        q.rule("- append 'nna'", name = "q-all")

        val qAllPpl = q.rule("""
            - apply rule 'q-ppl'
            - apply rule 'q-all'
            - append 'r'
        """.trimIndent())
        val result = qAllPpl.apply(q.word("falma"))
        assertEquals("falmalinnar", result.text)
        assertEquals(1, result.segments!!.size)
    }

    @Test
    fun deleteCharacterAdjustSegments() {
        q.rule("* e > 0", name = "oe-syncope")
        val oeAcc = q.rule("- append 'a'\n- apply rule 'oe-syncope'\n")
        val result = oeAcc.apply(q.word("swingel"))
        assertEquals("swingla", result.text)
        assertEquals(1, result.segments!!.size)
        assertEquals(6, result.segments!![0].firstCharacter)
        assertEquals(1, result.segments!![0].length)
    }

    @Test
    fun deleteCharacterAdjustSegmentsSpe() {
        q.rule("* V > 0", name = "oe-syncope")
        val oeAcc = q.rule("- append 'es'\n- apply sound rule 'oe-syncope' to second to last vowel\n")
        val result = oeAcc.apply(q.word("biscop"))
        assertEquals("biscpes", result.text)
        assertEquals(1, result.segments!!.size)
        assertEquals(5, result.segments!![0].firstCharacter)
        assertEquals(2, result.segments!![0].length)
    }

    @Test
    fun insertCharacterAdjustSegmentsSpe() {
        val breaking = q.rule("* i > io", name = "oe-breaking")
        val result = breaking.apply(q.word("lirnojan").also { it.segments = listOf(WordSegment(4, 4)) })
        assertEquals("liornojan", result.text)
        assertEquals(1, result.segments!!.size)
        assertEquals(5, result.segments!![0].firstCharacter)
        assertEquals(4, result.segments!![0].length)
    }

    @Test
    fun deleteCharacterAdjustSegmentsLongPhoneme() {
        q.rule("* V > 0", name = "oe-syncope")
        val oeAcc = q.rule("- append 'es'\n- apply sound rule 'oe-syncope' to second to last vowel\n")
        val result = oeAcc.apply(ce.word("bikhop"))
        assertEquals("bikhpes", result.text)
        assertEquals(1, result.segments!!.size)
        assertEquals(5, result.segments!![0].firstCharacter)
        assertEquals(2, result.segments!![0].length)
    }

    @Test
    fun deleteCharacterAdjustSegmentsWithApplySoundRule() {
        q.rule("* ć > c", name = "oe-depalatalize")
        q.rule("* e > 0\n= apply sound rule 'oe-depalatalize' to previous sound", name = "oe-syncope")
        val oeAcc = q.rule("- append 'a'\n- apply rule 'oe-syncope'\n")
        val result = oeAcc.apply(q.word("swingel"))
        assertEquals("swingla", result.text)
        assertEquals(1, result.segments!!.size)
        assertEquals(6, result.segments!![0].firstCharacter)
        assertEquals(1, result.segments!![0].length)
    }

    @Test
    fun normalizeSegmentsIntersecting() {
        q.rule("word ends with 'a':\n- change ending to 'r'", name = "on-3sg")
        val onMid = q.rule("- apply rule 'on-3sg'\nword ends with 'r':\n- change ending to 'sk'")
        val result = onMid.apply(q.word("dreifa"))
        assertEquals(1, result.segments!!.size)
    }

    @Test
    fun segmentsAfterDeleteVowel() {
        q.rule("* V > 0 if sound is morpheme-initial and previous sound is vowel",
            name = "on-article-vowel-deletion")
        val onDefDatDg = q.rule("- append 'inu'\n= apply rule 'on-article-vowel-deletion'")
        val result = onDefDatDg.apply(q.word("horni"))
        assertEquals(1, result.segments!!.size)
    }

    @Test
    fun segmentDisappears() {
        q.rule("* a > 0 / a_", name = "on-vowel-assimilation")
        val onGenPl = q.rule("- append 'a'\n= apply rule 'on-vowel-assimilation'")
        val result = onGenPl.apply(q.word("a"))
        assertEquals(0, result.segments!!.size)
    }
}
