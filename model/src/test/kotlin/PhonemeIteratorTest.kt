package ru.yole.etymograph

import org.junit.Test
import org.junit.Assert.*

class PhonemeIteratorTest : QBaseTest() {
    @Test
    fun phonemeIterator() {
        val it = PhonemeIterator(ce.word("khith"), null)
        assertEquals("kh", it.current)
        assertTrue(it.advance())
        assertEquals("i", it.current)
        assertTrue(it.advance())
        assertEquals("th", it.current)
        assertFalse(it.advance())
    }

    @Test
    fun seekToVowel() {
        val it = PhonemeIterator("lasse", q, null)
        it.seek(SeekTarget(1, v))
        assertEquals("a", it.current)
        it.seek(SeekTarget(2, v))
        assertEquals("e", it.current)
    }

    @Test
    fun parseSeekTarget() {
        val t = SeekTarget.parse("first vowel", q)
        assertEquals(1, t.index)
        assertEquals("vowel", t.phonemeClass!!.name)
        assertFalse(t.relative)
    }

    @Test
    fun parseSeekTargetRelative() {
        val t = SeekTarget.parse("next vowel", q)
        assertEquals(1, t.index)
        assertEquals("vowel", t.phonemeClass!!.name)
        assertTrue(t.relative)
    }

    @Test
    fun seekTargetToEditableText() {
        assertEquals("first vowel", SeekTarget(1, v).toEditableText())
    }

    @Test
    fun seekToLastVowel() {
        val it = PhonemeIterator("lasse", q, null)
        assertTrue(it.seek(SeekTarget(-1, v)))
        assertEquals("e", it.current)
    }

    @Test
    fun digraphs() {
        q.phonemes = listOf(phoneme("hy", "voiceless glide"))
        val it = PhonemeIterator("hyarmen", q, null)
        assertEquals("hy", it.current)
    }

    @Test
    fun phonemic() {
        q.phonemes = listOf(phoneme(listOf("c", "k"), "k", "consonant"))
        val w = q.word("calma").asPhonemic()
        val it = PhonemeIterator(w, null)
        assertEquals("kalma", it.result())
    }
}
