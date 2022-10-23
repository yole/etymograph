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
}