package ru.yole.etymograph

import org.junit.Assert.*
import org.junit.Test

class RuleTest {
    val q = Language("Quenya", "Q")
    val ce = Language("Common Eldarin", "CE")
    val v = CharacterClass("vowel", "aoiue")

    init {
        ce.digraphs = listOf("kh", "th")
        q.characterClasses.add(v)
    }

    @Test
    fun conditions() {
        val v = CharacterClass("vowel", "aeiou")
        val c = LeafRuleCondition(ConditionType.EndsWith, v, null)
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
        val v = CharacterClass(null, "eë")
        val c = LeafRuleCondition(ConditionType.EndsWith, v, null)
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
        assertEquals(v, c2.characterClass)
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
        assertEquals("vowel", l1.characterClass?.name)
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
        assertTrue(it.next())
        assertEquals("i", it.current)
        assertTrue(it.next())
        assertEquals("th", it.current)
        assertFalse(it.next())
    }

    @Test
    fun phonemeCondition() {
        val it = PhonemeIterator(Word(0, "khith", ce))
        val cond = LeafRuleCondition(ConditionType.PhonemeMatches, null, "kh")
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
        val rule = Rule(-1, "q", ce, q, Rule.parseBranches("""
            sound is 'th':
            - new sound is 's'
            sound is 'kh':
            - new sound is 'h'
        """.trimIndent(), ce), null, null, null, null)
        assertEquals("his", rule.apply(Word(-1, "khith", ce)).text)
    }
}
