package ru.yole.etymograph

enum class ConditionType(
    val condName: String,
    val phonemic: Boolean = false,
    val takesArgument: Boolean = true,
    val parameterParseCallback: ((ParseBuffer, Language) -> String)? = null
) {
    EndsWith(LeafRuleCondition.wordEndsWith),
    ClassMatches(LeafRuleCondition.wordIs, parameterParseCallback = { buf, language ->
        val param = buf.nextWord() ?: throw RuleParseException("Word class expected")
        if (language.findWordClass(param) == null)
            throw RuleParseException("Unknown word class '$param'")
        param
    }),
    NumberOfSyllables(LeafRuleCondition.numberOfSyllables, parameterParseCallback = { buf, _ ->
        val param = buf.nextWord()
        if (param?.toIntOrNull() == null) throw RuleParseException("Number of syllables should be a number")
        param
    }),
    PhonemeMatches(LeafRuleCondition.soundIs, phonemic = true),
    StressIs(LeafRuleCondition.stressIs, parameterParseCallback = { buf, _ ->
        val ord = Ordinals.parse(buf)
        if (ord == null || !buf.consume("syllable")) {
            throw RuleParseException("Syllable reference expected")
        }
        "${Ordinals.toString(ord)} syllable"
    }),
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
        fun parse(buffer: ParseBuffer, language: Language): RuleCondition {
            if (buffer.consume(OtherwiseCondition.OTHERWISE)) {
                return OtherwiseCondition
            }
            return parseOrClause(buffer, language)
        }

        private fun parseOrClause(buffer: ParseBuffer, language: Language): RuleCondition {
            val c = parseAndClause(buffer, language)
            val branches = mutableListOf(c)
            while (buffer.consume("or")) {
                branches.add(parseAndClause(buffer, language))
            }
            return branches.singleOrNull() ?: OrRuleCondition(branches)
        }

        private fun parseAndClause(buffer: ParseBuffer, language: Language): RuleCondition {
            val c = LeafRuleCondition.parse(buffer, language)
            val branches = mutableListOf(c)
            while (buffer.consume("and")) {
                branches.add(LeafRuleCondition.parse(buffer, language))
            }
            return branches.singleOrNull() ?: AndRuleCondition(branches)
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
        val (expectedIndex, _) = parameter?.let { Ordinals.parse(it) } ?: return false
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
            val parameterName = if (type.parameterParseCallback != null)
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

        fun parse(buffer: ParseBuffer, language: Language): RuleCondition {
            buffer.tryParse { SyllableRuleCondition.parse(buffer, language) }?.let { return it }
            buffer.tryParse { RelativePhonemeRuleCondition.parse(buffer, language) }?.let { return it}

            for (conditionType in ConditionType.entries) {
                if (buffer.consume(conditionType.condName)) {
                    return parseLeafCondition(conditionType, buffer, language)
                }
            }
            buffer.fail("Unrecognized condition")
        }

        private fun parseLeafCondition(
            conditionType: ConditionType,
            buffer: ParseBuffer,
            language: Language
        ): LeafRuleCondition {
            val negated = buffer.consume(notPrefix)
            if (!conditionType.takesArgument) {
                return LeafRuleCondition(conditionType, null, null, negated)
            }
            val (phonemeClass, parameter) = if (conditionType.parameterParseCallback != null) {
                null to conditionType.parameterParseCallback.invoke(buffer, language)
            }
            else {
                buffer.parseParameter(language)
            }

            return LeafRuleCondition(conditionType, phonemeClass, parameter, negated)
        }
    }
}

enum class SyllableMatchType(val condName: String) {
    Contains("contains"),
    EndsWith("ends with");

    companion object  {
        fun parse(buffer: ParseBuffer): SyllableMatchType? {
            for (entry in entries) {
                if (buffer.consume(entry.condName)) {
                    return entry
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

        fun parse(buffer: ParseBuffer, language: Language): SyllableRuleCondition? {
            val index = Ordinals.parse(buffer) ?: return null
            if (!buffer.consume(syllable)) return null
            val matchType = SyllableMatchType.parse(buffer) ?: return null

            val (phonemeClass, parameter) = buffer.parseParameter(language)
            return SyllableRuleCondition(matchType, index,  phonemeClass, parameter)
        }
    }
}

class RelativePhonemeRuleCondition(
    val relativeIndex: Int,
    val negated: Boolean,
    val targetPhonemeClass: PhonemeClass?,
    val matchPhonemeClass: PhonemeClass?,
    val parameter: String?
) : RuleCondition() {
    override fun isPhonemic(): Boolean = true

    override fun matches(phonemes: PhonemeIterator): Boolean {
        val it = phonemes.clone()
        val canAdvance = if (targetPhonemeClass != null)
            it.advanceToClass(targetPhonemeClass, relativeIndex)
        else
            it.advanceBy(relativeIndex)
        val matchResult = canAdvance && matchPhonemeClass?.matchesCurrent(it) ?: (parameter == it.current)
        return matchResult xor negated
    }

    override fun toEditableText(): String {
        return buildString {
            append(RelativeOrdinals.toString(relativeIndex))
            append(" ")
            if (targetPhonemeClass != null) {
                append(targetPhonemeClass.name)
            }
            else {
                append("sound")
            }
            append(" is ")
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
        fun parse(buffer: ParseBuffer, language: Language): RelativePhonemeRuleCondition? {
            val relativeIndex = RelativeOrdinals.parse(buffer) ?: return null

            val targetPhonemeClass: PhonemeClass?
            if (buffer.consume(LeafRuleCondition.soundIs)) {
                targetPhonemeClass = null
            }
            else {
                val targetPhonemeClassName = buffer.nextWord() ?: return null
                if (!buffer.consume("is")) return null
                targetPhonemeClass = language.phonemeClassByName(targetPhonemeClassName)
                    ?: throw RuleParseException("Unrecognized character class $targetPhonemeClassName")
            }
            val negated = buffer.consume(LeafRuleCondition.notPrefix)
            val (matchPhonemeClass, parameter) = buffer.parseParameter(language)
            return RelativePhonemeRuleCondition(relativeIndex, negated, targetPhonemeClass, matchPhonemeClass, parameter)
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
