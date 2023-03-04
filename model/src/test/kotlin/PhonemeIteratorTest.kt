package ru.yole.etymograph

import org.junit.Test
import org.junit.Assert.*

class PhonemeIteratorTest : QBaseTest() {
    @Test
    fun seekToVowel() {
        val it = PhonemeIterator("lasse", q)
        it.seek(SeekTarget(1, v))
        assertEquals("a", it.current)
        it.seek(SeekTarget(2, v))
        assertEquals("e", it.current)
    }

    @Test
    fun parseSeekTarget() {
        val t = SeekTarget.parse("first vowel", q)
        assertEquals(1, t.index)
        assertEquals("vowel", t.phonemeClass.name)
    }

    @Test
    fun seekTargetToEditableText() {
        assertEquals("first vowel", SeekTarget(1, v).toEditableText())
    }
}