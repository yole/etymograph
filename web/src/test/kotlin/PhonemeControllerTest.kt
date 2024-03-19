package ru.yole.etymograph.web

import org.junit.Assert.assertEquals
import org.junit.Test

class PhonemeControllerTest {
    @Test
    fun view() {
        val fixture = QTestFixture()
        val phoneme = fixture.graph.addPhoneme(fixture.q, listOf("a"), null, setOf("vowel"))
        val phonemeController = PhonemeController(fixture.graphService)
        val viewModel = phonemeController.phoneme(phoneme.id)
        assertEquals("vowel", viewModel.classes)
        assertEquals("Quenya", viewModel.languageFullName)
    }

    @Test
    fun new() {
        val fixture = QTestFixture()
        val phonemeController = PhonemeController(fixture.graphService)
        phonemeController.addPhoneme(fixture.q.shortName, PhonemeController.UpdatePhonemeParameters(
            "a", "", "vowel"
        ))
        val phoneme = fixture.q.phonemes.single()
        assertEquals("a", phoneme.graphemes.single())
        assertEquals("vowel", phoneme.classes.single())
    }

    @Test
    fun update() {
        val fixture = QTestFixture()
        val phoneme = fixture.graph.addPhoneme(fixture.q, listOf("a"), null, setOf("vowel"))
        val phonemeController = PhonemeController(fixture.graphService)
        phonemeController.updatePhoneme(phoneme.id, PhonemeController.UpdatePhonemeParameters(
            "a, ǎ", "a", "short vowel", "", ""
        ))
        assertEquals(listOf("a", "ǎ"), phoneme.graphemes)
        assertEquals("a", phoneme.sound)
        assertEquals(setOf("short", "vowel"), phoneme.classes)
    }

    @Test
    fun delete() {
        val fixture = QTestFixture()
        val phoneme = fixture.graph.addPhoneme(fixture.q, listOf("a"), null, setOf("vowel"))
        val phonemeController = PhonemeController(fixture.graphService)
        phonemeController.deletePhoneme(phoneme.id)
        assertEquals(0, fixture.q.phonemes.size)
    }
}
