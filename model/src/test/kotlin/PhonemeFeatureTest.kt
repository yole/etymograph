package page.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Test

class PhonemeFeatureTest {
    private val graph = InMemoryGraph()
    private val language = graph.addLanguage("Test", "T")
    private val phoneme = Phoneme(-1, listOf("p"), null, setOf("voiceless", "bilabial", "stop"))

    @Test
    fun phonemeFeaturesIncludesExplicitAndImplicitFeatures() {
        language.phonemes = listOf(phoneme)

        assertEquals(
            setOf("-voice", "labial", "-sonorant", "-continuant"),
            language.phonemeFeatures(phoneme)
        )
    }

    @Test
    fun updatePhonemesClearsPhonemeFeaturesCache() {
        language.phonemes = listOf(phoneme)
        assertEquals(
            setOf("-voice", "labial", "-sonorant", "-continuant"),
            language.phonemeFeatures(phoneme)
        )

        phoneme.classes = setOf("voiced", "velar", "fricative")
        language.updatePhonemes()

        assertEquals(
            setOf("+voice", "dorsal", "-sonorant", "+continuant", "-strident"),
            language.phonemeFeatures(phoneme)
        )
    }
}
