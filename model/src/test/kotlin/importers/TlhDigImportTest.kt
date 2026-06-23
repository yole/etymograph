package page.yole.etymograph.importers

import org.jdom2.input.SAXBuilder
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import page.yole.etymograph.*
import page.yole.etymograph.Link
import java.io.StringReader

class TlhDigImportTest {
    lateinit var graph: InMemoryGraph
    lateinit var hittite: Language

    @Before
    fun setup() {
        graph = InMemoryGraph()
        hittite = graph.addLanguage("Hittite", "Hitt")
        hittite
            .withGrammaticalCategory("Person", "V", "First" to "1" , "Second" to "2", "Third" to "3")
            .withGrammaticalCategory("Number", "V", "Singular" to "SG", "Plural" to "PL")
            .withGrammaticalCategory("Mood", "V", "Indicative" to "IND", "Imperative" to "IMP")
            .withGrammaticalCategory("Case", "N", "Nominative" to "NOM", "Ablative" to "ABL", "Dative/Locative" to "D/L")
            .withGrammaticalCategory("Non-finite form", "V", "Participle" to "PTCP")
            .withGrammaticalCategory("Gender", "ADJ", "Common" to "C")
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

    @Test
    fun akkadian() {
        importWord("""<w trans="BE-EL-TI₄-NI" mrp0sel=" 1d" mrp1="BĒLTU@Herrin@{d → D/L.SG(UNM)_PPRO.1PL.GEN}@@ (MUNUS)"><aGr>BE-EL-TI₄-NI</aGr></w>""")
        val corpusText = graph.allCorpusTexts().first()
        assertEquals("_BE-EL-TI4-NI", corpusText.text.trim())
        val word = findWord("_BE-EL-TI4-NI", true)

        val ni = findWord("_NI", syllabographic = true)
        assertEquals("PPRO.1PL.GEN", ni.gloss)

        val compound = graph.findCompoundsByCompoundWord(word).first()
        val headWord = compound.components[0]
        assertEquals("_BE-EL-TI4", headWord.text)

        assertNull(graph.getLinksFrom(word).singleOrNull { it.type == Link.Transcription })
        assertTrue(headWord.lemma.syllabographic)
    }

    @Test
    fun superscriptInLemma() {
        val corpusText = importWord("""<w trans="NU.KIRI₆" mrp0sel=" 1a" mrp1="NU.°GIŠ°KIRI₆=@NU.°GIŠ°KIRI₆@{a → PNm.NOM.SG(UNM)}@38.1@m"><d>m</d><sGr>NU.</sGr><d>GIŠ</d><sGr>KIRI₆</sGr></w>""")
        val word = corpusText.words[0].word
        val lemma = word.lemma
        assertEquals("NU.^GIŠ^KIRI6", lemma.text)
    }

    @Test
    fun findMatchingRule() {
        val rule = hittite.rule("", addedCategories = ".IMP.2SG")
        importWord("""<w trans="QÍ-BÍ-MA" mrp0sel=" 1" mrp1="QABÛ@sagen@2SG.IMP_CNJ@@ "><aGr>QÍ-B<del_in/>Í-M<del_fin/>A</aGr></w>""")
        val word = findWord("_QÍ-BÍ", true)
        val link = graph.getLinksFrom(word).single()
        assertEquals(rule, link.rules.single())
    }

    @Test
    fun unmarkedCase() {
        val rule = hittite.rule("", addedCategories = ".NOM.SG")
        importWord("""<w trans="NU.KIRI₆" mrp0sel=" 1a" mrp1="NU.°GIŠ°KIRI₆=@NU.°GIŠ°KIRI₆@{a → PNm.NOM.SG(UNM)}@38.1@m"><d>m</d><sGr>NU.</sGr><d>GIŠ</d><sGr>KIRI₆</sGr></w>""")
        val word = findWord("^m^NU.^GIŠ^KIRI6", true)
        assertEquals(rule, word.lemmaRule)
        assertEquals("NP", word.lemma.pos)
    }

    @Test
    fun clitics() {
        val rule = hittite.rule("", addedCategories = ".ABL")
        val corpusText = importWord("""<w trans="ḫaittazakan" mrp0sel=" 2" mrp2="ḫaitt=a-@Ḫaitta@GN.ABL@39.2 += ma=kkan@CNJctr=OBPk@@ URU"><d>URU</d>ḫa-it-ta-z<del_in/>a-k<del_fin/>án</w>""")
        val word = corpusText.words[0].word.transcription
        val compound = graph.findCompoundsByCompoundWord(word).first()
        assertEquals("ḫaittaz", compound.components[0].text)
        assertEquals("ma", compound.components[1].text)
        assertEquals(rule, compound.components[0].lemmaRule)
    }

    @Test
    fun cleanStemNumber() {
        val rule = hittite.rule("", addedCategories = ".NOM.SG")
        val corpusText = importWord("""<w trans="uriannieš" mrp0sel=" 1c" mrp1="uriyann=i-2@(Orakelvogel)@{c → LUW||HITT.NOM.SG.C}@30.1.1@">u-ri-an<corr c="(?)"/><del_fin/>-ni-eš<d>MUŠEN</d></w>""")
        val word = corpusText.words[0].word.transcription
        assertEquals("urii̯anni", word.lemma.text)
        assertEquals(rule, word.lemmaRule)
    }

    @Test
    fun akkadianCaseForm() {
        val rule = hittite.rule("", addedCategories = ".D/L.SG")
        val corpusText = importWord("""<w trans="I-NA zuliaššan" mrp0sel=" 1a" mrp1="zuli=a-@Zuliya@{a → …:D/L.SG}@39.2 += ššan@OBPs@@ ÍD"><aGr>I-NA</aGr> <d>ÍD</d>zu-li-aš-ša-an</w>""")
        assertEquals(1, corpusText.words[0].index)
        val word = corpusText.words[0].word.transcription
        val compound = graph.findCompoundsByCompoundWord(word).first()
        assertEquals("zulia", compound.components[0].lemma.text)
        assertEquals(rule, compound.components[0].lemmaRule)
    }

    @Test
    fun bracketInDeterminant() {
        val corpusText = importWord("""<w><d>MUŠ<del_fin/>EN</d></w>""")
        assertEquals("^MUŠ]EN^", corpusText.text.trim())
    }

    @Test
    fun multipleMrp() {
        val rule = hittite.rule("", addedCategories = ".NOM.PL")
        val corpusText = importWord("""<w trans="GUNeš" mrp0sel=" 2 2b" mrp1="GUN@Talent@{ a → NOM.PL.C} { b → ACC.PL.C}@29.1.1@" mrp2="GUN-eš@(Orakelterminus)@{ a → NOM.SG.C(ABBR)} { b → NOM.PL.C(ABBR)}@@ "><sGr>GUN</sGr>-eš₁₇</w>""")
        val word = corpusText.words[0].word
        assertEquals(rule, word.lemmaRule)
    }

    @Test
    fun diacriticsInEnclitics() {
        val rule = hittite.rule("", addedCategories = ".ABL")
        val corpusText = importWord("""<w trans="SIG₅azmankan" mrp0sel=" 2" mrp1="SIG₅@(niederer) Offizier@ABL@29.1.2 += ma=an=kkan@CNJctr=PPRO.3SG.C.ACC=OBPk@@ LÚ" mrp2="SIG₅-az@von der guten Seite@HITT.ABL@+= ma=an=kkan@CNJctr=PPRO.3SG.C.ACC=OBPk@@ "><sGr>SIG₅</sGr>-az-ma-an-kán</w>""")
        val word = corpusText.words[0].word
        val compound = graph.findCompoundsByCompoundWord(word).first()
        assertEquals(4, compound.components.size)
        assertEquals(rule, compound.components[0].lemmaRule)
    }

    @Test
    fun slashInLemma() {
        val rule = hittite.rule("", addedCategories = ".1PL", fromPOS = "V")
        val corpusText = importWord("""<w trans="ḫuekuani" mrp0sel=" 2" mrp1="ḫuek-/ḫuk-@schlachten@1PL.PRS@I.3.1@" mrp2="ḫuek-/ḫuk-2@beschwören@1PL.PRS@I.3.1@">ḫu-e-ku-wa-ni<note c="&lt;P_f_Footnote&gt;&lt;SP_f_AO_3a_-LIT&gt;Hagenbuchner A. 1989a&lt;/SP_f_AO_3a_-LIT&gt;, 84, restores and emends: &lt;del_in/&gt;&lt;SP_f_AO_3a_SumGRAM&gt;EGIR&lt;/SP_f_AO_3a_SumGRAM&gt;&lt;SP_f_AO_3a_Hittite&gt;-a&lt;del_fin/&gt;n-da&lt;/SP_f_AO_3a_Hittite&gt; &lt;SP_f_AO_3a_Hittite&gt;ḫu-e-〈iš〉-ku-wa-ni&lt;/SP_f_AO_3a_Hittite&gt;.&lt;/P_f_Footnote&gt;"/></w>""")
        val word = corpusText.words[0].word.transcription
        assertEquals("ḫuek", word.lemma.text)
        assertEquals("V", word.lemma.pos)
        assertEquals(rule, word.lemmaRule)
    }

    @Test
    fun dontAssociateXWords() {
        val corpusText = importWord("<w mrp0sel=\"DEL\"><del_fin/>x-<laes_in/>an<laes_fin/></w>")
        assertEquals(0, corpusText.words.size)
    }

    @Test
    fun ptcp() {
        val ptcpRule = hittite.rule("", addedCategories = ".PTCP", fromPOS = "V", toPOS = "ADJ")
        val adjRule = hittite.rule("", addedCategories = ".NOM.SG.C", fromPOS = "ADJ")
        val corpusText = importWord("""<w trans="taminkanza" mrp0sel=" 1a" mrp1="damenk=@anheften@{a → PTCP.NOM.SG.C}@I.1.10@"><laes_in/>ta<laes_fin/>-mi-in-kán-za</w>""")
        val word = corpusText.words[0].word.transcription
        val rules = graph.getLinksFrom(word).single { it.type == Link.Derived }.rules
        assertEquals(listOf(ptcpRule, adjRule), rules)
    }

    @Test
    fun noCategories() {
        val corpusText = importWord("""<w trans="EGIRannakan" mrp0sel=" 1a" mrp1="EGIR-an@danach@{a → ADV}@@ += ya=kkan@CNJadd=OBPk@"><sGr>EGIR</sGr>-an-na-kán</w>""")
    }

    private fun findWord(text: String, syllabographic: Boolean = false): Word =
        graph.wordsByText(hittite, text, syllabographic).single()

    private fun importWord(@org.intellij.lang.annotations.Language("XML") element: String): CorpusText {
        val doc = SAXBuilder().build(StringReader("<text>$element</text>"))
        importTLHDig(graph, "doc", doc.rootElement.children)
        return graph.allCorpusTexts().first()
    }

    private val Word.transcription get() = graph.getLinksFrom(this).single { it.type == Link.Transcription }.toEntity as Word
    private val Word.lemma get() = graph.getLinksFrom(this).single { it.type == Link.Derived }.toEntity as Word
    private val Word.lemmaRule get() = this.graph.getLinksFrom(this).single { it.type == Link.Derived }.rules.single()
}
