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
    ApplySoundRule("apply sound rule", "apply sound rule '(.+)' to (.+)", true),
    ApplyStress("stress is on", "stress is on (.+) syllable", true),
    ApplyClass("mark word as", "mark word as (.*)", true),
    Disallow("disallow", "disallow", false),
    ChangeSound("new sound is", "new sound is '(.+)'", true),
    ChangeNextSound("new next sound is", "new next sound is '(.+)'", true),
    ChangeSoundClass("becomes", "(previous\\s+|next\\s+)?(.+) becomes (.+)", true),
    SoundDisappears("sound disappears"),
    NextSoundDisappears("next sound disappears"),
    SoundInserted("is inserted", "'(.+)' is inserted before", true);

    val regex = Regex(pattern ?: Regex.escape(insnName))
}

const val DISALLOW_CLASS = "disallow"

open class RuleInstruction(val type: InstructionType, val arg: String) {
    open fun apply(rule: Rule, branch: RuleBranch?, word: Word, graph: GraphRepository): Word = when(type) {
        InstructionType.NoChange -> word
        InstructionType.ChangeEnding -> changeEnding(word, rule, branch)
        InstructionType.ApplyClass -> word.derive(word.text, newClasses = (word.classes + arg).toSet().toList())
        InstructionType.Disallow -> word.derive(word.text, newClasses = (word.classes + DISALLOW_CLASS).toSet().toList())
        else -> throw IllegalStateException("Can't apply phoneme instruction to full word")
    }

    private fun changeEnding(word: Word, rule: Rule, branch: RuleBranch?): Word {
        if (branch != null) {
            val condition = branch.condition.findLeafConditions(ConditionType.EndsWith)
                .firstOrNull { it.matches(word) }
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

    open fun reverseApply(rule: Rule, text: String, language: Language): List<String> {
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

    open fun apply(word: Word, phonemes: PhonemeIterator) {
        when (type) {
            InstructionType.ChangeSound -> phonemes.replace(arg)
            InstructionType.ChangeNextSound -> phonemes.replaceAtRelative(1, arg)
            InstructionType.SoundDisappears -> phonemes.delete()
            InstructionType.NextSoundDisappears -> phonemes.deleteNext()
            InstructionType.SoundInserted -> phonemes.insertBefore(arg)
            InstructionType.NoChange -> Unit
            else -> throw IllegalStateException("Can't apply word instruction to individual phoneme")
        }
    }

    open fun toEditableText(): String = type.pattern?.replace("(.+)", arg)?.replace("(.*)", arg) ?: type.insnName

    open fun toSummaryText(condition: RuleCondition): String? = when(type) {
        InstructionType.ChangeEnding ->
            if (arg.isNotEmpty()) "-$arg" else ""
        InstructionType.ChangeSound, InstructionType.ChangeNextSound,
        InstructionType.SoundDisappears, InstructionType.NextSoundDisappears,
        InstructionType.ChangeSoundClass ->
            toSummaryTextPhonemic(condition)
        else -> ""
    }

    protected fun toSummaryTextPhonemic(condition: RuleCondition): String? {
        val soundIs = condition.findLeafConditions(ConditionType.PhonemeMatches).singleOrNull() ?: return null
        val soundIsParameter = soundIs.phonemeClass?.name ?: "'${soundIs.parameter}'"
        var includeRelativePhoneme = true
        val changeSummary = when (type) {
            InstructionType.ChangeSound -> "$soundIsParameter -> '${arg}'"
            InstructionType.ChangeSoundClass -> (this as ChangePhonemeClassInstruction).oldClass + " -> " + newClass
            InstructionType.SoundDisappears -> "$soundIsParameter -> Ø"
            InstructionType.ChangeNextSound, InstructionType.NextSoundDisappears -> summarizeNextSound(condition).also {
                includeRelativePhoneme = false
            }
            else -> return null
        }
        val context = summarizeContext(condition, includeRelativePhoneme) ?: return null
        return changeSummary + context
    }

    private fun summarizeNextSound(condition: RuleCondition): String? {
        val soundIs = condition.findLeafConditions(ConditionType.PhonemeMatches).singleOrNull() ?: return null
        if (soundIs.phonemeClass != null) return null
        val nextPhonemeIs = condition.findLeafConditions { it is RelativePhonemeRuleCondition }.singleOrNull() as? RelativePhonemeRuleCondition
            ?: return null
        if (nextPhonemeIs.targetPhonemeClass != null || nextPhonemeIs.relativeIndex != 1) return null
        val nextSound = if (type == InstructionType.NextSoundDisappears) "" else arg
        return "'${soundIs.parameter}${nextPhonemeIs.parameter}' -> '${soundIs.parameter}$nextSound'"
    }

    private fun summarizeContext(condition: RuleCondition, includeRelativePhoneme: Boolean): String? {
        val relativePhonemeContext = if (includeRelativePhoneme) summarizeRelativePhoneme(condition) else ""
        val bow = condition.findLeafConditions(ConditionType.BeginningOfWord)
        if (bow.any()) return "$relativePhonemeContext at beginning of word"
        val eow = condition.findLeafConditions(ConditionType.EndOfWord)
        if (eow.any()) return "$relativePhonemeContext at end of word"
        val syllableIndexCondition = condition.findLeafConditions(ConditionType.SyllableIndex).singleOrNull()
        if (syllableIndexCondition != null) {
            val neg = if (syllableIndexCondition.negated) "not " else ""
            return "$relativePhonemeContext in $neg${Ordinals.toString(syllableIndexCondition.parameter!!.toInt())} syllable"
        }
        return relativePhonemeContext
    }

    private fun summarizeRelativePhoneme(condition: RuleCondition): String? {
        val relativePhonemes = condition.findLeafConditions { it is RelativePhonemeRuleCondition }
            .filterIsInstance<RelativePhonemeRuleCondition>()
        if (relativePhonemes.isEmpty()) return ""
        if (relativePhonemes.any { it.targetPhonemeClass != null }) return null

        val after = summarizeRelativePhonemeParameters(relativePhonemes, -1)
            .takeIf { it.isNotEmpty() }?.let { " after $it" } ?: ""
        val before = summarizeRelativePhonemeParameters(relativePhonemes, 1)
            .takeIf { it.isNotEmpty() }?.let { " before $it" } ?: ""
        return "$after$before"
    }

    private fun summarizeRelativePhonemeParameters(relativePhonemes: List<RelativePhonemeRuleCondition>, relIndex: Int): String {
        val relativePhonemeParameters = relativePhonemes.filter { it.relativeIndex == relIndex}.map {
            val p = it.matchPhonemeClass?.name ?: "'${it.parameter}'"
            val negation = if (it.negated) "not " else ""
            "$negation$p"
        }
        return relativePhonemeParameters.joinToString(" or ")
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
            ruleInstructions: List<RuleInstruction>
        ): List<String> {
            return ruleInstructions.reversed().fold(candidates) { c, instruction ->
                c.flatMap { instruction.reverseApply(rule, it, word.language) }
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

    override fun reverseApply(rule: Rule, text: String, language: Language): List<String> {
        val targetRule = ruleRef.resolve()
        return targetRule.reverseApply(Word(-1, text, language))
    }

    override fun toEditableText(): String =
        InstructionType.ApplyRule.insnName + " '" + ruleRef.resolve().name + "'"

    override fun toSummaryText(condition: RuleCondition): String =
        ruleRef.resolve().toSummaryText()
}

class ApplySoundRuleInstruction(language: Language, val ruleRef: RuleRef, arg: String)
    : RuleInstruction(InstructionType.ApplySoundRule, arg)
{
    val seekTarget = SeekTarget.parse(arg, language)

    override fun apply(rule: Rule, branch: RuleBranch?, word: Word, graph: GraphRepository): Word {
        val phonemes = PhonemeIterator(word.asPhonemic())
        if (phonemes.seek(seekTarget)) {
            ruleRef.resolve().applyToPhoneme(word, phonemes)
            return word.derive(phonemes.result(), phonemic = true).asOrthographic()
        }
        return word
    }

    override fun reverseApply(rule: Rule, text: String, language: Language): List<String> {
        val phonemes = PhonemeIterator(text, language)
        if (phonemes.seek(seekTarget)) {
            if (!ruleRef.resolve().reverseApplyToPhoneme(phonemes)) return emptyList()
            return listOf(phonemes.result())
        }
        return listOf(text)
    }

    override fun toEditableText(): String {
        return InstructionType.ApplySoundRule.insnName + " '" + ruleRef.resolve().name + "' to " + seekTarget.toEditableText()
    }

    companion object {
        fun parse(match: MatchResult, context: RuleParseContext): ApplySoundRuleInstruction {
            val ruleRef = context.ruleRefFactory(match.groupValues[1])
            return ApplySoundRuleInstruction(context.fromLanguage, ruleRef, match.groupValues[2])
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

    override fun reverseApply(rule: Rule, text: String, language: Language): List<String> {
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
            phonemes.insertBefore(arg)
        }
        else {
            phonemes.insertAfter(arg)
        }
        return word.derive(phonemes.result())
    }

    override fun toEditableText(): String {
        val relIndexWord = if (relIndex == -1) "before" else "after"
        return "insert '$arg' $relIndexWord ${seekTarget.toEditableText()}"
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
    override fun apply(word: Word, phonemes: PhonemeIterator) {
        val phoneme = phonemes.language.phonemes.find { phonemes.atRelative(relativeIndex) in it.graphemes } ?: return
        val newClasses = phoneme.classes.toMutableSet()
        if (oldClass !in newClasses) return
        newClasses.remove(oldClass)
        newClasses.add(newClass)
        val newPhoneme = phonemes.language.phonemes.singleOrNull { it.classes == newClasses } ?: return
        phonemes.replaceAtRelative(relativeIndex, newPhoneme.graphemes[0])
    }

    override fun toEditableText(): String {
        val relIndex = RelativeOrdinals.toString(relativeIndex)?.plus(" ") ?: ""
        return "$relIndex$oldClass becomes $newClass"
    }

    companion object {
        fun parse(match: MatchResult): ChangePhonemeClassInstruction {
            val relativeIndex = match.groupValues[1].takeIf { it.isNotEmpty() }?.trim()?.let {
                RelativeOrdinals.parse(it)?.first
            } ?: 0
            return ChangePhonemeClassInstruction(relativeIndex, match.groupValues[2], match.groupValues[3])
        }
    }
}
