package ru.yole.etymograph

class RuleParseException(msg: String): RuntimeException(msg)

fun interface RuleRef {
    fun resolve(): Rule

    companion object {
        fun to(rule: Rule): RuleRef = RuleRef { rule }
    }
}

class RuleParseContext(
    val fromLanguage: Language,
    val toLanguage: Language,
    val ruleRefFactory: (String) -> RuleRef
)

class RuleBranch(val condition: RuleCondition, val instructions: List<RuleInstruction>) {
    fun matches(word: Word) = condition.matches(word)

    fun apply(word: Word): String {
        return instructions.fold(word.text.trimEnd('-')) { s, i -> i.apply(s, word.language) }
    }

    fun toEditableText(): String {
        return condition.toEditableText() + ":\n" +
                instructions.joinToString("\n") { " - " + it.toEditableText() }
    }

    fun toSummaryText(): String {
        return instructions.joinToString("") { it.toSummaryText() }
    }

    companion object {
        fun parse(s: String, context: RuleParseContext): RuleBranch {
            var lines = s.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            val condition = if (lines[0].endsWith(":")) {
                val conditionList = lines[0].removeSuffix(":")
                lines = lines.drop(1)
                RuleCondition.parse(conditionList, context.fromLanguage)
            }
            else {
                OtherwiseCondition
            }
            val instructions = lines.map {
                if (!it.startsWith("-")) {
                    throw RuleParseException("Instructions must start with -")
                }
                RuleInstruction.parse(it.removePrefix("-").trim(), context)
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
    var addedCategories: String?,
    var replacedCategories: String?,
    source: String?,
    notes: String?
) : LangEntity(source, notes) {
    fun matches(word: Word): Boolean {
        return branches.any { it.matches(word) }
    }

    fun apply(word: Word): Word {
        if (branches.any { it.condition.isPhonemic() }) {
            val phonemes = PhonemeIterator(word)
            while (true) {
                applyToPhoneme(phonemes)
                if (!phonemes.advance()) break
            }
            return deriveWord(word, phonemes.result())
        }

        for (branch in branches) {
            if (branch.matches(word)) {
                val text = branch.apply(word)
                return deriveWord(word, text)
            }
        }
        return word
    }

    fun applyToPhoneme(phonemes: PhonemeIterator) {
        for (branch in branches) {
            if (branch.condition.matches(phonemes)) {
                for (instruction in branch.instructions) {
                    instruction.apply(phonemes)
                }
                break
            }
        }
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
        fun parseBranches(s: String, context: RuleParseContext): List<RuleBranch> {
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
            return branchTexts.map { RuleBranch.parse(it.joinToString("\n"), context) }
        }
    }
}
