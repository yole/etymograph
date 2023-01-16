package ru.yole.etymograph

import java.lang.RuntimeException

enum class ConditionType {
    EndsWith
}

class CharacterClass(val name: String?, val matchingCharacters: String)

class RuleParseException(msg: String): RuntimeException(msg)

sealed class RuleCondition {
    abstract fun matches(word: Word): Boolean
    abstract fun toEditableText(): String

    companion object {
        fun parse(s: String, characterClassLookup: (String) -> CharacterClass?): RuleCondition {
            if (s == OtherwiseCondition.OTHERWISE) {
                return OtherwiseCondition
            }
            val orBranches = s.split(OrRuleCondition.OR)
            if (orBranches.size > 1) {
                return OrRuleCondition(orBranches.map { parse(it, characterClassLookup) })
            }
            return LeafRuleCondition.parse(s, characterClassLookup)
        }
    }
}

class LeafRuleCondition(val type: ConditionType, val characterClass: CharacterClass) : RuleCondition() {
    override fun matches(word: Word): Boolean {
        return when (type) {
            ConditionType.EndsWith -> word.text.last() in characterClass.matchingCharacters
        }
    }

    override fun toEditableText(): String = when(type) {
        ConditionType.EndsWith -> wordEndsWith + (characterClass.name?.let { "a $it" } ?: "'${characterClass.matchingCharacters}'")
    }

    companion object {
        const val wordEndsWith = "word ends with "

        fun parse(s: String, characterClassLookup: (String) -> CharacterClass?): LeafRuleCondition {
            if (s.startsWith(wordEndsWith)) {
                val c = s.removePrefix(wordEndsWith)
                if (c.startsWith('\'')) {
                    return LeafRuleCondition(ConditionType.EndsWith, CharacterClass(null, c.removePrefix("'").removeSuffix("'")))
                }
                val characterClass = characterClassLookup(c.removePrefix("a "))
                    ?: throw RuleParseException("Unrecognized character class $c")
                return LeafRuleCondition(ConditionType.EndsWith, characterClass)
            }
            throw RuleParseException("Unrecognized condition $s")
        }
    }
}

class OrRuleCondition(val members: List<RuleCondition>) : RuleCondition() {
    override fun matches(word: Word): Boolean = members.any { it.matches(word) }

    override fun toEditableText(): String = members.joinToString(OR) { it.toEditableText() }

    companion object {
        const val OR = " or "
    }
}

object OtherwiseCondition : RuleCondition() {
    override fun matches(word: Word): Boolean = true
    override fun toEditableText(): String = OTHERWISE

    const val OTHERWISE = "otherwise"
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

    fun toEditableText(): String = type.insnName + (if (type.takesArgument)  " '$arg'" else "")

    fun toSummaryText() = when(type) {
        InstructionType.AddSuffix -> "-$arg"
        else -> ""
    }

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

class RuleBranch(val condition: RuleCondition, val instructions: List<RuleInstruction>) {
    fun matches(word: Word) = condition.matches(word)

    fun apply(word: Word): String {
        return instructions.fold(word.text) { s, i -> i.apply(s) }
    }

    fun toEditableText(): String {
        return condition.toEditableText() + ":\n" +
                instructions.joinToString("\n") { " - " + it.toEditableText() }
    }

    fun toSummaryText(): String {
        return instructions.joinToString("") { it.toSummaryText() }
    }

    companion object {
        fun parse(s: String, characterClassLookup: (String) -> CharacterClass?): RuleBranch {
            var lines = s.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            val condition = if (lines[0].endsWith(":")) {
                val conditionList = lines[0].removeSuffix(":")
                lines = lines.drop(1)
                RuleCondition.parse(conditionList, characterClassLookup)
            }
            else {
                OtherwiseCondition
            }
            val instructions = lines.map {
                if (!it.startsWith("-")) {
                    throw RuleParseException("Instructions must start with -")
                }
                RuleInstruction.parse(it.removePrefix("-").trim())
            }
            return RuleBranch(condition, instructions)
        }
    }
}

class Rule(
    val id: Int,
    val name: String,
    val fromLanguage: Language,
    val toLanguage: Language,
    var branches: List<RuleBranch>,
    val addedCategories: String?,
    val replacedCategories: String?,
    source: String?,
    notes: String?
) : LangEntity(source, notes) {
    fun matches(word: Word): Boolean {
        return branches.any { it.matches(word) }
    }

    fun apply(word: Word): Word {
        for (branch in branches) {
            if (branch.matches(word)) {
                val text = branch.apply(word)
                val gloss = word.gloss?.let { baseGloss ->
                    applyCategories(baseGloss)
                }
                return Word(-1, text, word.language, gloss, word.pos)
            }
        }
        return word
    }

    fun applyCategories(baseGloss: String) =
        (replacedCategories?.let { baseGloss.replace(it, "") } ?: baseGloss) + (addedCategories ?: "")

    fun toEditableText(): String {
        return branches.joinToString("\n\n") { it.toEditableText() }
    }

    fun toSummaryText(): String {
        val summaries = branches.map { it.toSummaryText() }.filter { it.isNotEmpty() }.toSet()
        return summaries.joinToString("/")
    }

    companion object {
        fun parseBranches(s: String, characterClassLookup: (String) -> CharacterClass?): List<RuleBranch> {
            if (s.isBlank()) return emptyList()
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
