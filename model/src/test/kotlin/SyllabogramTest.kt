package ru.yole.etymograph

import org.junit.Assert
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
}
