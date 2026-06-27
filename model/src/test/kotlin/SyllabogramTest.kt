package page.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Test

@Suppress("SpellCheckingInspection")
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
    fun squareBrackets() {
        val syllabogramSequence = TlhDigSyllabogramSyntax.parse("_QÍ-B[Í-M]A")
        assertEquals(Syllabogram("B[Í", SyllabogramType.LogogramAlt), syllabogramSequence.syllabograms[1])
    }

    @Test
    fun transcribe() {
        assertEquals("kittari", suggestTranscription("ki-it-ta-ri"))
    }

    @Test
    fun transcribeDifferentVowels() {
        assertEquals("kuitman", suggestTranscription("ku-it-ma-an"))
    }

    @Test
    fun transcribeLongVowel() {
        assertEquals("mān", suggestTranscription("ma-a-an"))
    }

    @Test
    fun transcribeDifferentVowelsLong() {
        assertEquals("uēš", suggestTranscription("ú-e-eš"))
    }

    @Test
    fun transcribeVowels2() {
        assertEquals("u̯aštāiš", suggestTranscription("u̯a-aš-ta-a-iš"))
    }

    @Test
    fun transcribeRemoveNumbers() {
        assertEquals("lenkanut", suggestTranscription("le-en-ka4-nu-ut"))
    }

    @Test
    fun transcribeW() {
        assertEquals("nuu̯anzaš", suggestTranscription("nu-u̯a-an-za-aš"))
    }

    @Test
    fun transcribeAkkadograms() {
        assertEquals("ii̯aḫrešša", suggestTranscription("^URU^_I-I̯A-AḪ-RE-EŠ-ŠA"))
    }
}
