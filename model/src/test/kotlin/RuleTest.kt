package ru.yole.etymograph

import org.junit.Assert.*
import org.junit.Test

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
        val i = RuleInstruction.parse("remove last character", q.parseContext())
        assertEquals(InstructionType.RemoveLastCharacter, i.type)

        val i2 = RuleInstruction.parse("add suffix 'a'", q.parseContext())
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
        """.trimIndent(), q.parseContext())
        assertEquals(2, branches.size)
        assertEquals(1, branches[0].instructions.size)
        assertEquals(1, branches[1].instructions.size)
    }

    @Test
    fun ruleParseWithoutConditions() {
        val branches = Rule.parseBranches("""
            - add suffix 'lye'
        """.trimIndent(), q.parseContext())
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
        """.trimIndent(), q.parseContext())
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
        """.trimIndent(), parseContext), null, null, null, null)
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
        assertEquals("beginning of word and sound is 'd'", rule.branches[0].condition.toEditableText())
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
}

fun Language.word(text: String) = Word(-1, text, this)

fun Language.parseContext() = RuleParseContext(this, this) { throw RuleParseException("no such rule") }

fun parseRule(fromLanguage: Language, toLanguage: Language, text: String): Rule = Rule(
    -1, "q", fromLanguage, toLanguage,
    Rule.parseBranches(text,
        RuleParseContext(fromLanguage, toLanguage) { throw RuleParseException("no such rule") }
    ),
    null, null, null, null
)
