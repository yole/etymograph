package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Test

class ParadigmTest : QBaseTest() {
    @Test
    fun paradigm() {
        val repo = InMemoryGraphRepository()
        repo.addLanguage(q)

        val paradigm = repo.addParadigm("Noun", q, listOf("N"))
        paradigm.addRow("Nom")
        paradigm.addRow("Gen")
        paradigm.addColumn("Sg")

        paradigm.setRule(0, 0, emptyList())
        val genRule = repo.addRule(
            "q-gen",
            q,
            q,
            Rule.parseBranches("- append 'o'", q.parseContext()),
            ".GEN"
        )
        paradigm.setRule(1, 0, listOf(genRule))

        val lasse = repo.findOrAddWord("lasse", q, "leaf", pos = "N")

        val lasseParadigm = paradigm.generate(lasse, repo)
        assertEquals("lasse", lasseParadigm[0][0]?.get(0)?.word?.text)
        assertEquals("lasseo", lasseParadigm[0][1]?.get(0)?.word?.text)
    }

    @Test
    fun paradigmParse() {
        val repo = InMemoryGraphRepository()
        repo.addLanguage(q)

        val plRule = repo.addRule(
            "q-nom-pl",
            q,
            q,
            Rule.parseBranches("- append 'r'", q.parseContext()),
            ".PL"
        )
        val genRule = repo.addRule(
            "q-gen",
            q,
            q,
            Rule.parseBranches("- append 'o'", q.parseContext()),
            ".GEN"
        )
        val genPlRule = repo.addRule(
            "q-gen-pl",
            q,
            q,
            Rule.parseBranches("- append 'on'", q.parseContext()),
            ".GEN.PL"
        )

        val paradigm = repo.addParadigm("Noun", q, listOf("N"))
        val paradigmText = """Sg Pl
            |Nom - q-nom-pl
            |Gen q-gen q-gen-pl
        """.trimMargin()
        paradigm.parse(paradigmText) { ruleName -> repo.ruleByName(ruleName) }
        assertEquals("Sg", paradigm.columns[0].title)
        assertEquals("Gen", paradigm.rowTitles[1])
        assertEquals(plRule, paradigm.columns[1].cells[0]!!.ruleAlternatives[0])
        assertEquals(genPlRule, paradigm.columns[1].cells[1]!!.ruleAlternatives[0])

        val editableText = paradigm.toEditableText()
        assertEquals(paradigmText, editableText)
    }
}
