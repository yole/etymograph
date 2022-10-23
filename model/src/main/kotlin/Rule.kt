package ru.yole.etymograph

enum class ConditionType {
    EndsWith
}

class CharacterClass(val name: String?, val matchingCharacters: String)

class RuleCondition(val type: ConditionType, val characterClass: CharacterClass) {
    fun matches(word: Word): Boolean {
        return when (type) {
            ConditionType.EndsWith -> word.text.last() in characterClass.matchingCharacters
        }
    }

    fun prettyPrint(): String = when(type) {
        ConditionType.EndsWith -> "word ends with " + (characterClass.name?.let { "a $it" } ?: "'${characterClass.matchingCharacters}'")
    }
}

enum class InstructionType {
    RemoveLastCharacter,
    AddSuffix
}

class RuleInstruction(val type: InstructionType, val arg: String) {
    fun apply(word: String): String = when(type) {
        InstructionType.RemoveLastCharacter -> word.substring(0, word.lastIndex)
        InstructionType.AddSuffix -> word + arg
    }

    fun prettyPrint(): String = when(type) {
        InstructionType.RemoveLastCharacter -> "remove last character"
        InstructionType.AddSuffix -> "add suffix '$arg'"
    }
}

class RuleBranch(val conditions: List<RuleCondition>, val instructions: List<RuleInstruction>) {
    fun matches(word: Word) = conditions.all { it.matches(word) }

    fun apply(word: Word): String {
        return instructions.fold(word.text) { s, i -> i.apply(s) }
    }
}

class Rule(
    val id: Int,
    val fromLanguage: Language,
    val toLanguage: Language,
    val branches: List<RuleBranch>,
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
}
