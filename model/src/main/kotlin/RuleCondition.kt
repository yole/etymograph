package ru.yole.etymograph

enum class ConditionType(
    val condName: String,
    val condNameAfterBaseLanguage: String? = null,
    val phonemic: Boolean = false,
    val takesArgument: Boolean = true,
    val parameterParseCallback: ((ParseBuffer, Language) -> String)? = null
) {
    EndsWith(LeafRuleCondition.wordEndsWith, condNameAfterBaseLanguage = LeafRuleCondition.endsWith),
    BeginsWith(LeafRuleCondition.wordBeginsWith),
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
    BeginningOfWord(LeafRuleCondition.beginningOfWord, phonemic = true, takesArgument = false),
    EndOfWord(LeafRuleCondition.endOfWord, phonemic = true, takesArgument = false),
    SyllableIsStressed(LeafRuleCondition.syllableIsStressed, phonemic = true, takesArgument = false),
    SyllableIndex(LeafRuleCondition.syllableIs, phonemic = true, parameterParseCallback = { buf, _ ->
        val param = Ordinals.parse(buf)
        if (param == null) throw RuleParseException("Invalid syllable index $param")
        param.toString()
    })
}

sealed class RuleCondition {
    abstract fun isPhonemic(): Boolean
    open fun matches(word: Word, graph: GraphRepository): Boolean {
        val it = PhonemeIterator(word)
        while (true) {
            if (matches(word, it)) return true
            if (!it.advance()) break
        }
        return false
    }

    abstract fun matches(word: Word, phonemes: PhonemeIterator): Boolean

    fun toEditableText(): String {
        return toRichText().toString()
    }

    abstract fun toRichText(): RichText

    open fun findLeafConditions(predicate: (RuleCondition) -> Boolean): List<RuleCondition> {
        return if (predicate(this)) listOf(this) else emptyList()
    }

    fun findLeafConditions(type: ConditionType): List<LeafRuleCondition> =
        findLeafConditions { it is LeafRuleCondition && it.type == type }.filterIsInstance<LeafRuleCondition>()

    open fun refersToPhoneme(phoneme: Phoneme): Boolean = false

    protected fun checkRefersToPhoneme(
        phoneme: Phoneme,
        parameter: String?,
        phonemeClass: PhonemeClass?
    ): Boolean {
        val phonemeAsString = if (isPhonemic()) phoneme.effectiveSound else phoneme.graphemes[0]
        if (parameter != null) {
            return parameter == phonemeAsString
        }
        if (phonemeClass != null) {
            return phonemeAsString in phonemeClass.matchingPhonemes
        }
        return false
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
            val c = parseLeaf(buffer, language)
            val branches = mutableListOf(c)
            while (buffer.consume("and")) {
                branches.add(parseLeaf(buffer, language))
            }
            return branches.singleOrNull() ?: AndRuleCondition(branches)
        }

        private fun parseLeaf(buffer: ParseBuffer, language: Language): RuleCondition {
            if (buffer.consume("(")) {
                val inParentheses = parseOrClause(buffer, language)
                if (!buffer.consume(")")) {
                    buffer.fail("Closing parenthesis expected")
                }
                return inParentheses
            }
            return LeafRuleCondition.parse(buffer, language)
        }
    }
}

class LeafRuleCondition(
    val type: ConditionType,
    val phonemeClass: PhonemeClass?,
    val parameter: String?,
    val negated: Boolean,
    val baseLanguageShortName: String?
) : RuleCondition() {
    override fun isPhonemic(): Boolean = type.phonemic

    private fun Boolean.negateIfNeeded() = if (negated) !this else this

    override fun matches(word: Word, graph: GraphRepository): Boolean {
        val matchWord = graph.findWordToMatch(word, baseLanguageShortName) ?: return false
        return when (type) {
            ConditionType.EndsWith -> (phonemeClass?.let { PhonemeIterator(matchWord).last in it.matchingPhonemes }
                ?: matchWord.text.trimEnd('-').endsWith(parameter!!)).negateIfNeeded()
            ConditionType.BeginsWith -> (phonemeClass?.let { PhonemeIterator(word).current in it.matchingPhonemes }
                ?: word.text.startsWith(parameter!!)).negateIfNeeded()
            ConditionType.NumberOfSyllables -> matchNumberOfSyllables(word)
            ConditionType.StressIs -> matchStress(word).negateIfNeeded()
            ConditionType.ClassMatches -> matchClass(word)
            else -> super.matches(word, graph)
        }
    }

    private fun matchNumberOfSyllables(word: Word) =
        (breakIntoSyllables(word).size == parameter!!.toInt()).negateIfNeeded()

    private fun matchClass(word: Word) =
        (parameter in word.classes).negateIfNeeded() || word.classes == listOf("*")

    private fun matchStress(word: Word): Boolean {
        val (expectedIndex, _) = parameter?.let { Ordinals.parse(it) } ?: return false
        val stress = word.calculateStress() ?: return false
        val syllables = breakIntoSyllables(word)
        val expectedStressSyllable = Ordinals.at(syllables, expectedIndex) ?: return false
        return stress.index >= expectedStressSyllable.startIndex && stress.index < expectedStressSyllable.endIndex
    }

    override fun matches(word: Word, phonemes: PhonemeIterator): Boolean {
        return when (type) {
            ConditionType.PhonemeMatches -> matchPhoneme(phonemes)
            ConditionType.BeginningOfWord -> phonemes.atBeginning().negateIfNeeded()
            ConditionType.EndOfWord -> phonemes.atEnd().negateIfNeeded()
            ConditionType.SyllableIsStressed -> word.calcStressedPhonemeIndex() == phonemes.index
            ConditionType.SyllableIndex -> matchSyllableIndex(phonemes, word)
            ConditionType.ClassMatches -> matchClass(word)
            ConditionType.NumberOfSyllables -> matchNumberOfSyllables(word)
            else -> throw IllegalStateException("Trying to use a word condition for matching phonemes")
        }
    }

    private fun matchSyllableIndex(phonemes: PhonemeIterator, word: Word): Boolean {
        val syllables = breakIntoSyllables(word)
        if (syllables.isEmpty()) return false.negateIfNeeded()
        val absIndex = Ordinals.toAbsoluteIndex(parameter!!.toInt(), syllables.size)
        for ((i, s) in syllables.withIndex()) {
            if (phonemes.index < s.endIndex) {
                return (i == absIndex).negateIfNeeded()
            }
        }
        return false.negateIfNeeded()
    }

    private fun matchPhoneme(phonemes: PhonemeIterator) =
        (phonemeClass?.matchesCurrent(phonemes) ?: (phonemes.current == parameter)).negateIfNeeded()

    override fun toRichText(): RichText {
        val maybeNotPrefix = (if (negated) notPrefix else "").rich()
        return if (type.takesArgument) {
            val parameterName = parameterToEditableText()
            val condName = if (baseLanguageShortName != null) {
                "base word in ".rich() + baseLanguageShortName.rich(emph = true) + " " + type.condNameAfterBaseLanguage!!.rich()
            }
            else {
                type.condName.richText()
            }
            condName + maybeNotPrefix + (parameterName ?: "").rich(emph = true)
        } else {
            maybeNotPrefix + type.condName.rich()
        }
    }

    private fun parameterToEditableText(): String? {
        if (type == ConditionType.SyllableIndex) {
            return Ordinals.toString(parameter!!.toInt())
        }

        val parameterName = if (type.parameterParseCallback != null)
            parameter
        else
            phonemeClass?.name?.let { "a $it" } ?: "'$parameter'"
        return parameterName
    }

    override fun refersToPhoneme(phoneme: Phoneme): Boolean {
        return when(type) {
            ConditionType.ClassMatches -> false
            else -> checkRefersToPhoneme(phoneme, parameter, phonemeClass)
        }
    }

    companion object {
        const val wordEndsWith = "word ends with "
        const val endsWith = "ends with "
        const val wordBeginsWith = "word begins with "
        const val wordIs = "word is "
        const val numberOfSyllables = "number of syllables is "
        const val soundIs = "sound is "
        const val stressIs = "stress is on "
        const val notPrefix = "not "
        const val beginningOfWord = "beginning of word"
        const val endOfWord = "end of word"
        const val syllableIsStressed = "syllable is stressed"
        const val syllableIs = "syllable is "
        const val indefiniteArticle = "a "

        fun parse(buffer: ParseBuffer, language: Language): RuleCondition {
            buffer.tryParse { SyllableRuleCondition.parse(buffer, language) }?.let { return it }
            buffer.tryParse { RelativePhonemeRuleCondition.parse(buffer, language) }?.let { return it }

            val baseLanguageShortName = if (buffer.consume("base word in")) {
                buffer.nextWord()
            }
            else {
                null
            }

            for (conditionType in ConditionType.entries) {
                val condNameToMatch = if (baseLanguageShortName != null) conditionType.condNameAfterBaseLanguage else conditionType.condName
                if (condNameToMatch != null && buffer.consume(condNameToMatch)) {
                    val negated = conditionType.takesArgument && buffer.consume(notPrefix)
                    return parseLeafCondition(conditionType, buffer, language, negated, baseLanguageShortName)
                }
                else if (!conditionType.takesArgument && buffer.consume("$notPrefix${conditionType.condName}")) {
                    return parseLeafCondition(conditionType, buffer, language, true, baseLanguageShortName)
                }
            }
            buffer.fail("Unrecognized condition")
        }

        private fun parseLeafCondition(
            conditionType: ConditionType,
            buffer: ParseBuffer,
            language: Language,
            negated: Boolean,
            baseLanguageShortName: String?
        ): LeafRuleCondition {
            if (!conditionType.takesArgument) {
                return LeafRuleCondition(conditionType, null, null, negated, null)
            }

            val (phonemeClass, parameter) = if (conditionType.parameterParseCallback != null) {
                null to conditionType.parameterParseCallback.invoke(buffer, language)
            }
            else {
                buffer.parseParameter(language)
            }

            return LeafRuleCondition(conditionType, phonemeClass, parameter, negated, baseLanguageShortName)
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

    override fun matches(word: Word, graph: GraphRepository): Boolean {
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

    override fun matches(word: Word, phonemes: PhonemeIterator): Boolean {
        throw IllegalStateException("This condition is not phonemic")
    }

    override fun toRichText(): RichText {
        val paramText = if (phonemeClass != null) "a ${phonemeClass.name}" else "'$parameter'"
        return richText("${Ordinals.toString(index)} $syllable ${matchType.condName} $paramText".rich())
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

fun GraphRepository.findWordToMatch(word: Word, baseLanguageShortName: String?): Word? {
    if (baseLanguageShortName != null) {
        val baseLang = languageByShortName(baseLanguageShortName) ?: return null
        val baseWords = getLinksFrom(word).filter { it.type == Link.Derived }.map { it.toEntity }
        return baseWords.filterIsInstance<Word>().find { it.language == baseLang }
    }
    return word
}

class RelativePhonemeRuleCondition(
    val negated: Boolean,
    val seekTarget: SeekTarget,
    val matchPhonemeClass: PhonemeClass?,
    val parameter: String?,
    val baseLanguageShortName: String?
) : RuleCondition() {
    override fun isPhonemic(): Boolean = seekTarget.relative

    override fun matches(word: Word, phonemes: PhonemeIterator): Boolean {
        val it = phonemes.clone()
        if (!it.seek(seekTarget)) return negated
        return matchesCurrent(it) xor negated
    }

    private fun matchesCurrent(it: PhonemeIterator) =
        matchPhonemeClass?.matchesCurrent(it) ?: (parameter == it.current)

    override fun matches(word: Word, graph: GraphRepository): Boolean {
        val matchWord = graph.findWordToMatch(word, baseLanguageShortName) ?: return false

        val phonemes = PhonemeIterator(matchWord)
        if (!phonemes.seek(seekTarget)) return negated
        return matchesCurrent(phonemes) xor negated
    }

    override fun toRichText(): RichText {
        return seekTarget.toRichText() +
            (if (baseLanguageShortName != null) " of base word in ".rich() else "".rich()) +
            (baseLanguageShortName?.rich(true) ?: "".rich()) +
            " is " +
            (if (negated) LeafRuleCondition.notPrefix.rich() else "".rich()) +
            (matchPhonemeClass?.name?.rich(true) ?: "'$parameter'".rich(true))
    }

    companion object {
        fun parse(buffer: ParseBuffer, language: Language): RelativePhonemeRuleCondition? {
            val seekTarget = SeekTarget.parse(buffer, language) ?: return null

            val baseLanguageShortName = if (!seekTarget.relative && buffer.consume("of base word in")) {
                buffer.nextWord()
            }
            else {
                null
            }

            if (!buffer.consume("is")) {
                return null
            }

            val negated = buffer.consume(LeafRuleCondition.notPrefix)
            val (matchPhonemeClass, parameter) = buffer.parseParameter(language)
            return RelativePhonemeRuleCondition(
                negated, seekTarget, matchPhonemeClass, parameter, baseLanguageShortName
            )
        }
    }
}

class OrRuleCondition(val members: List<RuleCondition>) : RuleCondition() {
    override fun isPhonemic(): Boolean {
        return members.any { it.isPhonemic() }
    }

    override fun matches(word: Word, graph: GraphRepository): Boolean = members.any { it.matches(word, graph) }

    override fun matches(word: Word, phonemes: PhonemeIterator) = members.any { it.matches(word, phonemes) }

    override fun toRichText(): RichText = members.joinToRichText(OR) {
        val et = it.toRichText()
        if (it is AndRuleCondition) et.prepend("(").append(")") else et
    }

    override fun findLeafConditions(predicate: (RuleCondition) -> Boolean): List<RuleCondition> {
        return members.flatMap { it.findLeafConditions(predicate) }
    }

    override fun refersToPhoneme(phoneme: Phoneme): Boolean {
        return members.any { it.refersToPhoneme(phoneme) }
    }

    companion object {
        const val OR = " or "
    }
}

class AndRuleCondition(val members: List<RuleCondition>) : RuleCondition() {
    override fun isPhonemic(): Boolean {
        return members.any { it.isPhonemic() }
    }

    override fun matches(word: Word, graph: GraphRepository): Boolean = members.all { it.matches(word, graph) }

    override fun matches(word: Word, phonemes: PhonemeIterator) = members.all { it.matches(word, phonemes) }

    override fun toRichText(): RichText = members.joinToRichText(AND) {
        val et = it.toRichText()
        if (it is OrRuleCondition) et.prepend("(").append(")") else et
    }

    override fun findLeafConditions(predicate: (RuleCondition) -> Boolean): List<RuleCondition> {
        return members.flatMap { it.findLeafConditions(predicate) }
    }

    override fun refersToPhoneme(phoneme: Phoneme): Boolean {
        return members.any { it.refersToPhoneme(phoneme) }
    }

    companion object {
        const val AND = " and "
    }
}

object OtherwiseCondition : RuleCondition() {
    override fun isPhonemic(): Boolean = false
    override fun matches(word: Word, graph: GraphRepository): Boolean = true
    override fun matches(word: Word, phonemes: PhonemeIterator) = true
    override fun toRichText(): RichText = OTHERWISE.richText()

    const val OTHERWISE = "otherwise"
}
