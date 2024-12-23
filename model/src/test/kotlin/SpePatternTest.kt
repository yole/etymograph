package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Test

class SpePatternTest : QBaseTest() {
    @Test
    fun testParseSimple() {
        val pattern = SpePattern.parse(q, "a -> o")
        val beforeNode = pattern.before.single()
        assertEquals("a", beforeNode.text)
        val afterNode = pattern.after.single()
        assertEquals("o", afterNode.text)
    }

    @Test
    fun testParseContext() {
        val pattern = SpePattern.parse(q, "a -> o / b_c")
        val precede = pattern.preceding.single()
        assertEquals("b", precede.text)
        val follow = pattern.following.single()
        assertEquals("c", follow.text)
    }

    @Test
    fun testParseClass() {
        val pattern = SpePattern.parse(q, "a -> o / [+voice]_")
        val precede = pattern.preceding.single()
        assertEquals("+voice", precede.phonemeClass!!.name)
    }

    @Test
    fun replaceWithoutContext() {
        val pattern = SpePattern.parse(q, "a -> o")
        assertEquals("bo", pattern.apply(q, "ba"))
    }

    @Test
    fun replaceMatchFollowing() {
        val pattern = SpePattern.parse(q, "a -> o / _d")
        assertEquals("bodak", pattern.apply(q, "badak"))
    }

    @Test
    fun replaceMatchPreceding() {
        val pattern = SpePattern.parse(q, "a -> o / b_")
        assertEquals("bodak", pattern.apply(q, "badak"))
    }

    @Test
    fun replaceMatchWordBoundaryForward() {
        val pattern = SpePattern.parse(q, "a -> o / _#")
        assertEquals("bado", pattern.apply(q, "bada"))
    }

    @Test
    fun replaceMatchWordBoundaryBackward() {
        val pattern = SpePattern.parse(q, "a -> o / #_")
        assertEquals("obad", pattern.apply(q, "abad"))
    }

    @Test
    fun replaceMatchPhonemeClassForward() {
        val pattern = SpePattern.parse(q, "a -> o / _[+voice]")
        assertEquals("obat", pattern.apply(q, "abat"))
    }

    @Test
    fun toStringSimple() {
        val pattern = SpePattern.parse(q, "a -> o")
        assertEquals("a -> o", pattern.toString())
    }

    @Test
    fun toStringContext() {
        val pattern = SpePattern.parse(q, "a -> o / b_c")
        assertEquals("a -> o / b_c", pattern.toString())
    }

    @Test
    fun toStringWordBoundary() {
        val pattern = SpePattern.parse(q, "a -> o / _#")
        assertEquals("a -> o / _#", pattern.toString())
    }

    @Test
    fun toStringPhonemeClass() {
        val pattern = SpePattern.parse(q, "a -> o / _[+voice]")
        assertEquals("a -> o / _[+voice]", pattern.toString())
    }
}
