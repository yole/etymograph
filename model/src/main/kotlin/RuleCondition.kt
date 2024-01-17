package ru.yole.etymograph

enum class ConditionType(
    val condName: String,
    val phonemic: Boolean = false,
    val takesArgument: Boolean = true,
    val takesPhonemeClass: Boolean = true
) {
    EndsWith(LeafRuleCondition.wordEndsWith),
    ClassMatches(LeafRuleCondition.wordIs, takesPhonemeClass = false),
    NumberOfSyllables(LeafRuleCondition.numberOfSyllables, takesPhonemeClass = false),
    PhonemeMatches(LeafRuleCondition.soundIs, phonemic = true),
    StressIs(LeafRuleCondition.stressIs, takesPhonemeClass = false),
    BeginningOfWord(LeafRuleCondition.beginningOfWord, phonemic = true, takesArgument = false)
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

    open fun findLeafConditions(type: ConditionType): List<LeafRuleCondition> {
        return emptyList()
    }

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

        @JvmStatic
        protected fun parseParameter(language: Language, tail: String): Pair<PhonemeClass?, String?> {
            if (tail.startsWith('\'')) {
                return null to tail.removePrefix("'").removeSuffix("'")
            }
            val characterClass = language.phonemeClassByName(tail.removePrefix(LeafRuleCondition.indefiniteArticle))
                ?: throw RuleParseException("Unrecognized character class $tail")
            return characterClass to null
        }
    }
}

class LeafRuleCondition(
    val type: ConditionType,
    val phonemeClass: PhonemeClass?,
    val parameter: String?,
    val negated: Boolean
) : RuleCondition() {
    override fun isPhonemic(): Boolean = type.phonemic

    private fun Boolean.negateIfNeeded() = if (negated) !this else this

    override fun matches(word: Word): Boolean {
        return when (type) {
            ConditionType.EndsWith -> (phonemeClass?.let { PhonemeIterator(word).last in it.matchingPhonemes }
                ?: word.text.trimEnd('-').endsWith(parameter!!)).negateIfNeeded()
            ConditionType.NumberOfSyllables -> breakIntoSyllables(word).size == parameter!!.toInt()
            ConditionType.StressIs -> matchStress(word)
            ConditionType.ClassMatches -> parameter in word.classes || word.classes == listOf("*")
            else -> super.matches(word)
        }
    }

    private fun matchStress(word: Word): Boolean {
        val (expectedIndex, _) = parameter?.let { Ordinals.parse(it) } ?: return false // TODO throw RuleParseException
        val stress = word.calculateStress() ?: return false
        val syllables = breakIntoSyllables(word)
        val expectedStressSyllable = Ordinals.at(syllables, expectedIndex) ?: return false
        return stress.index >= expectedStressSyllable.startIndex && stress.index < expectedStressSyllable.endIndex
    }

    override fun matches(phonemes: PhonemeIterator): Boolean {
        return when (type) {
            ConditionType.PhonemeMatches -> matchPhoneme(phonemes.current)
            ConditionType.BeginningOfWord -> phonemes.atBeginning()
            else -> throw IllegalStateException("Trying to use a word condition for matching phonemes")
        }
    }

    private fun matchPhoneme(phoneme: String?) =
        (phonemeClass?.let { phoneme != null && phoneme in it.matchingPhonemes }
            ?: (phoneme == parameter)).negateIfNeeded()

    override fun toEditableText(): String =
        if (type.takesArgument) {
            val parameterName = if (!type.takesPhonemeClass)
                parameter
            else
                phonemeClass?.name?.let { "a $it" } ?: "'$parameter'"

            type.condName + (if (negated) notPrefix else "") + parameterName
        }
        else
            type.condName

    override fun findLeafConditions(type: ConditionType): List<LeafRuleCondition> {
        return if (type == this.type) listOf(this) else emptyList()
    }

    companion object {
        const val wordEndsWith = "word ends with "
        const val wordIs = "word is "
        const val numberOfSyllables = "number of syllables is "
        const val soundIs = "sound is "
        const val stressIs = "stress is on "
        const val notPrefix = "not "
        const val beginningOfWord = "beginning of word"
        const val indefiniteArticle = "a "

        fun parse(s: String, language: Language): RuleCondition {
            if (SyllableRuleCondition.syllable in s) {
                SyllableRuleCondition.parse(s, language)?.let { return it }
            }

            val relOrd = RelativeOrdinals.parse(s)
            if (relOrd != null) {
                RelativePhonemeRuleCondition.parse(language, relOrd.first, relOrd.second)?.let { return it}
            }

            for (conditionType in ConditionType.entries) {
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
            val (phonemeClass, parameter) = if (!conditionType.takesPhonemeClass)
                null to condition.removePrefix("'").removeSuffix("'")
            else
                parseParameter(language, condition)
            return LeafRuleCondition(conditionType, phonemeClass, parameter, negated)
        }
    }
}

enum class SyllableMatchType(val condName: String) {
    Contains("contains"),
    EndsWith("ends with");

    companion object  {
        fun parse(s: String): Pair<SyllableMatchType, String>? {
            for (entry in entries) {
                if (s.startsWith(entry.condName)) {
                    return entry to s.removePrefix(entry.condName).trim()
                }
            }
            return null
        }
    }
}

class SyllableRuleCondition(
    val matchType: SyllableMatchType,
    val index: Int,
    val phonemeClass: PhonemeClass?,
    val parameter: String?
) : RuleCondition() {
    override fun isPhonemic(): Boolean = false

    override fun matches(word: Word): Boolean {
        val syllables = breakIntoSyllables(word)
        val syllable = Ordinals.at(syllables, index) ?: return false
        val phonemes = PhonemeIterator(word)
        if (matchType == SyllableMatchType.Contains) {
            if (phonemeClass != null) {
                return phonemes.findMatchInRange(syllable.startIndex, syllable.endIndex, phonemeClass) != null
            }
            return parameter!! in word.text.substring(syllable.startIndex, syllable.endIndex)
        }
        else if (matchType == SyllableMatchType.EndsWith) {
            if (phonemeClass != null) {
                phonemes.advanceTo(syllable.endIndex - 1)
                return phonemeClass.matchesCurrent(phonemes)
            }
            return word.text.substring(syllable.startIndex, syllable.endIndex).endsWith(parameter!!)
        }
        return false
    }

    override fun matches(phonemes: PhonemeIterator): Boolean {
        throw IllegalStateException("This condition is not phonemic")
    }

    override fun toEditableText(): String {
        val paramText = if (phonemeClass != null) "a ${phonemeClass.name}" else "'$parameter'"
        return "${Ordinals.toString(index)} $syllable ${matchType.condName} $paramText"
    }

    companion object {
        const val syllable = "syllable"

        fun parse(s: String, language: Language): SyllableRuleCondition? {
            val i = s.indexOf(syllable)
            val prefix = s.substring(0, i).trim()
            val index = Ordinals.parse(prefix)?.first ?: return null
            val tail = s.substring(i).removePrefix(syllable).trim()

            val (matchType, phonemeClassName) = SyllableMatchType.parse(tail)
                ?: return null

            val (phonemeClass, parameter) = parseParameter(language, phonemeClassName)
            return SyllableRuleCondition(matchType, index,  phonemeClass, parameter)
        }
    }
}

class RelativePhonemeRuleCondition(
    val relativeIndex: Int,
    val negated: Boolean,
    val matchPhonemeClass: PhonemeClass?,
    val parameter: String?
) : RuleCondition() {
    override fun isPhonemic(): Boolean = true

    override fun matches(phonemes: PhonemeIterator): Boolean {
        val it = phonemes.clone()
        val matchResult = if (!it.advanceBy(relativeIndex)) {
            false
        }
        else {
            matchPhonemeClass?.matchesCurrent(it) ?: (parameter == it.current)
        }
        return matchResult xor negated
    }

    override fun toEditableText(): String {
        return buildString {
            append(RelativeOrdinals.toString(relativeIndex))
            append(" sound is ")
            if (negated) {
                append(LeafRuleCondition.notPrefix)
            }
            if (matchPhonemeClass != null) {
                append(matchPhonemeClass.name)
            }
            else {
                append("'$parameter'")
            }
        }
    }

    companion object {
        fun parse(language: Language, relativeIndex: Int, tail: String): RelativePhonemeRuleCondition? {
            if (!tail.startsWith(LeafRuleCondition.soundIs)) {
                return null
            }
            var param = tail.removePrefix(LeafRuleCondition.soundIs)
            var negated = false

            if (param.startsWith(LeafRuleCondition.notPrefix)) {
                negated = true
                param = param.removePrefix(LeafRuleCondition.notPrefix)
            }
            param = param.trim()
            val (phonemeClass, parameter) = parseParameter(language, param)
            return RelativePhonemeRuleCondition(relativeIndex, negated, phonemeClass, parameter)
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

    override fun findLeafConditions(type: ConditionType): List<LeafRuleCondition> {
        return members.flatMap { it.findLeafConditions(type) }
    }

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

    override fun findLeafConditions(type: ConditionType): List<LeafRuleCondition> {
        return members.flatMap { it.findLeafConditions(type) }
    }

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
