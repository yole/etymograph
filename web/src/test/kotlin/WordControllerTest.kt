package ru.yole.etymograph.web

import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.yole.etymograph.Link
import ru.yole.etymograph.WordCategory
import ru.yole.etymograph.WordCategoryValue

class WordControllerTest {
    @Test
    fun testEmptyPOS() {
        val fixture = QTestFixture()
        val wordController = WordController(fixture.graphService)
        val addWordParams = WordController.AddWordParameters("ea", "be", "", "", "", "")
        val wordViewModel = wordController.addWord("q", addWordParams)

        Assert.assertNull(fixture.repo.wordsByText(fixture.q, "ea").single().pos)

        wordController.updateWord(wordViewModel.id, addWordParams)
        Assert.assertNull(fixture.repo.wordsByText(fixture.q, "ea").single().pos)
    }

    @Test
    fun testValidateWordClass() {
        val fixture = QTestFixture()
        fixture.q.wordClasses.add(WordCategory("Gender", listOf("N"), listOf(WordCategoryValue("Male", "m"))))

        val wordController = WordController(fixture.graphService)
        val addWordParams = WordController.AddWordParameters("ea", "be", "", "N m", "", "")
        val wordViewModel = wordController.addWord("q", addWordParams)
        assertEquals(listOf("m"), wordViewModel.classes)

        val badAddWordParams = WordController.AddWordParameters("ea", "be", "", "N f", "", "")
        Assert.assertThrows("Unknown word class 'f'", Exception::class.java) { wordController.addWord("q", badAddWordParams) }
    }

    @Test
    fun updateParadigm() {
        val fixture = QTestFixture()
        val accRule = fixture.setupParadigm()
        val elen = fixture.repo.findOrAddWord("elen", fixture.q, "star", pos = "N")

        val wc = WordController(fixture.graphService)
        val wordParadigms = wc.wordParadigms(elen.id)
        assertEquals(1, wordParadigms.paradigms.size)

        wc.updateParadigm(elen.id, WordController.UpdateParadigmParameters(arrayOf(arrayOf(accRule.id, "elena"))))
        val elena = fixture.repo.wordsByText(fixture.q, "elena").single()
        assertEquals("star.ACC", elena.getOrComputeGloss(fixture.repo))
    }

    @Test
    fun updateParadigmChangeText() {
        val fixture = QTestFixture()
        val accRule = fixture.setupParadigm()
        val elen = fixture.repo.findOrAddWord("elen", fixture.q, "star", pos = "N")
        val elena = fixture.repo.findOrAddWord("elena", fixture.q, null, pos = "N")
        fixture.repo.addLink(elena, elen, Link.Derived, listOf(accRule), emptyList(), null)

        val wc = WordController(fixture.graphService)
        val wordParadigms = wc.wordParadigms(elen.id)
        assertEquals(1, wordParadigms.paradigms.size)

        wc.updateParadigm(elen.id, WordController.UpdateParadigmParameters(arrayOf(arrayOf(accRule.id, "elenna"))))
        assertEquals(elena, fixture.repo.wordsByText(fixture.q, "elenna").single())
    }

    @Test
    fun suggestSequence() {
        val fixture = QTestFixture()
        val graph = fixture.graphService.graph
        val seq = fixture.setupRuleSequence()

        val w1 = graph.findOrAddWord("am", fixture.ce, null)
        val w2 = graph.findOrAddWord("an", fixture.q, null)
        val link = graph.addLink(w2, w1, Link.Derived, emptyList(), emptyList(), null)

        val wordController = WordController(fixture.graphService)
        val wordViewModel = wordController.singleWordJson("q", "an", w2.id)
        val linkTypeViewModel = wordViewModel.linksFrom.single()
        val linkViewModel = linkTypeViewModel.words.single()
        val seqViewModel = linkViewModel.suggestedSequences.single()
        assertEquals(seq.name, seqViewModel.name)
    }

    @Test
    fun suggestSequenceFromBase() {
        val fixture = QTestFixture()
        val graph = fixture.graphService.graph
        val seq = fixture.setupRuleSequence()

        val w1 = graph.findOrAddWord("am", fixture.ce, null)
        val w2 = graph.findOrAddWord("an", fixture.q, null)
        val link = graph.addLink(w2, w1, Link.Derived, emptyList(), emptyList(), null)

        val wordController = WordController(fixture.graphService)
        val wordViewModel = wordController.singleWordJson("ce", "am", w1.id)
        val linkTypeViewModel = wordViewModel.linksTo.single()
        val linkViewModel = linkTypeViewModel.words.single()
        val seqViewModel = linkViewModel.suggestedSequences.single()
        assertEquals(seq.name, seqViewModel.name)
    }
}
