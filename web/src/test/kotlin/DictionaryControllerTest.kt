package page.yole.etymograph.web

import org.junit.Assert.assertEquals
import org.junit.Test
import page.yole.etymograph.KnownPartsOfSpeech
import page.yole.etymograph.web.controllers.DictionaryController
import page.yole.etymograph.word

class DictionaryControllerTest {
    @Test
    fun affixesAreGroupedWithoutLeadingHyphen() {
        val fixture = QTestFixture()
        fixture.q.word("-a", "suffix", pos = KnownPartsOfSpeech.affix.abbreviation)
        fixture.q.word("-nyë", "first person", pos = KnownPartsOfSpeech.affix.abbreviation)

        val dictionary = DictionaryController().dictionary(fixture.graph, "q", "affixes", null)

        assertEquals(listOf("a", "n"), dictionary.wordsByLetter!!.keys.toList())
        assertEquals("-a", dictionary.wordsByLetter["a"]!!.single().ref.text)
    }
}
