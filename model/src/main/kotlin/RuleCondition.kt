package ru.yole.etymograph

enum class ConditionType(val condName: String, val takesArgument: Boolean = true) {
    EndsWith(LeafRuleCondition.wordEndsWith),
    PhonemeMatches(LeafRuleCondition.soundIs),
    PrevPhonemeMatches(LeafRuleCondition.prevSoundIs),
    BeginningOfWord(LeafRuleCondition.beginningOfWord, false)
}

sealed class RuleCondition {
    abstract fun isPhonemic(): Boolean
    open fun matches(word: Word): Boolean {
        val it = PhonemeIterator(word)
        while (true) {
            if (matches(it)) return true
            if (!it.advance()) break
        }
        return false
    }

    abstract fun matches(phonemes: PhonemeIterator): Boolean
    abstract fun toEditableText(): String

    companion object {
        fun parse(s: String, language: Language): RuleCondition {
            if (s == OtherwiseCondition.OTHERWISE) {
                return OtherwiseCondition
            }
            val orBranches = s.split(OrRuleCondition.OR)
            if (orBranches.size > 1) {
                return OrRuleCondition(orBranches.map { parse(it, language) })
            }
            val andBranches = s.split(AndRuleCondition.AND)
            if (andBranches.size > 1) {
                return AndRuleCondition(andBranches.map { parse(it, language) })
            }
            return LeafRuleCondition.parse(s, language)
        }
    }
}

class LeafRuleCondition(
    val type: ConditionType,
    val phonemeClass: PhonemeClass?,
    val parameter: String?,
    val negated: Boolean
) : RuleCondition() {
    override fun isPhonemic(): Boolean {
        return type == ConditionType.PhonemeMatches
    }

    private fun Boolean.negateIfNeeded() = if (negated) !this else this

    override fun matches(word: Word): Boolean {
        return when (type) {
            ConditionType.EndsWith -> (phonemeClass?.let { PhonemeIterator(word).last in it.matchingPhonemes }
                ?: word.text.trimEnd('-').endsWith(parameter!!)).negateIfNeeded()
            else -> super.matches(word)
        }
    }

    override fun matches(phonemes: PhonemeIterator): Boolean {
        return when (type) {
            ConditionType.PhonemeMatches -> matchPhoneme(phonemes.current)
            ConditionType.PrevPhonemeMatches -> matchPhoneme(phonemes.previous)
            ConditionType.BeginningOfWord -> phonemes.atBeginning()
            else -> throw IllegalStateException("Trying to use a word condition for matching phonemes")
        }
    }

    private fun matchPhoneme(phoneme: String?) =
        (phonemeClass?.let { phoneme != null && phoneme in it.matchingPhonemes }
            ?: (phoneme == parameter)).negateIfNeeded()

    override fun toEditableText(): String =
        if (type.takesArgument)
            type.condName + (if (negated) notPrefix else "") + (phonemeClass?.name?.let { "a $it" } ?: "'$parameter'")
        else
            type.condName

    companion object {
        const val wordEndsWith = "word ends with "
        const val soundIs = "sound is "
        const val prevSoundIs = "previous sound is "
        const val notPrefix = "not "
        const val beginningOfWord = "beginning of word"
        const val indefiniteArticle = "a "

        fun parse(s: String, language: Language): RuleCondition {
            if (SyllableRuleCondition.syllableContains in s) {
                return SyllableRuleCondition.parse(s, language)
            }

            for (conditionType in ConditionType.values()) {
                if (s.startsWith(conditionType.condName)) {
                    return parseLeafCondition(conditionType, s.removePrefix(conditionType.condName), language)
                }
            }
            throw RuleParseException("Unrecognized condition $s")
        }

        private fun parseLeafCondition(
            conditionType: ConditionType,
            c: String,
            language: Language
        ): LeafRuleCondition {
            var negated = false
            var condition = c
            if (c.startsWith(notPrefix)) {
                negated = true
                condition = c.removePrefix(notPrefix)
            }
            if (!conditionType.takesArgument) {
                return LeafRuleCondition(conditionType, null, null, negated)
            }
            if (condition.startsWith('\'')) {
                return LeafRuleCondition(conditionType, null,
                    condition.removePrefix("'").removeSuffix("'"),
                    negated)
            }
            val characterClass = language.phonemeClassByName(condition.removePrefix(indefiniteArticle))
                ?: throw RuleParseException("Unrecognized character class $c")
            return LeafRuleCondition(conditionType, characterClass, null, negated)
        }
    }
}

class SyllableRuleCondition(val index: Int, val phonemeClass: PhonemeClass) : RuleCondition() {
    override fun isPhonemic(): Boolean = false

    override fun matches(word: Word): Boolean {
        val syllables = breakIntoSyllables(word)
        val indexToMatch = if (index < 0) syllables.size + index else index
        if (indexToMatch !in syllables.indices) return false
        val syllable = syllables[indexToMatch]
        val phonemes = PhonemeIterator(word)
        for (i in syllable.startIndex until syllable.endIndex) {
            if (phonemes[i] in phonemeClass.matchingPhonemes) {
                return true
            }
        }
        return false
    }

    override fun matches(phonemes: PhonemeIterator): Boolean {
        throw IllegalStateException("This condition is not phonemic")
    }

    override fun toEditableText(): String {
        return "${Ordinals.toString(index)} $syllableContains a ${phonemeClass.name}"
    }

    companion object {
        const val syllableContains = "syllable contains"

        fun parse(s: String, language: Language): SyllableRuleCondition {
            val i = s.indexOf(syllableContains)
            val prefix = s.substring(0, i).trim()
            val index = Ordinals.parse(prefix)?.first ?: throw RuleParseException("Cannot parse ordinal $prefix")
            val tail = s.substring(i)
                .removePrefix(syllableContains).trim()
                .removePrefix(LeafRuleCondition.indefiniteArticle).trim()
            val phonemeClass = language.phonemeClassByName(tail) ?: throw RuleParseException("Unknown phoneme class $tail")
            return SyllableRuleCondition(index, phonemeClass)
        }
    }
}

class OrRuleCondition(val members: List<RuleCondition>) : RuleCondition() {
    override fun isPhonemic(): Boolean {
        return members.any { it.isPhonemic() }
    }

    override fun matches(word: Word): Boolean = members.any { it.matches(word) }

    override fun matches(phonemes: PhonemeIterator) = members.any { it.matches(phonemes) }

    override fun toEditableText(): String = members.joinToString(OR) { it.toEditableText() }

    companion object {
        const val OR = " or "
    }
}

class AndRuleCondition(val members: List<RuleCondition>) : RuleCondition() {
    override fun isPhonemic(): Boolean {
        return members.any { it.isPhonemic() }
    }

    override fun matches(word: Word): Boolean = members.all { it.matches(word) }

    override fun matches(phonemes: PhonemeIterator) = members.all { it.matches(phonemes) }

    override fun toEditableText(): String = members.joinToString(AND) { it.toEditableText() }

    companion object {
        const val AND = " and "
    }
}

object OtherwiseCondition : RuleCondition() {
    override fun isPhonemic(): Boolean = false
    override fun matches(word: Word): Boolean = true
    override fun matches(phonemes: PhonemeIterator) = true
    override fun toEditableText(): String = OTHERWISE

    const val OTHERWISE = "otherwise"
}
