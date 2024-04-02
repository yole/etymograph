package ru.yole.etymograph

enum class InstructionType(
    val insnName: String,
    @org.intellij.lang.annotations.Language("RegExp") val pattern: String? = null,
    val takesArgument: Boolean = false
) {
    NoChange("no change"),
    ChangeEnding("change ending to", "change ending to '(.*)'", true),
    Prepend("prepend", "prepend (.+)", true),
    Append("append", "append (.+)", true),
    Insert("insert", "insert '(.+)' (before|after) (.+)"),
    ApplyRule("apply rule", "apply rule '(.+)'", true),
    ApplySoundRule("apply sound rule", "apply sound rule '(.+)'( to (.+))?", true),
    ApplyStress("stress is on", "stress is on (.+) syllable", true),
    ApplyClass("mark word as", "mark word as (.*)", true),
    Disallow("disallow", "disallow", false),
    ChangeSound("new sound is", "new sound is '(.+)'", true),
    ChangeNextSound("new next sound is", "new next sound is '(.+)'", true),
    ChangeSoundClass("becomes", RelativeOrdinals.toPattern() + "?(.+) becomes (.+)", true),
    SoundDisappears("sound disappears", RelativeOrdinals.toPattern() + "?sound disappears", true),
    SoundIsGeminated("sound is geminated"),
    SoundInserted("is inserted", "'(.+)' is inserted before", true);

    val regex = Regex(pattern ?: Regex.escape(insnName))
}

const val DISALLOW_CLASS = "disallow"

open class RuleInstruction(val type: InstructionType, val arg: String) {
    open fun apply(rule: Rule, branch: RuleBranch?, word: Word, graph: GraphRepository): Word = when(type) {
        InstructionType.NoChange -> word
        InstructionType.ChangeEnding -> changeEnding(word, rule, branch, graph)
        InstructionType.ApplyClass -> word.derive(word.text, newClasses = (word.classes + arg).toSet().toList())
        InstructionType.Disallow -> word.derive(word.text, newClasses = (word.classes + DISALLOW_CLASS).toSet().toList())
        else -> throw IllegalStateException("Can't apply phoneme instruction to full word")
    }

    private fun changeEnding(word: Word, rule: Rule, branch: RuleBranch?, graph: GraphRepository): Word {
        if (branch != null) {
            val condition = branch.condition.findLeafConditions(ConditionType.EndsWith)
                .firstOrNull { it.matches(word, graph) }
                ?: return word
            val textWithoutEnding = if (condition.phonemeClass != null) {
                // TODO parse with PhonemeIterator
                word.text.dropLast(1)
            }
            else {
                word.text.dropLast(condition.parameter!!.length)
            }
            return word.derive(textWithoutEnding + arg, WordSegment(textWithoutEnding.length, arg.length, rule.addedCategories, rule))
        }
        return word
    }

    open fun reverseApply(rule: Rule, text: String, language: Language, graph: GraphRepository): List<String> {
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

    open fun apply(word: Word, phonemes: PhonemeIterator, graph: GraphRepository) {
        when (type) {
            InstructionType.ChangeSound -> phonemes.replace(arg)
            InstructionType.ChangeNextSound -> phonemes.replaceAtRelative(1, arg)
            InstructionType.SoundDisappears -> phonemes.deleteAtRelative(if (arg.isEmpty()) 0 else arg.toInt())
            InstructionType.SoundInserted -> phonemes.insertAtRelative(0, arg)
            InstructionType.SoundIsGeminated -> phonemes.insertAtRelative(1, phonemes.current)
            InstructionType.NoChange -> Unit
            else -> throw IllegalStateException("Can't apply word instruction to individual phoneme")
        }
    }

    fun toEditableText(): String = toRichText().toString()

    open fun toRichText(): RichText {
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

    open fun toSummaryText(condition: RuleCondition): String? = when(type) {
        InstructionType.ChangeEnding ->
            if (arg.isNotEmpty()) "-$arg" else ""
        InstructionType.ChangeSound, InstructionType.ChangeNextSound,
        InstructionType.SoundDisappears,
        InstructionType.ChangeSoundClass, InstructionType.SoundIsGeminated ->
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
            InstructionType.ChangeSound -> "$soundIsParameter -> '${arg}'"
            InstructionType.ChangeSoundClass -> (this as ChangePhonemeClassInstruction).oldClass + " -> " + newClass
            InstructionType.SoundDisappears -> when (arg) {
                "0" -> "$soundIsParameter -> Ø"
                "1" -> summarizeNextSound(condition).also {
                    includeRelativePhoneme = false
                }
                else -> null
            }
            InstructionType.ChangeNextSound -> summarizeNextSound(condition).also {
                includeRelativePhoneme = false
            }
            else -> return null
        }
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
            nextPhonemeIs.seekTarget.index != 1 ||
            !nextPhonemeIs.seekTarget.relative)
        {
            return null
        }
        val nextSound = if (type == InstructionType.SoundDisappears) "" else arg
        return "'${soundIs.phonemePattern.literal}${nextPhonemeIs.phonemePattern.literal}' -> '${soundIs.phonemePattern.literal}$nextSound'"
    }

    private fun summarizeContext(condition: RuleCondition, includeRelativePhoneme: Boolean): String? {
        fun LeafRuleCondition.maybeNot() = if (negated) "not " else ""

        val relativePhonemeContext = if (includeRelativePhoneme)
            summarizeRelativePhoneme(condition) ?: ""
        else
            ""
        val bow = condition.findLeafConditions(ConditionType.BeginningOfWord).firstOrNull()
        if (bow != null) return "$relativePhonemeContext ${bow.maybeNot()}at beginning of word"
        val eow = condition.findLeafConditions(ConditionType.EndOfWord).firstOrNull()
        if (eow != null) return "$relativePhonemeContext ${eow.maybeNot()}at end of word"
        val syllableIndexCondition = condition.findLeafConditions(ConditionType.SyllableIndex).singleOrNull()
        if (syllableIndexCondition != null) {
            val index = Ordinals.toString(syllableIndexCondition.parameter!!.toInt())
            return "$relativePhonemeContext in ${syllableIndexCondition.maybeNot()}$index syllable"
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
        val before = summarizeRelativePhonemeParameters(relativePhonemes, 1)
            .takeIf { it.isNotEmpty() }?.let { " before $it" } ?: ""
        return "$after$before"
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

    fun refersToPhoneme(phoneme: Phoneme): Boolean {
        return when (type) {
            InstructionType.ChangeSound, InstructionType.ChangeNextSound -> phoneme.effectiveSound == arg
            else -> false
        }
    }

    companion object {
        fun parse(s: String, context: RuleParseContext): RuleInstruction {
            if (!s.startsWith("-")) {
                throw RuleParseException("Instructions must start with -")
            }
            val trimmed = s.removePrefix("-").trim()
            for (type in InstructionType.entries) {
                val match = type.regex.matchEntire(trimmed)
                if (match != null) {
                    val arg = if (match.groups.size > 1) match.groupValues[1] else ""
                    return when(type) {
                        InstructionType.ApplyRule -> ApplyRuleInstruction(context.ruleRefFactory(arg))
                        InstructionType.ApplySoundRule -> ApplySoundRuleInstruction.parse(match, context)
                        InstructionType.ApplyStress -> ApplyStressInstruction(context.fromLanguage, arg)
                        InstructionType.Prepend, InstructionType.Append ->
                            PrependAppendInstruction(type, context.fromLanguage, arg)
                        InstructionType.Insert -> InsertInstruction.parse(context.fromLanguage, match)
                        InstructionType.ChangeSoundClass -> ChangePhonemeClassInstruction.parse(match)
                        InstructionType.SoundDisappears -> RuleInstruction(type, RelativeOrdinals.parseMatch(match, 1).toString())
                        else -> RuleInstruction(type, arg)
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
            graph: GraphRepository
        ): List<String> {
            return ruleInstructions.reversed().fold(candidates) { c, instruction ->
                c.flatMap { instruction.reverseApply(rule, it, word.language, graph) }
            }
        }
    }
}

fun List<RuleInstruction>.apply(rule: Rule, branch: RuleBranch?, word: Word, graph: GraphRepository): Word {
    val normalizedWord = word.derive(word.text.trimEnd('-'))
    return fold(normalizedWord) { s, i -> i.apply(rule, branch, s, graph) }
}

class ApplyRuleInstruction(val ruleRef: RuleRef)
    : RuleInstruction(InstructionType.ApplyRule, "")
{
    override fun apply(rule: Rule, branch: RuleBranch?, word: Word, graph: GraphRepository): Word {
        val targetRule = ruleRef.resolve()
        val link = graph.getLinksTo(word).find { it.rules == listOf(targetRule) }
        (link?.fromEntity as? Word)?.let { return it }
        val result = targetRule.apply(word, graph).asOrthographic()
        return result.remapSegments { s ->
            if (s.sourceRule == targetRule)
                WordSegment(s.firstCharacter, s.length, rule.addedCategories, rule)
            else
                s
        }
    }

    override fun reverseApply(rule: Rule, text: String, language: Language, graph: GraphRepository): List<String> {
        val targetRule = ruleRef.resolve()
        return targetRule.reverseApply(Word(-1, text, language), graph)
    }

    override fun toRichText(): RichText {
        val rule = ruleRef.resolve()
        return InstructionType.ApplyRule.insnName.rich() +
                " '".rich() +
                rule.name.rich(linkType = "rule", linkId = rule.id) +
                "'".rich()
    }

    override fun toSummaryText(condition: RuleCondition): String =
        ruleRef.resolve().toSummaryText()
}

class ApplySoundRuleInstruction(language: Language, val ruleRef: RuleRef, arg: String?)
    : RuleInstruction(InstructionType.ApplySoundRule, arg ?: "")
{
    val seekTarget = arg?.let { SeekTarget.parse(it, language) }

    override fun apply(word: Word, phonemes: PhonemeIterator, graph: GraphRepository) {
        if (seekTarget != null && seekTarget.relative) {
            phonemes.seek(seekTarget)
        }
        ruleRef.resolve().applyToPhoneme(word, phonemes, graph)
    }

    override fun apply(rule: Rule, branch: RuleBranch?, word: Word, graph: GraphRepository): Word {
        if (seekTarget == null) return word
        val phonemes = PhonemeIterator(word.asPhonemic())
        if (phonemes.seek(seekTarget)) {
            ruleRef.resolve().applyToPhoneme(word, phonemes, graph)
            return word.derive(phonemes.result(), phonemic = true).asOrthographic()
        }
        return word
    }

    override fun reverseApply(rule: Rule, text: String, language: Language, graph: GraphRepository): List<String> {
        if (seekTarget == null) return listOf(text)
        val phonemes = PhonemeIterator(text, language)
        if (phonemes.seek(seekTarget)) {
            return ruleRef.resolve().reverseApplyToPhoneme(phonemes)
        }
        return listOf(text)
    }

    override fun toRichText(): RichText {
        val rule = ruleRef.resolve()
        return InstructionType.ApplySoundRule.insnName.rich() + " '".rich() +
                rule.name.rich(linkType = "rule", linkId = rule.id) + "'" +
                (seekTarget?.let { " to "} ?: "") +
                (seekTarget?.toEditableText()?.rich(emph = true) ?: "".rich())
    }

    companion object {
        fun parse(match: MatchResult, context: RuleParseContext): ApplySoundRuleInstruction {
            val ruleRef = context.ruleRefFactory(match.groupValues[1])
            return ApplySoundRuleInstruction(context.fromLanguage, ruleRef, match.groupValues[3].takeIf { it.isNotEmpty() })
        }
    }
}

class ApplyStressInstruction(val language: Language, arg: String) : RuleInstruction(InstructionType.ApplyStress, arg) {
    private val syllableIndex = Ordinals.parse(arg)?.first ?: throw RuleParseException("Can't parse ordinal '$arg'")

    override fun apply(rule: Rule, branch: RuleBranch?, word: Word, graph: GraphRepository): Word {
        val syllables = breakIntoSyllables(word)
        val vowel = language.phonemeClassByName(PhonemeClass.vowelClassName) ?: return word
        val syllable = Ordinals.at(syllables, syllableIndex) ?: return word
        val stressIndex = PhonemeIterator(word).findMatchInRange(syllable.startIndex, syllable.endIndex, vowel)
            ?: return word
        word.stressedPhonemeIndex = stressIndex    // TODO create a copy of the word here?
        return word
    }
}

class PrependAppendInstruction(type: InstructionType, language: Language, arg: String) : RuleInstruction(type, arg) {
    val seekTarget = if (arg.startsWith('\'')) null else SeekTarget.parse(arg, language)
    val literalArg = if (arg.startsWith('\'')) arg.removePrefix("'").removeSuffix("'") else null

    init {
        if (type != InstructionType.Append && type != InstructionType.Prepend) {
            throw IllegalStateException("Unsupported instruction type for this instruction implementation")
        }
    }

    override fun apply(rule: Rule, branch: RuleBranch?, word: Word, graph: GraphRepository): Word {
        if (literalArg != null) {
            return if (type == InstructionType.Prepend)
                word.derive(literalArg + word.text)
            else
                word.derive(word.text + literalArg, WordSegment(word.text.length, literalArg.length, rule.addedCategories, rule))
        }
        val phonemes = PhonemeIterator(word)
        if (phonemes.seek(seekTarget!!)) {
            val phoneme = phonemes.current
            if (type == InstructionType.Prepend) {
                return word.derive(phoneme + word.text)
            }
            return word.derive(word.text + phoneme, WordSegment(word.text.length, phoneme.length, rule.addedCategories, rule))
        }
        return word
    }

    override fun reverseApply(rule: Rule, text: String, language: Language, graph: GraphRepository): List<String> {
        if (literalArg != null) {
            return when (type) {
                InstructionType.Append -> if (text.endsWith(literalArg)) listOf(text.removeSuffix(literalArg)) else emptyList()
                else -> if (text.startsWith(literalArg)) listOf(text.removePrefix(literalArg)) else emptyList()
            }
        }
        return emptyList()
    }

    override fun toSummaryText(condition: RuleCondition): String? {
        if (literalArg != null) {
            return if (type == InstructionType.Prepend) "$literalArg-" else "-$literalArg"
        }
        return super.toSummaryText(condition)
    }
}

class InsertInstruction(arg: String, val relIndex: Int, val seekTarget: SeekTarget) : RuleInstruction(InstructionType.Insert, arg) {
    override fun apply(rule: Rule, branch: RuleBranch?, word: Word, graph: GraphRepository): Word {
        val phonemes = PhonemeIterator(word)
        if (!phonemes.seek(seekTarget)) return word
        if (relIndex == -1) {
            phonemes.insertAtRelative(0, arg)
        }
        else {
            phonemes.insertAtRelative(1, arg)
        }
        return word.derive(phonemes.result())
    }

    override fun toRichText(): RichText {
        val relIndexWord = if (relIndex == -1) "before" else "after"
        return "insert '$arg' $relIndexWord ${seekTarget.toEditableText()}".richText()
    }

    companion object {
        fun parse(language: Language, match: MatchResult): InsertInstruction {
            return InsertInstruction(
                match.groupValues[1],
                if (match.groupValues[2] == "before") -1 else 1,
                SeekTarget.parse(match.groupValues[3], language)
            )
        }
    }
}

class ChangePhonemeClassInstruction(val relativeIndex: Int, val oldClass: String, val newClass: String)
    : RuleInstruction(InstructionType.ChangeSoundClass, "")
{
    override fun apply(word: Word, phonemes: PhonemeIterator, graph: GraphRepository) {
        replacePhonemeClass(phonemes, oldClass, newClass)
    }

    private fun replacePhonemeClass(phonemes: PhonemeIterator, fromClass: String, toClass: String): Boolean {
        val phoneme = phonemes.language.phonemes.find { phonemes.atRelative(relativeIndex) in it.graphemes } ?: return false
        val newClasses = phoneme.classes.toMutableSet()
        if (fromClass !in newClasses) return false
        newClasses.remove(fromClass)
        newClasses.addAll(toClass.split(' '))
        val newPhoneme = phonemes.language.phonemes.singleOrNull { it.classes == newClasses } ?: return false
        phonemes.replaceAtRelative(relativeIndex, newPhoneme.graphemes[0])
        return true
    }

    override fun toRichText(): RichText {
        val relIndex = if (relativeIndex == 0) "" else RelativeOrdinals.toString(relativeIndex) + " "
        return relIndex.rich() + oldClass.rich(emph = true) + " becomes ".rich() + newClass.rich(emph = true)
    }

    override fun reverseApplyToPhoneme(phonemes: PhonemeIterator, condition: RuleCondition): List<String>? {
        val newClassRef = phonemes.language.phonemeClassByName(newClass) ?: return null
        if (newClassRef.matchesCurrent(phonemes)) {
            val newPhonemes = phonemes.clone()
            if (!replacePhonemeClass(newPhonemes, newClass, oldClass)) return null
            return listOf(newPhonemes.result())
        }
        return emptyList()
    }

    companion object {
        fun parse(match: MatchResult): ChangePhonemeClassInstruction {
            val relativeIndex = RelativeOrdinals.parseMatch(match, 1)
            return ChangePhonemeClassInstruction(relativeIndex, match.groupValues[2], match.groupValues[3])
        }
    }
}
