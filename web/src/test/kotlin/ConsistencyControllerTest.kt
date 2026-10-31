package page.yole.etymograph.web

import org.junit.Assert.*
import org.junit.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import page.yole.etymograph.MorphoRuleLogic
import page.yole.etymograph.Link
import page.yole.etymograph.WordCategoryValue
import page.yole.etymograph.web.controllers.ConsistencyController

class ConsistencyControllerTest {
    private val fixture = QTestFixture()
    private val graph = fixture.graph
    private val controller = ConsistencyController()

    @Test
    fun groupsWordIssuesByType() {
        val first = graph.addWord("alda", fixture.q, null)
        val second = graph.addWord("elen", fixture.q, null)

        val report = controller.consistency(graph, "q")

        assertEquals("q", report.language)
        assertEquals("Quenya", report.languageFullName)
        assertEquals(listOf("Word text", "Parts of speech", "Glosses"), report.issueGroups.map { it.type })
        val groups = report.issueGroups.associate { it.type to it.issues }
        assertEquals(listOf("No POS specified for alda 'null'", "No POS specified for elen 'null'"), groups["Parts of speech"]?.map { it.toString() })
        assertEquals(listOf(
            "Orphan word (ID=${first.id}) for alda 'null'",
            "Orphan word (ID=${second.id}) for elen 'null'"
        ), groups["Glosses"]?.map { it.toString() })
        for (group in report.issueGroups) {
            val links = group.issues.map { it.fragments.single { it.linkType == "word" } }
            assertEquals(listOf(first.id, second.id), links.map { it.linkId })
            assertEquals(listOf("alda", "elen"), links.map { it.text })
            assertTrue(links.all { it.linkLanguage == "q" })
            assertTrue(group.issues.all { issue ->
                issue.fragments.filter { it.linkType == null }.joinToString("") { it.text }.endsWith(" 'null'")
            })
        }
    }

    @Test
    fun includesCorpusAndRuleIssuesOnlyForRequestedLanguage() {
        val qWord = graph.addWord("elen", fixture.q, "star")
        val corpusText = graph.addCorpusText("alda", "Quenya text", fixture.q)
        corpusText.associateWord(0, qWord)
        val rule = graph.addRule("q-invalid", fixture.ce, fixture.q, MorphoRuleLogic.empty(), fromPOS = listOf("unknown"))
        val ceWord = graph.addWord("other", fixture.ce, null)
        graph.addCorpusText("mismatch", "Eldarin text", fixture.ce).associateWord(0, ceWord)
        graph.addRule("ce-invalid", fixture.ce, fixture.ce, MorphoRuleLogic.empty(), fromPOS = listOf("unknown"))

        val groups = controller.consistency(graph, "q").issueGroups.associate { it.type to it.issues }

        assertEquals(listOf(
            "Corpus word text 'alda' doesn't match word text 'elen', index 0 in Quenya text"
        ), groups["Corpus texts"]?.map { it.toString() })
        assertEquals(listOf("Unknown rule from POS 'unknown' in q-invalid"), groups["Rules"]?.map { it.toString() })
        val corpusLink = groups.getValue("Corpus texts").single().fragments.first()
        assertEquals("corpusText", corpusLink.linkType)
        assertEquals(corpusText.id, corpusLink.linkId)
        val ruleLink = groups.getValue("Rules").single().fragments.first()
        assertEquals("rule", ruleLink.linkType)
        assertEquals(rule.id, ruleLink.linkId)
        assertFalse(groups.values.flatten().any { "other" in it.toString() || "Eldarin text" in it.toString() || "ce-invalid" in it.toString() })
    }

    @Test
    fun linksWordClassAndLinkIssuesToTheAffectedWord() {
        val word = graph.addWord("alda", fixture.q, "tree", classes = listOf("unknown"))
        val base = graph.addWord("root", fixture.q, "root")
        graph.addLink(word, base, Link.Derived)

        val groups = controller.consistency(graph, "q").issueGroups.associate { it.type to it.issues }

        for (type in listOf("Word classes", "Links")) {
            val fragment = groups.getValue(type).single().fragments.single { it.linkType == "word" }
            assertEquals("word", fragment.linkType)
            assertEquals(word.id, fragment.linkId)
        }
    }

    @Test
    fun distinguishesHomonymsAndUsesSyllabographicUrlKeys() {
        val first = graph.addWord("^d^_IŠKUR", fixture.q, "first", syllabographic = true)
        val second = graph.addWord("^d^_IŠKUR", fixture.q, "second", syllabographic = true)

        val issues = controller.consistency(graph, "q").issueGroups.single().issues.map {
            it.fragments.single { it.linkType == "word" }
        }

        assertEquals(listOf(first.id, second.id), issues.map { it.linkId })
        assertEquals(listOf("d_IŠKUR", "d_IŠKUR"), issues.map { it.linkData })
    }

    @Test
    fun preservesSyllabogramsAndGlossInWordIssues() {
        val word = graph.addWord("^d^_DU3", fixture.q, "god", syllabographic = true)

        val issue = controller.consistency(graph, "q").issueGroups.single().issues.single()
        val wordFragment = issue.fragments.single { it.syllabogramSequence != null }

        assertEquals("No POS specified for ^d^_DU3 'god'", issue.toString())
        assertEquals(word.text, wordFragment.text)
        assertEquals(word.syllabogramSequence, wordFragment.syllabogramSequence)
        assertEquals(listOf(wordFragment), issue.fragments.filter { it.linkType != null })
        assertEquals("word", wordFragment.linkType)
        assertEquals(word.id, wordFragment.linkId)
        assertEquals("No POS specified for  'god'",
            issue.fragments.filter { it.linkType == null }.joinToString("") { it.text })
        assertEquals(word.syllabogramSequence,
            issue.format(monospaced = true).fragments.single { it.syllabogramSequence != null }.syllabogramSequence)
    }

    @Test
    fun preservesSyllabogramsInCorpusIssues() {
        fixture.q.syllabographic = true
        val word = graph.addWord("^d^_DU3", fixture.q, "god", syllabographic = true)
        val corpusText = graph.addCorpusText("^d^_IŠKUR", "Test text", fixture.q)
        corpusText.associateWord(0, word)

        val issue = controller.consistency(graph, "q").issueGroups.single { it.type == "Corpus texts" }.issues.single()
        val wordFragments = issue.fragments.filter { it.syllabogramSequence != null }

        assertEquals(listOf("^d^_IŠKUR", "^d^_DU3"), wordFragments.map { it.text })
        assertEquals(word.syllabogramSequence, wordFragments.last().syllabogramSequence)
        assertTrue(issue.fragments.all { it.linkType == "corpusText" && it.linkId == corpusText.id })
    }

    @Test
    fun returnsNoGroupsForConsistentLanguage() {
        fixture.q.pos.add(WordCategoryValue("Noun", "N"))
        graph.addWord("a", fixture.q, "tree", pos = "N", syllabographic = true)

        assertTrue(controller.consistency(graph, "q").issueGroups.isEmpty())
    }

    @Test
    fun rerunsChecksAfterDataChanges() {
        fixture.q.pos.add(WordCategoryValue("Noun", "N"))
        val word = graph.addWord("a", fixture.q, "tree", syllabographic = true)
        assertEquals(listOf("Parts of speech"), controller.consistency(graph, "q").issueGroups.map { it.type })

        word.pos = "N"

        assertTrue(controller.consistency(graph, "q").issueGroups.isEmpty())
    }

    @Test
    fun unknownLanguageReturnsNotFound() {
        val exception = assertThrows(ResponseStatusException::class.java) {
            controller.consistency(graph, "missing")
        }
        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
    }
}
