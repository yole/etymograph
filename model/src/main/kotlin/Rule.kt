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

class RuleTraceData(val matchedBranch: RuleBranch)

class RuleTrace {
    val traceData = mutableMapOf<Pair<Int, Int>, RuleTraceData>()

    fun logMatchedBranch(rule: Rule, word: Word, branch: RuleBranch) {
        traceData[rule.id to word.id] = RuleTraceData(branch)
    }

    fun findMatchedBranch(rule: Rule, word: Word): RuleBranch? {
        return traceData[rule.id to word.id]?.matchedBranch
    }
}

data class ParseCandidate(val text: String, val rules: List<Rule>, val pos: String?, val word: Word?) {
    val categories: String
        get() = rules.fold("") { t, rule -> t + rule.addedCategories.orEmpty() }
}

class RuleBranch(val condition: RuleCondition, val instructions: List<RuleInstruction>) {
    fun matches(word: Word, graph: GraphRepository) = condition.matches(word, graph)

    fun apply(rule: Rule, word: Word, graph: GraphRepository): Word {
        return instructions.apply(rule, this, word, graph)
    }

    fun reverseApply(rule: Rule, word: Word, graph: GraphRepository): List<String> {
        var candidates = listOf(word.language.normalizeWord(word.text))
        candidates = RuleInstruction.reverseApplyInstructions(candidates, rule, word, instructions, graph)
        candidates = candidates.mapNotNull { replaceStarWithConditionText(it) }
        return candidates.filter { condition.matches(word.derive(it, newClasses = listOf("*")), graph) }
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

    fun toSummaryText(phonemic: Boolean): String? {
        if (phonemic) {
            val insn = instructions.singleOrNull() ?: return null
            return insn.toSummaryText(condition)
        }
        val summaries = instructions.map { it.toSummaryText(condition) ?: return null }
        return summaries.joinToString("")
    }

    fun refersToPhoneme(phoneme: Phoneme): Boolean {
        return condition.refersToPhoneme(phoneme) || instructions.any { it.refersToPhoneme(phoneme) }
    }

    companion object {
        fun parse(s: String, context: RuleParseContext): RuleBranch {
            var lines = s.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            val condition = if (lines[0].endsWith(":")) {
                val buffer = ParseBuffer(lines[0])
                lines = lines.drop(1)
                RuleCondition.parse(buffer, context.fromLanguage).also {
                    if (!buffer.consume(":")) {
                        buffer.fail("':' expected after condition")
                    }
                }
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
    fun isPhonemic(): Boolean = logic.branches.any { it.condition.isPhonemic() }

    fun apply(word: Word, graph: GraphRepository, trace: RuleTrace? = null): Word {
        if (isPhonemic()) {
            val phonemic = word.asPhonemic()
            val phonemes = PhonemeIterator(phonemic, graph)
            var anyChanges = false
            while (true) {
                anyChanges = anyChanges or applyToPhoneme(phonemic, phonemes, graph, trace)
                if (!phonemes.advance()) break
            }
            return if (anyChanges)
                deriveWord(phonemic, phonemes.result(), toLanguage, -1, null, word.classes)
            else
                word
        }

        val preWord = if (logic.preInstructions.isEmpty()) word else logic.preInstructions.apply(this, null, word, graph)
        for (branch in logic.branches) {
            if (branch.matches(preWord, graph)) {
                trace?.logMatchedBranch(this, word, branch)
                val resultWord = branch.apply(this, preWord, graph)
                return deriveWord(word, resultWord.text, toLanguage, resultWord.stressedPhonemeIndex, resultWord.segments, resultWord.classes)
            }
        }
        return Word(-1, "?", word.language)
    }

    fun reverseApply(word: Word, graph: GraphRepository): List<String> {
        if (logic.branches.isEmpty()) {
            return listOf(word.text)
        }
        return logic.branches.flatMap {
            val candidates = it.reverseApply(this, word, graph)
            RuleInstruction.reverseApplyInstructions(candidates, this, word, logic.preInstructions, graph)
        }
    }

    fun applyToPhoneme(word: Word, phonemes: PhonemeIterator, graph: GraphRepository, trace: RuleTrace? = null): Boolean {
        for (branch in logic.branches) {
            if (branch.condition.matches(word, phonemes, graph)) {
                trace?.logMatchedBranch(this, word, branch)
                for (instruction in branch.instructions) {
                    instruction.apply(word, phonemes, graph)
                }
                return true
            }
        }
        return false
    }

    fun reverseApplyToPhoneme(phonemes: PhonemeIterator): List<String> {
        for (branch in logic.branches) {
            val instruction = branch.instructions.singleOrNull() ?: return emptyList()
            val result = instruction.reverseApplyToPhoneme(phonemes.clone(), branch.condition) ?: return emptyList()
            if (result.isNotEmpty()) return result
        }
        return listOf(phonemes.result())
    }

    private fun deriveWord(word: Word, text: String, language: Language, stressIndex: Int, segments: List<WordSegment>?,
                           classes: List<String>): Word {
        val gloss = word.glossOrNP()?.let { baseGloss ->
            applyCategories(baseGloss, segments?.any { it.sourceRule == this} == true)
        }
        return Word(-1, text, language, gloss, pos = word.pos, classes = classes).also {
            it.stressedPhonemeIndex = stressIndex
            val sourceSegments = word.segments
            if (segments != null) {
                it.segments = segments
            }
            else if (sourceSegments != null) {
                it.segments = sourceSegments
            }
            it.isPhonemic = word.isPhonemic
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
        val summaries = logic.branches.map { it.toSummaryText(isPhonemic()) }
        if (summaries.any { it == null }) return ""
        val filteredSummaries = summaries.filter { !it.isNullOrEmpty() }
        if (isPhonemic()) {
            return filteredSummaries.joinToString(", ")
        }
        return filteredSummaries.toSet().joinToString("/")
    }

    fun addedGrammaticalCategories(): List<WordCategory> {
        return addedCategories?.let { cv -> parseCategoryValues(fromLanguage, cv).mapNotNull { it.first } } ?: emptyList()
    }

    fun refersToPhoneme(phoneme: Phoneme): Boolean {
        return logic.branches.any { branch -> branch.refersToPhoneme(phoneme) }
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

data class RuleSequenceStepRef(
    val ruleId: Int,
    val optional: Boolean
)

data class RuleSequenceStep(
    val rule: LangEntity,
    val optional: Boolean
)

class RuleSequence(
    id: Int, var name: String,
    var fromLanguage: Language,
    var toLanguage: Language,
    var steps: List<RuleSequenceStepRef>,
    source: List<SourceRef>, notes: String?
) : LangEntity(id, source, notes) {

    fun resolveSteps(graph: GraphRepository): List<RuleSequenceStep> {
        val result = mutableListOf<RuleSequenceStep>()
        for ((ruleId, optional) in steps) {
            val entity = graph.langEntityById(ruleId)
            if (entity is Rule) {
                result.add(RuleSequenceStep(entity, optional))
            }
            else if (entity is RuleSequence) {
                result.addAll(entity.resolveSteps(graph))
            }
        }
        return result
    }

    fun resolveRules(graph: GraphRepository): List<Rule> {
        return resolveSteps(graph).map { it.rule as Rule }
    }

    fun resolveVariants(graph: GraphRepository): List<List<Rule>> {
        val steps = resolveSteps(graph)
        val maxOptionMask = 1 shl steps.count { it.optional }
        if (maxOptionMask == 1) {
            return listOf(steps.map { it.rule as Rule })
        }
        return (0 until maxOptionMask).map { mask ->
            steps.filterOptionsByMask(mask)
        }
    }

    private fun List<RuleSequenceStep>.filterOptionsByMask(mask: Int): List<Rule> {
        val result = mutableListOf<Rule>()
        var stepMask = 1
        for (step in this) {
            if (step.optional) {
                // mask 0 should mean 'all optional rules are applied'
                if (stepMask and mask == 0) {
                    result.add(step.rule as Rule)
                }
                stepMask = stepMask shl 1
            }
            else {
                result.add(step.rule as Rule)
            }
        }
        return result
    }
}

fun parseCategoryValues(language: Language, categoryValues: String): List<Pair<WordCategory?, WordCategoryValue?>> {
    return categoryValues.split('.').filter { it.isNotEmpty() }.flatMap {
        language.findGrammaticalCategory(it)?.let { listOf(it) }
            ?: language.findNumberPersonCategories(it)
            ?: listOf(null to null)
    }
}
