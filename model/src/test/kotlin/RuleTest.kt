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
        assertTrue(c.matches(Word(0, "parma", q), emptyRepo))
        assertFalse(c.matches(Word(0, "formen", q), emptyRepo))
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

        assertTrue(r.matches(Word(0, "lasse", q), emptyRepo))
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
        assertTrue(branches[0].matches(Word(0, "abc", q), emptyRepo))
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
    fun beginsWith() {
        val rule = parseRule(q, q, "word begins with 'b':\n- prepend 'm'")
        assertEquals("mbar", rule.apply(q.word("bar"),emptyRepo).text)
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
    fun applySoundRuleToSound() {
        val soundRule = parseRule(q, q, """
            sound is 'a':
            - new sound is 'á'
        """.trimIndent(), name = "q-lengthen-sound")
        val parseContext = RuleParseContext(q, q) {
            if (it == "q-lengthen-sound") RuleRef.to(soundRule) else throw RuleParseException("no such rule")
        }
        val applySoundRule = Rule(-1, "q-lengthen", q, q, Rule.parseBranches("""
            - apply sound rule 'q-lengthen-sound' to first sound
        """.trimIndent(), parseContext), null, null, null, null, emptyList(), null)
        assertEquals("áire", applySoundRule.apply(q.word("aire"), emptyRepo).text)
        assertEquals("apply sound rule 'q-lengthen-sound' to first sound",
            applySoundRule.logic.branches[0].instructions[0].toEditableText())
    }

    @Test
    fun applySoundRuleOrtho() {
        q.phonemes = listOf(Phoneme(listOf("c", "k"), "k", emptySet()), Phoneme(listOf("ch"), "x", emptySet()))
        val soundRule = parseRule(q, q, """
            sound is 'k':
            - new sound is 'x'
        """.trimIndent(), name = "q-lengthen-sound")
        val parseContext = RuleParseContext(q, q) {
            if (it == "q-lengthen-sound") RuleRef.to(soundRule) else throw RuleParseException("no such rule")
        }
        val applySoundRule = Rule(-1, "q-lengthen", q, q, Rule.parseBranches("""
            - apply sound rule 'q-lengthen-sound' to first sound
        """.trimIndent(), parseContext), null, null, null, null, emptyList(), null)
        assertEquals("chaered", applySoundRule.apply(q.word("caered"), emptyRepo).text)
    }

    @Test
    fun applySoundRuleCase() {
        q.phonemes = listOf(Phoneme(listOf("c", "k"), "k", emptySet()), Phoneme(listOf("ch"), "x", emptySet()))
        val soundRule = parseRule(q, q, """
            sound is 'p':
            - new sound is 'ph'
        """.trimIndent(), name = "q-lengthen-sound")
        val parseContext = RuleParseContext(q, q) {
            if (it == "q-lengthen-sound") RuleRef.to(soundRule) else throw RuleParseException("no such rule")
        }
        val applySoundRule = Rule(-1, "q-lengthen", q, q, Rule.parseBranches("""
            - apply sound rule 'q-lengthen-sound' to first sound
        """.trimIndent(), parseContext), null, null, null, null, emptyList(), null)
        assertEquals("pherian", applySoundRule.apply(q.word("Perian"), emptyRepo).text)
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
    fun endOfWord() {
        val rule = parseRule(ce, q, """
            end of word and sound is 'i':
            - new sound is 'e'
        """.trimIndent())
        assertEquals("limbe", rule.apply(ce.word("limbi"), emptyRepo).text)
        assertEquals("end of word and sound is 'i'", rule.logic.branches[0].condition.toEditableText())
    }

    @Test
    fun syllableMatcher() {
        val condition = RuleCondition.parse(ParseBuffer("second to last syllable contains a long vowel"), q) as SyllableRuleCondition
        assertEquals(-2, condition.index)
        assertEquals("long vowel", condition.phonemeClass!!.name)
        assertTrue(condition.matches(q.word("andúna"), emptyRepo))
        assertFalse(condition.matches(q.word("anca"), emptyRepo))
        assertEquals("second to last syllable contains a long vowel", condition.toEditableText())
    }

    @Test
    fun syllableMatcherSpecific() {
        val condition = RuleCondition.parse(ParseBuffer("second to last syllable contains 'ú'"), q) as SyllableRuleCondition
        assertEquals(-2, condition.index)
        assertEquals("ú", condition.parameter!!)
        assertTrue(condition.matches(q.word("andúna"), emptyRepo))
        assertFalse(condition.matches(q.word("anca"), emptyRepo))
        assertEquals("second to last syllable contains 'ú'", condition.toEditableText())
    }

    @Test
    fun syllableMatcherDiphthong() {
        val condition = RuleCondition.parse(ParseBuffer("first syllable contains a diphthong"), q) as SyllableRuleCondition
        assertTrue(condition.matches(q.word("rauca"), emptyRepo))
        assertFalse(condition.matches(q.word("tie"), emptyRepo))
    }

    @Test
    fun syllableMatcherEndsWith() {
        val condition = RuleCondition.parse(ParseBuffer("first syllable ends with a consonant"), q) as SyllableRuleCondition
        assertTrue(condition.matches(q.word("ampa"), emptyRepo))
        assertFalse(condition.matches(q.word("tie"), emptyRepo))
    }

    @Test
    fun syllableCount() {
        val condition = RuleCondition.parse(ParseBuffer("number of syllables is 3"), q) as LeafRuleCondition
        assertEquals("3", condition.parameter)
        assertTrue(condition.matches(q.word("andúna"), emptyRepo))
        assertFalse(condition.matches(q.word("anca"), emptyRepo))
    }

    @Test
    fun syllableCountNegated() {
        val condition = RuleCondition.parse(ParseBuffer("number of syllables is not 3"), q) as LeafRuleCondition
        assertEquals("3", condition.parameter)
        assertFalse(condition.matches(q.word("andúna"), emptyRepo))
        assertTrue(condition.matches(q.word("anca"), emptyRepo))
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
        assertEquals("change ending to ''", rule.singleInstruction().toEditableText())
        assertEquals("", rule.logic.branches[0].toSummaryText(false))
    }

    @Test
    fun applyClass() {
        val rule = parseRule(q, q, "word ends with 'r':\n- mark word as strong")
        val result = rule.apply(q.word("anar"), emptyRepo)
        assertEquals("strong", result.classes.single())
        assertEquals("mark word as strong", rule.singleInstruction().toEditableText())
    }

    private fun Rule.singleInstruction() = logic.branches.single().instructions.single()

    @Test
    fun stressCondition() {
        val ciryali = q.word("ciryali")
        ciryali.stressedPhonemeIndex = 1
        val condition = RuleCondition.parse(ParseBuffer("stress is on third to last syllable"), q)
        assertTrue(condition.matches(ciryali, emptyRepo))
        assertFalse(condition.matches(q.word("lasse").apply { stressedPhonemeIndex = 1 }, emptyRepo))
        assertEquals("stress is on third to last syllable", condition.toEditableText())
    }

    @Test
    fun insert() {
        val rule = parseRule(q, q, "- insert 'i' before last consonant")
        assertEquals("adain", rule.apply(q.word("adan"), emptyRepo).text)
        assertEquals("insert 'i' before last consonant", rule.singleInstruction().toEditableText())
    }

    @Test
    fun absolutePhonemeRuleCondition() {
        val rule = parseRule(q, q, """
            last sound is consonant and second to last sound is vowel:
            - insert 'i' before last consonant
            otherwise:
            - no change
         """.trimIndent())
        assertEquals("adain", rule.apply(q.word("adan"), emptyRepo).text)
        assertEquals("fela", rule.apply(q.word("fela"), emptyRepo).text)
    }

    @Test
    fun baseWord() {
        val rule = parseRule(q, q, """
            first sound of base word in CE is 'm':
            - prepend 'm'
            otherwise:
            - no change
         """.trimIndent())

        val repo = repoWithQ().apply {
            addLanguage(ce)
        }
        val mbar = repo.addWord("mbar", language = ce)
        val bar = repo.addWord("bar")
        repo.addLink(bar, mbar, Link.Derived, emptyList(), emptyList(), null)

        assertEquals("mbar", rule.apply(bar, repo).text)
        assertEquals("first sound of base word in CE is 'm'", rule.logic.branches[0].condition.toEditableText())
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

fun createParseContext(
    fromLanguage: Language,
    toLanguage: Language,
    repo: GraphRepository?
) = RuleParseContext(fromLanguage, toLanguage) {
    repo?.ruleByName(it)?.let { RuleRef.to(it) } ?: throw RuleParseException("no such rule")
}
