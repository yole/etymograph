package page.yole.etymograph

import org.junit.Assert.assertThrows
import org.junit.Test

class LinkTest : QBaseTest() {
    @Test
    fun duplicateLink() {
        val word1 = q.word("elen", "elen")
        val word2 = q.word("sila", "sila")
        graph.addLink(word1, word2, Link.Related)
        assertThrows(IllegalArgumentException::class.java) {
            graph.addLink(word1, word2, Link.Related)
        }
    }

    @Test
    fun linkCycle() {
        val word1 = q.word("elen", "elen")
        val word2 = q.word("sila", "sila")
        val word3 = q.word("lumenn", "lumenn")
        graph.addLink(word1, word2, Link.Derived)
        graph.addLink(word2, word3, Link.Derived)
        assertThrows(IllegalArgumentException::class.java) {
            graph.addLink(word1, word3, Link.Derived)
        }
    }
}
