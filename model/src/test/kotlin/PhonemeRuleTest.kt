package ru.yole.etymograph

import org.junit.Assert.*
import org.junit.Test
import ru.yole.etymograph.JsonGraphRepository.Companion.toSerializedFormat

class PhonemeRuleTest : QBaseTest() {

    @Test
    fun phonemeCondition() {
        val word = ce.word("khith")
        val it = PhonemeIterator(word, null)
        val cond = RelativePhonemeRuleCondition(false, null, PhonemePattern(null, "kh"), null)
        assertTrue(cond.matches(word, it, emptyRepo))
    }

    @Test
    fun phonemeConditionParse() {
        val word = ce.word("khith")
        val it = PhonemeIterator(word, null)
        val cond = RuleCondition.parse(ParseBuffer("sound is 'kh'"), ce)
        assertTrue(cond.matches(word, it, emptyRepo))
    }

    @Test
    fun soundCorrespondence() {
        val rule = parseRule(
            ce, q,
            """
            sound is 'th':
            - new sound is 's'
            sound is 'kh':
            - new sound is 'h'
            """.trimIndent()
        )
        assertEquals("his", rule.apply(ce.word("khith"), emptyRepo).text)
        assertEquals("'th' -> 's', 'kh' -> 'h'", rule.toSummaryText())
    }

    @Test
    fun soundDisappears() {
        val rule = parseRule(ce, q, """
            sound is 'i':
            - sound disappears
            sound is 'th':
            - new sound is 's'
        """.trimIndent())
        assertEquals("khs", rule.apply(ce.word("khithi"), emptyRepo).text)
        assertEquals("'i' -> Ø, 'th' -> 's'", rule.toSummaryText())
        assertTrue(rule.refersToPhoneme(ce.phonemes.find { "th" in it.graphemes }!!))
        assertFalse(rule.refersToPhoneme(q.phonemes.find { "t" in it.graphemes }!!))
    }

    @Test
    fun nextSoundDisappears() {
        val rule = parseRule(ce, q, """
            sound is 'm' and next sound is 'b':
            - next sound disappears
            sound is 'b':
            - new sound is 'w'
        """.trimIndent())
        assertEquals("mawa", rule.apply(ce.word("mbaba"), emptyRepo).text)
        assertEquals("'mb' -> 'm', 'b' -> 'w'", rule.toSummaryText())
    }

    @Test
    fun previousSoundDisappears() {
        val rule = parseRule(q, q, """
            sound is 'z' and previous sound is 'i' and second previous sound is 'a':
            - previous sound disappears
            - sound disappears
        """.trimIndent())
        assertEquals("ma", applyRule(rule, q.word("maiz")))
        assertEquals("previous sound disappears", rule.logic.branches[0].instructions[0].toEditableText())
    }

    @Test
    fun secondNextSoundDisappears() {
        val rule = parseRule(q, q, """
            second next sound is 'z':
            - next sound disappears
            - second next sound disappears
        """.trimIndent())
        assertEquals("ma", applyRule(rule, q.word("maiz")))
    }

    @Test
    fun previousSound() {
        val rule = parseRule(ce, q, """
            sound is 'i' and previous sound is 'kh':
            - sound disappears
        """.trimIndent())
        assertEquals("khthi", rule.apply(ce.word("khithi"), emptyRepo).text)
        assertEquals("'i' -> Ø after 'kh'", rule.toSummaryText())
    }

    @Test
    fun previousSoundPhonemeClass() {
        val rule = parseRule(q, q, """
            sound is 'i' and previous sound is not vowel:
            - sound disappears
        """.trimIndent())
        assertEquals("khai", rule.apply(ce.word("khiai"), emptyRepo).text)
        assertEquals("'i' -> Ø after not vowel", rule.toSummaryText())
    }

    @Test
    fun previousSoundPhonemeClassFirst() {
        val rule = parseRule(q, q, """
            sound is 'i' and previous sound is not vowel:
            - sound disappears
        """.trimIndent())
        assertEquals("da", rule.apply(ce.word("ida"), emptyRepo).text)
    }

    @Test
    fun previousSoundNegated() {
        val rule = parseRule(ce, q, """
            sound is 'i' and previous sound is not 'kh':
            - sound disappears
        """.trimIndent())
        assertEquals("khith", rule.apply(ce.word("khithi"), emptyRepo).text)
        assertEquals(
            "sound is 'i' and previous sound is not 'kh'",
            rule.logic.branches[0].condition.toEditableText()
        )
    }

    @Test
    fun nextSound() {
        val rule = parseRule(ce, q, """
            sound is 'i' and next sound is 'kh':
            - sound disappears
        """.trimIndent())
        assertEquals("khthis", rule.apply(ce.word("ikhthis"), emptyRepo).text)
        assertEquals("'i' -> Ø before 'kh'", rule.toSummaryText())
    }

    @Test
    fun nextPhonemeClass() {
        val rule = parseRule(q, q, """
            sound is 'i' and next vowel is 'a':
            - sound disappears
        """.trimIndent())
        assertEquals("khtha", rule.apply(ce.word("khitha"), emptyRepo).text)
        assertEquals("sound is 'i' and next vowel is 'a'", rule.logic.branches[0].condition.toEditableText())
    }

    @Test
    fun nextPhonemeClassComplex() {
        val rule = parseRule(q, q, """
            sound is 'i' and next short vowel is 'a':
            - sound disappears
        """.trimIndent())
        assertEquals("khtha", rule.apply(ce.word("khitha"), emptyRepo).text)
        assertEquals("sound is 'i' and next short vowel is 'a'", rule.logic.branches[0].condition.toEditableText())
    }

    @Test
    fun diphtong() {
        val rule = parseRule(q, q, """
            sound is diphthong:
            - no change
            sound is 'a':
            - new sound is 'o'
        """.trimIndent())
        assertEquals("ainu", rule.apply(q.word("ainu"), emptyRepo).text)
        assertEquals("omo", rule.apply(q.word("ama"), emptyRepo).text)
    }

    @Test
    fun diphtongSecondVowel() {
        val rule = parseRule(q, q, """
            sound is 'i' and sound is not diphthong:
            - sound disappears
        """.trimIndent())
        assertEquals("ainu", rule.apply(q.word("ainu"), emptyRepo).text)
        assertEquals("sla", rule.apply(q.word("sila"), emptyRepo).text)
    }

    @Test
    fun changePhonemeClass() {
        val rule = parseRule(q, q, """
            sound is voiceless stop and next sound is nasal:
            - voiceless becomes voiced
        """.trimIndent())
        assertEquals("utubnu", rule.apply(q.word("utupnu"), emptyRepo).text)
        assertEquals("voiceless -> voiced before nasal", rule.toSummaryText())
        assertTrue(rule.refersToPhoneme(q.phonemes.first { "p" in it.graphemes }))
        assertFalse(rule.refersToPhoneme(q.phonemes.first { "b" in it.graphemes }))

        val ruleInstruction = rule.logic.branches[0].instructions[0]
        assertEquals("voiceless becomes voiced", ruleInstruction.toEditableText())

        val data = ruleInstruction.toSerializedFormat()
        val deserialized = JsonGraphRepository.ruleInstructionFromSerializedFormat(emptyRepo, q, data)
        assertEquals("voiceless becomes voiced", deserialized.toEditableText())
    }

    @Test
    fun changePhonemeClassMultiple() {
        q.phonemes = listOf(
            phoneme("a", "short back open vowel"),
            phoneme("á", "long back open vowel"),
            phoneme("ã", "long back open nasal vowel")
        )
        val rule = parseRule(q, q, """
            sound is 'a':
            - short becomes long nasal
        """.trimIndent())
        assertEquals("ãi", applyRule(rule, q.word("ai")))
    }

    @Test
    fun changePreviousPhonemeClass() {
        val rule = parseRule(q, q, """
            sound is nasal and previous sound is voiceless stop:
            - previous voiceless becomes voiced
        """.trimIndent())
        assertEquals("utubnu", rule.apply(q.word("utupnu"), emptyRepo).text)

        val ruleInstruction = rule.logic.branches[0].instructions[0]
        assertEquals("previous voiceless becomes voiced", ruleInstruction.toEditableText())

        val data = ruleInstruction.toSerializedFormat()
        val deserialized = JsonGraphRepository.ruleInstructionFromSerializedFormat(emptyRepo, q, data)
        assertEquals("previous voiceless becomes voiced", deserialized.toEditableText())
    }

    @Test
    fun stressedSoundCondition() {
        val rule = parseRule(q, q, """
            sound is 'o' and previous sound is 'w' and sound is stressed:
            - new sound is 'a'
        """.trimIndent())
        assertEquals("wawo", rule.apply(q.word("wowo").apply { stressedPhonemeIndex = 1 }, emptyRepo).text)
    }

    @Test
    fun stressedSoundCombinedCondition() {
        val rule = parseRule(q, q, """
            sound is stressed 'o' and previous sound is 'w':
            - new sound is 'a'
        """.trimIndent())
        assertEquals("wawo", rule.apply(q.word("wowo").apply { stressedPhonemeIndex = 1 }, emptyRepo).text)
        assertEquals("wiwo", rule.apply(q.word("wiwo").apply { stressedPhonemeIndex = 1 }, emptyRepo).text)
        assertEquals("sound is stressed 'o' and previous sound is 'w'", rule.logic.branches[0].condition.toEditableText())
        assertEquals("stressed 'o' -> 'a' after 'w'", rule.toSummaryText())
    }

    @Test
    fun stressedSoundConditionNegated() {
        val rule = parseRule(q, q, """
            sound is 'o' and previous sound is 'w' and sound is not stressed:
            - new sound is 'a'
        """.trimIndent())
        assertEquals("wowa", rule.apply(q.word("wowo").apply { stressedPhonemeIndex = 1 }, emptyRepo).text)
    }

    @Test
    fun unstressedSoundCondition() {
        val rule = parseRule(q, q, """
            sound is non-stressed 'o' and previous sound is 'w':
            - new sound is 'a'
        """.trimIndent())
        assertEquals("wowa", rule.apply(q.word("wowo").apply { stressedPhonemeIndex = 1 }, emptyRepo).text)
    }

    @Test
    fun nextSoundIs() {
        val rule = parseRule(q, q, """
            beginning of word and sound is 's' and next sound is 'p':
            - new next sound is 'ph'
        """.trimIndent())
        assertEquals("sphin", rule.apply(q.word("spin"), emptyRepo).text)
        assertEquals("'sp' -> 'sph' at beginning of word", rule.toSummaryText())
    }

    @Test
    fun nextSoundIsClassParameter() {
        val rule = parseRule(q, q, """
            sound is 's' and next sound is non-word-final 'p':
            - new next sound is 'ph'
        """.trimIndent())
        assertEquals("sphisvosp", rule.apply(q.word("spisvosp"), emptyRepo).text)
        assertEquals("sound is 's' and next sound is non-word-final 'p'", rule.logic.branches[0].condition.toEditableText())
    }

    @Test
    fun beginningOfWordNegated() {
        val rule = parseRule(q, q, """
            not beginning of word and sound is 'a':
            - new sound is 'o'
        """.trimIndent())
        assertEquals("aistono", rule.apply(q.word("aistana"), emptyRepo).text)
        assertEquals("not beginning of word and sound is 'a'", rule.logic.branches[0].condition.toEditableText())
        assertEquals("'a' -> 'o' not at beginning of word", rule.toSummaryText())
    }

    @Test
    fun syllableIs() {
        val rule = parseRule(q, q, """
            syllable is second to last and sound is 'i' and next vowel is 'a':
            - new sound is 'e'
        """.trimIndent())
        assertEquals("findela", rule.apply(q.word("findila"), emptyRepo).text)
        val ruleCondition = rule.logic.branches[0].condition
        assertEquals("syllable is second to last and sound is 'i' and next vowel is 'a'", ruleCondition.toEditableText())
    }

    @Test
    fun syllableIsNegated() {
        val rule = parseRule(q, q, """
            syllable is not last and sound is 'o':
            - new sound is 'e'
            syllable is last and sound is 'o':
            - new sound is 'y'
        """.trimIndent())
        assertEquals("yrch", rule.apply(q.word("orch"), emptyRepo).text)
        val ruleCondition = rule.logic.branches[0].condition
        assertEquals("syllable is not last and sound is 'o'", ruleCondition.toEditableText())
        assertEquals("'o' -> 'e' in not last syllable", rule.logic.branches[0].toSummaryText(true))
    }

    @Test
    fun syllableIsSummary() {
        val rule = parseRule(q, q, """
            syllable is second to last and sound is 'i' and next sound is 'a':
            - new sound is 'e'
        """.trimIndent())
        assertEquals("'i' -> 'e' before 'a' in second to last syllable", rule.toSummaryText())
    }

    @Test
    fun syllableIsOpen() {
        val rule = parseRule(q, q, """
            syllable is open and sound is 'i':
            - new sound is 'e'
        """.trimIndent())
        assertEquals("wilwe", applyRule(rule, q.word("wilwi")))
    }

    @Test
    fun prevSyllableIsClosed() {
        val rule = parseRule(q, q, """
            previous syllable is closed and sound is 'a':
            - new sound is 'e'
        """.trimIndent())
        assertEquals("wilwena", applyRule(rule, q.word("wilwana")))
    }

    @Test
    fun summaryWithOr() {
        val rule = parseRule(q, q, """
            sound is 'i' and (previous sound is 'a' or previous sound is 'o'):
            - new sound is 'e'
        """.trimIndent())
        assertEquals("'i' -> 'e' after 'a' or 'o'", rule.toSummaryText())
    }

    @Test
    fun summaryBeforeAfter() {
        val rule = parseRule(q, q, """
            sound is 'kh' and previous sound is vowel and next sound is 'th':
            - new sound is 'i'
        """.trimIndent())
        assertEquals("'kh' -> 'i' after vowel before 'th'", rule.toSummaryText())
    }

    @Test
    fun soundIsInserted() {
        val rule = parseRule(q, q, """
            sound is a consonant and previous sound is a vowel and next sound is 'i':
            - 'i' is inserted before
            - next sound disappears
        """.trimIndent())
        assertEquals("eir", rule.apply(q.word("eri"), emptyRepo).text)
        assertEquals("'i' is inserted before", rule.logic.branches[0].instructions[0].toEditableText())
    }

    @Test
    fun phonemeRulesWorkWithSoundValues() {
        val rule = parseRule(ce, q, """
            sound is 'j' and next sound is 'u':
            - new sound is 'y'
            - next sound disappears
        """.trimIndent())
        ce.phonemes = listOf(phoneme(listOf("y"), "j", "semivowel"))
        assertEquals("ylma", rule.apply(ce.word("yulma"), emptyRepo).asOrthographic().text)
    }

    @Test
    fun phonemeClassesWithSoundValues() {
        val rule = parseRule(ce, ce, """
            sound is 'i' and next sound is consonant:
            - new sound is 'y'
        """.trimIndent())
        ce.phonemes = listOf(phoneme(listOf("c", "k"), "k", "consonant"))
        assertEquals("ycra", rule.apply(ce.word("ikra"), emptyRepo).asOrthographic().text)
    }

    @Test
    fun applySoundRulePhonemic() {
        val soundRule = parseRule(q, q, """
            sound is 'a':
            - new sound is 'á'
        """.trimIndent(), name = "q-lengthen")
        val parseContext = q.parseContext(null, soundRule)
        val rule = parseRule(q, q, """
            sound is vowel:
            - apply sound rule 'q-lengthen'
        """.trimIndent(), context = parseContext)
        assertEquals("silá", rule.apply(q.word("sila"), emptyRepo).text)
    }

    @Test
    fun applySoundRulePhonemicNext() {
        val soundRule = parseRule(q, q, """
            sound is 'a':
            - new sound is 'á'
        """.trimIndent(), name = "q-lengthen")
        val parseContext = q.parseContext(null, soundRule)
        val rule = parseRule(q, q, """
            sound is 'l':
            - apply sound rule 'q-lengthen' to next vowel
        """.trimIndent(), context = parseContext)
        assertEquals("silá", rule.apply(q.word("sila"), emptyRepo).text)
    }

    @Test
    fun applySoundRulePhonemicNextComplexClass() {
        val soundRule = parseRule(q, q, """
            sound is 'a':
            - new sound is 'á'
        """.trimIndent(), name = "q-lengthen")
        val parseContext = q.parseContext(null, soundRule)
        val rule = parseRule(q, q, """
            sound is 's':
            - apply sound rule 'q-lengthen' to next short vowel
        """.trimIndent(), context = parseContext)
        assertEquals("sílá", rule.apply(q.word("síla"), emptyRepo).text)
    }

    @Test
    fun applySoundRuleSeesResultOfPreviousInstructions() {
        val soundRule = parseRule(q, q, """
            sound is 't' and next sound is consonant:
            - new sound is 'd'
        """.trimIndent(), name = "q-voicing")
        val parseContext = q.parseContext(null, soundRule)
        val rule = parseRule(q, q, """
            sound is 'a':
            - sound disappears
            - apply sound rule 'q-voicing' to previous sound
        """.trimIndent(), context = parseContext)
        assertEquals("dmo", applyRule(rule, q.word("tamo")))
    }

    @Test
    fun phonemeEquality() {
        val rule = parseRule(q, q, """
            sound is 'a' and sound is same as previous vowel:
            - sound disappears
        """.trimIndent())
        assertEquals("glawre", rule.apply(q.word("glaware"), emptyRepo).text)
        assertEquals("sound is 'a' and sound is same as previous vowel", rule.logic.branches.single().condition.toEditableText())
    }

    @Test
    fun phonemeEqualityRelative() {
        val rule = parseRule(q, q, """
            sound is 'a' and next sound is same as second next sound:
            - new sound is 'o'
        """.trimIndent())
        assertEquals("ottale", rule.apply(q.word("attale"), emptyRepo).text)
        assertEquals("sound is 'a' and next sound is same as second next sound", rule.logic.branches.single().condition.toEditableText())
    }

    @Test
    fun wordFinal() {
        val rule = parseRule(q, q, """
            sound is 'a' and next sound is word-final:
            - sound disappears
        """.trimIndent())
        assertEquals("glawr", rule.apply(q.word("glawar"), emptyRepo).text)
    }

    @Test
    fun nonWordInital() {
        val rule = parseRule(q, q, """
            sound is non-word-initial 'w' and next sound is 'i':
            - sound disappears
        """.trimIndent())
        assertEquals("wii", applyRule(rule, q.word("wiwi")))
    }

    @Test
    fun soundIsGeminated() {
        val rule = parseRule(q, q, """
            sound is 'k' and next sound is 'w':
            - sound is geminated
        """.trimIndent())
        assertEquals("kkwenya", applyRule(rule, q.word("kwenya")))
    }

    @Test
    fun syllableFinal() {
        val rule = parseRule(q, q, """
            sound is syllable-final 'n':
            - new sound is 'm'
        """.trimIndent())
        assertEquals("inomdem", applyRule(rule, q.word("inonden")))
    }
}
