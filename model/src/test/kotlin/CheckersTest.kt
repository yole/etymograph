package page.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Test

class CheckersTest : QBaseTest() {
    @Test
    fun reportsOrphanWord() {
        val word = q.word("alda")
        val issues = mutableListOf<ConsistencyCheckerIssue>()

        GlossChecker.checkWord(word, issues::add)

        assertEquals(listOf(ConsistencyCheckerIssue("Orphan word (ID=${word.id})", word)), issues)
    }

    @Test
    fun reportsCorpusTextIssueWithTextTitle() {
        val word = q.word("elen", "star")
        val corpusText = graph.addCorpusText("alda", "Test text", q)
        corpusText.associateWord(0, word)
        val issues = mutableListOf<ConsistencyCheckerIssue>()

        CorpusTextChecker.check(graph, q, issues::add)

        assertEquals("Corpus word text 'alda' doesn't match word text 'elen', index 0 in Test text",
            issues.single().description.toString())
        assertEquals(corpusText, issues.single().entity)
    }

    @Test
    fun acceptsSimplifiedCorpusWordSpelling() {
        q.phonemes += phoneme("ã"). apply { simplifiedGrapheme = "a" }
        val word = q.word("cãnem", "dog")
        val corpusText = graph.addCorpusText("canem", "Test text", q)
        corpusText.associateWord(0, word)
        val issues = mutableListOf<ConsistencyCheckerIssue>()

        CorpusTextChecker.checkCorpusText(corpusText, issues::add)

        assertEquals(emptyList<ConsistencyCheckerIssue>(), issues)
    }
}
