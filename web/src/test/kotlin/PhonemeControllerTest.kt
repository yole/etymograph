package ru.yole.etymograph.web

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.yole.etymograph.GraphRepository
import ru.yole.etymograph.Rule
import ru.yole.etymograph.RuleParseContext
import ru.yole.etymograph.RuleParseException

class PhonemeControllerTest {
    lateinit var fixture: QTestFixture
    lateinit var phonemeController: PhonemeController
    lateinit var graph: GraphRepository

    @Before
    fun setup() {
        fixture = QTestFixture()
        phonemeController = PhonemeController(fixture.graphService)
        graph = fixture.graph
    }

    @Test
    fun view() {
        val phoneme = graph.addPhoneme(fixture.q, listOf("a"), null, setOf("vowel"))
        val viewModel = phonemeController.phoneme(phoneme.id)
        assertEquals("vowel", viewModel.classes)
        assertEquals("Quenya", viewModel.languageFullName)
    }

    @Test
    fun new() {
        phonemeController.addPhoneme(fixture.q.shortName, PhonemeController.UpdatePhonemeParameters(
            "a", "", "vowel"
        ))
        val phoneme = fixture.q.phonemes.single()
        assertEquals("a", phoneme.graphemes.single())
        assertEquals("vowel", phoneme.classes.single())
    }

    @Test
    fun update() {
        val phoneme = graph.addPhoneme(fixture.q, listOf("a"), null, setOf("vowel"))
        phonemeController.updatePhoneme(phoneme.id, PhonemeController.UpdatePhonemeParameters(
            "a, ǎ", "a", "short vowel", false,"", ""
        ))
        assertEquals(listOf("a", "ǎ"), phoneme.graphemes)
        assertEquals("a", phoneme.sound)
        assertEquals(setOf("short", "vowel"), phoneme.classes)
    }

    @Test
    fun updateTrim() {
        val phoneme = graph.addPhoneme(fixture.q, listOf("a"), null, setOf("vowel"))
        phonemeController.updatePhoneme(phoneme.id, PhonemeController.UpdatePhonemeParameters(
            "a, ǎ", "a", " short vowel ", false,"", ""
        ))
        assertEquals(setOf("short", "vowel"), phoneme.classes)
    }

    @Test
    fun delete() {
        val phoneme = graph.addPhoneme(fixture.q, listOf("a"), null, setOf("vowel"))
        phonemeController.deletePhoneme(phoneme.id)
        assertEquals(0, fixture.q.phonemes.size)
    }

    @Test
    fun relatedRules() {
        val wPhoneme = graph.addPhoneme(fixture.q, listOf("w"), null, setOf())
        val uPhoneme = graph.addPhoneme(fixture.q, listOf("u"), null, setOf())
        val rule = graph.addRule("q-gen", fixture.ce, fixture.q,
            Rule.parseBranches("sound is 'w':\n- new sound is 'u'",
                RuleParseContext(fixture.q, fixture.q) { throw RuleParseException("no such rule")})
        )
        val seq = graph.addRuleSequence("ce-to-q", fixture.ce, fixture.q, listOf(rule))

        val wPhonemeViewModel = phonemeController.phoneme(wPhoneme.id)
        assertEquals(1, wPhonemeViewModel.relatedRules.size)
        val group = wPhonemeViewModel.relatedRules.single()
        assertEquals("Origin", group.title)
        assertEquals("q-gen", group.rules.single().name)

        val uPhonemeViewModel = phonemeController.phoneme(uPhoneme.id)
        assertEquals(1, uPhonemeViewModel.relatedRules.size)
    }
}
