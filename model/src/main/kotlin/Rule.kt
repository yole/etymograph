package ru.yole.etymograph

import java.lang.RuntimeException
import java.util.*

enum class ConditionType(val condName: String) {
    EndsWith(LeafRuleCondition.wordEndsWith),
    PhonemeMatches(LeafRuleCondition.soundIs)
}

class CharacterClass(val name: String?, val matchingCharacters: String)

class RuleParseException(msg: String): RuntimeException(msg)

class PhonemeIterator(val word: Word) {
    private val phonemes = splitPhonemes(word.text.lowercase(Locale.getDefault()), word.language.digraphs)
    private val resultPhonemes = phonemes.toMutableList()
    private var phonemeIndex = 0

    val current: String get() = phonemes[phonemeIndex]

    fun next(): Boolean {
        if (phonemeIndex < phonemes.size - 1) {
            phonemeIndex++
            return true
        }
        return false
    }

    fun replace(s: String) {
        resultPhonemes[phonemeIndex] = s
    }

    fun result(): String {
        return resultPhonemes.joinToString("")
    }

    private fun splitPhonemes(text: String, digraphs: List<String>): List<String> {
        val result = mutableListOf<String>()
        var offset = 0
        while (offset < text.length - 1) {
            val digraph = digraphs.firstOrNull { text.startsWith(it, offset) }
            if (digraph != null) {
                result.add(digraph)
                offset += digraph.length
            }
            else {
                result.add(text.substring(offset, offset + 1))
                offset++
            }
        }
        return result
    }
}

sealed class RuleCondition {
    abstract fun isPhonemic(): Boolean
    open fun matches(word: Word): Boolean {
        val it = PhonemeIterator(word)
        while (true) {
            if (matches(it)) return true
            if (!it.next()) break
        }
        return false
    }

    abstract fun matches(phoneme: PhonemeIterator): Boolean
    abstract fun toEditableText(): String

    companion object {
        fun parse(s: String, language: Language): RuleCondition {
            if (s == OtherwiseCondition.OTHERWISE) {
                return OtherwiseCondition
            }
            val orBranches = s.split(OrRuleCondition.OR)
            if (orBranches.size > 1) {
                return OrRuleCondition(orBranches.map { parse(it, language) })
            }
            val andBranches = s.split(AndRuleCondition.AND)
            if (andBranches.size > 1) {
                return AndRuleCondition(andBranches.map { parse(it, language) })
            }
            return LeafRuleCondition.parse(s, language)
        }
    }
}

class LeafRuleCondition(
    val type: ConditionType,
    val characterClass: CharacterClass?,
    val parameter: String?
) : RuleCondition() {
    override fun isPhonemic(): Boolean {
        return type == ConditionType.PhonemeMatches
    }

    override fun matches(word: Word): Boolean {
        return when (type) {
            ConditionType.EndsWith -> characterClass?.let { word.text.last() in it.matchingCharacters }
                ?: word.text.endsWith(parameter!!)
            else -> super.matches(word)
        }
    }

    override fun matches(phoneme: PhonemeIterator): Boolean {
        return when (type) {
            ConditionType.PhonemeMatches -> phoneme.current == parameter
            else -> throw IllegalStateException("Trying to use a word condition for matching phonemes")
        }
    }

    override fun toEditableText(): String =
        type.condName + (characterClass?.name?.let { "a $it" } ?: "'$parameter'")

    companion object {
        const val wordEndsWith = "word ends with "
        const val soundIs = "sound is "

        fun parse(s: String, language: Language): LeafRuleCondition {
            for (conditionType in ConditionType.values()) {
                if (s.startsWith(conditionType.condName)) {
                    return parseLeafCondition(conditionType, s.removePrefix(conditionType.condName), language)
                }
            }
            throw RuleParseException("Unrecognized condition $s")
        }

        private fun parseLeafCondition(
            conditionType: ConditionType,
            c: String,
            language: Language
        ): LeafRuleCondition {
            if (c.startsWith('\'')) {
                return LeafRuleCondition(conditionType, null, c.removePrefix("'").removeSuffix("'"))
            }
            val characterClass = language.characterClassByName(c.removePrefix("a "))
                ?: throw RuleParseException("Unrecognized character class $c")
            return LeafRuleCondition(conditionType, characterClass, null)
        }
    }
}

class OrRuleCondition(val members: List<RuleCondition>) : RuleCondition() {
    override fun isPhonemic(): Boolean {
        return members.any { it.isPhonemic() }
    }

    override fun matches(word: Word): Boolean = members.any { it.matches(word) }

    override fun matches(phoneme: PhonemeIterator) = members.any { it.matches(phoneme) }

    override fun toEditableText(): String = members.joinToString(OR) { it.toEditableText() }

    companion object {
        const val OR = " or "
    }
}

class AndRuleCondition(val members: List<RuleCondition>) : RuleCondition() {
    override fun isPhonemic(): Boolean {
        return members.any { it.isPhonemic() }
    }

    override fun matches(word: Word): Boolean = members.all { it.matches(word) }

    override fun matches(phoneme: PhonemeIterator) = members.all { it.matches(phoneme) }

    override fun toEditableText(): String = members.joinToString(AND) { it.toEditableText() }

    companion object {
        const val AND = " and "
    }
}

object OtherwiseCondition : RuleCondition() {
    override fun isPhonemic(): Boolean = false
    override fun matches(word: Word): Boolean = true
    override fun matches(phoneme: PhonemeIterator) = true
    override fun toEditableText(): String = OTHERWISE

    const val OTHERWISE = "otherwise"
}

enum class InstructionType(val insnName: String, val takesArgument: Boolean) {
    NoChange("no change", false),
    RemoveLastCharacter("remove last character", false),
    AddSuffix("add suffix", true),
    ChangeSound("new sound is", true)
}

class RuleInstruction(val type: InstructionType, val arg: String) {
    fun apply(word: String): String = when(type) {
        InstructionType.NoChange -> word
        InstructionType.RemoveLastCharacter -> word.substring(0, word.lastIndex)
        InstructionType.AddSuffix -> word + arg
        else -> throw IllegalStateException("Can't apply phoneme instruction to full word")
    }

    fun apply(word: Word, phoneme: PhonemeIterator) {
        when (type) {
            InstructionType.ChangeSound -> phoneme.replace(arg)
            else -> throw IllegalStateException("Can't apply word instruction to individual phoneme")
        }
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
        fun parse(s: String, language: Language): RuleBranch {
            var lines = s.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            val condition = if (lines[0].endsWith(":")) {
                val conditionList = lines[0].removeSuffix(":")
                lines = lines.drop(1)
                RuleCondition.parse(conditionList, language)
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
        if (branches.any { it.condition.isPhonemic() }) {
            val it = PhonemeIterator(word)
            while (true) {
                for (branch in branches) {
                    if (branch.condition.matches(it)) {
                        for (instruction in branch.instructions) {
                            instruction.apply(word, it)
                        }
                        break
                    }
                }
                if (!it.next()) break
            }
            return deriveWord(word, it.result())
        }

        for (branch in branches) {
            if (branch.matches(word)) {
                val text = branch.apply(word)
                return deriveWord(word, text)
            }
        }
        return word
    }

    private fun deriveWord(word: Word, text: String): Word {
        val gloss = word.gloss?.let { baseGloss ->
            applyCategories(baseGloss)
        }
        return Word(-1, text, word.language, gloss, word.pos)
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
        fun parseBranches(s: String, language: Language): List<RuleBranch> {
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
            return branchTexts.map { RuleBranch.parse(it.joinToString("\n"), language) }
        }
    }
}
