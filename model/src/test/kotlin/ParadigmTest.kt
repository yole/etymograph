package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Test

class ParadigmTest {
    val q = Language("Quenya", "Q")

    @Test
    fun paradigm() {
        val repo = InMemoryGraphRepository()
        repo.addLanguage(q)

        val paradigm = repo.addParadigm("Noun", q, "N")
        paradigm.addRow("Nom")
        paradigm.addRow("Gen")
        paradigm.addColumn("Sg")

        paradigm.setRule(0, 0, emptyList())
        val genRule = repo.addRule("q-gen", q, q, Rule.parseBranches("- add suffix 'o'") { null }, ".GEN", null, null)
        paradigm.setRule(1, 0, listOf(genRule))

        val lasse = repo.addWord("lasse", q, "leaf", "N", null, null)

        val lasseParadigm = paradigm.generate(lasse)
        assertEquals("lasse", lasseParadigm[0][0]?.text)
        assertEquals("lasseo", lasseParadigm[0][1]?.text)
    }

    @Test
    fun paradigmParse() {
        val repo = InMemoryGraphRepository()
        repo.addLanguage(q)

        val plRule = repo.addRule("q-nom-pl", q, q, Rule.parseBranches("- add suffix 'r'") { null }, ".PL", null, null)
        val genRule = repo.addRule("q-gen", q, q, Rule.parseBranches("- add suffix 'o'") { null }, ".GEN", null, null)
        val genPlRule = repo.addRule("q-gen-pl", q, q, Rule.parseBranches("- add suffix 'on'") { null }, ".GEN.PL", null, null)

        val paradigm = repo.addParadigm("Noun", q, "N")
        val paradigmText = """Sg Pl
            |Nom - q-nom-pl
            |Gen q-gen q-nom-pl,q-gen-pl
        """.trimMargin()
        paradigm.parse(paradigmText) { ruleName -> repo.ruleByName(ruleName) }
        assertEquals("Sg", paradigm.columns[0].title)
        assertEquals("Gen", paradigm.rowTitles[1])
        assertEquals(plRule, paradigm.columns[1].cells[0]!!.rules[0])
        assertEquals(plRule, paradigm.columns[1].cells[1]!!.rules[0])
        assertEquals(genPlRule, paradigm.columns[1].cells[1]!!.rules[1])

        val editableText = paradigm.toEditableText()
        assertEquals(paradigmText, editableText)
    }
}
