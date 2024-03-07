package ru.yole.etymograph

import org.junit.Test
import org.junit.Assert.*

class PhonemeIteratorTest : QBaseTest() {
    @Test
    fun phonemeIterator() {
        val it = PhonemeIterator(ce.word("khith"))
        assertEquals("kh", it.current)
        assertTrue(it.advance())
        assertEquals("i", it.current)
        assertTrue(it.advance())
        assertEquals("th", it.current)
        assertFalse(it.advance())
    }

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

    @Test
    fun seekToLastVowel() {
        val it = PhonemeIterator("lasse", q)
        assertTrue(it.seek(SeekTarget(-1, v)))
        assertEquals("e", it.current)
    }

    @Test
    fun digraphs() {
        q.phonemes = mutableListOf(Phoneme(listOf("hy"), setOf("voiceless", "glide")))
        val it = PhonemeIterator("hyarmen", q)
        assertEquals("hy", it.current)
    }
}
