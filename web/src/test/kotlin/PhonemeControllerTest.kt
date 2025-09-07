package ru.yole.etymograph.web

import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.springframework.web.server.ResponseStatusException
import ru.yole.etymograph.*
import ru.yole.etymograph.web.controllers.PhonemeController

class PhonemeControllerTest {
    private lateinit var fixture: QTestFixture
    private lateinit var phonemeController: PhonemeController
    private lateinit var graph: GraphRepository

    @Before
    fun setup() {
        fixture = QTestFixture()
        phonemeController = PhonemeController()
        graph = fixture.graph
    }

    @Test
    fun view() {
        val phoneme = graph.addPhoneme(fixture.q, listOf("a"), null, setOf("vowel"))
        val viewModel = phonemeController.phoneme(graph, phoneme.id)
        assertEquals("vowel", viewModel.classes)
        assertEquals("Quenya", viewModel.languageFullName)
    }

    @Test
    fun new() {
        phonemeController.addPhoneme(graph, fixture.q.shortName, PhonemeController.UpdatePhonemeParameters(
            "a", "", "vowel"
        ))
        val phoneme = fixture.q.phonemes.single()
        assertEquals("a", phoneme.graphemes.single())
        assertEquals("vowel", phoneme.classes.single())
    }

    @Test
    fun duplicateGrapheme() {
        graph.addPhoneme(fixture.q, listOf("a"), null, setOf("vowel"))
        assertBadRequest("Duplicate graphemes [a]") {
            phonemeController.addPhoneme(graph, fixture.q.shortName, PhonemeController.UpdatePhonemeParameters(
                "a", "", "vowel"
            ))
        }
    }

    @Test
    fun update() {
        val phoneme = graph.addPhoneme(fixture.q, listOf("a"), null, setOf("vowel"))
        phonemeController.updatePhoneme(graph, phoneme.id, PhonemeController.UpdatePhonemeParameters(
            "a, ǎ", "a", "short vowel", false,"", ""
        ))
        assertEquals(listOf("a", "ǎ"), phoneme.graphemes)
        assertEquals("a", phoneme.sound)
        assertEquals(setOf("short", "vowel"), phoneme.classes)
    }

    @Test
    fun updateTrim() {
        val phoneme = graph.addPhoneme(fixture.q, listOf("a"), null, setOf("vowel"))
        phonemeController.updatePhoneme(graph, phoneme.id, PhonemeController.UpdatePhonemeParameters(
            "a, ǎ", "a", " short vowel ", false,"", ""
        ))
        assertEquals(setOf("short", "vowel"), phoneme.classes)
    }

    @Test
    fun delete() {
        val phoneme = graph.addPhoneme(fixture.q, listOf("a"), null, setOf("vowel"))
        phonemeController.deletePhoneme(graph, phoneme.id)
        assertEquals(0, fixture.q.phonemes.size)
    }

    @Test
    fun relatedRules() {
        val wPhoneme = graph.addPhoneme(fixture.q, listOf("w"), null, setOf())
        val uPhoneme = graph.addPhoneme(fixture.q, listOf("u"), null, setOf())
        val rule = graph.rule("* w > u / _#", fixture.ce, fixture.q, "q-gen")
        val seq = graph.addRuleSequence("ce-to-q", fixture.ce, fixture.q, listOf(rule.step()))

        val wPhonemeViewModel = phonemeController.phoneme(graph, wPhoneme.id)
        assertEquals(1, wPhonemeViewModel.relatedRules.size)
        val group = wPhonemeViewModel.relatedRules.single()
        assertEquals("Origin", group.title)
        assertEquals("q-gen", group.rules.single().name)

        val uPhonemeViewModel = phonemeController.phoneme(graph, uPhoneme.id)
        assertEquals(1, uPhonemeViewModel.relatedRules.size)
    }
}

fun assertBadRequest(message: String, block: () -> Unit) {
    try {
        block()
        Assert.fail("Bad request exception expected")
    }
    catch(e: ResponseStatusException) {
        assertEquals(400, e.body.status)
        assertEquals(message, e.body.detail)
    }
    catch(e: AssertionError) {
        throw e
    }
    catch(e: Throwable) {
        Assert.fail("Unexpected exception $e")
    }
}
