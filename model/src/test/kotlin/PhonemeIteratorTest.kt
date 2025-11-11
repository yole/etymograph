package ru.yole.etymograph

import org.junit.Test
import org.junit.Assert.*
import java.text.Normalizer

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
        val it = PhonemeIterator("lasse", q, repo = null)
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
        val it = PhonemeIterator("lasse", q, repo = null)
        assertTrue(it.seek(SeekTarget(-1, v)))
        assertEquals("e", it.current)
    }

    @Test
    fun digraphs() {
        q.phonemes = listOf(phoneme("hy", "voiceless glide"))
        val it = PhonemeIterator("hyarmen", q, repo = null)
        assertEquals("hy", it.current)
    }

    @Test
    fun phonemic() {
        q.phonemes = listOf(phoneme(listOf("c", "k"), "k", "consonant"))
        val w = q.word("calma").asPhonemic()
        val it = PhonemeIterator(w, null)
        assertEquals("kalma", it.result())
    }

    @Test
    fun remapIndex() {
        val it = PhonemeIterator(q.word("calma"), null)
        it.deleteAtRelative(4)
        val index = it.mapIndex(5)
        assertEquals(4, index)
    }

    @Test
    fun accent() {
        ce.accentTypes = setOf(AccentType.Acute)
        val composed = ce.word(Normalizer.normalize("áham", Normalizer.Form.NFKC))
        val it = PhonemeIterator(composed, null)
        assertEquals(AccentType.Acute, it.currentAccentType)
        assertEquals("a", it.current)
    }

    @Test
    fun accentDecomposed() {
        ce.accentTypes = setOf(AccentType.Acute)
        val decomposed = ce.word(Normalizer.normalize("áham", Normalizer.Form.NFKD))
        val dit = PhonemeIterator(decomposed, null)
        assertEquals(AccentType.Acute, dit.currentAccentType)
        assertEquals("a", dit.current)
    }

    @Test
    fun multilanguage() {
        q.phonemes += phoneme("kʰ", "")
        val mixedIt = PhonemeIterator("kʰith", q, ce, null)
        assertEquals("kʰ", mixedIt[0])
        assertEquals("th", mixedIt[2])
    }
}
