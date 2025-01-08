package ru.yole.etymograph.web

import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import ru.yole.etymograph.GraphRepository
import ru.yole.etymograph.Language
import ru.yole.etymograph.Link
import ru.yole.etymograph.WordCategory
import ru.yole.etymograph.WordCategoryValue
import ru.yole.etymograph.web.controllers.WordController

class WordControllerTest {
    private lateinit var fixture: QTestFixture
    private lateinit var graph: GraphRepository
    private lateinit var wordController: WordController
    private lateinit var oe: Language

    @Before
    fun setup() {
        fixture = QTestFixture()
        graph = fixture.graph

        oe = Language("Old English", "OE")
        graph.addLanguage(oe)

        wordController = WordController(TestDictionaryService())
    }

    @Test
    fun testEmptyPOS() {
        val addWordParams = WordController.AddWordParameters("ea", "be", "", "", "",false,"", "")
        val wordViewModel = wordController.addWord(graph, "q", addWordParams)

        assertNull(fixture.graph.wordsByText(fixture.q, "ea").single().pos)

        wordController.updateWord(graph, wordViewModel.id, addWordParams)
        assertNull(fixture.graph.wordsByText(fixture.q, "ea").single().pos)
    }

    @Test
    fun testValidateWordClass() {
        fixture.q.wordClasses.add(WordCategory("Gender", listOf("N"), listOf(WordCategoryValue("Male", "m"))))

        val addWordParams = WordController.AddWordParameters("ea", "be", "", "N", "m", false, "", "")
        val wordViewModel = wordController.addWord(graph, "q", addWordParams)
        assertEquals(listOf("m"), wordViewModel.classes)

        val badAddWordParams = WordController.AddWordParameters("ea", "be", "", "N", "f", false, "", "")
        Assert.assertThrows("Unknown word class 'f'", Exception::class.java) { wordController.addWord(graph, "q", badAddWordParams) }
    }

    @Test
    fun updateWordParadigm() {
        val accRule = fixture.setupParadigm()
        val elen = fixture.graph.findOrAddWord("elen", fixture.q, "star", pos = "N")

        val wordParadigms = wordController.wordParadigms(graph, elen.id)
        assertEquals(1, wordParadigms.paradigms.size)

        wordController.updateWordParadigm(graph, elen.id, WordController.UpdateWordParadigmParameters(arrayOf(arrayOf(accRule.id, "elena"))))
        val elena = fixture.graph.wordsByText(fixture.q, "elena").single()
        assertEquals("star.ACC", elena.getOrComputeGloss(fixture.graph))
    }

    @Test
    fun updateWordParadigmChangeText() {
        val accRule = fixture.setupParadigm()
        val elen = graph.findOrAddWord("elen", fixture.q, "star", pos = "N")
        val elena = graph.findOrAddWord("elena", fixture.q, null, pos = "N")
        graph.addLink(elena, elen, Link.Derived, listOf(accRule))

        val wordParadigms = wordController.wordParadigms(graph, elen.id)
        assertEquals(1, wordParadigms.paradigms.size)

        wordController.updateWordParadigm(graph, elen.id, WordController.UpdateWordParadigmParameters(arrayOf(arrayOf(accRule.id, "elenna"))))
        assertEquals(elena, fixture.graph.wordsByText(fixture.q, "elenna").single())
    }

    @Test
    fun suggestSequence() {
        val seq = fixture.setupRuleSequence()

        val w1 = graph.findOrAddWord("am", fixture.ce, null)
        val w2 = graph.findOrAddWord("an", fixture.q, null)
        val link = graph.addLink(w2, w1, Link.Origin)

        val wordViewModel = wordController.singleWordJson(graph, "q", "an", w2.id)
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
        graph.addLink(w2, w1, Link.Origin)

        val wordViewModel = wordController.singleWordJson(graph, "ce", "am", w1.id)
        val linkTypeViewModel = wordViewModel.linksTo.single()
        val linkViewModel = linkTypeViewModel.words.single()
        val seqViewModel = linkViewModel.suggestedSequences.single()
        assertEquals(seq.name, seqViewModel.name)
    }

    @Test
    fun deriveThroughSequence() {
        val seq = fixture.setupRuleSequence()
        val cew = graph.findOrAddWord("am", fixture.ce, null)
        val wordViewModel = wordController.singleWordJson(graph, "ce", "am", cew.id)
        assertEquals(1, wordViewModel.suggestedDeriveSequences.size)

        val qWordViewModel = wordController.derive(graph, cew.id, WordController.DeriveThroughSequenceParams(seq.id))
        assertEquals("an", qWordViewModel.text)

        val qw = graph.wordById(qWordViewModel.id)!!
        val link = graph.findLink(qw, cew, Link.Origin)!!
        assertEquals("q-final-consonant", link.rules.single().name)
    }

    @Test
    fun addWordSequence() {
        val seq = fixture.setupRuleSequence()

        wordController.addWordSequence(graph, WordController.WordSequenceParams("ce am 'smth, other' > q an", "PE xx"))
        val ceWord = graph.wordsByText(fixture.ce, "am").single()
        assertEquals("smth, other", ceWord.gloss)
        assertEquals("PE xx", ceWord.source.single().refText)

        val qWord = graph.wordsByText(fixture.q, "an").single()
        assertEquals("smth, other", qWord.gloss)
        assertEquals("PE xx", qWord.source.single().refText)

        val link = graph.findLink(qWord, ceWord, Link.Origin)
        assertEquals("q-final-consonant", link!!.rules.single().name)
        assertEquals("PE xx", link.source.single().refText)
    }

    @Test
    fun explicitStress() {
        val word = wordController.addWord(graph, "q", WordController.AddWordParameters(
            "eˈa", null, null, null, null, false, null, null)
        )
        assertEquals("ea", word.text)
        assertEquals(1, word.stressIndex)
        assertEquals(1, word.stressLength)

        val wordModel = wordController.wordJson(graph, "q", "ea").single()
        assertEquals(1, wordModel.stressIndex)
        assertEquals(1, wordModel.stressLength)
        assertEquals("eˈa", wordModel.textWithExplicitStress)

        wordController.updateWord(graph, word.id, WordController.AddWordParameters("ˈea"))
        val wordById = graph.wordById(word.id)!!
        assertEquals("ea", wordById.text)
        assertEquals(0, wordById.stressedPhonemeIndex)
    }

    @Test
    fun lookup() {
        val word = graph.findOrAddWord("bridel", oe, null)
        wordController.lookup(graph, word.id, WordController.LookupParameters("wiktionary"))
        assertEquals("bridle", word.gloss)
    }

    @Test
    fun lookupAmbiguous() {
        val word = graph.findOrAddWord("īsern", oe, null)
        val result = wordController.lookup(graph, word.id, WordController.LookupParameters("wiktionary"))
        assertEquals(2, result.variants.size)
        val result2 = wordController.lookup(graph, word.id,
            WordController.LookupParameters("wiktionary", result.variants[0].disambiguation))
        assertEquals("the metal iron", word.gloss)
    }

    @Test
    fun suggestCompound() {
        val laece = graph.findOrAddWord("lǣċe", oe, null)
        val cynn = graph.findOrAddWord("cynn", oe, null)
        val laececynn = graph.findOrAddWord("lǣċecynn", oe, null)

        val result = wordController.suggestCompound(graph, laececynn.id, WordController.SuggestCompoundParameters(null))
        assertEquals(1, result.suggestions.size)
        assertEquals(laece.id, result.suggestions[0].id)

        val compound = graph.createCompound(laececynn, listOf(laece))
        val result2 = wordController.suggestCompound(graph, laececynn.id, WordController.SuggestCompoundParameters(compound.id))
        assertEquals(cynn.id, result2.suggestions.single().id)
    }
}
