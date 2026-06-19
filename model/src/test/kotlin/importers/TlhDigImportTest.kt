package ru.yole.etymograph.importers

import org.jdom2.input.SAXBuilder
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.yole.etymograph.InMemoryGraphRepository
import ru.yole.etymograph.Language
import ru.yole.etymograph.Word
import java.io.StringReader

class TlhDigImportTest {
    lateinit var repo: InMemoryGraphRepository
    lateinit var hittite: Language

    @Before
    fun setup() {
        repo = InMemoryGraphRepository()
        hittite = Language(repo, "Hittite", "Hitt")
        repo.addLanguage(hittite)
    }

    @Test
    fun simple() {
        importWord("""<w trans="arḫa" mrp0sel=" 2" mrp2="arḫa@weg@@ ADV@">ar-ḫa</w>""")
        val arha = findWord("arḫa")
        assertEquals("weg", arha.gloss)
    }

    @Test
    fun cleanStem() {
        importWord("""<w trans="auaua" mrp0sel=" 1a" mrp1="auau=a-@Awauwa@{a → PNm.NOM.SG(UNM)}@38.2@m"><d>m</d>a-wa-u-wa-a</w>""")
        val auaua = findWord("auaua")
        assertEquals("Awauwa", auaua.gloss)
    }

    private fun findWord(text: String): Word = repo.wordsByText(hittite, text, false).single()

    private fun importWord(@org.intellij.lang.annotations.Language("XML") element: String) {
        val doc = SAXBuilder().build(StringReader("<text>$element</text>"))
        importTLHDig(repo, "doc", doc.rootElement.children)
    }
}
