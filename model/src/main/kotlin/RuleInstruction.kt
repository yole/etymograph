package ru.yole.etymograph

enum class InstructionType(val insnName: String, val takesArgument: Boolean) {
    NoChange("no change", false),
    RemoveLastCharacter("remove last character", false),
    AddSuffix("add suffix", true),
    ApplyRule("apply rule", true),
    ApplySoundRule("apply sound rule", true),
    ChangeSound("new sound is", true),
    SoundDisappears("sound disappears", false)
}

open class RuleInstruction(val type: InstructionType, val arg: String) {
    open fun apply(word: Word, graph: GraphRepository): Word = when(type) {
        InstructionType.NoChange -> word
        InstructionType.RemoveLastCharacter -> word.derive(word.text.substring(0, word.text.lastIndex))
        InstructionType.AddSuffix -> word.derive(word.text + arg)
        else -> throw IllegalStateException("Can't apply phoneme instruction to full word")
    }

    fun apply(phoneme: PhonemeIterator) {
        when (type) {
            InstructionType.ChangeSound -> phoneme.replace(arg)
            InstructionType.SoundDisappears -> phoneme.delete()
            else -> throw IllegalStateException("Can't apply word instruction to individual phoneme")
        }
    }

    open fun toEditableText(): String = type.insnName + (if (type.takesArgument)  " '$arg'" else "")

    open fun toSummaryText() = when(type) {
        InstructionType.AddSuffix -> "-$arg"
        else -> ""
    }

    companion object {
        fun parse(s: String, context: RuleParseContext): RuleInstruction {
            for (type in InstructionType.values()) {
                if (type.takesArgument && s.startsWith(type.insnName + " '")) {
                    val arg = s.removePrefix(type.insnName + " '").removeSuffix("'")
                    return when(type) {
                        InstructionType.ApplyRule -> ApplyRuleInstruction(context.ruleRefFactory(arg))
                        InstructionType.ApplySoundRule -> ApplySoundRuleInstruction.parse(arg, context)
                        else -> RuleInstruction(type, arg)
                    }
                }
                if (!type.takesArgument && s == type.insnName) {
                    return RuleInstruction(type, "")
                }
            }
            throw RuleParseException("Unrecognized instruction $s")
        }
    }
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
        const val delimiter = "' to"

        fun parse(s: String, context: RuleParseContext): ApplySoundRuleInstruction {
            val index = s.indexOf(delimiter)
            if (index < 0) {
                throw RuleParseException("Incorrect syntax of 'apply sound rule'")
            }
            val ruleRef = context.ruleRefFactory(s.substring(0, index))
            return ApplySoundRuleInstruction(context.fromLanguage, ruleRef, s.substring(index + delimiter.length).trim())
        }
    }
}
