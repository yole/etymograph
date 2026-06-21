package page.yole.etymograph

import org.junit.Assert.*
import org.junit.Test

class GraphTest : QBaseTest() {
    @Test
    fun links() {
        val abc = graph.addWord("abc")
        val def = graph.addWord("def")
        graph.addLink(abc, def, Link.Derived)
        assertEquals(1, graph.getLinksTo(def).count())
        assertTrue(graph.deleteLink(abc, def, Link.Derived))
        assertEquals(0, graph.getLinksTo(def).count())
    }

    @Test
    fun deleteWord() {
        val abc = graph.addWord("abc")
        graph.addWord("def")
        graph.deleteWord(abc)
        assertEquals(1, graph.dictionaryWords(q).size)
        assertTrue(graph.wordById(abc.id) == null)
        assertEquals(0, graph.wordsByText(q, "abc").size)
    }

    @Test
    fun classifyWordI() {
        val ek = graph.addWord("ek", "I")
        assertEquals(1, graph.dictionaryWords(q).size)
    }

    @Test
    fun classifyWordWithGloss() {
        val ek = graph.addWord("ek", "me.1SG")
        assertEquals(1, graph.dictionaryWords(q).size)
    }

    @Test
    fun classifyWordDerived() {
        val ek = graph.addWord("ek", "me.1SG")
        val derived = graph.addWord("och")
        graph.addLink(derived, ek, Link.Derived)
        assertEquals(1, graph.dictionaryWords(q).size)
    }

    @Test
    fun wordByTextSyllabographic() {
        val ht = graph.addLanguage("Hittite", "Ht")
        ht.syllabographic = true
        graph.addWord("_A-NA", language = ht, gloss = "on", syllabographic = true)
        assertEquals(1, graph.wordsByText(ht, "_A-NA", true).size)
    }

    @Test
    fun wordByTextCaseInsensitive() {
        val pie = graph.addLanguage("Proto-Indo-European", "PIE")
        pie.phonemes += phoneme("H", "")
        graph.addWord("bheroH", language = pie, gloss = "carry.1SG")
        assertEquals(1, graph.wordsByText(pie, "bheroH").size)
        assertEquals(0, graph.wordsByText(pie, "bheroh").size)
    }
}
