package ru.yole.etymograph.web

import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Test
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
}
