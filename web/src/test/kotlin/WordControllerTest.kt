package ru.yole.etymograph.web

import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import ru.yole.etymograph.GraphRepository
import ru.yole.etymograph.Link
import ru.yole.etymograph.WordCategory
import ru.yole.etymograph.WordCategoryValue

class WordControllerTest {
    private lateinit var fixture: QTestFixture
    private lateinit var graph: GraphRepository
    private lateinit var wordController: WordController

    @Before
    fun setup() {
        fixture = QTestFixture()
        graph = fixture.graph
        wordController = WordController(fixture.graphService)
    }

    @Test
    fun testEmptyPOS() {
        val addWordParams = WordController.AddWordParameters("ea", "be", "", "", false,"", "")
        val wordViewModel = wordController.addWord("q", addWordParams)

        assertNull(fixture.graph.wordsByText(fixture.q, "ea").single().pos)

        wordController.updateWord(wordViewModel.id, addWordParams)
        assertNull(fixture.graph.wordsByText(fixture.q, "ea").single().pos)
    }

    @Test
    fun testValidateWordClass() {
        fixture.q.wordClasses.add(WordCategory("Gender", listOf("N"), listOf(WordCategoryValue("Male", "m"))))

        val addWordParams = WordController.AddWordParameters("ea", "be", "", "N m", false, "", "")
        val wordViewModel = wordController.addWord("q", addWordParams)
        assertEquals(listOf("m"), wordViewModel.classes)

        val badAddWordParams = WordController.AddWordParameters("ea", "be", "", "N f", false, "", "")
        Assert.assertThrows("Unknown word class 'f'", Exception::class.java) { wordController.addWord("q", badAddWordParams) }
    }

    @Test
    fun updateParadigm() {
        val accRule = fixture.setupParadigm()
        val elen = fixture.graph.findOrAddWord("elen", fixture.q, "star", pos = "N")

        val wordParadigms = wordController.wordParadigms(elen.id)
        assertEquals(1, wordParadigms.paradigms.size)

        wordController.updateParadigm(elen.id, WordController.UpdateParadigmParameters(arrayOf(arrayOf(accRule.id, "elena"))))
        val elena = fixture.graph.wordsByText(fixture.q, "elena").single()
        assertEquals("star.ACC", elena.getOrComputeGloss(fixture.graph))
    }

    @Test
    fun updateParadigmChangeText() {
        val accRule = fixture.setupParadigm()
        val elen = graph.findOrAddWord("elen", fixture.q, "star", pos = "N")
        val elena = graph.findOrAddWord("elena", fixture.q, null, pos = "N")
        graph.addLink(elena, elen, Link.Derived, listOf(accRule), emptyList(), null)

        val wordParadigms = wordController.wordParadigms(elen.id)
        assertEquals(1, wordParadigms.paradigms.size)

        wordController.updateParadigm(elen.id, WordController.UpdateParadigmParameters(arrayOf(arrayOf(accRule.id, "elenna"))))
        assertEquals(elena, fixture.graph.wordsByText(fixture.q, "elenna").single())
    }

    @Test
    fun suggestSequence() {
        val seq = fixture.setupRuleSequence()

        val w1 = graph.findOrAddWord("am", fixture.ce, null)
        val w2 = graph.findOrAddWord("an", fixture.q, null)
        val link = graph.addLink(w2, w1, Link.Origin, emptyList(), emptyList(), null)

        val wordViewModel = wordController.singleWordJson("q", "an", w2.id)
        val linkTypeViewModel = wordViewModel.linksFrom.single()
        val linkViewModel = linkTypeViewModel.words.single()
        val seqViewModel = linkViewModel.suggestedSequences.single()
        assertEquals(seq.name, seqViewModel.name)
    }

    @Test
    fun suggestSequenceFromBase() {
        val seq = fixture.setupRuleSequence()

        val w1 = graph.findOrAddWord("am", fixture.ce, null)
        val w2 = graph.findOrAddWord("an", fixture.q, null)
        val link = graph.addLink(w2, w1, Link.Origin, emptyList(), emptyList(), null)

        val wordViewModel = wordController.singleWordJson("ce", "am", w1.id)
        val linkTypeViewModel = wordViewModel.linksTo.single()
        val linkViewModel = linkTypeViewModel.words.single()
        val seqViewModel = linkViewModel.suggestedSequences.single()
        assertEquals(seq.name, seqViewModel.name)
    }

    @Test
    fun deriveThroughSequence() {
        val seq = fixture.setupRuleSequence()
        val cew = graph.findOrAddWord("am", fixture.ce, null)
        val wordViewModel = wordController.singleWordJson("ce", "am", cew.id)
        assertEquals(1, wordViewModel.suggestedDeriveSequences.size)

        val qWordViewModel = wordController.derive(cew.id, WordController.DeriveThroughSequenceParams(seq.id))
        assertEquals("an", qWordViewModel.text)

        val qw = graph.wordById(qWordViewModel.id)!!
        val link = graph.findLink(qw, cew, Link.Origin)!!
        assertEquals("q-final-consonant", link.rules.single().name)
    }
}
