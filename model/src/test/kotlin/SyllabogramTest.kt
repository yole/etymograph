package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Test

class SyllabogramTest {
    @Test
    fun parseSyllabogramsSimple() {
        val syllabogramSequence = TlhDigSyllabogramSyntax.parse("pé-ra-an")
        assertEquals(3, syllabogramSequence.syllabograms.size)
        assertEquals("pé", syllabogramSequence.syllabograms[0].text)
        assertEquals(SyllabogramType.Syllabogram, syllabogramSequence.syllabograms[0].type)
        assertEquals("pé-ra-an", TlhDigSyllabogramSyntax.render(syllabogramSequence))
    }

    @Test
    fun parseSyllabogramsLogogram() {
        val syllabogramSequence = TlhDigSyllabogramSyntax.parse("LUGAL.GAL")
        assertEquals(2, syllabogramSequence.syllabograms.size)
        assertEquals("LUGAL", syllabogramSequence.syllabograms[0].text)
        assertEquals(SyllabogramType.Logogram, syllabogramSequence.syllabograms[0].type)
        assertEquals("LUGAL.GAL", TlhDigSyllabogramSyntax.render(syllabogramSequence))
    }

    @Test
    fun parseSyllabogramsAlt() {
        val syllabogramSequence = TlhDigSyllabogramSyntax.parse("_A-NA")
        assertEquals(2, syllabogramSequence.syllabograms.size)
        assertEquals("A", syllabogramSequence.syllabograms[0].text)
        assertEquals(SyllabogramType.LogogramAlt, syllabogramSequence.syllabograms[0].type)
        assertEquals("_A-NA", TlhDigSyllabogramSyntax.render(syllabogramSequence))
    }

    @Test
    fun parseSyllabogramsMixed() {
        val text = "É-ŠU"
        val syllabogramSequence = TlhDigSyllabogramSyntax.parse(text)
        assertEquals(2, syllabogramSequence.syllabograms.size)
        assertEquals("É", syllabogramSequence.syllabograms[0].text)
        assertEquals("ŠU", syllabogramSequence.syllabograms[1].text)
        assertEquals(SyllabogramType.Logogram, syllabogramSequence.syllabograms[0].type)
        assertEquals(SyllabogramType.LogogramAlt, syllabogramSequence.syllabograms[1].type)
        assertEquals(text, TlhDigSyllabogramSyntax.render(syllabogramSequence))
    }

    @Test
    fun parseSyllabogramsMixedDiacritics() {
        val text = "DUMU-I̯A"
        val syllabogramSequence = TlhDigSyllabogramSyntax.parse(text)
        assertEquals(2, syllabogramSequence.syllabograms.size)
        assertEquals("I̯A", syllabogramSequence.syllabograms[1].text)
        assertEquals(SyllabogramType.LogogramAlt, syllabogramSequence.syllabograms[1].type)
        assertEquals(text, TlhDigSyllabogramSyntax.render(syllabogramSequence))
    }

    @Test
    fun parseDeterminative() {
        val text = "^d^_SÎN-aš"
        val syllabogramSequence = TlhDigSyllabogramSyntax.parse(text)
        assertEquals(3, syllabogramSequence.syllabograms.size)
        assertEquals("d", syllabogramSequence.syllabograms[0].text)
        assertEquals(SyllabogramType.Determinative, syllabogramSequence.syllabograms[0].type)
        assertEquals("SÎN", syllabogramSequence.syllabograms[1].text)
        assertEquals(SyllabogramType.LogogramAlt, syllabogramSequence.syllabograms[1].type)
        assertEquals(text, TlhDigSyllabogramSyntax.render(syllabogramSequence))
    }

    @Test
    fun parseDeterminativeWordFinal() {
        val text = "DINGIR^MEŠ^"
        val syllabogramSequence = TlhDigSyllabogramSyntax.parse(text)
        assertEquals(2, syllabogramSequence.syllabograms.size)
        assertEquals("DINGIR", syllabogramSequence.syllabograms[0].text)
        assertEquals(SyllabogramType.Logogram, syllabogramSequence.syllabograms[0].type)
        assertEquals("MEŠ", syllabogramSequence.syllabograms[1].text)
        assertEquals(SyllabogramType.Determinative, syllabogramSequence.syllabograms[1].type)
        assertEquals(text, TlhDigSyllabogramSyntax.render(syllabogramSequence))
    }

    @Test
    fun parseDeterminativeAlt() {
        val text = "DINGIR^_LIM^"
        val syllabogramSequence = TlhDigSyllabogramSyntax.parse(text)
        assertEquals(2, syllabogramSequence.syllabograms.size)
        assertEquals("DINGIR", syllabogramSequence.syllabograms[0].text)
        assertEquals(SyllabogramType.Logogram, syllabogramSequence.syllabograms[0].type)
        assertEquals("LIM", syllabogramSequence.syllabograms[1].text)
        assertEquals(SyllabogramType.DeterminativeAlt, syllabogramSequence.syllabograms[1].type)
        assertEquals(text, TlhDigSyllabogramSyntax.render(syllabogramSequence))
    }

    @Test
    fun syllabogramAfterSumerogram() {
        val text = "EGIR-an-ma"
        val syllabogramSequence = TlhDigSyllabogramSyntax.parse(text)
        assertEquals(Syllabogram("an", SyllabogramType.Syllabogram), syllabogramSequence.syllabograms[1])
    }

    @Test
    fun transcribe() {
        val word = hittiteWord("ki-it-ta-ri")
        assertEquals("kittari", suggestTranscription(word))
    }

    @Test
    fun transcribeDifferentVowels() {
        val word = hittiteWord("ku-it-ma-an")
        assertEquals("kuitman", suggestTranscription(word))
    }

    @Test
    fun transcribeLongVowel() {
        val word = hittiteWord("ma-a-an")
        assertEquals("mān", suggestTranscription(word))
    }

    @Test
    fun transcribeDifferentVowelsLong() {
        val word = hittiteWord("ú-e-eš")
        assertEquals("uēš", suggestTranscription(word))
    }

    @Test
    fun transcribeVowels2() {
        val word = hittiteWord("u̯a-aš-ta-a-iš")
        assertEquals("u̯aštāiš", suggestTranscription(word))
    }

    private fun hittiteWord(transliteration: String): Word {
        val ht = Language("Hittite", "Ht")
        ht.syllabographic = true
        val repo = InMemoryGraphRepository()
        repo.addLanguage(ht)
        return ht.word(transliteration, syllabographic = true)
    }
}
