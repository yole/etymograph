package ru.yole.etymograph

import org.junit.Assert.*
import org.junit.Test

class RuleTest {
    val q = Language("Quenya", "Q")

    @Test
    fun conditions() {
        val v = CharacterClass("vowel", "aeiou")
        val c = RuleCondition(ConditionType.EndsWith, v)
        assertTrue(c.matches(Word("parma", q)))
        assertFalse(c.matches(Word("formen", q)))
    }

    @Test
    fun instructions() {
        val i = RuleInstruction(InstructionType.RemoveLastCharacter, "")
        assertEquals("parm", i.apply("parma"))
    }

    @Test
    fun rule() {
        val v = CharacterClass(null, "eë")
        val c = RuleCondition(ConditionType.EndsWith, v)
        val i1 = RuleInstruction(InstructionType.RemoveLastCharacter, "")
        val i2 = RuleInstruction(InstructionType.AddSuffix, "i")
        val r = RuleBranch(listOf(c), listOf(i1, i2))

        assertTrue(r.matches(Word("lasse", q)))
        assertEquals("lassi", r.apply(Word("lasse", q)))
    }

    @Test
    fun conditionParse() {
        val c = RuleCondition.parse("word ends with 'eë'") { null }
        assertEquals(ConditionType.EndsWith, c.type)
        assertEquals("eë", c.characterClass.matchingCharacters)

        val v = CharacterClass("vowel", "aoiue")
        val c2 = RuleCondition.parse("word ends with a vowel") { if (it == "vowel") v else null }
        assertEquals(ConditionType.EndsWith, c2.type)
        assertEquals(v, c2.characterClass)
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
        """.trimIndent()) { null }
        assertEquals("e", b.conditions[0].characterClass.matchingCharacters)
        assertEquals("a", b.instructions[0].arg)
    }
}