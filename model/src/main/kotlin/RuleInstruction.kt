package ru.yole.etymograph

enum class InstructionType(
    val insnName: String,
    @org.intellij.lang.annotations.Language("RegExp") val pattern: String? = null,
    val takesArgument: Boolean = false
) {
    NoChange("no change"),
    ChangeEnding("change ending to", "change ending to '(.*)'", true),
    PrependMorpheme("prepend morpheme", "prepend morpheme '(.+?): (.+)'", true),
    AppendMorpheme("append morpheme", "append morpheme '(.+?): (.+)'", true),
    Prepend("prepend", "prepend (.+)", true),
    Append("append", "append (.+)", true),
    Insert("insert", "insert '(.+)' (before|after) (.+)"),
    ApplyRule("apply rule", "apply rule '(.+)'", true),
    ApplySoundRule("apply sound rule", "apply sound rule '(.+)'( to (.+))?", true),
    ApplyStress("stress is on", "stress is on (.+) syllable", true),
    ApplyClass("mark word as", "mark word as (.*)", true),
    Disallow("disallow", "disallow", false),
    ChangeSound("new sound is", "new sound is '(.+)'", true),
    SoundDisappears("sound disappears", RelativeOrdinals.toPattern() + "?sound disappears", true),
    Spe("SPE pattern", "", true);

    val regex = Regex(pattern ?: Regex.escape(insnName))
}

const val DISALLOW_CLASS = "disallow"

open class RuleInstruction(val type: InstructionType, val arg: String, val comment: String?)  {
    open fun apply(word: Word, context: RuleApplyContext): Word = when(type) {
        InstructionType.NoChange -> word
        InstructionType.ChangeEnding -> changeEnding(word, context.rule, context.branch, context.graph)
        InstructionType.ApplyClass -> word.derive(word.text, newClasses = (word.classes + arg).toSet().toList(), id = word.id)
        InstructionType.Disallow -> word.derive(word.text, newClasses = (word.classes + DISALLOW_CLASS).toSet().toList())
        else -> throw IllegalStateException("Can't apply phoneme instruction $type to full word")
    }

    private fun changeEnding(word: Word, rule: Rule, branch: RuleBranch?, graph: GraphRepository): Word {
        if (branch != null) {
            val condition = branch.condition.findLeafConditions(ConditionType.EndsWith)
                .firstOrNull { it.matches(word, graph) }
                ?: return word
            val phonemes = PhonemeIterator(word, graph)
            if (condition.phonemeClass != null) {
                phonemes.deleteAtRelative(phonemes.size - 1)
            }
            else {
                val conditionPhonemes = PhonemeIterator(condition.parameter!!, word.language, graph)
                val phonemesToDelete = conditionPhonemes.size
                for (i in phonemes.size - 1 downTo phonemes.size - phonemesToDelete) {
                    phonemes.deleteAtRelative(i)
                }
            }
            return word.derive(phonemes.result() + arg,
                segments = remapSegments(phonemes, word.segments),
                addSegment = WordSegment.create(phonemes.result().length, arg.length, rule.addedCategories, null, rule))
        }
        return word
    }

    open fun reverseApply(rule: Rule, text: String, language: Language, graph: GraphRepository, trace: RuleTrace? = null): List<String> {
        return when (type) {
            InstructionType.ChangeEnding -> reverseChangeEnding(text, rule)
            InstructionType.NoChange -> listOf(text)
            InstructionType.ApplyClass -> listOf(text)
            else -> emptyList()
        }
    }

    private fun reverseChangeEnding(text: String, rule: Rule): List<String> {
        if (!text.endsWith(arg)) return emptyList()
        for (branch in rule.logic.branches) {
            if (this in branch.instructions) {
                val conditions = branch.condition.findLeafConditions(ConditionType.EndsWith)
                return conditions.map { condition ->
                     text.removeSuffix(arg) + if (condition.phonemeClass != null) "*" else condition.parameter
                }
            }
        }
        return emptyList()
    }

    open fun apply(word: Word, phonemes: PhonemeIterator, graph: GraphRepository, trace: RuleTrace? = null) {
        when (type) {
            InstructionType.ChangeSound -> phonemes.replace(arg)
            InstructionType.SoundDisappears -> phonemes.deleteAtRelative(if (arg.isEmpty()) 0 else arg.toInt())
            InstructionType.NoChange -> Unit
            else -> throw IllegalStateException("Can't apply word instruction to individual phoneme")
        }
    }

    open fun toEditableText(graph: GraphRepository): String = toRichText(graph).toString()

    open fun toRichText(graph: GraphRepository): RichText {
        if (type == InstructionType.SoundDisappears && arg != "0" && arg.isNotEmpty()) {
            return RelativeOrdinals.toString(arg.toInt()).rich() + " ".rich() + type.insnName.rich()
        }
        if (type.pattern != null) {
            val argIndex = type.pattern.indexOfAny(listOf("(.+)", "(.*)"))
            if (argIndex > 0) {
                return type.pattern.substring(0, argIndex).rich() +
                        arg.rich(emph = true) +
                        type.pattern.substring(argIndex + 4).rich()
            }
        }
        return type.insnName.richText()
    }

    open fun toSummaryText(graph: GraphRepository, condition: RuleCondition): String? = when(type) {
        InstructionType.ChangeEnding ->
            if (arg.isNotEmpty()) "-$arg" else ""
        InstructionType.ChangeSound,
        InstructionType.SoundDisappears ->
            toSummaryTextPhonemic(condition)
        else -> ""
    }

    protected fun toSummaryTextPhonemic(condition: RuleCondition): String? {
        val soundIs = condition.findLeafConditions {
            it is RelativePhonemeRuleCondition && it.seekTarget == null
        }.singleOrNull() as RelativePhonemeRuleCondition? ?: return null

        val soundIsParameter = soundIs.phonemePattern.toEditableText()
        var includeRelativePhoneme = true
        val changeSummary = when (type) {
            InstructionType.ChangeSound -> "$soundIsParameter > '${arg}'"
            InstructionType.SoundDisappears -> when (arg) {
                "0" -> "$soundIsParameter > Ø"
                "1" -> summarizeNextSound(condition).also {
                    includeRelativePhoneme = false
                }
                else -> null
            }
            else -> return null
        }
        if (changeSummary == null) return null
        val context = summarizeContext(condition, includeRelativePhoneme) ?: return null
        return changeSummary + context
    }

    private fun summarizeNextSound(condition: RuleCondition): String? {
        val soundIs = condition.findLeafConditions {
            it is RelativePhonemeRuleCondition && it.seekTarget == null
        }.singleOrNull() as RelativePhonemeRuleCondition? ?: return null

        if (soundIs.phonemePattern.phonemeClass != null) return null
        val nextPhonemeIs = condition.findLeafConditions {
            it is RelativePhonemeRuleCondition && it.seekTarget != null
        }.singleOrNull() as? RelativePhonemeRuleCondition
            ?: return null
        if (nextPhonemeIs.seekTarget!!.phonemeClass != null ||
            nextPhonemeIs.phonemePattern.phonemeClass != null ||
            nextPhonemeIs.seekTarget.index != 1 ||
            !nextPhonemeIs.seekTarget.relative)
        {
            return null
        }
        val nextSound = if (type == InstructionType.SoundDisappears) "" else arg
        return "'${soundIs.phonemePattern.literal}${nextPhonemeIs.phonemePattern.literal}' > '${soundIs.phonemePattern.literal}$nextSound'"
    }

    private fun summarizeContext(condition: RuleCondition, includeRelativePhoneme: Boolean): String? {
        fun LeafRuleCondition.maybeNot() = if (negated) "not " else ""

        val relativePhonemeContext = if (includeRelativePhoneme)
            summarizeRelativePhoneme(condition) ?: ""
        else
            ""
        val syllableIndexCondition = condition.findLeafConditions { it is RelativeSyllableRuleCondition }.singleOrNull() as RelativeSyllableRuleCondition?
        if (syllableIndexCondition != null && syllableIndexCondition.matchClass == null) {
            val index = Ordinals.toString(syllableIndexCondition.matchIndex!!)
            return "$relativePhonemeContext in ${syllableIndexCondition.maybeNot}$index syllable"
        }
        return relativePhonemeContext
    }

    private fun summarizeRelativePhoneme(condition: RuleCondition): String? {
        val relativePhonemes = condition.findLeafConditions { it is RelativePhonemeRuleCondition }
            .filterIsInstance<RelativePhonemeRuleCondition>()
            .filter { it.seekTarget != null }
        if (relativePhonemes.isEmpty()) return ""
        if (relativePhonemes.any { it.seekTarget!!.phonemeClass != null }) return null

        val after = summarizeRelativePhonemeParameters(relativePhonemes, -1)
            .takeIf { it.isNotEmpty() }?.let { " after $it" } ?: ""
        val precededBy = summarizeRelativePhonemeParameters(relativePhonemes, -2)
            .takeIf { it.isNotEmpty() }?.let { " preceded by $it" } ?: ""
        val before = summarizeRelativePhonemeParameters(relativePhonemes, 1)
            .takeIf { it.isNotEmpty() }?.let { " before $it" } ?: ""
        val followedBy = summarizeRelativePhonemeParameters(relativePhonemes, 2)
            .takeIf { it.isNotEmpty() }?.let { " followed by $it" } ?: ""
        return "$after$precededBy$before$followedBy"
    }

    private fun summarizeRelativePhonemeParameters(relativePhonemes: List<RelativePhonemeRuleCondition>, relIndex: Int): String {
        val relativePhonemeParameters = relativePhonemes.filter { it.seekTarget?.index == relIndex}.map {
            val p = it.phonemePattern.toEditableText()
            val negation = if (it.negated) "not " else ""
            "$negation$p"
        }
        return relativePhonemeParameters.joinToString(" or ")
    }

    open fun reverseApplyToPhoneme(phonemes: PhonemeIterator, condition: RuleCondition): List<String>? {
        if (type == InstructionType.NoChange) return emptyList()
        if (type == InstructionType.ChangeSound) {
            if (arg == phonemes.current) {
                val leafCondition = condition as? RelativePhonemeRuleCondition ?: return null
                if (leafCondition.seekTarget == null && leafCondition.phonemePattern.literal != null) {
                    phonemes.replace(leafCondition.phonemePattern.literal)
                    return listOf(phonemes.result())
                }
                else {
                    return null
                }
            }
            return emptyList()
        }
        return null
    }

    open fun refersToPhoneme(phoneme: Phoneme): Boolean {
        return when (type) {
            InstructionType.ChangeSound -> phoneme.effectiveSound == arg
            else -> false
        }
    }

    open fun referencedRules(): Set<Rule> {
        return emptySet()
    }

    companion object {
        fun parse(s: String, context: RuleParseContext, prefix: String = "-", comment: String? = null): RuleInstruction {
            if (s.startsWith("*")) {
                val conditionPos = s.indexOf(" if ")
                val conditionText = if (conditionPos >= 0) s.substring(conditionPos + 4).trim() else null
                val patternText = if (conditionPos >= 0) s.substring(0, conditionPos) else s
                val pattern = SpePattern.parse(context.fromLanguage, context.toLanguage, patternText.removePrefix("*").trim())
                val condition = conditionText?.let { RuleCondition.parse(ParseBuffer(conditionText), context.fromLanguage) }
                return SpeInstruction(pattern, condition, comment)
            }
            if (!s.startsWith(prefix)) {
                throw RuleParseException("Instructions must start with $prefix")
            }
            val trimmed = s.removePrefix(prefix).trim()
            for (type in InstructionType.entries) {
                val match = type.regex.matchEntire(trimmed)
                if (match != null) {
                    val arg = if (match.groups.size > 1) match.groupValues[1] else ""
                    return when(type) {
                        InstructionType.ApplyRule -> ApplyRuleInstruction(context.ruleRefFactory(arg), comment)
                        InstructionType.ApplySoundRule -> ApplySoundRuleInstruction.parse(match, context, comment)
                        InstructionType.ApplyStress -> ApplyStressInstruction(context.fromLanguage, arg, comment)
                        InstructionType.Prepend, InstructionType.Append ->
                            PrependAppendInstruction(type, context.fromLanguage, arg, comment)
                        InstructionType.PrependMorpheme, InstructionType.AppendMorpheme -> {
                            val text = match.groupValues[1]
                            val gloss = match.groupValues[2]
                            val word = context.repo.wordsByText(context.fromLanguage, text)
                                .firstOrNull { it.getOrComputeGloss(context.repo) == gloss }
                                ?: throw RuleParseException("Cannot find word with text '$text' and gloss '$gloss'")
                            MorphemeInstruction(type, word.id, comment)
                        }
                        InstructionType.Insert -> InsertInstruction.parse(context.fromLanguage, match, comment)
                        InstructionType.SoundDisappears -> RuleInstruction(type, RelativeOrdinals.parseMatch(match, 1).toString(), comment)
                        else -> RuleInstruction(type, arg, comment)
                    }
                }
            }
            throw RuleParseException("Unrecognized instruction '$s'")
        }

        fun reverseApplyInstructions(
            candidates: List<String>,
            rule: Rule,
            word: Word,
            ruleInstructions: List<RuleInstruction>,
            graph: GraphRepository,
            trace: RuleTrace? = null
        ): List<String> {
            return ruleInstructions.reversed().fold(candidates) { c, instruction ->
                c.flatMap { text ->
                    instruction.reverseApply(rule, text, word.language, graph, trace).also {
                        trace?.logReverseApplyInstruction(graph, instruction, text, it)
                    }
                }
            }
        }
    }
}

fun List<RuleInstruction>.apply(word: Word, context: RuleApplyContext): Word {
    if (isEmpty()) return word
    val normalizedWord = word.derive(word.text.trimEnd('-'), id = word.id, phonemic = word.isPhonemic)
    return fold(normalizedWord) { s, i -> i.apply(s, context) }
}

class ApplyRuleInstruction(val ruleRef: RuleRef, comment: String?)
    : RuleInstruction(InstructionType.ApplyRule, "", comment)
{
    override fun apply(word: Word, context: RuleApplyContext): Word {
        val targetRule = ruleRef.resolve()
        val link = context.graph.getLinksTo(word).find { it.rules == listOf(targetRule) }
        val existingFormText = (link?.fromEntity as? Word)?.text
        val applyPrePostRules = context.graph.paradigmForRule(context.rule) != context.graph.paradigmForRule(targetRule)
        val result = targetRule.apply(word, context.graph, context.trace, normalizeSegments = false, applyPrePostRules = applyPrePostRules)
            .asOrthographic(context.originalWord)
        if (existingFormText != null && existingFormText != result.text) {
            return word.derive(existingFormText, word.id)
        }
        return result.derive(result.text, word.id).remapSegments { s ->
            if (s.sourceRule == targetRule) {
                WordSegment(s.firstCharacter, s.length, context.rule.addedCategories, null, context.rule)}
            else
                s
        }
    }

    override fun apply(word: Word, phonemes: PhonemeIterator, graph: GraphRepository, trace: RuleTrace?) {
        phonemes.commit()
        ruleRef.resolve().applyToPhoneme(word, phonemes, graph, trace)
    }

    override fun reverseApply(rule: Rule, text: String, language: Language, graph: GraphRepository, trace: RuleTrace?): List<String> {
        val targetRule = ruleRef.resolve()
        return targetRule.reverseApply(Word(-1, text, language), graph, trace)
    }

    override fun toRichText(graph: GraphRepository): RichText {
        val rule = ruleRef.resolve()
        return InstructionType.ApplyRule.insnName.rich() +
                " '".rich() +
                rule.name.rich(linkType = "rule", linkId = rule.id) +
                "'".rich()
    }

    override fun toSummaryText(graph: GraphRepository, condition: RuleCondition): String {
        val rule = ruleRef.resolve()
        if (rule.isPhonemic()) return ""
        return rule.toSummaryText(graph)
    }

    override fun referencedRules(): Set<Rule> {
        return setOf(ruleRef.resolve())
    }
}

class ApplySoundRuleInstruction(language: Language, val ruleRef: RuleRef, arg: String?, comment: String?)
    : RuleInstruction(InstructionType.ApplySoundRule, arg ?: "", comment)
{
    val seekTarget = arg?.let { SeekTarget.parse(it, language) }

    override fun apply(word: Word, phonemes: PhonemeIterator, graph: GraphRepository, trace: RuleTrace?) {
        phonemes.commit()
        val targetIt = phonemes.clone()
        if (seekTarget != null && seekTarget.relative) {
            targetIt.seek(seekTarget)
        }
        ruleRef.resolve().applyToPhoneme(word, targetIt, graph, trace)
    }

    override fun apply(word: Word, context: RuleApplyContext): Word {
        if (seekTarget == null) return word
        val phonemes = PhonemeIterator(word.asPhonemic(), context.graph)

        if (phonemes.seek(seekTarget)) {
            val count = if (seekTarget.phonemeClass == null) (seekTarget.targetSyllable(phonemes.syllables)?.length ?: 1) else 1
            val rule = ruleRef.resolve()
            for (i in 0..<count) {
                rule.applyToPhoneme(word, phonemes, context.graph, context.trace)
                if (!phonemes.advance()) break
            }
            val segments = remapSegments(phonemes, word.segments)
            return word.derive(phonemes.result(), segments = segments, phonemic = true, keepStress = false)
                .asOrthographic()
        }
        return word
    }

    override fun reverseApply(rule: Rule, text: String, language: Language, graph: GraphRepository, trace: RuleTrace?): List<String> {
        if (seekTarget == null) return listOf(text)
        val phonemes = PhonemeIterator(text, language, graph)
        if (phonemes.seek(seekTarget)) {
            return ruleRef.resolve().reverseApplyToPhoneme(phonemes)
        }
        return listOf(text)
    }

    override fun toRichText(graph: GraphRepository): RichText {
        val rule = ruleRef.resolve()
        return InstructionType.ApplySoundRule.insnName.rich() + " '".rich() +
                rule.name.rich(linkType = "rule", linkId = rule.id) + "'" +
                (seekTarget?.let { " to "} ?: "") +
                (seekTarget?.toEditableText()?.rich(emph = true) ?: "".rich())
    }

    override fun referencedRules(): Set<Rule> {
        return setOf(ruleRef.resolve())
    }

    companion object {
        fun parse(match: MatchResult, context: RuleParseContext, comment: String?): ApplySoundRuleInstruction {
            val ruleRef = context.ruleRefFactory(match.groupValues[1])
            return ApplySoundRuleInstruction(context.fromLanguage, ruleRef, match.groupValues[3].takeIf { it.isNotEmpty() }, comment)
        }
    }
}

class ApplyStressInstruction(val language: Language, arg: String, comment: String?) : RuleInstruction(InstructionType.ApplyStress, arg, comment) {
    private val syllableIndex: Int
    private val root: Boolean

    init {
        val ordinalParse = Ordinals.parse(arg) ?: throw RuleParseException("Can't parse ordinal '$arg'")
        syllableIndex = ordinalParse.first
        if (ordinalParse.second == "root") {
            root = true
        }
        else if (ordinalParse.second.isEmpty()) {
            root = false
        }
        else {
            throw RuleParseException("Can't parse syllable type ${ordinalParse.second}")
        }
    }


    override fun apply(word: Word, context: RuleApplyContext): Word {
        var syllables = breakIntoSyllables(word)
        if (root) {
            val firstRootSyllable = findFirstRootSyllable(context.graph, word)
            if (firstRootSyllable != null) {
                syllables = syllables.drop(firstRootSyllable)
            }
        }
        val vowel = language.phonemeClassByName(PhonemeClass.vowelClassName) ?: return word
        val syllable = Ordinals.at(syllables, syllableIndex) ?: return word
        val stressIndex = PhonemeIterator(word, context.graph).findMatchInRange(syllable.startIndex, syllable.endIndex, vowel)
            ?: return word
        word.stressedPhonemeIndex = stressIndex    // TODO create a copy of the word here?
        return word
    }

    private fun findFirstRootSyllable(graph: GraphRepository, word: Word): Int? {
        val orthoWord = word.asOrthographic()
        val segments = graph.restoreSegments(orthoWord).segments
        val rootSegment = segments?.firstOrNull {
            it.sourceRule == null &&
                    (it.sourceWord == null ||
                     (it.sourceWord.pos != KnownPartsOfSpeech.preverb.abbreviation &&
                      it.sourceWord.pos != KnownPartsOfSpeech.affix.abbreviation))
        }
        if (rootSegment != null) {
            val syllable = breakIntoSyllables(word).indexOfFirst { it.startIndex >= rootSegment.firstCharacter }
            if (syllable >= 0) {
                return syllable
            }
        }
        return null
    }
}

class PrependAppendInstruction(type: InstructionType, language: Language, arg: String, comment: String?)
    : RuleInstruction(type, arg, comment)
{
    val seekTarget = if (arg.startsWith('\'')) null else SeekTarget.parse(arg, language)
    val literalArg = if (arg.startsWith('\'')) arg.removePrefix("'").removeSuffix("'") else null

    init {
        if (type != InstructionType.Append && type != InstructionType.Prepend) {
            throw IllegalStateException("Unsupported instruction type for this instruction implementation")
        }
    }

    override fun apply(word: Word, context: RuleApplyContext): Word {
        if (literalArg != null) {
            return if (type == InstructionType.Prepend)
                word.derive(literalArg + word.text)
            else
                word.derive(word.text + literalArg,
                    addSegment = WordSegment(word.text.length, literalArg.length, context.rule.addedCategories, null, context.rule))
        }
        val phonemes = PhonemeIterator(word, context.graph)
        if (phonemes.seek(seekTarget!!)) {
            val phoneme = phonemes.current
            if (type == InstructionType.Prepend) {
                return word.derive(phoneme + word.text)
            }
            return word.derive(word.text + phoneme,
                addSegment = WordSegment(word.text.length, phoneme.length, context.rule.addedCategories, null, context.rule))
        }
        return word
    }

    override fun reverseApply(rule: Rule, text: String, language: Language, graph: GraphRepository, trace: RuleTrace?): List<String> {
        if (literalArg != null) {
            return when (type) {
                InstructionType.Append -> if (text.endsWith(literalArg)) listOf(text.removeSuffix(literalArg)) else emptyList()
                else -> if (text.startsWith(literalArg)) listOf(text.removePrefix(literalArg)) else emptyList()
            }
        }
        return emptyList()
    }

    override fun toSummaryText(graph: GraphRepository, condition: RuleCondition): String? {
        if (literalArg != null) {
            return if (type == InstructionType.Prepend) "$literalArg-" else "-$literalArg"
        }
        return super.toSummaryText(graph, condition)
    }
}

class InsertInstruction(arg: String, val relIndex: Int, val seekTarget: SeekTarget, comment: String?)
    : RuleInstruction(InstructionType.Insert, arg, comment)
{
    override fun apply(word: Word, context: RuleApplyContext): Word {
        val phonemes = PhonemeIterator(word, context.graph)
        if (!phonemes.seek(seekTarget)) return word
        if (relIndex == -1) {
            phonemes.insertAtRelative(0, arg)
        }
        else {
            phonemes.insertAtRelative(1, arg)
        }
        return word.derive(phonemes.result())
    }

    override fun toRichText(graph: GraphRepository): RichText {
        val relIndexWord = if (relIndex == -1) "before" else "after"
        return "insert '$arg' $relIndexWord ${seekTarget.toEditableText()}".richText()
    }

    companion object {
        fun parse(language: Language, match: MatchResult, comment: String?): InsertInstruction {
            return InsertInstruction(
                match.groupValues[1],
                if (match.groupValues[2] == "before") -1 else 1,
                SeekTarget.parse(match.groupValues[3], language),
                comment
            )
        }
    }
}

class MorphemeInstruction(type: InstructionType, val morphemeId: Int, comment: String?)
    : RuleInstruction(type, morphemeId.toString(), comment)
{
    init {
        if (type != InstructionType.PrependMorpheme && type != InstructionType.AppendMorpheme) {
            throw IllegalStateException("Unsupported instruction type for this instruction implementation")
        }
    }

    override fun apply(word: Word, context: RuleApplyContext): Word {
        val morpheme = context.graph.wordById(morphemeId) ?: throw IllegalStateException("Target morpheme not found")
        return when (type) {
            InstructionType.PrependMorpheme ->
                word.derive(morpheme.text.trimEnd('-') + word.text)
            InstructionType.AppendMorpheme ->
                word.derive(word.text + morpheme.text.trimStart('-'),
                    addSegment = WordSegment(word.text.length, morpheme.text.length, context.rule.addedCategories, morpheme, context.rule))
            else -> throw IllegalStateException("Unsupported instruction type for this instruction implementation")
        }
    }

    override fun toRichText(graph: GraphRepository): RichText {
        val word = graph.wordById(morphemeId) ?: throw IllegalStateException("Target morpheme not found")
        return richText(
            (type.insnName + " '").rich(),
            (word.text + ": " + word.getOrComputeGloss(graph)).rich(linkType = "word",
                linkId = word.id, linkData = word.text, linkLanguage = word.language.shortName),
            "'".rich()
        )
    }

    override fun toSummaryText(graph: GraphRepository, condition: RuleCondition): String? {
        val word = graph.wordById(morphemeId) ?: return null
        return if (type == InstructionType.PrependMorpheme)
            "${word.text.trimEnd('-')}-"
        else
            "-${word.text.trimStart('-')}"
    }
}

class SpeInstruction(val pattern: SpePattern, val condition: RuleCondition? = null, comment: String?)
    : RuleInstruction(InstructionType.Spe, pattern.toString(), comment)
{
    override fun toRichText(graph: GraphRepository): RichText {
        return pattern.toRichText() + (condition?.let { " if ".richText() + it.toRichText() } ?: "".richText())
    }

    override fun toEditableText(graph: GraphRepository): String {
        return pattern.toString() + (condition?.let { " if " + it.toEditableText() } ?: "")
    }

    override fun toSummaryText(graph: GraphRepository, condition: RuleCondition): String {
        return pattern.toRichText().toString()
    }

    override fun apply(word: Word, context: RuleApplyContext): Word {
        val trace = context.trace
        trace?.logInstruction {
            "Matching pattern $pattern to ${word.text}"
        }
        val phonemicWord = word.asPhonemic()
        val it = pattern.apply(
            phonemicWord,
            { condition == null || condition.matches(phonemicWord, it, context.graph, trace).also { result -> trace?.logCondition(condition, result) } },
            { context.rule.logic.postInstructions.forEach { insn -> insn.apply(word, it, context.graph, trace )} },
            trace
        )
        if (it.result() != phonemicWord.text) {
            trace?.logMatchedInstruction(context.rule, word, this)
            val stress = if (word.stressedPhonemeIndex != -1)
                it.mapIndex(word.stressedPhonemeIndex)
            else
                null
            val segments = remapSegments(it, word.segments)
            return phonemicWord.derive(it.result(), phonemic = true, segments = segments).also {
                if (stress != null && stress >= 0) {
                    val vowels = context.rule.toLanguage.phonemeClassByName(PhonemeClass.vowelClassName)
                    val stressIt = PhonemeIterator(it, context.graph)
                    if (vowels != null && stressIt.advanceTo(stress) && !vowels.matchesCurrent(stressIt)) {
                        val syllables = breakIntoSyllables(it)
                        val syllableIndex = findSyllable(syllables, stress)
                        if (syllableIndex >= 0) {
                            it.stressedPhonemeIndex = syllables[syllableIndex].vowelIndex
                        }
                        else {
                            it.stressedPhonemeIndex = stress
                        }
                    }
                    else {
                        it.stressedPhonemeIndex = stress
                    }
                    it.explicitStress = word.explicitStress
                }
            }
        }
        return phonemicWord
    }

    override fun apply(word: Word, phonemes: PhonemeIterator, graph: GraphRepository, trace: RuleTrace?) {
        pattern.applyAtCurrent(
            phonemes,
            { condition == null || condition.matches(word, it, graph, trace).also { result -> trace?.logCondition(condition, result) } },
            null,
            trace
        )
    }

    override fun refersToPhoneme(phoneme: Phoneme): Boolean {
        return pattern.before.any { it.refersToPhoneme(phoneme) } ||
                pattern.after.any { it.refersToPhoneme(phoneme) }
    }
}
