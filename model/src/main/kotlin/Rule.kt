package ru.yole.etymograph

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

class RuleBranch(val condition: RuleCondition, val instructions: List<RuleInstruction>, val comment: String? = null) {
    fun matches(word: Word, graph: GraphRepository, trace: RuleTrace? = null): Boolean {
        val result = condition.matches(word, graph, trace)
        trace?.logCondition(condition, result)
        return result
    }

    fun apply(rule: Rule, word: Word, graph: GraphRepository, trace: RuleTrace? = null): Word {
        return instructions.apply(rule, this, word, graph, trace)
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

    fun toSummaryText(graph: GraphRepository, phonemic: Boolean): String? {
        if (phonemic) {
            val insn = instructions.singleOrNull() ?: return null
            return insn.toSummaryText(graph, condition)
        }
        val summaries = instructions.map { it.toSummaryText(graph, condition) ?: return null }
        if (instructions.any { it is SpeInstruction }) {
            return summaries.joinToString(", ")
        }
        return summaries.joinToString("")
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

class RuleLogic(
    val preInstructions: List<RuleInstruction>,
    val branches: List<RuleBranch>,
    val postInstructions: List<RuleInstruction>
) {
    companion object {
        fun empty(): RuleLogic = RuleLogic(emptyList(), emptyList(), emptyList())
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
    fun isPhonemic(): Boolean = logic.branches.any { it.condition.isPhonemic() }
    fun isSPE(): Boolean = logic.branches.any { it.instructions.any { i -> i.type == InstructionType.Spe } }

    fun apply(word: Word, graph: GraphRepository, trace: RuleTrace? = null,
              normalizeSegments: Boolean = true,
              applyPrePostRules: Boolean = true,
              preserveId: Boolean = false): Word {
        trace?.logRule(this, word)
        if (isPhonemic()) {
            val phonemic = word.asPhonemic()
            val phonemes = PhonemeIterator(phonemic, graph)
            var anyChanges = false
            while (true) {
                anyChanges = anyChanges or applyToPhoneme(phonemic, phonemes, graph, trace)
                if (!phonemes.advance()) break
            }
            val result = if (anyChanges) {
                val stress = if (word.explicitStress)
                    phonemes.mapIndex(word.stressedPhonemeIndex)
                else
                    null

                val segments = remapSegments(phonemes, word.segments)
                deriveWord(phonemic, phonemes.result(), toLanguage, true, -1, segments, word.classes, stress)
            }
            else
                word
            trace?.logRuleResult(this, result)
            return result
        }

        val compound = graph.findCompoundsByCompoundWord(word).singleOrNull()
        if (compound != null && compound.headIndex == compound.components.size - 1) {
            val headWord = compound.components.last()
            if (word.text.endsWith(headWord.text)) {
                val headWordForm = graph.getLinksTo(headWord).find { it.rules == listOf(this) }?.fromEntity as? Word
                if (headWordForm != null) {
                    val derivedForm = word.text.substring(0, word.text.length-headWord.text.length) + headWordForm.text
                    return deriveWord(word, derivedForm, toLanguage, word.isPhonemic, -1, null, headWordForm.classes,
                        normalizeSegments = normalizeSegments)
                }
            }
        }

        val paradigm = graph.paradigmForRule(this)
        val paraPreWord = if (applyPrePostRules) (paradigm?.preRule?.apply(word, graph, trace, preserveId = true) ?: word) else word
        val preWord = logic.preInstructions.apply(this, null, paraPreWord, graph)
        for (branch in logic.branches) {
            if (branch.matches(preWord, graph, trace)) {
                trace?.logMatchedBranch(this, word, null, branch)
                var resultWord = branch.apply(this, preWord, graph, trace)
                if (!isSPE()) {
                    resultWord = logic.postInstructions.apply(this, null, resultWord, graph)
                }
                if (applyPrePostRules) {
                    resultWord = paradigm?.postRule?.apply(resultWord, graph, trace, preserveId = true) ?: resultWord
                }
                val result = deriveWord(word, resultWord.text, toLanguage, resultWord.isPhonemic, resultWord.stressedPhonemeIndex,
                    resultWord.segments, resultWord.classes, normalizeSegments = normalizeSegments,
                    id = if (preserveId) word.id else -1,
                    stress = if (resultWord.explicitStress) resultWord.stressedPhonemeIndex else null)
                trace?.logRuleResult(this, result)
                return result
            }
            else {
                trace?.logUnmatchedBranch(this, word, null, branch)
            }
        }
        return Word(-1, "?", word.language)
    }

    fun reverseApply(word: Word, graph: GraphRepository, trace: RuleTrace? = null): List<String> {
        if (logic.branches.isEmpty()) {
            return listOf(word.text)
        }
        return logic.branches.flatMap {
            val candidates = it.reverseApply(this, word, graph, trace)
            RuleInstruction.reverseApplyInstructions(candidates, this, word, logic.preInstructions, graph)
        }
    }

    fun applyToPhoneme(word: Word, phonemes: PhonemeIterator, graph: GraphRepository, trace: RuleTrace? = null): Boolean {
        for (branch in logic.branches) {
            if (branch.condition.matches(word, phonemes, graph, trace)) {
                trace?.logMatchedBranch(this, word, phonemes.current, branch)
                for (instruction in branch.instructions) {
                    instruction.apply(word, phonemes, graph, trace)
                }
                for (postInstruction in logic.postInstructions) {
                    postInstruction.apply(word, phonemes, graph, trace)
                }
                return true
            }
            else {
                trace?.logUnmatchedBranch(this, word, phonemes.current, branch)
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

    private fun deriveWord(word: Word, text: String, language: Language, phonemic: Boolean, stressIndex: Int, segments: List<WordSegment>?,
                           classes: List<String>, stress: Int? = null, normalizeSegments: Boolean = true,
                           id: Int = -1): Word {
        val gloss = word.glossOrNP()?.let { baseGloss ->
            applyCategories(baseGloss, segments?.any { it.sourceRule == this} == true)
        }
        return Word(id, text, language, gloss, pos = word.pos, classes = classes).also {
            it.stressedPhonemeIndex = stressIndex
            val sourceSegments = word.segments
            if (segments != null) {
                it.segments = if (normalizeSegments) Word.normalizeSegments(segments) else segments
            }
            else if (sourceSegments != null) {
                it.segments = sourceSegments
            }
            it.isPhonemic = phonemic
        }.apply {
            if (stress != null) {
                stressedPhonemeIndex = stress
                explicitStress = true
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

    fun toEditableText(graph: GraphRepository): String {
        if (isUnconditional()) {
            val branch = logic.branches[0]
            val commentString = RuleBranch.commentToString(branch.comment)
            return commentString + branch.instructions.joinToString("\n") {
                RuleBranch.commentToString(it.comment) + (if (it is SpeInstruction) "* " else " - ") + it.toEditableText(graph)
            }
        }
        return logic.preInstructions.joinToString("") { " - " + it.toEditableText(graph) + "\n" } +
               logic.branches.joinToString("\n\n") { it.toEditableText(graph) } +
               (if (logic.postInstructions.isNotEmpty()) "\n\n" else "") +
               logic.postInstructions.joinToString("\n") { " = " + it.toEditableText(graph) }
    }

    fun isUnconditional() =
        logic.branches.size == 1 && logic.branches[0].condition is OtherwiseCondition && logic.preInstructions.isEmpty()

    fun toSummaryText(graph: GraphRepository): String {
        val summaries = logic.branches.map { it.toSummaryText(graph, isPhonemic()) }
        if (summaries.any { it == null }) return ""
        val filteredSummaries = summaries.filter { !it.isNullOrEmpty() }
        if (isPhonemic()) {
            return filteredSummaries.joinToString(", ")
        }
        return filteredSummaries.toSet().joinToString("/")
    }

    fun addedGrammaticalCategories(): List<WordCategory> {
        return addedCategories?.let { cv -> parseCategoryValues(fromLanguage, cv).mapNotNull { it?.category } } ?: emptyList()
    }

    fun refersToPhoneme(phoneme: Phoneme): Boolean {
        return logic.preInstructions.any { it.refersToPhoneme(phoneme) } ||
                logic.branches.any { branch -> branch.refersToPhoneme(phoneme) } ||
                logic.postInstructions.any { it.refersToPhoneme(phoneme) }
    }

    fun refersToRule(rule: Rule): Boolean {
        return logic.preInstructions.any { it.refersToRule(rule) } ||
                logic.branches.any { branch -> branch.instructions.any { it.refersToRule(rule) } } ||
                logic.postInstructions.any { it.refersToRule(rule) }
    }

    data class RuleChangeResult(val word: Word, val oldResult: String, val newResult: String)

    fun previewChanges(graph: GraphRepository, newText: String): List<RuleChangeResult> {
        val newLogic = parseBranches(newText, RuleParseContext(graph, fromLanguage, toLanguage,) {
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
        fun parseBranches(s: String, context: RuleParseContext): RuleLogic {
            if (s.isBlank()) return RuleLogic(emptyList(), emptyList(), emptyList())
            val preInstructions = mutableListOf<RuleInstruction>()
            val postInstructions = mutableListOf<RuleInstruction>()
            val branches = mutableListOf<RuleBranch>()
            val lines = LineBuffer(s)

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
                return RuleLogic(
                    emptyList(),
                    listOf(RuleBranch(OtherwiseCondition, preInstructions, null)),
                    postInstructions
                )
            }

            return RuleLogic(preInstructions, branches, postInstructions)
        }
    }
}

data class RuleSequenceStepRef(
    val ruleId: Int,
    val optional: Boolean,
    val dispreferred: Boolean
)

data class RuleSequenceStep(
    val rule: LangEntity,
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
        for ((ruleId, optional, dispreferred) in steps) {
            val entity = graph.langEntityById(ruleId)
            if (entity is Rule) {
                result.add(RuleSequenceStep(entity, optional, dispreferred))
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
