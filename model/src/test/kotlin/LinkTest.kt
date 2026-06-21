package ru.yole.etymograph

import org.junit.Assert.assertThrows
import org.junit.Test

class LinkTest : QBaseTest() {
    @Test
    fun testDuplicateLink() {
        val word1 = graph.addWord("elen")
        val word2 = graph.addWord("sila")
        graph.addLink(word1, word2, Link.Related)
        assertThrows(IllegalArgumentException::class.java) {
            graph.addLink(word1, word2, Link.Related)
        }
    }

    @Test
    fun testLinkCycle() {
        val word1 = graph.addWord("elen")
        val word2 = graph.addWord("sila")
        val word3 = graph.addWord("lumenn")
        graph.addLink(word1, word2, Link.Derived)
        graph.addLink(word2, word3, Link.Derived)
        assertThrows(IllegalArgumentException::class.java) {
            graph.addLink(word1, word3, Link.Derived)
        }
    }
}