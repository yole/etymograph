package ru.yole.etymograph

import org.junit.Assert.assertThrows
import org.junit.Test

class LinkTest : QBaseTest() {
    @Test
    fun testDuplicateLink() {
        val repo = repoWithQ()
        val word1 = repo.addWord("elen")
        val word2 = repo.addWord("sila")
        repo.addLink(word1, word2, Link.Related, emptyList())
        assertThrows(IllegalArgumentException::class.java) {
            repo.addLink(word1, word2, Link.Related, emptyList(), emptyList(), null)
        }
    }

    @Test
    fun testLinkCycle() {
        val repo = repoWithQ()
        val word1 = repo.addWord("elen")
        val word2 = repo.addWord("sila")
        val word3 = repo.addWord("lumenn")
        repo.addLink(word1, word2, Link.Derived, emptyList())
        repo.addLink(word2, word3, Link.Derived, emptyList())
        assertThrows(IllegalArgumentException::class.java) {
            repo.addLink(word1, word3, Link.Derived, emptyList())
        }
    }
}