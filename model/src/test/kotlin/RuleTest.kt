package ru.yole.etymograph

import org.junit.Assert.*
import org.junit.Test

class RuleTest {
    val q = Language("Quenya", "Q")
    val ce = Language("Common Eldarin", "CE")
    val v = PhonemeClass("vowel", listOf("a", "o", "u", "i", "e"))
    val nasals = PhonemeClass("nasal", listOf("m", "n"))

    init {
        ce.digraphs = listOf("kh", "th")
        q.phonemeClasses.add(v)
        ce.phonemeClasses.add(nasals)
    }

    @Test
    fun conditions() {
        val c = LeafRuleCondition(ConditionType.EndsWith, v, null, false)
        assertTrue(c.matches(Word(0, "parma", q)))
        assertFalse(c.matches(Word(0, "formen", q)))
    }

    @Test
    fun instructions() {
        val i = RuleInstruction(InstructionType.RemoveLastCharacter, "")
        assertEquals("parm", i.apply("parma"))
    }

    @Test
    fun rule() {
        val v = PhonemeClass(null, listOf("e", "ë"))
        val c = LeafRuleCondition(ConditionType.EndsWith, v, null, false)
        val i1 = RuleInstruction(InstructionType.RemoveLastCharacter, "")
        val i2 = RuleInstruction(InstructionType.AddSuffix, "i")
        val r = RuleBranch(c, listOf(i1, i2))

        assertTrue(r.matches(Word(0, "lasse", q)))
        assertEquals("lassi", r.apply(Word(0, "lasse", q)))
    }

    @Test
    fun conditionParse() {
        val c = LeafRuleCondition.parse("word ends with 'eë'", q)
        assertEquals(ConditionType.EndsWith, c.type)
        assertEquals("eë", c.parameter)

        val c2 = LeafRuleCondition.parse("word ends with a vowel", q)
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
        val i = RuleInstruction.parse("remove last character")
        assertEquals(InstructionType.RemoveLastCharacter, i.type)

        val i2 = RuleInstruction.parse("add suffix 'a'")
        assertEquals(InstructionType.AddSuffix, i2.type)
        assertEquals("a", i2.arg)
    }

    @Test
    fun branchParse() {
        val b = RuleBranch.parse("""
            word ends with 'e':
            - add suffix 'a'
        """.trimIndent(), q)
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
        """.trimIndent(), q)
        assertEquals(2, branches.size)
        assertEquals(1, branches[0].instructions.size)
        assertEquals(1, branches[1].instructions.size)
    }

    @Test
    fun ruleParseWithoutConditions() {
        val branches = Rule.parseBranches("""
            - add suffix 'lye'
        """.trimIndent(), q)
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
        """.trimIndent(), q)
        assertEquals(2, branches.size)
        assertEquals(1, branches[0].instructions.size)
        assertEquals(OtherwiseCondition, branches[1].condition)
    }

    @Test
    fun phonemeIterator() {
        val it = PhonemeIterator(Word(0, "khith", ce))
        assertEquals("kh", it.current)
        assertTrue(it.advance())
        assertEquals("i", it.current)
        assertTrue(it.advance())
        assertEquals("th", it.current)
        assertFalse(it.advance())
    }

    @Test
    fun phonemeCondition() {
        val it = PhonemeIterator(Word(0, "khith", ce))
        val cond = LeafRuleCondition(ConditionType.PhonemeMatches, null, "kh", false)
        assertTrue(cond.matches(it))
    }

    @Test
    fun phonemeConditionParse() {
        val it = PhonemeIterator(Word(0, "khith", ce))
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
        assertEquals("his", rule.apply(Word(-1, "khith", ce)).text)
    }

    private fun parseRule(fromLanguage: Language, toLanguage: Language, text: String): Rule = Rule(
        -1, "q", fromLanguage, toLanguage,
        Rule.parseBranches(text, fromLanguage), null, null, null, null
    )

    @Test
    fun soundDisappears() {
        val rule = parseRule(ce, q, """
            sound is 'i':
            - sound disappears
            sound is 'th':
            - new sound is 's'
        """.trimIndent())
        assertEquals("khs", rule.apply(Word(-1, "khithi", ce)).text)
    }

    @Test
    fun previousSound() {
        val rule = parseRule(ce, q, """
            sound is 'i' and previous sound is 'kh':
            - sound disappears
        """.trimIndent())
        assertEquals("khthi", rule.apply(Word(-1, "khithi", ce)).text)
    }

    @Test
    fun previousSoundNegated() {
        val rule = parseRule(ce, q, """
            sound is 'i' and previous sound is not 'kh':
            - sound disappears
        """.trimIndent())
        assertEquals("khith", rule.apply(Word(-1, "khithi", ce)).text)
    }
}
