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
    StressIs(LeafRuleCondition.stressIs, parameterParseCallback = { buf, _ ->
        val ord = Ordinals.parse(buf)
        if (ord == null || !buf.consume("syllable")) {
            throw RuleParseException("Syllable reference expected")
        }
        "${Ordinals.toString(ord)} syllable"
    }),
    BeginningOfWord(LeafRuleCondition.beginningOfWord, phonemic = true, takesArgument = false),
    EndOfWord(LeafRuleCondition.endOfWord, phonemic = true, takesArgument = false),
    SoundEquals(LeafRuleCondition.soundIsSame, phonemic = true, takesArgument = true)
}

interface RuleConditionParser {
    fun tryParse(buffer: ParseBuffer, language: Language): RuleCondition?
}

sealed class RuleCondition(val negated: Boolean = false) {
    open fun isPhonemic(): Boolean = false

    open fun matches(word: Word, graph: GraphRepository, trace: RuleTrace? = null): Boolean {
        if (isPhonemic()) throw IllegalStateException("Condition requires phonemic context")
        return false
    }

    open fun matches(word: Word, phonemes: PhonemeIterator, graph: GraphRepository, trace: RuleTrace? = null): Boolean {
        return matches(word, graph, trace)
    }

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

    protected fun Boolean.negateIfNeeded() = if (negated) !this else this

    val maybeNot: RichTextFragment
        get() = (if (negated) LeafRuleCondition.notPrefix else "").rich()

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
    negated: Boolean,
    val baseLanguageShortName: String?
) : RuleCondition(negated) {
    override fun isPhonemic(): Boolean = type.phonemic

    override fun matches(word: Word, graph: GraphRepository, trace: RuleTrace?): Boolean {
        val matchWord = graph.findWordToMatch(word, baseLanguageShortName) ?: return false
        return when (type) {
            ConditionType.EndsWith -> (phonemeClass?.let { PhonemeIterator(matchWord, graph).last in it.matchingPhonemes }
                ?: matchWord.text.trimEnd('-').endsWith(parameter!!)).negateIfNeeded()
            ConditionType.BeginsWith -> (phonemeClass?.let { PhonemeIterator(word, graph).current in it.matchingPhonemes }
                ?: word.text.startsWith(parameter!!)).negateIfNeeded()
            ConditionType.StressIs -> matchStress(word, graph).negateIfNeeded()
            ConditionType.ClassMatches -> matchClass(word, graph)
            else -> throw IllegalStateException()
        }
    }

    private fun matchClass(word: Word, graph: GraphRepository): Boolean {
        var classes = word.classes
        val variationOf = word.getVariationOf(graph)
        if (variationOf != null) {
            classes += variationOf.classes
        }
        else {
            val compound = graph.findCompoundsByCompoundWord(word).singleOrNull()
            compound?.headIndex?.let {
                classes += compound.components[it].classes
            }
        }

        return (parameter in classes).negateIfNeeded() || classes == listOf("*")
    }

    private fun matchStress(word: Word, graph: GraphRepository): Boolean {
        val (expectedIndex, _) = parameter?.let { Ordinals.parse(it) } ?: return false
        val stress = word.calculateStress(graph) ?: return false
        val syllables = breakIntoSyllables(word)
        val expectedStressSyllable = Ordinals.at(syllables, expectedIndex) ?: return false
        return stress.index >= expectedStressSyllable.startIndex && stress.index < expectedStressSyllable.endIndex
    }

    override fun matches(word: Word, phonemes: PhonemeIterator, graph: GraphRepository, trace: RuleTrace?): Boolean {
        return when (type) {
            ConditionType.BeginningOfWord -> phonemes.atBeginning().negateIfNeeded()
            ConditionType.EndOfWord -> (phonemes.index == phonemes.size - 1).negateIfNeeded()
            ConditionType.ClassMatches -> matchClass(word, graph)
            else -> throw IllegalStateException("Trying to use a word condition for matching phonemes")
        }
    }

    override fun toRichText(): RichText {
        return if (type.takesArgument) {
            val parameterName = parameterToEditableText()
            val condName = if (baseLanguageShortName != null) {
                "base word in ".rich() + baseLanguageShortName.rich(emph = true) + " " + type.condNameAfterBaseLanguage!!.rich()
            }
            else {
                type.condName.richText()
            }
            condName + maybeNot + (parameterName ?: "").rich(emph = true)
        } else {
            maybeNot + type.condName.rich()
        }
    }

    private fun parameterToEditableText(): String? {
        val parameterName = if (type.parameterParseCallback != null)
            parameter
        else {
            combineToEditableText(phonemeClass, parameter)
        }
        return parameterName
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
        const val syllableIs = "syllable is "
        const val indefiniteArticle = "a "
        const val soundIsSame = "sound is same as "
        const val soundIsNotSame = "sound is not same as "

        fun parse(buffer: ParseBuffer, language: Language): RuleCondition {
            for (parser in arrayOf(
                SyllableRuleCondition,
                SyllableCountRuleCondition,
                RelativeSyllableRuleCondition,
                PhonemeEqualsRuleCondition,
                RelativePhonemeRuleCondition
            )) {
                buffer.tryParse { parser.tryParse(buffer, language) }?.let { return it }
            }

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
                val pattern = buffer.parsePhonemePattern(language)
                    ?: buffer.fail("Can't parse phoneme pattern")
                pattern.phonemeClass to pattern.literal
            }

            return LeafRuleCondition(conditionType, phonemeClass, parameter, negated, baseLanguageShortName)
        }

        fun combineToEditableText(phonemeClass: PhonemeClass?, parameter: String?) = arrayOf(
            phonemeClass?.name,
            parameter?.let { "'$it'" }
        ).filterNotNull().joinToString(" ")
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
    val phonemePattern: PhonemePattern
) : RuleCondition() {
    override fun isPhonemic(): Boolean = false

    override fun matches(word: Word, graph: GraphRepository, trace: RuleTrace?): Boolean {
        val syllables = breakIntoSyllables(word)
        val syllable = Ordinals.at(syllables, index) ?: return false
        val phonemes = PhonemeIterator(word, graph)
        if (matchType == SyllableMatchType.Contains) {
            if (phonemePattern.phonemeClass != null) {
                return phonemes.findMatchInRange(syllable.startIndex, syllable.endIndex, phonemePattern.phonemeClass) != null
            }
            return phonemePattern.literal!! in word.text.substring(syllable.startIndex, syllable.endIndex)
        }
        else if (matchType == SyllableMatchType.EndsWith) {
            if (phonemePattern.phonemeClass != null) {
                phonemes.advanceTo(syllable.endIndex - 1)
                return phonemePattern.phonemeClass.matchesCurrent(phonemes)
            }
            return word.text.substring(syllable.startIndex, syllable.endIndex).endsWith(phonemePattern.literal!!)
        }
        return false
    }

    override fun matches(word: Word, phonemes: PhonemeIterator, graph: GraphRepository, trace: RuleTrace?): Boolean {
        throw IllegalStateException("This condition is not phonemic")
    }

    override fun toRichText(): RichText {
        val paramText = phonemePattern.toEditableText()
        return Ordinals.toString(index).rich(emph = true) +
                " syllable ${matchType.condName} ".rich() +
                paramText.rich(emph = true)
    }

    companion object : RuleConditionParser {
        const val syllable = "syllable"

        override fun tryParse(buffer: ParseBuffer, language: Language): SyllableRuleCondition? {
            val index = Ordinals.parse(buffer) ?: return null
            if (!buffer.consume(syllable)) return null
            val matchType = SyllableMatchType.parse(buffer) ?: return null

            val phonemePattern = buffer.parsePhonemePattern(language)
                ?: buffer.fail("Can't parse phoneme pattern")
            return SyllableRuleCondition(matchType, index, phonemePattern)
        }
    }
}

fun GraphRepository.findWordToMatch(word: Word, baseLanguageShortName: String?): Word? {
    if (baseLanguageShortName != null) {
        val baseLang = languageByShortName(baseLanguageShortName) ?: return null
        val baseWords = getLinksFrom(word).filter { it.type == Link.Origin }.map { it.toEntity }
        return baseWords.filterIsInstance<Word>().find { it.language == baseLang }
    }
    return word
}

class SyllableCountRuleCondition(val condition: String?, negated: Boolean, val expectCount: Int)
    : RuleCondition(negated)
{
    override fun matches(word: Word, graph: GraphRepository, trace: RuleTrace?): Boolean {
        val compare = when (condition) {
            ">=" -> { a: Int, b: Int -> a >= b }
            "<=" -> { a: Int, b: Int -> a <= b }
            else ->  { a: Int, b: Int -> a == b }
        }
        return compare(breakIntoSyllables(word).size, expectCount) xor negated
    }

    override fun toRichText(): RichText {
        val param = when(condition) {
            ">=" -> "at least "
            "<=" -> "at most "
            else -> ""
        }
        return LeafRuleCondition.numberOfSyllables.richText() + maybeNot + param.rich(true) +
                expectCount.toString().rich(true)
    }

    companion object : RuleConditionParser {
        override fun tryParse(buffer: ParseBuffer, language: Language): RuleCondition? {
            if (!buffer.consume(LeafRuleCondition.numberOfSyllables)) return null
            val negated = buffer.consume(LeafRuleCondition.notPrefix)
            val condition = if (buffer.consume("at least"))
                ">="
            else if (buffer.consume("at most"))
                "<="
            else
                null
            val param = buffer.nextWord()?.toIntOrNull()
                ?: throw RuleParseException("Number of syllables should be a number")
            return SyllableCountRuleCondition(condition, negated, param)
        }
    }
}

class RelativeSyllableRuleCondition(val matchIndex: Int?, val matchClass: String?, val relativeIndex: Int?, negated: Boolean)
    : RuleCondition(negated)
{
    override fun isPhonemic(): Boolean = true

    override fun matches(word: Word, phonemes: PhonemeIterator, graph: GraphRepository, trace: RuleTrace?): Boolean {
        val syllables = phonemes.syllables
        if (syllables.isNullOrEmpty()) return false.negateIfNeeded()

        var currentSyllableIndex = findSyllable(syllables, phonemes.index)
        if (currentSyllableIndex == -1) return false.negateIfNeeded()
        if (relativeIndex != null) {
            currentSyllableIndex += relativeIndex
            if (currentSyllableIndex < 0 || currentSyllableIndex >= syllables.size) return false.negateIfNeeded()
        }
        if (matchIndex != null) {
            val absIndex = matchIndex.let { Ordinals.toAbsoluteIndex(it, syllables.size) }
            return (currentSyllableIndex == absIndex).negateIfNeeded()
        }

        val syllable = syllables[currentSyllableIndex]
        return when (matchClass) {
            "closed" -> syllable.closed.negateIfNeeded()
            "open" -> !syllable.closed.negateIfNeeded()
            else -> false
        }
    }

    override fun toRichText(): RichText {
        val relIndex = if (relativeIndex == null) "" else RelativeOrdinals.toString(relativeIndex) + " "
        return relIndex.richText() + "syllable is " + maybeNot +
                (matchIndex?.let { Ordinals.toString(it) }?.rich(true) ?: matchClass!!.rich(true))
    }

    companion object : RuleConditionParser {
        override fun tryParse(buffer: ParseBuffer, language: Language): RuleCondition? {
            val relativeIndex = RelativeOrdinals.parse(buffer)
            if (!buffer.consume(LeafRuleCondition.syllableIs)) {
                return null
            }
            val negated = buffer.consume("not")
            if (buffer.consume("open")) {
                return RelativeSyllableRuleCondition(null, "open", relativeIndex, negated)
            }
            else if (buffer.consume("closed")) {
                return RelativeSyllableRuleCondition(null, "closed", relativeIndex, negated)
            }
            else {
                val index = Ordinals.parse(buffer) ?: return null
                return RelativeSyllableRuleCondition(index, null, relativeIndex, negated)
            }
        }
    }
}

class RelativePhonemeRuleCondition(
    negated: Boolean,
    val seekTarget: SeekTarget?,
    val phonemePattern: PhonemePattern,
    val baseLanguageShortName: String?
) : RuleCondition(negated) {
    override fun isPhonemic(): Boolean = seekTarget?.relative ?: true

    override fun matches(word: Word, phonemes: PhonemeIterator, graph: GraphRepository, trace: RuleTrace?): Boolean {
        val it = if (seekTarget == null)
            phonemes
        else {
            phonemes.clone().also {
                if (!it.seek(seekTarget)) return negated
            }
        }
        return phonemePattern.matchesCurrent(it) xor negated
    }

    override fun matches(word: Word, graph: GraphRepository, trace: RuleTrace?): Boolean {
        if (seekTarget == null) {
            throw IllegalStateException("This condition is phonemic")
        }
        val matchWord = graph.findWordToMatch(word, baseLanguageShortName) ?: return false

        val phonemes = PhonemeIterator(matchWord, graph)
        if (!phonemes.seek(seekTarget)) return negated
        return phonemePattern.matchesCurrent(phonemes) xor negated
    }

    override fun toRichText(): RichText {
        return (seekTarget?.toRichText() ?: "sound".richText()) +
            (if (baseLanguageShortName != null) " of base word in ".rich() else "".rich()) +
            (baseLanguageShortName?.rich(true) ?: "".rich()) +
            " is " + maybeNot +
            phonemePattern.toRichText()
    }

    override fun refersToPhoneme(phoneme: Phoneme): Boolean {
        return seekTarget == null && phonemePattern.refersToPhoneme(phoneme, true)
    }

    companion object : RuleConditionParser {
        override fun tryParse(buffer: ParseBuffer, language: Language): RelativePhonemeRuleCondition? {
            if (buffer.consume(LeafRuleCondition.soundIs)) {
                return parseTail(buffer, language, null, null)
            }

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

            return parseTail(buffer, language, seekTarget, baseLanguageShortName)
        }

        private fun parseTail(
            buffer: ParseBuffer,
            language: Language,
            seekTarget: SeekTarget?,
            baseLanguageShortName: String?
        ): RelativePhonemeRuleCondition? {
            val negated = buffer.consume(LeafRuleCondition.notPrefix)
            val phonemePattern = buffer.parsePhonemePattern(language) ?: return null
            return RelativePhonemeRuleCondition(
                negated, seekTarget, phonemePattern, baseLanguageShortName
            )
        }
    }
}

class PhonemeEqualsRuleCondition(val target: SeekTarget, val matchTarget: SeekTarget?, negated: Boolean)
    : RuleCondition(negated)
{
    override fun isPhonemic(): Boolean = true

    override fun matches(word: Word, phonemes: PhonemeIterator, graph: GraphRepository, trace: RuleTrace?): Boolean {
        val targetIterator = phonemes.clone()
        if (!targetIterator.seek(target)) return false
        val matchPhoneme = if (matchTarget != null) {
            val matchIterator = phonemes.clone()
            if (!matchIterator.seek(matchTarget)) return false
            matchIterator.current
        }
        else {
            phonemes.current
        }

        return (matchPhoneme == targetIterator.current).negateIfNeeded()
    }

    override fun toRichText(): RichText {
        if (matchTarget == null) {
            return ("sound is " + maybeNot + "same as ").richText() + target.toRichText()
        }
        return matchTarget.toRichText() + " is " + maybeNot + "same as " + target.toRichText()
    }

    companion object : RuleConditionParser {
        override fun tryParse(buffer: ParseBuffer, language: Language): PhonemeEqualsRuleCondition? {
            val matchTarget = SeekTarget.parse(buffer, language)
            val negated: Boolean

            if (buffer.consume(if (matchTarget != null) "is same as" else LeafRuleCondition.soundIsSame)) {
                negated = false
            }
            else if (buffer.consume(if (matchTarget != null) "is not same as" else LeafRuleCondition.soundIsNotSame)) {
                negated = true
            }
            else {
                return null
            }
            val target = SeekTarget.parse(buffer, language) ?: return null
            return PhonemeEqualsRuleCondition(target, matchTarget, negated)
        }
    }
}

sealed class CompositeRuleCondition(val members: List<RuleCondition>) : RuleCondition() {
    override fun isPhonemic(): Boolean {
        return members.any { it.isPhonemic() }
    }

    override fun findLeafConditions(predicate: (RuleCondition) -> Boolean): List<RuleCondition> {
        return members.flatMap { it.findLeafConditions(predicate) }
    }

    override fun refersToPhoneme(phoneme: Phoneme): Boolean {
        return members.any { it.refersToPhoneme(phoneme) }
    }
}

class OrRuleCondition(members: List<RuleCondition>) : CompositeRuleCondition(members) {
    override fun matches(word: Word, graph: GraphRepository, trace: RuleTrace?): Boolean =
        members.any { c -> c.matches(word, graph, trace).also { trace?.logCondition(c, it) } }

    override fun matches(word: Word, phonemes: PhonemeIterator, graph: GraphRepository, trace: RuleTrace?): Boolean =
        members.any { c -> c.matches(word, phonemes, graph, trace).also { trace?.logCondition(c, it) } }

    override fun toRichText(): RichText = members.joinToRichText(OR) {
        val et = it.toRichText()
        if (it is AndRuleCondition) et.prepend("(").append(")") else et
    }

    companion object {
        const val OR = " or "
    }
}

class AndRuleCondition(members: List<RuleCondition>) : CompositeRuleCondition(members) {
    override fun matches(word: Word, graph: GraphRepository, trace: RuleTrace?): Boolean =
        members.all { c -> c.matches(word, graph, trace).also { trace?.logCondition(c, it)} }

    override fun matches(word: Word, phonemes: PhonemeIterator, graph: GraphRepository, trace: RuleTrace?) =
        members.all { c -> c.matches(word, phonemes, graph, trace).also { trace?.logCondition(c, it) } }

    override fun toRichText(): RichText = members.joinToRichText(AND) {
        val et = it.toRichText()
        if (it is OrRuleCondition) et.prepend("(").append(")") else et
    }

    companion object {
        const val AND = " and "
    }
}

object OtherwiseCondition : RuleCondition() {
    override fun isPhonemic(): Boolean = false
    override fun matches(word: Word, graph: GraphRepository, trace: RuleTrace?): Boolean = true
    override fun matches(word: Word, phonemes: PhonemeIterator, graph: GraphRepository, trace: RuleTrace?) = true
    override fun toRichText(): RichText = OTHERWISE.richText()

    const val OTHERWISE = "otherwise"
}
