package ru.yole.etymograph

import org.junit.Assert.*
import org.junit.Test
import ru.yole.etymograph.JsonGraphRepository.Companion.ruleBranchesFromSerializedFormat
import ru.yole.etymograph.JsonGraphRepository.Companion.ruleInstructionFromSerializedFormat
import ru.yole.etymograph.JsonGraphRepository.Companion.ruleToSerializedFormat
import ru.yole.etymograph.JsonGraphRepository.Companion.toSerializedFormat

class RuleTest : QBaseTest() {
    private val dummyRule = parseRule(q, q, "- append 'a'")

    @Test
    fun conditions() {
        val c = LeafRuleCondition(ConditionType.EndsWith, v, null, false)
        assertTrue(c.matches(Word(0, "parma", q)))
        assertFalse(c.matches(Word(0, "formen", q)))
    }

    @Test
    fun instructions() {
        val i = PrependAppendInstruction(InstructionType.Append, q, "'a'")
        assertEquals("parma", i.apply(dummyRule, null, q.word("parm"), emptyRepo).text)
    }

    @Test
    fun rule() {
        val v = PhonemeClass("e", listOf("e", "ë"))
        val c = LeafRuleCondition(ConditionType.EndsWith, v, null, false)
        val i2 = PrependAppendInstruction(InstructionType.Append, q, "'i'")
        val r = RuleBranch(c, listOf(i2))

        assertTrue(r.matches(Word(0, "lasse", q)))
        assertEquals("atani", r.apply(dummyRule, q.word("atan"), emptyRepo).text)
    }

    @Test
    fun conditionParse() {
        val c = LeafRuleCondition.parse(ParseBuffer("word ends with 'eë'"), q) as LeafRuleCondition
        assertEquals(ConditionType.EndsWith, c.type)
        assertEquals("eë", c.parameter)

        val c2 = LeafRuleCondition.parse(ParseBuffer("word ends with a vowel"), q) as LeafRuleCondition
        assertEquals(ConditionType.EndsWith, c2.type)
        assertEquals(v, c2.phonemeClass)
    }

    @Test
    fun conditionParseOr() {
        val c = RuleCondition.parse(ParseBuffer("word ends with 'e' or word ends with 'ë'"), q)
        assertTrue(c is OrRuleCondition)
        val l1 = (c as OrRuleCondition).members[0] as LeafRuleCondition
        assertEquals(ConditionType.EndsWith, l1.type)
        assertEquals("e", l1.parameter)
    }

    @Test
    fun conditionParseAnd() {
        val c = RuleCondition.parse(ParseBuffer("word ends with a vowel and word ends with 'a'"), q)
        assertTrue(c is AndRuleCondition)
        val l1 = (c as AndRuleCondition).members[0] as LeafRuleCondition
        assertEquals(ConditionType.EndsWith, l1.type)
        assertEquals("vowel", l1.phonemeClass?.name)
    }

    @Test
    fun parseParentheses() {
        val text = "(word ends with a vowel and word ends with 'a') or word ends with 'c'"
        val c = RuleCondition.parse(ParseBuffer(text), q)
        assertTrue(c is OrRuleCondition)
        val a = (c as OrRuleCondition).members[0] as AndRuleCondition
        val l1 = a.members[0] as LeafRuleCondition
        assertEquals(ConditionType.EndsWith, l1.type)
        assertEquals("vowel", l1.phonemeClass?.name)
        assertEquals(text, c.toEditableText())
    }

    @Test
    fun instructionParse() {
        val i = RuleInstruction.parse("- sound disappears", q.parseContext())
        assertEquals(InstructionType.SoundDisappears, i.type)

        val i2 = RuleInstruction.parse("- append 'a'", q.parseContext())
        assertEquals(InstructionType.Append, i2.type)
        assertEquals("'a'", i2.arg)
    }

    @Test
    fun branchParse() {
        val b = RuleBranch.parse("""
            word ends with 'e':
            - append 'a'
        """.trimIndent(), q.parseContext())
        assertEquals("e", (b.condition as LeafRuleCondition).parameter)
        assertEquals("'a'", b.instructions[0].arg)
    }

    @Test
    fun ruleParse() {
        val branches = Rule.parseBranches("""
            word ends with 'e':
            - append 'a'
            word ends with 'i':
            - append 'r'
        """.trimIndent(), q.parseContext()).branches
        assertEquals(2, branches.size)
        assertEquals(1, branches[0].instructions.size)
        assertEquals(1, branches[1].instructions.size)
    }

    @Test
    fun ruleParseWithoutConditions() {
        val branches = Rule.parseBranches("""
            - append 'lye'
        """.trimIndent(), q.parseContext()).branches
        assertEquals(1, branches.size)
        assertEquals(1, branches[0].instructions.size)
        assertTrue(branches[0].matches(Word(0, "abc", q)))
    }

    @Test
    fun ruleParseOtherwise() {
        val branches = Rule.parseBranches("""
            word ends with 'e':
            - append 'a'
            otherwise:
            - append 'r'
        """.trimIndent(), q.parseContext()).branches
        assertEquals(2, branches.size)
        assertEquals(1, branches[0].instructions.size)
        assertEquals(OtherwiseCondition, branches[1].condition)
    }

    @Test
    fun wordIsUnknownClass() {
        assertThrows("Unknown word class 'm'", RuleParseException::class.java) {
            Rule.parseBranches("word is m:\n- no change", q.parseContext())
        }
    }

    @Test
    fun applySoundRule() {
        val soundRule = parseRule(q, q, """
            sound is 'a':
            - new sound is 'á'
        """.trimIndent())
        val applySoundRuleInstruction = ApplySoundRuleInstruction(q, RuleRef.to(soundRule), "first vowel")
        assertEquals("lásse", applySoundRuleInstruction.apply(soundRule, null, q.word("lasse"), emptyRepo).text)
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
        """.trimIndent(), parseContext), null, null, null, null, emptyList(), null)
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
        val condition = RuleCondition.parse(ParseBuffer("second to last syllable contains a long vowel"), q) as SyllableRuleCondition
        assertEquals(-2, condition.index)
        assertEquals("long vowel", condition.phonemeClass!!.name)
        assertTrue(condition.matches(q.word("andúna")))
        assertFalse(condition.matches(q.word("anca")))
        assertEquals("second to last syllable contains a long vowel", condition.toEditableText())
    }

    @Test
    fun syllableMatcherSpecific() {
        val condition = RuleCondition.parse(ParseBuffer("second to last syllable contains 'ú'"), q) as SyllableRuleCondition
        assertEquals(-2, condition.index)
        assertEquals("ú", condition.parameter!!)
        assertTrue(condition.matches(q.word("andúna")))
        assertFalse(condition.matches(q.word("anca")))
        assertEquals("second to last syllable contains 'ú'", condition.toEditableText())
    }

    @Test
    fun syllableMatcherDiphthong() {
        val condition = RuleCondition.parse(ParseBuffer("first syllable contains a diphthong"), q) as SyllableRuleCondition
        assertTrue(condition.matches(q.word("rauca")))
        assertFalse(condition.matches(q.word("tie")))
    }

    @Test
    fun syllableMatcherEndsWith() {
        val condition = RuleCondition.parse(ParseBuffer("first syllable ends with a consonant"), q) as SyllableRuleCondition
        assertTrue(condition.matches(q.word("ampa")))
        assertFalse(condition.matches(q.word("tie")))
    }

    @Test
    fun syllableCount() {
        val condition = RuleCondition.parse(ParseBuffer("number of syllables is 3"), q) as LeafRuleCondition
        assertEquals("3", condition.parameter)
        assertTrue(condition.matches(q.word("andúna")))
        assertFalse(condition.matches(q.word("anca")))
    }

    @Test
    fun syllableCountInvalid() {
        assertThrows("Number of syllables should be a number", RuleParseException::class.java) {
            RuleCondition.parse(ParseBuffer("number of syllables is foo"), q) as LeafRuleCondition
        }
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
            | - prepend 'a'
            |word ends with 'r':
            | - append 'i'
        """.trimMargin("|")
        val rule = parseRule(q, q, ruleText)
        assertEquals(1, rule.logic.preInstructions.size)

        val word = rule.apply(q.word("sur"), emptyRepo)
        assertEquals("asuri", word.text)

        assertEquals(ruleText, rule.toEditableText())
    }

    @Test
    fun prepend() {
        val rule = parseRule(q, q, "- prepend first vowel")
        val instruction = rule.logic.branches[0].instructions[0]
        assertEquals("utul", instruction.apply(rule, null, q.word("tul"), emptyRepo).text)
        val data = instruction.toSerializedFormat()
        val deserialized = ruleInstructionFromSerializedFormat(emptyRepo, q, data)
        assertEquals("utul", deserialized.apply(rule, null, q.word("tul"), emptyRepo).text)
        assertEquals("prepend first vowel", instruction.toEditableText())
    }

    @Test
    fun changeEndingToEmpty() {
        val rule = parseRule(q, q, "word ends with 'ea':\n- change ending to ''")
        val result = rule.apply(q.word("yaimea"), emptyRepo)
        assertEquals("yaim", result.text)
        assertEquals("change ending to ''", rule.logic.branches.single().instructions.single().toEditableText())
    }

    @Test
    fun applyClass() {
        val rule = parseRule(q, q, "word ends with 'r':\n- mark word as strong")
        val result = rule.apply(q.word("anar"), emptyRepo)
        assertEquals("strong", result.classes.single())
        assertEquals("mark word as strong", rule.logic.branches.single().instructions.single().toEditableText())
    }

    @Test
    fun stressCondition() {
        val ciryali = q.word("ciryali")
        ciryali.stressedPhonemeIndex = 2
        val condition = RuleCondition.parse(ParseBuffer("stress is on third to last syllable"), q)
        assertTrue(condition.matches(ciryali))
        assertFalse(condition.matches(q.word("lasse").apply { stressedPhonemeIndex = 1 }))
        assertEquals("stress is on third to last syllable", condition.toEditableText())
    }

    @Test
    fun reverseApply() {
        val rule = parseRule(q, q, "- append 'llo'")
        val candidates = rule.reverseApply(q.word("hrestallo"))
        assertEquals(1, candidates.size)
        assertEquals("hresta", candidates[0])
    }

    @Test
    fun reverseApplyAppend() {
        val rule = parseRule(q, q, "- append 'llo'")
        val candidates = rule.reverseApply(q.word("hrestallo"))
        assertEquals(1, candidates.size)
        assertEquals("hresta", candidates[0])
    }

    @Test
    fun reverseApplyNormalize() {
        val rule = parseRule(q, q, "- append 'sse'")
        val candidates = rule.reverseApply(q.word("auressë"))
        assertEquals(1, candidates.size)
        assertEquals("aure", candidates[0])
    }

    @Test
    fun reverseApplyMatch() {
        val rule = parseRule(q, q, "word ends with a consonant:\n- append 'i'")
        val candidate = rule.reverseApply(q.word("nai"))
        assertEquals(0, candidate.size)
    }

    @Test
    fun reverseApplyChangeEnding() {
        val rule = parseRule(q, q, "word ends with 'ea':\n- change ending to 'ie'")
        val candidate = rule.reverseApply(q.word("yaimie"))
        assertEquals("yaimea", candidate.single())
    }

    @Test
    fun reverseApplyIgnoreClass() {
        q.wordClasses.add(WordCategory("gender", listOf("N"), listOf(WordCategoryValue("female", "f"))))
        val rule = parseRule(q, q, "word ends with 'ea' and word is f:\n- change ending to 'ie'")
        val candidate = rule.reverseApply(q.word("yaimie"))
        assertEquals("yaimea", candidate.single())
    }

    @Test
    fun reverseApplyChangeEndingOr() {
        val rule = parseRule(q, q, "word ends with 'ea' or word ends with 'ao':\n- change ending to 'ie'")
        val candidates = rule.reverseApply(q.word("yaimie"))
        assertEquals(2, candidates.size)
        assertTrue("yaimao" in candidates)
    }

    @Test
    fun reverseApplyToPhoneme() {
        val rule = parseRule(q, q, "sound is 'i':\n- new sound is 'í'")
        val phonemes = PhonemeIterator(q.word("círa"))
        phonemes.advanceTo(1)
        assertTrue(rule.reverseApplyToPhoneme(phonemes))
        assertEquals("cira", phonemes.result())

        val applySoundRuleInstruction = ApplySoundRuleInstruction(q, RuleRef.to(rule), "first vowel")
        assertEquals("cira", applySoundRuleInstruction.reverseApply(rule, "círa", q).single())
    }

    @Test
    fun reverseApplyMultiple() {
        val rule = parseRule(q, q, "word ends with a consonant:\n- append 'ala'\notherwise:\n- append 'la'")
        val candidates = rule.reverseApply(q.word("picala"))
        assertEquals(2, candidates.size)
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

fun Language.word(text: String, gloss: String? = null, pos: String? = null) = Word(-1, text, this, gloss, pos = pos)

fun Language.parseContext(repo: GraphRepository? = null) = createParseContext(this, this, repo)

fun parseRule(
    fromLanguage: Language, toLanguage: Language, text: String, name: String = "q", repo: GraphRepository? = null,
    addedCategories: String? = null, fromPOS: String? = null, toPOS: String? = null
): Rule = Rule(
    -1, name, fromLanguage, toLanguage,
    Rule.parseBranches(text, createParseContext(fromLanguage, toLanguage, repo)),
    addedCategories, null, fromPOS, toPOS, emptyList(), null
)

private fun createParseContext(
    fromLanguage: Language,
    toLanguage: Language,
    repo: GraphRepository?
) = RuleParseContext(fromLanguage, toLanguage) {
    repo?.ruleByName(it)?.let { RuleRef.to(it) } ?: throw RuleParseException("no such rule")
}
