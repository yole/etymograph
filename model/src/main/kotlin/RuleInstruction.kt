package ru.yole.etymograph

enum class InstructionType(
    val insnName: String,
    @org.intellij.lang.annotations.Language("RegExp") val pattern: String? = null,
    val takesArgument: Boolean = false
) {
    NoChange("no change"),
    RemoveLastCharacter("remove last character"),
    AddSuffix("add suffix", "add suffix '(.+)'", true),
    Prepend("prepend", "prepend (.+)", true),
    ApplyRule("apply rule", "apply rule '(.+)'", true),
    ApplySoundRule("apply sound rule", "apply sound rule '(.+)' to (.+)", true),
    ApplyStress("stress is on", "stress is on (.+) syllable", true),
    ChangeSound("new sound is", "new sound is '(.+)'", true),
    SoundDisappears("sound disappears");

    val regex = Regex(pattern ?: Regex.escape(insnName))
}

open class RuleInstruction(val type: InstructionType, val arg: String) {
    open fun apply(word: Word, graph: GraphRepository): Word = when(type) {
        InstructionType.NoChange -> word
        InstructionType.RemoveLastCharacter -> word.derive(word.text.substring(0, word.text.lastIndex))
        InstructionType.AddSuffix -> word.derive(word.text + arg)
        else -> throw IllegalStateException("Can't apply phoneme instruction to full word")
    }

    open fun reverseApply(text: String): String? {
        return when (type) {
            InstructionType.AddSuffix -> if (text.endsWith(arg)) text.removeSuffix(arg) else null
            else -> null
        }
    }

    fun apply(phoneme: PhonemeIterator) {
        when (type) {
            InstructionType.ChangeSound -> phoneme.replace(arg)
            InstructionType.SoundDisappears -> phoneme.delete()
            else -> throw IllegalStateException("Can't apply word instruction to individual phoneme")
        }
    }

    open fun toEditableText(): String = type.pattern?.replace("(.+)", arg) ?: type.insnName

    open fun toSummaryText() = when(type) {
        InstructionType.AddSuffix -> "-$arg"
        else -> ""
    }

    companion object {
        fun parse(s: String, context: RuleParseContext): RuleInstruction {
            if (!s.startsWith("-")) {
                throw RuleParseException("Instructions must start with -")
            }
            val trimmed = s.removePrefix("-").trim()
            for (type in InstructionType.values()) {
                val match = type.regex.matchEntire(trimmed)
                if (match != null) {
                    val arg = if (match.groups.size > 1) match.groupValues[1] else ""
                    return when(type) {
                        InstructionType.ApplyRule -> ApplyRuleInstruction(context.ruleRefFactory(arg))
                        InstructionType.ApplySoundRule -> ApplySoundRuleInstruction.parse(match, context)
                        InstructionType.ApplyStress -> ApplyStressInstruction(context.fromLanguage, arg)
                        InstructionType.Prepend -> PrependInstruction(context.fromLanguage, arg)
                        else -> RuleInstruction(type, arg)
                    }
                }
            }
            throw RuleParseException("Unrecognized instruction '$s'")
        }
    }
}

fun List<RuleInstruction>.apply(word: Word, graph: GraphRepository): Word {
    val normalizedWord = word.derive(word.text.trimEnd('-'))
    return fold(normalizedWord) { s, i -> i.apply(s, graph) }
}

class ApplyRuleInstruction(val ruleRef: RuleRef)
    : RuleInstruction(InstructionType.ApplyRule, "")
{
    override fun apply(word: Word, graph: GraphRepository): Word {
        val rule = ruleRef.resolve()
        val link = graph.getLinksTo(word).find { it.rules == listOf(rule) }
        return link?.fromEntity as? Word ?: rule.apply(word, graph)
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

    override fun apply(word: Word, graph: GraphRepository): Word {
        val phonemes = PhonemeIterator(word)
        if (phonemes.seek(seekTarget)) {
            ruleRef.resolve().applyToPhoneme(phonemes)
            return word.derive(phonemes.result())
        }
        return word
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

    override fun apply(word: Word, graph: GraphRepository): Word {
        val syllables = breakIntoSyllables(word)
        val vowel = language.phonemeClassByName(PhonemeClass.vowelClassName) ?: return word
        val syllable = Ordinals.at(syllables, syllableIndex) ?: return word
        val stressIndex = PhonemeIterator(word).findMatchInRange(syllable.startIndex, syllable.endIndex, vowel)
            ?: return word
        word.stressedPhonemeIndex = stressIndex    // TODO create a copy of the word here?
        return word
    }
}

class PrependInstruction(language: Language, arg: String) : RuleInstruction(InstructionType.Prepend, arg) {
    val seekTarget = SeekTarget.parse(arg, language)

    override fun apply(word: Word, graph: GraphRepository): Word {
        val phonemes = PhonemeIterator(word)
        if (phonemes.seek(seekTarget)) {
            return word.derive(phonemes.current + word.text)
        }
        return word
    }
}
