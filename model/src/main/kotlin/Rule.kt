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

data class ParseCandidate(val text: String, val rules: List<Rule>, val pos: String?, val word: Word?)

class RuleBranch(val condition: RuleCondition, val instructions: List<RuleInstruction>) {
    fun matches(word: Word) = condition.matches(word)

    fun apply(rule: Rule, word: Word, graph: GraphRepository): Word {
        return instructions.apply(rule, word, graph)
    }

    fun reverseApply(word: Word): List<String> {
        var candidates = listOf(word.language.normalizeWord(word.text))
        for (instruction in instructions.reversed()) {
            candidates = candidates.flatMap { instruction.reverseApply(it, word.language) }
            if (candidates.isEmpty()) break
        }
        candidates = candidates.mapNotNull { replaceStarWithConditionText(it) }
        return candidates.filter { condition.matches(word.derive(it)) }
    }

    private fun replaceStarWithConditionText(text: String): String? {
        if (!text.endsWith("*")) {
            return text
        }

        val leafCondition = condition as? LeafRuleCondition ?: return null
        if (leafCondition.type != ConditionType.EndsWith || leafCondition.parameter == null) {
            return null
        }
        val stars = text.takeLastWhile { it == '*' }
        if (leafCondition.parameter.length >= stars.length) {
            return text.removeSuffix(stars) + leafCondition.parameter.substring(leafCondition.parameter.length - stars.length)
        }

        return null
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
                RuleInstruction.parse(it, context)
            }
            return RuleBranch(condition, instructions)
        }
    }
}

class RuleLogic(val preInstructions: List<RuleInstruction>, val branches: List<RuleBranch>)

class Rule(
    id: Int,
    var name: String,
    var fromLanguage: Language,
    var toLanguage: Language,
    var logic: RuleLogic,
    var addedCategories: String?,
    var replacedCategories: String?,
    var fromPOS: String?,
    var toPOS: String?,
    source: List<SourceRef>,
    notes: String?
) : LangEntity(id, source, notes) {
    fun matches(word: Word): Boolean {
        return logic.branches.any { it.matches(word) }
    }

    fun isPhonemic(): Boolean = logic.branches.any { it.condition.isPhonemic() }

    fun apply(word: Word, graph: GraphRepository): Word {
        if (isPhonemic()) {
            val phonemes = PhonemeIterator(word)
            while (true) {
                applyToPhoneme(phonemes)
                if (!phonemes.advance()) break
            }
            return deriveWord(word, phonemes.result(), word.stressedPhonemeIndex, null)
        }

        val preWord = if (logic.preInstructions.isEmpty()) word else logic.preInstructions.apply(this, word, graph)
        for (branch in logic.branches) {
            if (branch.matches(preWord)) {
                val resultWord = branch.apply(this, preWord, graph)
                return deriveWord(word, resultWord.text, resultWord.stressedPhonemeIndex, resultWord.segments)
            }
        }
        return word
    }

    fun reverseApply(word: Word): List<String> {
        if (logic.branches.isEmpty()) {
            return listOf(word.text)
        }
        return logic.branches.flatMap { it.reverseApply(word) }
    }

    fun applyToPhoneme(phonemes: PhonemeIterator) {
        for (branch in logic.branches) {
            if (branch.condition.matches(phonemes)) {
                for (instruction in branch.instructions) {
                    instruction.apply(phonemes)
                }
                break
            }
        }
    }

    fun reverseApplyToPhoneme(phonemes: PhonemeIterator): Boolean {
        for (branch in logic.branches) {
            val instruction = branch.instructions.singleOrNull()
            if (instruction?.type == InstructionType.ChangeSound) {
                if (instruction.arg == phonemes.current) {
                    val condition = branch.condition as? LeafRuleCondition ?: return false
                    if (condition.type == ConditionType.PhonemeMatches && condition.parameter != null) {
                        phonemes.replace(condition.parameter)
                        break
                    }
                    else {
                        return false
                    }
                }
            }
            else {
                return false
            }
        }
        return true
    }

    private fun deriveWord(word: Word, text: String, stressIndex: Int, segments: List<WordSegment>?): Word {
        val gloss = word.gloss?.let { baseGloss ->
            applyCategories(baseGloss, segments?.any { it.sourceRule == this} == true)
        }
        return Word(-1, text, word.language, gloss, word.pos).also {
            it.stressedPhonemeIndex = stressIndex
            val sourceSegments = word.segments
            if (segments != null) {
                it.segments = segments
            }
            else if (sourceSegments != null) {
                it.segments = sourceSegments
            }
        }
    }

    fun applyCategories(baseGloss: String, fromSegment: Boolean = false): String {
        val replacedGloss = replacedCategories?.let { baseGloss.replace(it, "") } ?: baseGloss
        addedCategories?.let {
            return if (fromSegment) replacedGloss + "-" + it.removePrefix(".") else replacedGloss + it 
        }
        return replacedGloss
    }

    fun toEditableText(): String {
        if (isUnconditional()) {
            return logic.branches[0].instructions.joinToString("\n") { " - " + it.toEditableText() }
        }
        return logic.preInstructions.joinToString("") { " - " + it.toEditableText() + "\n" } +
                logic.branches.joinToString("\n\n") { it.toEditableText() }
    }

    fun isUnconditional() =
        logic.branches.size == 1 && logic.branches[0].condition is OtherwiseCondition && logic.preInstructions.isEmpty()

    fun toSummaryText(): String {
        val summaries = logic.branches.map { it.toSummaryText() }.filter { it.isNotEmpty() }.toSet()
        return summaries.joinToString("/")
    }

    companion object {
        fun parseBranches(s: String, context: RuleParseContext): RuleLogic {
            if (s.isBlank()) return RuleLogic(emptyList(), emptyList())
            val lines = s.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            val branchTexts = mutableListOf<List<String>>()
            var currentBranchText = mutableListOf<String>()
            val preInstructionsText = mutableListOf<String>()
            for (l in lines) {
                if (l.endsWith(':')) {
                    if (branchTexts.isEmpty()) {
                        preInstructionsText.addAll(currentBranchText)
                    }
                    currentBranchText = mutableListOf()
                    branchTexts.add(currentBranchText)
                }
                currentBranchText.add(l)
            }
            if (branchTexts.isEmpty()) {   // rule with no conditions
                branchTexts.add(currentBranchText)
            }
            val preInstructions = preInstructionsText.map { RuleInstruction.parse(it, context) }
            val branches = branchTexts.map { RuleBranch.parse(it.joinToString("\n"), context) }
            return RuleLogic(preInstructions, branches)
        }
    }
}
