package ru.yole.etymograph.web

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.yole.etymograph.Rule
import ru.yole.etymograph.RuleParseContext
import ru.yole.etymograph.RuleParseException

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
            "a, ǎ", "a", "short vowel", false,"", ""
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

    @Test
    fun relatedRules() {
        val fixture = QTestFixture()
        val wPhoneme = fixture.graph.addPhoneme(fixture.q, listOf("w"), null, setOf())
        val uPhoneme = fixture.graph.addPhoneme(fixture.q, listOf("u"), null, setOf())
        val rule = fixture.graphService.graph.addRule("q-gen", fixture.ce, fixture.q,
            Rule.parseBranches("sound is 'w':\n- new sound is 'u'",
                RuleParseContext(fixture.q, fixture.q) { throw RuleParseException("no such rule")})
        )
        val seq = fixture.graphService.graph.addRuleSequence("ce-to-q", fixture.ce, fixture.q, listOf(rule))

        val wPhonemeViewModel = PhonemeController(fixture.graphService).phoneme(wPhoneme.id)
        assertEquals(1, wPhonemeViewModel.relatedRules.size)
        val uPhonemeViewModel = PhonemeController(fixture.graphService).phoneme(uPhoneme.id)
        assertEquals(1, uPhonemeViewModel.relatedRules.size)
    }
}
