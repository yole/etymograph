package page.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class ParadigmTest : QBaseTest() {
    @Before
    fun setup() {
        q
            .withGrammaticalCategory("Case", "N,ADJ", "Nominative" to "NOM", "Dative" to "DAT")
            .withGrammaticalCategory("Number", "N,ADJ", "Singular" to "SG", "Plural" to "PL")
            .withGrammaticalCategory("Gender", "ADJ", "Masculine" to "M", "Feminine" to "F")
    }

    private fun setupNounParadigm(): Paradigm {
        val paradigm = graph.addParadigm("Noun", q, listOf("N"))
        paradigm.addRow("Nom")
        paradigm.addRow("Gen")
        paradigm.addColumn("Sg")
        return paradigm
    }

    @Test
    fun paradigm() {
        val paradigm = setupNounParadigm()

        paradigm.setRule(0, 0, emptyList())
        val genRule = q.rule("- append 'o'", name = "q-gen", addedCategories = ".GEN")
        paradigm.setRule(1, 0, listOf(genRule))

        val lasse = graph.findOrAddWord("lasse", q, "leaf", pos = "N")

        val lasseParadigm = paradigm.generate(lasse)
        assertEquals("lasse", lasseParadigm[0][0]?.get(0)?.word?.text)
        assertEquals("lasseo", lasseParadigm[0][1]?.get(0)?.word?.text)
    }

    @Test
    fun paradigmParse() {
        val plRule = graph.addRule(
            "q-nom-pl",
            q,
            q,
            Rule.parseLogic("- append 'r'", q.parseContext()),
            ".PL"
        )
        val genRule = q.rule("- append 'o'", name = "q-gen", addedCategories = ".GEN")
        val genPlRule = graph.addRule(
            "q-gen-pl",
            q,
            q,
            Rule.parseLogic("- append 'on'", q.parseContext()),
            ".GEN.PL"
        )

        val paradigm = graph.addParadigm("Noun", q, listOf("N"))
        val paradigmText = """Sg Pl
            |Nom - q-nom-pl
            |Gen q-gen q-gen-pl
        """.trimMargin()
        paradigm.parse(paradigmText) { ruleName -> graph.ruleByName(ruleName) }
        assertEquals("Sg", paradigm.columns[0].title)
        assertEquals("Gen", paradigm.rowTitles[1])
        assertEquals(plRule, paradigm.columns[1].cells[0]!!.ruleAlternatives[0])
        assertEquals(genPlRule, paradigm.columns[1].cells[1]!!.ruleAlternatives[0])

        val editableText = paradigm.toEditableText()
        assertEquals(paradigmText, editableText)
    }

    @Test
    fun paradigmPreRule() {
        val paradigm = setupNounParadigm()

        val genRule = q.rule("- append 'o'", name = "q-gen", addedCategories = ".GEN")
        paradigm.setRule(1, 0, listOf(genRule))

        val preRule = q.rule("word ends with 'a':\n- change ending to ''", name = "q-pre")
        paradigm.preRule = preRule

        val cirya = graph.findOrAddWord("cirya", q, "ship", pos = "N")
        val result = genRule.apply(cirya)
        assertEquals("ciryo", result.text)
    }


    @Test
    fun paradigmPreRuleBaseForm() {
        val paradigm = setupNounParadigm()

        val genRule = q.rule("- use base form", name = "q-gen", addedCategories = ".GEN")
        paradigm.setRule(1, 0, listOf(genRule))

        val preRule = q.rule("word ends with 'a':\n- change ending to ''", name = "q-pre")
        paradigm.preRule = preRule

        val cirya = graph.findOrAddWord("cirya", q, "ship", pos = "N")
        val result = genRule.apply(cirya)
        assertEquals("cirya", result.text)
    }

    @Test
    fun paradigmPreRuleLink() {
        val paradigm = setupNounParadigm()

        val genRule = q.rule("- append 'o'", name = "q-gen", addedCategories = ".GEN")
        paradigm.setRule(1, 0, listOf(genRule))

        val preRule = q.rule("word ends with 'a':\n- change ending to ''", name = "q-pre")
        paradigm.preRule = preRule

        val cirya = graph.findOrAddWord("cirya", q, "ship", pos = "N")
        val cir = q.word("cir")
        graph.addLink(cir, cirya, Link.Derived, listOf(preRule))

        val result = genRule.apply(cirya)
        assertEquals("ciro", result.text)
    }

    @Test
    fun paradigmPreRulePreservesAccent() {
        q.accentTypes = setOf(AccentType.Grave)
        val paradigm = setupNounParadigm()

        val genRule = q.rule("- append 'o'", name = "q-gen", addedCategories = ".GEN")
        paradigm.setRule(1, 0, listOf(genRule))

        val preRule = q.rule("word ends with 'a':\n- change ending to ''", name = "q-pre")
        paradigm.preRule = preRule

        val cirya = graph.findOrAddWord("cìrya", q, "ship", pos = "N")
        val result = genRule.apply(cirya)
        assertEquals("cìryo", result.asOrthographic().text)
    }


    @Test
    fun paradigmPostRule() {
        val paradigm = setupNounParadigm()

        val genRule = q.rule("- append 'o'", name = "q-gen", addedCategories = ".GEN")
        paradigm.setRule(1, 0, listOf(genRule))

        val postRule = q.rule("word ends with 'ao':\n- change ending to 'o'", name = "q-post")
        paradigm.postRule = postRule

        val cirya = graph.findOrAddWord("cirya", q, "ship", pos = "N")
        val result = genRule.apply(cirya)
        assertEquals("ciryo", result.text)
    }

    @Test
    fun paradigmPostRuleDelegation() {
        val paradigm = setupNounParadigm()
        paradigm.addColumn("Pl")

        val genRule = q.rule("- append 'o'", name = "q-gen", addedCategories = ".GEN")
        paradigm.setRule(1, 0, listOf(genRule))
        val genPlRule = q.rule("- apply rule 'q-gen'", name = "q-gen-pl")
        paradigm.setRule(1, 1, listOf(genPlRule))

        val postRule = q.rule("word ends with 'o':\n- change ending to ''", name = "q-post")
        paradigm.postRule = postRule

        val cirya = graph.findOrAddWord("kiryamo", q, "mariner", pos = "N")
        val result = genPlRule.apply(cirya)
        assertEquals("kiryamo", result.text)
    }

    @Test
    fun generateParadigm() {
        val paradigm = generateParadigm(
            q, "Noun", listOf("N", "ADJ"),
            listOf("Case"), listOf("Number"), "q-", "", emptyList())

        assertEquals(listOf("N", "ADJ"), paradigm.pos)
        assertNotNull(graph.ruleByName("q-nom-sg"))
    }

    @Test
    fun generateProductParadigm() {
        val paradigm = generateParadigm(
            q, "Adjective", listOf("ADJ"),
            listOf("Case"), listOf("Number", "Gender"), "q-", "", emptyList())

        assertNotNull(graph.ruleByName("q-nom-sg-m"))
    }

    @Test
    fun generateParadigmWithEndings() {
        val paradigm = generateParadigm(
            q, "Noun", listOf("N"),
            listOf("Case"), listOf("Number"), "q-", "", listOf("", "n"))

        val morpheme = graph.wordsByText(q, "n").single()
        assertEquals("Noun dat.sg. ending", morpheme.gloss)
        val rule = graph.ruleByName("q-dat-sg")!!
        assertEquals("lassen", rule.apply(q.word("lasse")).text)
    }

    @Test
    fun deleteRule() {
        val paradigm = setupNounParadigm()

        val genRule = q.rule("- append 'o'", name = "q-gen", addedCategories = ".GEN")
        paradigm.setRule(1, 0, listOf(genRule))

        graph.deleteRule(genRule)
        assertEquals(0, paradigm.allRules.size)
    }

}
