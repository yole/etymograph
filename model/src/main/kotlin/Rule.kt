package ru.yole.etymograph

import ru.yole.etymograph.RuleBranch.Companion.parseComment
import java.util.Locale
import kotlin.collections.fold

class RuleParseException(msg: String): RuntimeException(msg)

fun interface RuleRef {
    fun resolve(): Rule

    companion object {
        fun to(rule: Rule): RuleRef = RuleRef { rule }
    }
}

class RuleParseContext(
    val repo: GraphRepository,
    val fromLanguage: Language,
    val toLanguage: Language,
    val ruleRefFactory: (String) -> RuleRef
)

class RuleTraceData(
    val matchedBranches: MutableSet<RuleBranch> = mutableSetOf(),
    val matchedInstructions: MutableSet<RuleInstruction> = mutableSetOf()
)

class RuleTrace {
    private val traceData = mutableMapOf<Pair<Int, Int>, RuleTraceData>()
    private val log = StringBuilder()

    fun logRule(rule: Rule, word: Word) {
        log.append("Applying rule ${rule.name} to ${word.text} (language ${word.language.shortName}, phonemic ${word.isPhonemic}, segments ${word.segments}, stress ${word.stressedPhonemeIndex})\n")
    }

    fun logRuleResult(rule: Rule, word: Word) {
        log.append("Applied rule ${rule.name}, result is ${word.text} (segments ${word.segments}, stress ${word.stressedPhonemeIndex})\n")
    }

    fun logMatchedBranch(rule: Rule, word: Word, phoneme: String?, branch: RuleBranch) {
        val data = traceData.getOrPut(rule.id to word.id) { RuleTraceData() }
        data.matchedBranches.add(branch)
        val phonemeTrace = phoneme?.let { " for $it" } ?: ""
        log.append("Branch '${branch.condition.toEditableText()}' matched$phonemeTrace\n")
    }

    fun logMatchedInstruction(rule: Rule, word: Word, instruction: RuleInstruction) {
        val data = traceData.getOrPut(rule.id to word.id) { RuleTraceData() }
        data.matchedInstructions.add(instruction)
    }

    fun logUnmatchedBranch(rule: Rule, word: Word, phoneme: String?, branch: RuleBranch) {
        val phonemeTrace = phoneme?.let { " for $it" } ?: ""
        log.append("Branch '${branch.condition.toEditableText()}' not matched$phonemeTrace\n")
    }

    fun logNodeMatch(it: PhonemeIterator, node: SpeNode, result: Boolean) {
        log.append("Node $node match result at ${it.current}: $result\n")
    }

    fun logCondition(ruleCondition: RuleCondition, result: Boolean) {
        if (ruleCondition !is CompositeRuleCondition) {
            log.append("${ruleCondition.toEditableText()} -> $result\n")
        }
    }

    fun logInstruction(callback: () -> String) {
        log.append(callback()).append("\n")
    }

    fun logReverseApplyCandidates(condition: RuleCondition, candidates: List<String>) {
        log.append("${condition.toEditableText()} candidates: [${candidates.joinToString(", ")}]\n")
    }

    fun logReverseApplyInstruction(graph: GraphRepository, instruction: RuleInstruction, base: String, candidates: List<String>) {
        log.append("${instruction.toEditableText(graph)}: $base -> [${candidates.joinToString(", ")}]\n")
    }

    fun findMatchedBranches(rule: Rule, word: Word): Set<RuleBranch> {
        return traceData[rule.id to word.id]?.matchedBranches ?: emptySet()
    }

    fun findMatchedInstructions(rule: Rule, word: Word): Set<RuleInstruction> {
        return traceData[rule.id to word.id]?.matchedInstructions ?: emptySet()
    }

    fun log(): String {
        return log.toString()
    }
}

data class ParseCandidate(val text: String, val rules: List<Rule>, val pos: String?, val word: Word?) {
    val categories: String
        get() = rules.fold("") { t, rule -> t + rule.addedCategories.orEmpty() }
}

class LineBuffer(s: String) {
    private val lines: List<String> = s.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    private var index = 0

    fun peek(): String? {
        return lines.elementAtOrNull(index)
    }

    fun next(): String? {
        return lines.elementAtOrNull(index++)
    }

    fun nextIf(condition: (String) -> Boolean): String? {
        if (index < lines.size && condition(lines[index])) {
            return lines[index++]
        }
        return null
    }

    fun nextWhile(condition: (String) -> Boolean, callback: (String) -> Unit) {
        while (index < lines.size && condition(lines[index])) {
            callback(lines[index++])
        }
    }

    fun mark(): Int = index

    fun rollbackTo(mark: Int) {
        index = mark
    }
}

class RuleApplyContext(
    val rule: Rule,
    val branch: RuleBranch?,
    val graph: GraphRepository,
    val originalWord: Word? = null,
    val trace: RuleTrace? = null
)

class RuleBranch(val condition: RuleCondition, val instructions: List<RuleInstruction>, val comment: String? = null) {
    fun matches(word: Word, graph: GraphRepository, trace: RuleTrace? = null): Boolean {
        val result = condition.matches(word, graph, trace)
        trace?.logCondition(condition, result)
        return result
    }

    fun apply(rule: Rule, word: Word, originalWord: Word, graph: GraphRepository, trace: RuleTrace? = null): Word {
        return instructions.apply(word, RuleApplyContext(rule, this, graph, originalWord, trace))
    }

    fun reverseApply(rule: Rule, word: Word, graph: GraphRepository, trace: RuleTrace? = null): List<String> {
        var candidates = listOf(word.language.normalizeWord(word.text))
        candidates = RuleInstruction.reverseApplyInstructions(candidates, rule, word, instructions, graph, trace)
        trace?.logReverseApplyCandidates(condition, candidates)
        candidates = candidates.mapNotNull { replaceStarWithConditionText(it) }
        return candidates.filter { condition.matches(word.derive(it, newClasses = listOf("*")), graph, trace) }
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

    fun toEditableText(graph: GraphRepository): String {
        return commentToString(comment) + condition.toEditableText() + ":\n" +
                instructions.joinToString("\n") {
                    commentToString(it.comment) + (if (it is SpeInstruction) "* " else " - ") + it.toEditableText(graph)
                }
    }

    fun toSummaryText(graph: GraphRepository): String? {
        return instructions.map { it.toSummaryText(graph) ?: return null }.joinToString("")
    }

    fun refersToPhoneme(phoneme: Phoneme): Boolean {
        return condition.refersToPhoneme(phoneme) || instructions.any { it.refersToPhoneme(phoneme) }
    }

    companion object {
        const val COMMENT_DELIMITER = "$"

        fun parse(lines: LineBuffer, context: RuleParseContext): RuleBranch {
            val comment = parseComment(lines)

            val condition = lines.nextIf { it.endsWith(":") }?.let { line ->
                val buffer = ParseBuffer(line)
                RuleCondition.parse(buffer, context.fromLanguage).also {
                    if (!buffer.consume(":")) {
                        buffer.fail("':' expected after condition")
                    }
                }
            } ?: OtherwiseCondition

            val instructions = mutableListOf<RuleInstruction>()
            while (true) {
                val mark = lines.mark()
                val insnComment = parseComment(lines)
                val line = lines.nextIf { !it.startsWith("=" )&& !it.endsWith(":") }
                if (line == null) {
                    lines.rollbackTo(mark)
                    break
                }
                instructions.add(RuleInstruction.parse(line, context, comment = insnComment))
            }
            if (instructions.isEmpty()) {
                throw RuleParseException("Rule must contain at least one instruction")
            }
            return RuleBranch(condition, instructions, comment)
        }

        fun parseComment(lines: LineBuffer): String? {
            val comment = mutableListOf<String>()
            lines.nextWhile({ line -> line.startsWith(COMMENT_DELIMITER) }) { line ->
                comment.add(line.removePrefix(COMMENT_DELIMITER).trim())
            }
            return comment.takeIf { it.isNotEmpty() }?.joinToString("\n")
        }

        fun commentToString(comment: String?) = (comment?.split('\n')?.joinToString("") { "$COMMENT_DELIMITER $it\n" }) ?: ""
    }
}

sealed class RuleLogic {
    abstract val postInstructions: List<RuleInstruction>

    abstract fun apply(
        word: Word,
        rule: Rule,
        graph: GraphRepository,
        trace: RuleTrace? = null,
        normalizeSegments: Boolean = true,
        applyPrePostRules: Boolean = true,
        preserveId: Boolean = false
    ): Word

    abstract fun reverseApply(word: Word, rule: Rule, graph: GraphRepository, trace: RuleTrace? = null): List<String>
    abstract fun toEditableText(graph: GraphRepository): String
    abstract fun toSummaryText(graph: GraphRepository): String
    open fun findConditionForInstruction(instruction: RuleInstruction): RuleCondition? = null
    open fun referencedRules(): Set<Rule> = emptySet()
    abstract fun refersToPhoneme(phoneme: Phoneme): Boolean

    protected fun deriveResult(
        resultWord: Word,
        rule: Rule,
        graph: GraphRepository,
        word: Word,
        preserveId: Boolean,
        normalizeSegments: Boolean,
        trace: RuleTrace?
    ): Word {
        val stressIndex = resultWord.remapViaCharacterIndex(resultWord.stressedPhonemeIndex, rule.toLanguage, graph)
        val resultText = if (word.text.first().isUpperCase()) {
            resultWord.text.replaceFirstChar { it.titlecase(Locale.FRANCE) }
        } else {
            resultWord.text
        }
        val gloss = word.glossOrNP()?.let { baseGloss ->
            rule.applyCategories(baseGloss, resultWord.segments?.any { it.sourceRule == rule } == true)
        }
        val result = word.derive(
            resultText,
            id = if (preserveId) word.id else -1,
            newLanguage = rule.toLanguage,
            newGloss = gloss,
            phonemic = resultWord.isPhonemic,
            stressIndex = stressIndex,
            segments = if (normalizeSegments) Word.normalizeSegments(resultWord.segments) else resultWord.segments,
            newClasses = resultWord.classes
        )
        trace?.logRuleResult(rule, result)
        return result
    }
}

class MorphoRuleLogic(
    val preInstructions: List<RuleInstruction>,
    val branches: List<RuleBranch>,
    override val postInstructions: List<RuleInstruction>
): RuleLogic() {
    fun isUnconditional() =
        branches.size == 1 && branches[0].condition is OtherwiseCondition && preInstructions.isEmpty()

    override fun apply(
        word: Word,
        rule: Rule,
        graph: GraphRepository,
        trace: RuleTrace?,
        normalizeSegments: Boolean,
        applyPrePostRules: Boolean,
        preserveId: Boolean
    ): Word {
        val paradigm = graph.paradigmForRule(rule)
        val paraPreWord = if (applyPrePostRules) (paradigm?.preRule?.apply(word, graph, trace, preserveId = true) ?: word) else word
        val prePostApplyContext = RuleApplyContext(rule, null, graph, word, trace)
        val preWord = preInstructions.apply(paraPreWord, prePostApplyContext)
        for (branch in branches) {
            if (branch.matches(preWord, graph, trace)) {
                trace?.logMatchedBranch(rule, word, null, branch)
                var resultWord = branch.apply(rule, preWord, word, graph, trace)
                if (!rule.isSPE()) {
                    resultWord = postInstructions.apply(resultWord, prePostApplyContext)
                }
                if (applyPrePostRules) {
                    resultWord = paradigm?.postRule?.apply(resultWord, graph, trace, preserveId = true) ?: resultWord
                }

                return deriveResult(resultWord, rule, graph, word, preserveId, normalizeSegments, trace)
            }
            else {
                trace?.logUnmatchedBranch(rule, word, null, branch)
            }
        }
        return Word(-1, "?", word.language)
    }

    override fun reverseApply(word: Word, rule: Rule, graph: GraphRepository, trace: RuleTrace?): List<String> {
        if (branches.isEmpty()) {
            return listOf(word.text)
        }
        return branches.flatMap {
            val candidates = it.reverseApply(rule, word, graph, trace)
            RuleInstruction.reverseApplyInstructions(candidates, rule, word, preInstructions, graph)
        }
    }

    override fun toEditableText(graph: GraphRepository): String {
        if (isUnconditional()) {
            val branch = branches[0]
            val commentString = RuleBranch.commentToString(branch.comment)
            return commentString + branch.instructions.joinToString("\n") {
                RuleBranch.commentToString(it.comment) + " - " + it.toEditableText(graph)
            }
        }
        return preInstructions.joinToString("") { " - " + it.toEditableText(graph) + "\n" } +
                branches.joinToString("\n\n") { it.toEditableText(graph) } +
                (if (postInstructions.isNotEmpty()) "\n\n" else "") +
                postInstructions.joinToString("\n") { " = " + it.toEditableText(graph) }
    }

    override fun toSummaryText(graph: GraphRepository): String {
        val summaries = branches.map { it.toSummaryText(graph) }
        if (summaries.any { it == null }) return ""
        val filteredSummaries = summaries.filter { !it.isNullOrEmpty() }
        return filteredSummaries.toSet().joinToString("/")
    }

    override fun refersToPhoneme(phoneme: Phoneme): Boolean {
        return preInstructions.any { it.refersToPhoneme(phoneme) } ||
                branches.any { branch -> branch.refersToPhoneme(phoneme) } ||
                postInstructions.any { it.refersToPhoneme(phoneme) }
    }

    override fun referencedRules(): Set<Rule> {
        return (preInstructions.flatMap { it.referencedRules() } +
                branches.flatMap {
                        branch -> branch.instructions.flatMap { it.referencedRules() }
                } + postInstructions.flatMap { it.referencedRules() }).toSet()
    }

    override fun findConditionForInstruction(instruction: RuleInstruction): RuleCondition? {
        return branches.find { instruction in it.instructions }?.condition
    }

    companion object {
        fun empty(): RuleLogic = MorphoRuleLogic(emptyList(), emptyList(), emptyList())

        fun parse(text: String, context: RuleParseContext): MorphoRuleLogic {
            val lines = LineBuffer(text)
            val preInstructions = mutableListOf<RuleInstruction>()
            val postInstructions = mutableListOf<RuleInstruction>()
            val branches = mutableListOf<RuleBranch>()

            lines.nextWhile({ line -> line.startsWith("-") }) { line ->
                preInstructions.add(RuleInstruction.parse(line, context, comment = null))
            }

            while (true) {
                val nextLine = lines.peek()
                if (nextLine == null || nextLine.startsWith("=")) {
                    break
                }
                branches.add(RuleBranch.parse(lines, context))
            }
            while (true) {
                val next = lines.next() ?: break
                postInstructions.add(RuleInstruction.parse(next, context, "=", null))
            }
            if (branches.isEmpty() && preInstructions.isNotEmpty()) {
                return MorphoRuleLogic(
                    emptyList(),
                    listOf(RuleBranch(OtherwiseCondition, preInstructions, null)),
                    postInstructions
                )
            }

            return MorphoRuleLogic(preInstructions, branches, postInstructions)
        }
    }
}

class SpeRuleLogic(
    val instructions: List<SpeInstruction>,
    override val postInstructions: List<RuleInstruction>
) : RuleLogic() {
    override fun apply(
        word: Word,
        rule: Rule,
        graph: GraphRepository,
        trace: RuleTrace?,
        normalizeSegments: Boolean,
        applyPrePostRules: Boolean,
        preserveId: Boolean
    ): Word {
        val normalizedWord = word.derive(word.text.trimEnd('-'), id = word.id, phonemic = word.isPhonemic)
        val context = RuleApplyContext(rule, null, graph, word, trace)
        for (instruction in instructions) {
            val resultWord = instruction.apply(normalizedWord, context, postInstructions)
            if (resultWord.text != normalizedWord.text) {
                return deriveResult(resultWord, rule, graph, word, preserveId, normalizeSegments, trace)
            }
        }
        return normalizedWord
    }

    override fun reverseApply(word: Word, rule: Rule, graph: GraphRepository, trace: RuleTrace?): List<String> {
        return emptyList()
    }

    fun applyToPhoneme(word: Word, phonemes: PhonemeIterator, graph: GraphRepository, trace: RuleTrace? = null): Boolean {
        for (instruction in instructions) {
            instruction.apply(word, phonemes, graph, trace)
        }
        for (postInstruction in postInstructions) {
            postInstruction.apply(word, phonemes, graph, trace)
        }
        return true
    }

    fun reverseApplyToPhoneme(phonemes: PhonemeIterator): List<String> {
        /*
        for (branch in branches) {
            val instruction = branch.instructions.singleOrNull() ?: return emptyList()
            val result = instruction.reverseApplyToPhoneme(phonemes.clone(), branch.condition) ?: return emptyList()
            if (result.isNotEmpty()) return result
        }
        return listOf(phonemes.result())
         */
        return emptyList()
    }

    override fun toEditableText(graph: GraphRepository): String {
        return instructions.joinToString("\n") {
            RuleBranch.commentToString(it.comment) + "* " + it.toEditableText(graph)
        } + postInstructions.joinToString("\n") {
            RuleBranch.commentToString(it.comment) + "= " + it.toEditableText(graph)
        }
    }

    override fun toSummaryText(graph: GraphRepository): String {
        val summaries = instructions.map { it.toSummaryText(graph)  }
        return summaries.joinToString(", ")
    }

    override fun refersToPhoneme(phoneme: Phoneme): Boolean {
        return instructions.any { it.refersToPhoneme(phoneme) } || postInstructions.any { it.refersToPhoneme(phoneme) }
    }

    companion object {
        fun parse(lines: LineBuffer, context: RuleParseContext): RuleLogic {
            val instructions = mutableListOf<SpeInstruction>()
            val postInstructions = mutableListOf<RuleInstruction>()
            while (true) {
                val comment = parseComment(lines)
                val nextLine = lines.peek()
                if (nextLine == null || nextLine.startsWith("=")) {
                    break
                }
                instructions.add(SpeInstruction.parse(lines.next()!!, context, comment))
            }
            while (true) {
                val next = lines.next() ?: break
                postInstructions.add(RuleInstruction.parse(next, context, "=", null))
            }
            return SpeRuleLogic(instructions, postInstructions)
        }
    }
}

class Rule(
    id: Int,
    var name: String,
    var fromLanguage: Language,
    var toLanguage: Language,
    var logic: RuleLogic,
    var addedCategories: String? = null,
    var replacedCategories: String? = null,
    var fromPOS: List<String> = emptyList(),
    var toPOS: String? = null,
    source: List<SourceRef> = emptyList(),
    notes: String? = null
) : LangEntity(id, source, notes) {
    fun isSPE(): Boolean = logic is SpeRuleLogic

    fun apply(word: Word, graph: GraphRepository, trace: RuleTrace? = null,
              normalizeSegments: Boolean = true,
              applyPrePostRules: Boolean = true,
              preserveId: Boolean = false): Word {
        trace?.logRule(this, word)

        val compound = graph.findCompoundsByCompoundWord(word).singleOrNull()
        if (compound != null && compound.headIndex == compound.components.size - 1) {
            val headWord = compound.components.last()
            if (word.text.endsWith(headWord.text)) {
                val headWordForm = graph.getLinksTo(headWord).find { it.rules == listOf(this) }?.fromEntity as? Word
                if (headWordForm != null) {
                    val derivedForm = word.text.substring(0, word.text.length-headWord.text.length) + headWordForm.text
                    val gloss = word.glossOrNP()?.let { baseGloss -> applyCategories(baseGloss) }
                    return word.derive(
                        derivedForm,
                        id = if (preserveId) word.id else -1,
                        newLanguage = toLanguage,
                        newGloss = gloss, phonemic = word.isPhonemic,
                        newClasses = headWordForm.classes
                    )
                }
            }
        }

        return logic.apply(word, this, graph, trace, normalizeSegments, applyPrePostRules, preserveId)
    }

    fun reverseApply(word: Word, graph: GraphRepository, trace: RuleTrace? = null): List<String>  =
        logic.reverseApply(word, this, graph, trace)

    fun applyCategories(baseGloss: String, fromSegment: Boolean = false): String {
        val replacedGloss = replacedCategories?.let { baseGloss.replace(it, "") } ?: baseGloss
        addedCategories?.let {
            return if (fromSegment) replacedGloss + "-" + it.removePrefix(".") else replacedGloss + it 
        }
        return replacedGloss
    }

    fun toEditableText(graph: GraphRepository): String = logic.toEditableText(graph)
    fun toSummaryText(graph: GraphRepository): String = logic.toSummaryText(graph)

    fun addedGrammaticalCategories(): List<WordCategory> {
        return addedCategories?.let { cv -> parseCategoryValues(fromLanguage, cv).mapNotNull { it?.category } } ?: emptyList()
    }

    data class RuleChangeResult(val word: Word, val oldResult: String, val newResult: String)

    fun previewChanges(graph: GraphRepository, newText: String): List<RuleChangeResult> {
        val newLogic = parseLogic(newText, RuleParseContext(graph, fromLanguage, toLanguage,) {
            RuleRef.to(graph.ruleByName(it) ?: throw RuleParseException("No rule with name '$it'"))
        })
        val newRule = Rule(-1, name, fromLanguage, toLanguage, newLogic)
        val results = mutableListOf<RuleChangeResult>()
        for (word in graph.allWords(fromLanguage)) {
            if (word.pos in fromPOS) {
                val oldResult = apply(word, graph)
                val newResult = newRule.apply(word, graph)
                if (oldResult.text != newResult.text) {
                    results.add(RuleChangeResult(word, oldResult.text, newResult.text))
                }
            }
        }

        return results
    }

    companion object {
        fun parseLogic(s: String, context: RuleParseContext): RuleLogic {
            if (s.isBlank()) return MorphoRuleLogic.empty()

            val lines = LineBuffer(s)
            parseComment(lines)
            if (lines.peek()?.startsWith("*") == true) {
                lines.rollbackTo(0)
                return SpeRuleLogic.parse(lines, context)
            }
            return MorphoRuleLogic.parse(s, context)
        }
    }
}

data class RuleSequenceStepRef(
    val ruleId: Int,
    val alternativeRuleId: Int? = null,
    val optional: Boolean = false,
    val dispreferred: Boolean = false
)

data class RuleSequenceStep(
    val rule: LangEntity,
    val alternative: LangEntity?,
    val optional: Boolean,
    val dispreferred: Boolean
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
        for ((ruleId, alternativeRuleId, optional, dispreferred) in steps) {
            val entity = graph.langEntityById(ruleId)
            if (entity is Rule) {
                val alternative = alternativeRuleId?.let { graph.langEntityById(it) as Rule }
                result.add(RuleSequenceStep(entity, alternative, optional, dispreferred))
            }
            else if (entity is RuleSequence) {
                result.addAll(entity.resolveSteps(graph))
            }
        }
        return result
    }

    fun previousRule(graph: GraphRepository, rule: Rule): Rule? {
        val rules = resolveRules(graph)
        val index = rules.indexOf(rule)
        return if (index <= 0) null else rules[index-1]
    }

    fun nextRule(graph: GraphRepository, rule: Rule): Rule? {
        val rules = resolveRules(graph)
        val index = rules.indexOf(rule)
        return if (index < 0 || index == rules.size - 1) null else rules[index+1]
    }

    fun resolveRules(graph: GraphRepository): List<Rule> {
        return resolveSteps(graph).filter { !it.dispreferred }.map { it.rule as Rule }
    }
}

fun parseCategoryValues(language: Language, categoryValues: String): List<WordCategoryWithValue?> {
    return categoryValues.split('.').filter { it.isNotEmpty() }.flatMap {
        language.findGrammaticalCategory(it)?.let { listOf(it) }
            ?: language.findNumberPersonCategories(it)
            ?: listOf(null)
    }
}
