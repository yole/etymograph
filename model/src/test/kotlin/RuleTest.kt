package ru.yole.etymograph

import org.junit.Assert.*
import org.junit.Test
import ru.yole.etymograph.JsonGraphRepository.Companion.ruleBranchesFromSerializedFormat
import ru.yole.etymograph.JsonGraphRepository.Companion.ruleInstructionFromSerializedFormat
import ru.yole.etymograph.JsonGraphRepository.Companion.ruleToSerializedFormat
import ru.yole.etymograph.JsonGraphRepository.Companion.toSerializedFormat

class RuleTest : QBaseTest() {
    @Test
    fun conditions() {
        val c = LeafRuleCondition(ConditionType.EndsWith, v, null, false)
        assertTrue(c.matches(Word(0, "parma", q)))
        assertFalse(c.matches(Word(0, "formen", q)))
    }

    @Test
    fun instructions() {
        val i = RuleInstruction(InstructionType.RemoveLastCharacter, "")
        assertEquals("parm", i.apply(q.word("parma"), emptyRepo).text)
    }

    @Test
    fun rule() {
        val v = PhonemeClass("e", listOf("e", "ë"))
        val c = LeafRuleCondition(ConditionType.EndsWith, v, null, false)
        val i1 = RuleInstruction(InstructionType.RemoveLastCharacter, "")
        val i2 = RuleInstruction(InstructionType.AddSuffix, "i")
        val r = RuleBranch(c, listOf(i1, i2))

        assertTrue(r.matches(Word(0, "lasse", q)))
        assertEquals("lassi", r.apply(q.word("lasse"), emptyRepo).text)
    }

    @Test
    fun conditionParse() {
        val c = LeafRuleCondition.parse("word ends with 'eë'", q) as LeafRuleCondition
        assertEquals(ConditionType.EndsWith, c.type)
        assertEquals("eë", c.parameter)

        val c2 = LeafRuleCondition.parse("word ends with a vowel", q) as LeafRuleCondition
        assertEquals(ConditionType.EndsWith, c2.type)
        assertEquals(v, c2.phonemeClass)
    }

    @Test
    fun conditionParseOr() {
        val c = RuleCondition.parse("word ends with 'e' or word ends with 'ë'", q)
        assertTrue(c is OrRuleCondition)
        val l1 = (c as OrRuleCondition).members[0] as LeafRuleCondition
        assertEquals(ConditionType.EndsWith, l1.type)
        assertEquals("e", l1.parameter)
    }

    @Test
    fun conditionParseAnd() {
        val c = RuleCondition.parse("word ends with a vowel and word ends with 'a'", q)
        assertTrue(c is AndRuleCondition)
        val l1 = (c as AndRuleCondition).members[0] as LeafRuleCondition
        assertEquals(ConditionType.EndsWith, l1.type)
        assertEquals("vowel", l1.phonemeClass?.name)
    }

    @Test
    fun instructionParse() {
        val i = RuleInstruction.parse("- remove last character", q.parseContext())
        assertEquals(InstructionType.RemoveLastCharacter, i.type)

        val i2 = RuleInstruction.parse("- add suffix 'a'", q.parseContext())
        assertEquals(InstructionType.AddSuffix, i2.type)
        assertEquals("a", i2.arg)
    }

    @Test
    fun branchParse() {
        val b = RuleBranch.parse("""
            word ends with 'e':
            - add suffix 'a'
        """.trimIndent(), q.parseContext())
        assertEquals("e", (b.condition as LeafRuleCondition).parameter)
        assertEquals("a", b.instructions[0].arg)
    }

    @Test
    fun ruleParse() {
        val branches = Rule.parseBranches("""
            word ends with 'e':
            - add suffix 'a'
            word ends with 'i':
            - add suffix 'r'
        """.trimIndent(), q.parseContext()).branches
        assertEquals(2, branches.size)
        assertEquals(1, branches[0].instructions.size)
        assertEquals(1, branches[1].instructions.size)
    }

    @Test
    fun ruleParseWithoutConditions() {
        val branches = Rule.parseBranches("""
            - add suffix 'lye'
        """.trimIndent(), q.parseContext()).branches
        assertEquals(1, branches.size)
        assertEquals(1, branches[0].instructions.size)
        assertTrue(branches[0].matches(Word(0, "abc", q)))
    }

    @Test
    fun ruleParseOtherwise() {
        val branches = Rule.parseBranches("""
            word ends with 'e':
            - add suffix 'a'
            otherwise:
            - add suffix 'r'
        """.trimIndent(), q.parseContext()).branches
        assertEquals(2, branches.size)
        assertEquals(1, branches[0].instructions.size)
        assertEquals(OtherwiseCondition, branches[1].condition)
    }

    @Test
    fun phonemeIterator() {
        val it = PhonemeIterator(ce.word("khith"))
        assertEquals("kh", it.current)
        assertTrue(it.advance())
        assertEquals("i", it.current)
        assertTrue(it.advance())
        assertEquals("th", it.current)
        assertFalse(it.advance())
    }

    @Test
    fun phonemeCondition() {
        val it = PhonemeIterator(ce.word("khith"))
        val cond = LeafRuleCondition(ConditionType.PhonemeMatches, null, "kh", false)
        assertTrue(cond.matches(it))
    }

    @Test
    fun phonemeConditionParse() {
        val it = PhonemeIterator(ce.word("khith"))
        val cond = RuleCondition.parse("sound is 'kh'", ce)
        assertTrue(cond.matches(it))
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
    }

    @Test
    fun previousSound() {
        val rule = parseRule(ce, q, """
            sound is 'i' and previous sound is 'kh':
            - sound disappears
        """.trimIndent())
        assertEquals("khthi", rule.apply(ce.word("khithi"), emptyRepo).text)
    }

    @Test
    fun previousSoundNegated() {
        val rule = parseRule(ce, q, """
            sound is 'i' and previous sound is not 'kh':
            - sound disappears
        """.trimIndent())
        assertEquals("khith", rule.apply(ce.word("khithi"), emptyRepo).text)
    }

    @Test
    fun applySoundRule() {
        val soundRule = parseRule(q, q, """
            sound is 'a':
            - new sound is 'á'
        """.trimIndent())
        val applySoundRuleInstruction = ApplySoundRuleInstruction(q, RuleRef.to(soundRule), "first vowel")
        assertEquals("lásse", applySoundRuleInstruction.apply(q.word("lasse"), emptyRepo).text)
    }

    @Test
    fun parseApplySoundRule() {
        val soundRule = parseRule(q, q, """
            sound is 'a':
            - new sound is 'á'
        """.trimIndent())
        val parseContext = RuleParseContext(q, q) {
            if (it == "q-lengthen") RuleRef.to(soundRule) else throw RuleParseException("no such rule")
        }
        val applySoundRule = Rule(-1, "q-lengthen", q, q, Rule.parseBranches("""
            - apply sound rule 'q-lengthen' to first vowel
        """.trimIndent(), parseContext), null, null, null, null, null, null)
        assertEquals("lásse", applySoundRule.apply(q.word("lasse"), emptyRepo).text)
    }

    @Test
    fun soundRuleToEditableText() {
        val soundRule = parseRule(q, q, """
            sound is 'a':
            - new sound is 'á'
        """.trimIndent())
        val applySoundRuleInstruction = ApplySoundRuleInstruction(q, RuleRef.to(soundRule), "first vowel")
        assertEquals("apply sound rule 'q' to first vowel", applySoundRuleInstruction.toEditableText())
    }

    @Test
    fun beginningOfWord() {
        val rule = parseRule(ce, q, """
            beginning of word and sound is 'd':
            - new sound is 'l'
        """.trimIndent())
        assertEquals("lanta", rule.apply(ce.word("danta"), emptyRepo).text)
        assertEquals("beginning of word and sound is 'd'", rule.logic.branches[0].condition.toEditableText())
    }

    @Test
    fun syllableMatcher() {
        val condition = RuleCondition.parse("second to last syllable contains a long vowel", q) as SyllableRuleCondition
        assertEquals(-2, condition.index)
        assertEquals("long vowel", condition.phonemeClass.name)
        assertTrue(condition.matches(q.word("andúna")))
        assertFalse(condition.matches(q.word("anca")))
        assertEquals("second to last syllable contains a long vowel", condition.toEditableText())
    }

    @Test
    fun syllableMatcherDiphthong() {
        val condition = RuleCondition.parse("first syllable contains a diphthong", q) as SyllableRuleCondition
        assertTrue(condition.matches(q.word("rauca")))
        assertFalse(condition.matches(q.word("tie")))
    }

    @Test
    fun syllableMatcherEndsWith() {
        val condition = RuleCondition.parse("first syllable ends with a consonant", q) as SyllableRuleCondition
        assertTrue(condition.matches(q.word("ampa")))
        assertFalse(condition.matches(q.word("tie")))
    }

    @Test
    fun syllableCount() {
        val condition = RuleCondition.parse("number of syllables is 3", q) as LeafRuleCondition
        assertEquals("3", condition.parameter)
        assertTrue(condition.matches(q.word("andúna")))
        assertFalse(condition.matches(q.word("anca")))
    }

    @Test
    fun stress() {
        val rule = parseRule(q, q, """
            number of syllables is 2:
            - stress is on first syllable
            """.trimIndent()
        )
        val word = rule.apply(q.word("lasse"), emptyRepo)
        assertEquals(1, word.stressedPhonemeIndex)
    }

    @Test
    fun serializeStressRule() {
        val rule = parseRule(q, q, """
            number of syllables is 2:
            - stress is on first syllable
            """.trimIndent()
        )

        val serializedRule = rule.ruleToSerializedFormat()
        val branches = ruleBranchesFromSerializedFormat(emptyRepo, q, serializedRule.branches)
        assertTrue(branches[0].instructions[0] is ApplyStressInstruction)
    }

    @Test
    fun preInstructions() {
        val ruleText = """
            | - remove last character
            |word ends with 'r':
            | - add suffix 'i'
        """.trimMargin("|")
        val rule = parseRule(q, q, ruleText)
        assertEquals(1, rule.logic.preInstructions.size)

        val word = rule.apply(q.word("sure"), emptyRepo)
        assertEquals("suri", word.text)

        assertEquals(ruleText, rule.toEditableText())
    }

    @Test
    fun prepend() {
        val instruction = RuleInstruction.parse("- prepend first vowel", q.parseContext())
        assertEquals("utul", instruction.apply(q.word("tul"), emptyRepo).text)
        val data = instruction.toSerializedFormat()
        val deserialized = ruleInstructionFromSerializedFormat(emptyRepo, q, data)
        assertEquals("utul", deserialized.apply(q.word("tul"), emptyRepo).text)
        assertEquals("prepend first vowel", instruction.toEditableText())
    }

    @Test
    fun stressCondition() {
        val ciryali = q.word("ciryali")
        ciryali.stressedPhonemeIndex = 2
        val condition = RuleCondition.parse("stress is on third to last syllable", q)
        assertTrue(condition.matches(ciryali))
        assertFalse(condition.matches(q.word("lasse").apply { stressedPhonemeIndex = 1 }))
        assertEquals("stress is on third to last syllable", condition.toEditableText())
    }

    @Test
    fun reverseApply() {
        val rule = parseRule(q, q, "- add suffix 'llo'")
        val candidates = rule.reverseApply(q.word("hrestallo"))
        assertEquals(1, candidates.size)
        assertEquals("hresta", candidates[0])
    }

    @Test
    fun reverseApplyNormalize() {
        val rule = parseRule(q, q, "- add suffix 'sse'")
        val candidates = rule.reverseApply(q.word("auressë"))
        assertEquals(1, candidates.size)
        assertEquals("aure", candidates[0])
    }

    @Test
    fun reverseApplyMatch() {
        val rule = parseRule(q, q, "word ends with a consonant:\n- add suffix 'i'")
        val candidate = rule.reverseApply(q.word("nai"))
        assertEquals(0, candidate.size)
    }

    @Test
    fun reverseApplyLastCharacter() {
        val rule = parseRule(q, q, "word ends with 'e':\n- remove last character\n- add suffix 'i'")
        val candidate = rule.reverseApply(q.word("fairi"))
        assertEquals("faire", candidate.single())
    }

    @Test
    fun reverseApplyLastCharacterTwice() {
        val rule = parseRule(q, q, "word ends with 'ea':\n- remove last character\n- remove last character\n- add suffix 'ie'")
        val candidate = rule.reverseApply(q.word("yaimie"))
        assertEquals("yaimea", candidate.single())
    }

    @Test
    fun reverseApplyToPhoneme() {
        val rule = parseRule(q, q, "sound is 'i':\n- new sound is 'í'")
        val phonemes = PhonemeIterator(q.word("círa"))
        phonemes.advanceTo(1)
        assertTrue(rule.reverseApplyToPhoneme(phonemes))
        assertEquals("cira", phonemes.result())

        val applySoundRuleInstruction = ApplySoundRuleInstruction(q, RuleRef.to(rule), "first vowel")
        assertEquals("cira", applySoundRuleInstruction.reverseApply("círa", q))
    }

    /*

    @Test
    fun applyRuleToSyllable() {
        val soundRule = parseRule(q, q, """
            sound is 'u':
            - new sound is 'ú'
        """.trimIndent(), "q-long")
        val repo = InMemoryGraphRepository()
        repo.addRule(soundRule)

        val instruction = RuleInstruction.parse("- apply sound rule 'q-long' to first syllable", q.parseContext(repo))
        assertEquals("túl", instruction.apply(q.word("tul"), emptyRepo).text)
    }
     */

    /*
    @Test
    fun chainedSummaryText() {
        val qNomPl = parseRule(q, q, """
            word ends with a vowel:
            - add suffix 'r'
            otherwise:
            - add suffix 'i'
        """.trimIndent(), "q-nom-pl")
        val repo = InMemoryGraphRepository()
        repo.addRule(qNomPl)

        val qGenPl = parseRule(q, q, """
            - apply rule 'q-nom-pl'
            - add suffix 'on'
            """.trimIndent(), repo = repo)
        assertEquals("-ron/-ion", qGenPl.toSummaryText())
    }
     */
}

fun Language.word(text: String) = Word(-1, text, this)

fun Language.parseContext(repo: GraphRepository? = null) = createParseContext(this, this, repo)

fun parseRule(fromLanguage: Language, toLanguage: Language, text: String, name: String = "q", repo: GraphRepository? = null): Rule = Rule(
    -1, name, fromLanguage, toLanguage,
    Rule.parseBranches(text, createParseContext(fromLanguage, toLanguage, repo)),
    null, null, null, null, null, null
)

private fun createParseContext(
    fromLanguage: Language,
    toLanguage: Language,
    repo: GraphRepository?
) = RuleParseContext(fromLanguage, toLanguage) {
    repo?.ruleByName(it)?.let { RuleRef.to(it) } ?: throw RuleParseException("no such rule")
}
