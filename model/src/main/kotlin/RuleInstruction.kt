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
    ApplyRule("apply rule", "apply rule '(.+)'", true),
    ApplySoundRule("apply sound rule", "apply sound rule '(.+)' to (.+)", true),
    ApplyStress("stress is on", "stress is on (.+) syllable", true),
    ApplyClass("mark word as", "mark word as (.*)", true),
    ChangeSound("new sound is", "new sound is '(.+)'", true),
    SoundDisappears("sound disappears");

    val regex = Regex(pattern ?: Regex.escape(insnName))
}

open class RuleInstruction(val type: InstructionType, val arg: String) {
    open fun apply(rule: Rule, branch: RuleBranch?, word: Word, graph: GraphRepository): Word = when(type) {
        InstructionType.NoChange -> word
        InstructionType.ChangeEnding -> changeEnding(word, rule, branch)
        InstructionType.ApplyClass -> word.derive(word.text, newClasses = (word.classes + arg).toSet().toList())
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

    fun apply(phoneme: PhonemeIterator) {
        when (type) {
            InstructionType.ChangeSound -> phoneme.replace(arg)
            InstructionType.SoundDisappears -> phoneme.delete()
            else -> throw IllegalStateException("Can't apply word instruction to individual phoneme")
        }
    }

    open fun toEditableText(): String = type.pattern?.replace("(.+)", arg)?.replace("(.*)", arg) ?: type.insnName

    open fun toSummaryText() = when(type) {
        InstructionType.ChangeEnding -> if (arg.isNotEmpty()) "-$arg" else ""
        else -> ""
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
                        else -> RuleInstruction(type, arg)
                    }
                }
            }
            throw RuleParseException("Unrecognized instruction '$s'")
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
        return link?.fromEntity as? Word
            ?: targetRule.apply(word, graph).remapSegments { s ->
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

    override fun toSummaryText(): String =
        ruleRef.resolve().toSummaryText()
}

class ApplySoundRuleInstruction(language: Language, val ruleRef: RuleRef, arg: String)
    : RuleInstruction(InstructionType.ApplySoundRule, arg)
{
    val seekTarget = SeekTarget.parse(arg, language)

    override fun apply(rule: Rule, branch: RuleBranch?, word: Word, graph: GraphRepository): Word {
        val phonemes = PhonemeIterator(word)
        if (phonemes.seek(seekTarget)) {
            ruleRef.resolve().applyToPhoneme(phonemes)
            return word.derive(phonemes.result())
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

    override fun toSummaryText(): String {
        if (literalArg != null) {
            return if (type == InstructionType.Prepend) "$literalArg-" else "-$literalArg"
        }
        return super.toSummaryText()
    }
}
