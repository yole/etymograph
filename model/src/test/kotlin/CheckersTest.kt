package page.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Test

class CheckersTest : QBaseTest() {
    @Test
    fun reportsOrphanWord() {
        val word = q.word("alda")
        val issues = mutableListOf<ConsistencyCheckerIssue>()

        GlossChecker.checkWord(word, issues::add)

        assertEquals(listOf(ConsistencyCheckerIssue("Orphan word (ID=${word.id})")), issues)
    }
}
