package ru.yole.etymograph

import java.lang.RuntimeException

enum class ConditionType {
    EndsWith
}

class CharacterClass(val name: String?, val matchingCharacters: String)

class RuleParseException(msg: String): RuntimeException(msg)

class RuleCondition(val type: ConditionType, val characterClass: CharacterClass) {
    fun matches(word: Word): Boolean {
        return when (type) {
            ConditionType.EndsWith -> word.text.last() in characterClass.matchingCharacters
        }
    }

    fun prettyPrint(): String = when(type) {
        ConditionType.EndsWith -> wordEndsWith + (characterClass.name?.let { "a $it" } ?: "'${characterClass.matchingCharacters}'")
    }

    companion object {
        const val wordEndsWith = "word ends with "

        fun parse(s: String, characterClassLookup: (String) -> CharacterClass?): RuleCondition {
            if (s.startsWith(wordEndsWith)) {
                val c = s.removePrefix(wordEndsWith)
                if (c.startsWith('\'')) {
                    return RuleCondition(ConditionType.EndsWith, CharacterClass(null, c.removePrefix("'").removeSuffix("'")))
                }
                val characterClass = characterClassLookup(c.removePrefix("a "))
                    ?: throw RuleParseException("Unrecognized character class $c")
                return RuleCondition(ConditionType.EndsWith, characterClass)
            }
            throw RuleParseException("Unrecognized condition $s")
        }
    }
}

enum class InstructionType(val insnName: String, val takesArgument: Boolean) {
    NoChange("no change", false),
    RemoveLastCharacter("remove last character", false),
    AddSuffix("add suffix", true)
}

class RuleInstruction(val type: InstructionType, val arg: String) {
    fun apply(word: String): String = when(type) {
        InstructionType.NoChange -> word
        InstructionType.RemoveLastCharacter -> word.substring(0, word.lastIndex)
        InstructionType.AddSuffix -> word + arg
    }

    fun prettyPrint(): String = type.insnName + (if (type.takesArgument)  " '$arg'" else "")

    companion object {
        fun parse(s: String): RuleInstruction {
            for (type in InstructionType.values()) {
                if (type.takesArgument && s.startsWith(type.insnName + " '")) {
                    return RuleInstruction(type, s.removePrefix(type.insnName + " '").removeSuffix("'"))
                }
                if (!type.takesArgument && s == type.insnName) {
                    return RuleInstruction(type, "")
                }
            }
            throw RuleParseException("Unrecognized instruction $s")
        }
    }
}

class RuleBranch(val conditions: List<RuleCondition>, val instructions: List<RuleInstruction>) {
    fun matches(word: Word) = conditions.all { it.matches(word) }

    fun apply(word: Word): String {
        return instructions.fold(word.text) { s, i -> i.apply(s) }
    }

    fun prettyPrint(): String {
        val prettyConditions = if (conditions.isEmpty())
            "otherwise"
        else
            conditions.joinToString(" and ") { it.prettyPrint() }
        return prettyConditions + ":\n" +
                instructions.joinToString("\n") { " - " + it.prettyPrint() }
    }

    companion object {
        fun parse(s: String, characterClassLookup: (String) -> CharacterClass?): RuleBranch {
            var lines = s.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            val conditions = if (lines[0].endsWith(":")) {
                listOf(RuleCondition.parse(lines[0].removeSuffix(":"), characterClassLookup)).also {
                    lines = lines.drop(1)
                }
            }
            else {
                emptyList()
            }
            val instructions = lines.map {
                if (!it.startsWith("-")) {
                    throw RuleParseException("Instructions must start with -")
                }
                RuleInstruction.parse(it.removePrefix("-").trim())
            }
            return RuleBranch(conditions, instructions)
        }
    }
}

class Rule(
    val id: Int,
    val fromLanguage: Language,
    val toLanguage: Language,
    var branches: List<RuleBranch>,
    val addedCategories: String?,
    source: String?,
    notes: String?
) : LangEntity(source, notes) {
    fun matches(word: Word): Boolean {
        return branches.any { it.matches(word) }
    }

    fun apply(word: Word): String {
        for (branch in branches) {
            if (branch.matches(word)) {
                return branch.apply(word)
            }
        }
        return word.text
    }

    fun prettyPrint(): String {
        return branches.joinToString("\n\n") { it.prettyPrint() }
    }

    companion object {
        fun parseBranches(s: String, characterClassLookup: (String) -> CharacterClass?): List<RuleBranch> {
            val lines = s.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            val branchTexts = mutableListOf<List<String>>()
            var currentBranchText = mutableListOf<String>()
            for (l in lines) {
                if (l.endsWith(':')) {
                    currentBranchText = mutableListOf()
                    branchTexts.add(currentBranchText)
                }
                currentBranchText.add(l)
            }
            if (branchTexts.isEmpty()) {   // rule with no conditions
                branchTexts.add(currentBranchText)
            }
            return branchTexts.map { RuleBranch.parse(it.joinToString("\n"), characterClassLookup) }
        }
    }
}
