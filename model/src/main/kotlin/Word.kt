package page.yole.etymograph

import java.text.Normalizer
import java.util.*
import kotlin.math.min

class WordSegment(
    val firstCharacter: Int,
    val length: Int,
    val category: String? = null,
    val sourceWord: Word? = null,
    val sourceRule: Rule? = null,
    val clitic: Boolean = false
) {
    init {
        if (firstCharacter < 0) {
            throw IllegalArgumentException("Invalid segment start index")
        }
        if (length <= 0) {
            throw IllegalArgumentException("Invalid segment length $length")
        }
    }

    override fun toString(): String {
        return "[$firstCharacter,$length]"
    }

    companion object {
        fun create(firstCharacter: Int, length: Int, category: String?, sourceWord: Word?, sourceRule: Rule?): WordSegment? {
            if (length == 0) return null
            return WordSegment(firstCharacter, length, category, sourceWord, sourceRule)
        }
    }

    fun shiftTo(startIndex: Int, length: Int? = null) =
        WordSegment(startIndex, length ?: this.length, category, sourceWord, sourceRule, clitic)
}

fun remapSegments(phonemes: PhonemeIterator, segments: List<WordSegment>?): List<WordSegment>? {
    return segments?.mapNotNull { segment ->
        val startPhoneme = phonemes.characterToPhonemeIndex(segment.firstCharacter, true)
        val endPhoneme = phonemes.characterToPhonemeIndex(segment.firstCharacter + segment.length, true)
        val start = phonemes.mapNextValidIndex(startPhoneme)
        val end = phonemes.mapIndex(endPhoneme)
        if (start < 0 || end < 0 || start == end) {
            null
        }
        else {
            val startChar = phonemes.resultPhonemeToCharacterIndex(start)
            val endChar = phonemes.resultPhonemeToCharacterIndex(end)
            segment.shiftTo(startChar, endChar - startChar)
        }
    }
}

enum class AccentType(val combiningMark: Char) {
    None(0.toChar()),
    Acute('\u0301'),
    Grave('\u0300'),
    Circumflex('\u0302');

    fun combine(text: String): String {
        return if (this == None) text else Normalizer.normalize(text + combiningMark, Normalizer.Form.NFKC)
    }

    companion object {
        fun find(arg: String): AccentType? = entries.find { it.name.equals(arg, ignoreCase = true) }
    }
}

class Word(
    id: Int,
    text: String,
    val language: Language,
    var gloss: String? = null,
    var fullGloss: String? = null,
    var pos: String? = null,
    var classes: List<String> = emptyList(),
    var reconstructed: Boolean = false,
    var syllabographic: Boolean = false,
    source: List<SourceRef> = emptyList(),
    notes: String? = null
) : LangEntity(id, source, notes) {
    private var _stressedPhonemeIndex: Int? = null

    val stressedPhonemeIndex: Int
        get() = _stressedPhonemeIndex ?: calcStressedPhonemeIndex()

    var accentType: AccentType? = null
    var explicitStress: Boolean = false
    val graph: Graph get() = language.graph

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Word

        if (text != other.text) return false
        if (language != other.language) return false
        if (gloss != other.gloss) return false

        return true
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + language.hashCode()
        result = 31 * result + (gloss?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "${language.shortName} $text '$gloss'"
    }

    var text = text
        set(value) {
            field = value
            _stressedPhonemeIndex = null
        }

    val syllabogramSequence: SyllabogramSequence?
        get() = if (syllabographic) TlhDigSyllabogramSyntax.parse(text) else null

    val normalizedText: String get() = language.normalizeWord(text)
    val normalized: Word get() = derive(normalizedText, id)

    var isPhonemic: Boolean = false

    fun asPhonemic(): Word {
        if (isPhonemic) return this
        val (phonemicText, newSegments) = language.cachePhonemicText(text, segments) {
            val pronunciationRule = language.pronunciationRule?.resolve()
            val it = PhonemeIterator(this, resultPhonemic = true)
            if (pronunciationRule != null) {
                while (true) {
                    (pronunciationRule.logic as SpeRuleLogic).applyToPhoneme(this, it)
                    if (!it.advance()) break
                }
            }
            it.result() to remapSegments(it, this.segments)
        }

        return derive(phonemicText, id = id, phonemic = true, segments = newSegments,
            newAccentType = accentType, stressIndex = if (accentType != null) stressedPhonemeIndex else null)
    }

    fun asOrthographic(referenceWord: Word? = null): Word {
        if (!isPhonemic && accentType == null) return this
        val orthoRule = language.orthographyRule?.resolve()
        var wordSegments = segments
        val orthoText: String = if (isPhonemic) {
            var refIt = referenceWord?.let { PhonemeIterator(it) }
            val it = PhonemeIterator(this, resultPhonemic = false)
            while (true) {
                if (refIt?.current != it.current) {
                    if (orthoRule != null) {
                        (orthoRule.logic as SpeRuleLogic).applyToPhoneme(this, it)
                    }
                }
                val accent = accentType.takeIf { _ -> it.index == _stressedPhonemeIndex }
                if (accent != null) {
                    it.replace(accent.combine(it.current))
                }

                if (refIt?.advance() == false) refIt = null
                if (!it.advance()) break
            }
            wordSegments = remapSegments(it, wordSegments)
            it.result()
        }
        else {
            buildString {
                var index = 0
                val lookup = if (isPhonemic) language.phonoPhonemeLookup else language.orthoPhonemeLookup
                lookup.iteratePhonemes(text) { phonemeText, phoneme, _ ->
                    if (phoneme != null) {
                        val text = phoneme.graphemes[0]
                        val accent = accentType.takeIf { index == stressedPhonemeIndex }
                        append(accent?.combine(text) ?: text)
                    }
                    else {
                        append(phonemeText!!)
                    }
                    index++
                }
            }
        }
        return derive(orthoText, id = id, phonemic = false, segments = wordSegments, newAccentType = null)
    }

    fun asOrthographic(asLanguage: Language): Word {
        if (language != asLanguage) {
            val phonemic = asPhonemic()
            val targetWord = Word(-1, phonemic.text, asLanguage).also {
                it.isPhonemic = true
            }
            return targetWord.asOrthographic()
        }
        return asOrthographic()
    }

    fun derive(text: String,
               id: Int? = null,
               newLanguage: Language? = null,
               newGloss: String? = null,
               addSegment: WordSegment? = null,
               segments: List<WordSegment>? = null,
               newClasses: List<String>? = null,
               phonemic: Boolean? = null,
               stressIndex: Int? = null,
               newAccentType: AccentType? = null,
               keepStress: Boolean = true): Word {
        val sourceSegments = if (text == this.text || addSegment != null) this.segments else null
        return if (this.text == text && newClasses == null && phonemic == null && id == null && stressIndex == null)
            this
        else
            Word(id ?: -1, text, newLanguage ?: language, newGloss ?: gloss, fullGloss, pos, newClasses ?: classes).also {
                if (newAccentType != null) {
                    if (stressIndex != null) {
                        it._stressedPhonemeIndex = stressIndex
                    }
                    it.accentType = newAccentType
                }
                else if (stressIndex != null) {
                   it._stressedPhonemeIndex = stressIndex
                   it.explicitStress = explicitStress
               }
               else if (keepStress) {
                    it._stressedPhonemeIndex =  if (language.accentTypes.isNotEmpty()) stressedPhonemeIndex else _stressedPhonemeIndex
                    it.explicitStress = explicitStress
                    it.accentType = accentType
               }
               it.segments = appendSegments(segments ?: sourceSegments, addSegment)
               if (phonemic != null) it.isPhonemic = phonemic
           }
    }

    fun setExplicitStress(index: Int) {
        explicitStress = true
        _stressedPhonemeIndex = index
    }

    private fun calcStressedPhonemeIndex(): Int {
        if (_stressedPhonemeIndex == null) {
            if (language.accentTypes.isNotEmpty()) {
                val it = PhonemeIterator(this)
                while (!it.atEnd()) {
                    if (it.currentAccentType != null) {
                        _stressedPhonemeIndex = it.index
                        accentType = it.currentAccentType
                        return it.index
                    }
                    it.advance()
                }
            }
            else {
                _stressedPhonemeIndex = Int.MIN_VALUE // protect from SOE
                val wordWithStress = language.stressRule?.resolve()?.apply(this)
                if (wordWithStress != null) {
                    _stressedPhonemeIndex = wordWithStress.stressedPhonemeIndex
                    return wordWithStress.stressedPhonemeIndex
                }
            }
        }
        _stressedPhonemeIndex = -1
        return -1
    }

    var segments: List<WordSegment>? = null
        set(value) {
            if (value != null) {
                for (segment in value) {
                    if (segment.firstCharacter !in text.indices) {
                        throw IllegalArgumentException("Invalid segment start index: ${segment.firstCharacter}")
                    }
                    if (segment.firstCharacter + segment.length > text.length) {
                        throw IllegalArgumentException("Segment exceeds word boundary: start ${segment.firstCharacter}, length: ${segment.length}, word '$text'")
                    }
                }
            }
            field = value
        }

    fun baseWord(): Word? {
        getTransliterationOf()?.let { return it }
        getVariationOf()?.let { return it }
        val derivation = baseWordLink()
        if (derivation != null) {
            return derivation.toEntity as? Word
        }
        return null
    }

    fun getOrComputeGloss(): String? {
        gloss?.let { return it }
        getTransliterationOf()?.let { return it.getOrComputeGloss() }

        val variationOf = getVariationOf()
        if (variationOf != null) {
            return variationOf.getOrComputeGloss()
        }
        val compound = graph.findCompoundsByCompoundWord(this).firstOrNull()
        if (compound != null) {
            return compound.components.joinToString("-") {
                it.getOrComputeGloss()?.substringBefore(", ")?.removePrefix("-")?.removeSuffix("-") ?: "?"
            }
        }
        val derivation = baseWordLink()
        if (derivation != null) {
            if (derivation.rules.any { it.addedCategories != null }) {
                (derivation.toEntity as Word).getOrComputeGloss()?.let { fromGloss ->
                    return derivation.rules.fold(fromGloss) { gloss, rule ->
                        val segment = segments?.any { it.sourceRule == rule }
                        rule.applyCategories(gloss, segment != null)
                    }
                }
            }
        }
        if (pos == KnownPartsOfSpeech.properName.abbreviation) {
            val effectiveText = if (syllabographic) {
                suggestTranscription(this)
            }
            else {
                text
            }
            return effectiveText.replaceFirstChar { it.uppercase(Locale.FRANCE) }.removeSuffix("-")
        }
        return null
    }

    fun glossOrNP(): String? =
        gloss ?: (
                if (pos == KnownPartsOfSpeech.properName.abbreviation)
                    text.replaceFirstChar { c -> c.uppercase(Locale.FRANCE) }
                else
                    null
                )

    fun getVariationOf(): Word? =
        graph.getLinksFrom(this).singleOrNull { it.type == Link.Variation && it.toEntity is Word }?.toEntity as Word?

    fun getTransliterationOf(): Word? =
        graph.getLinksFrom(this).singleOrNull { it.type == Link.Transcription && it.toEntity is Word }?.toEntity as Word?

    fun getTextVariations(): List<String> {
        val baseWord = getVariationOf() ?: this
        val variations = graph.getLinksTo(baseWord)
            .filter { it.type == Link.Variation }
            .mapNotNull { (it.fromEntity as? Word)?.text }
        return listOf(baseWord.text) + variations
    }

    fun baseWordLink(): Link? =
        graph.getLinksFrom(this).singleOrNull { it.type == Link.Derived && it.toEntity is Word }

    fun segmentedText(): String {
        val segments = segments?.filter { it.length > 0 }?.takeIf { it.isNotEmpty() } ?: return text
        return buildString {
            var index = 0
            for ((segIndex, segment) in segments.withIndex()) {
                if (index < segment.firstCharacter) {
                    append(text.substring(index, segment.firstCharacter))
                }
                index = segment.firstCharacter
                if (index > 0) {
                    if (segment.clitic || segments.getOrNull(segIndex-1)?.clitic == true) {
                        append("=")
                    }
                    else {
                        append("-")
                    }
                }
            }
            append(text.substring(index))
        }
    }

    fun grammaticalCategorySuffix(): String? {
        val gloss = getOrComputeGloss()
        val suffix = gloss?.substringAfterLast('.', "")
        return suffix.takeIf { !it.isNullOrEmpty() && it.all { c -> c.isUpperCase() || c.isDigit() } }
    }

    fun remapSegments(mapper: (WordSegment) -> WordSegment): Word {
        segments = segments?.map(mapper)
        return this
    }

    fun remapViaCharacterIndex(index: Int, toLanguage: Language): Int {
        if (language == toLanguage || index < 0) {
            return index
        }
        val fromIt = PhonemeIterator(this)
        val fromIndex = fromIt.phonemeToCharacterIndex(index)
        val toIt = PhonemeIterator(text, toLanguage)
        return toIt.characterToPhonemeIndex(fromIndex)
    }

    fun getOrComputePOS(): String? {
        if (pos != null) {
            return pos
        }
        val variationOf = getVariationOf()
        if (variationOf != null) {
            return variationOf.getOrComputePOS()
        }
        for (compound in graph.findCompoundsByCompoundWord(this)) {
            compound.headComponent()?.pos?.let { return it }
        }
        return null
    }

    fun findRootSegment(): WordSegment? {
        val orthoWord = asOrthographic()
        val segments = language.graph.restoreSegments(orthoWord).segments
        return segments?.firstOrNull {
            it.sourceRule == null &&
                    (it.sourceWord == null ||
                            (it.sourceWord.pos != KnownPartsOfSpeech.preverb.abbreviation &&
                             it.sourceWord.pos != KnownPartsOfSpeech.affix.abbreviation))
        }
    }

    companion object {
        fun appendSegments(sourceSegments: List<WordSegment>?, newSegment: WordSegment?): List<WordSegment>? {
            if (newSegment == null) {
                return sourceSegments
            }
            if (sourceSegments == null) {
                return listOf(newSegment)
            }
            return sourceSegments + newSegment
        }

        fun normalizeSegments(segments: List<WordSegment>?): List<WordSegment>? {
            var result = segments ?: return null
            while (result.size >= 2) {
                val beforeLast = result[result.size - 2]
                val last = result.last()
                if (beforeLast.sourceRule == last.sourceRule &&
                    beforeLast.firstCharacter + beforeLast.length >= last.firstCharacter) {
                    result = result.dropLast(2) + WordSegment(
                        beforeLast.firstCharacter,
                        beforeLast.length + last.length,
                        last.category,
                        last.sourceWord,
                        last.sourceRule,
                        last.clitic
                    )
                }
                else {
                    break
                }
            }
            return result
        }
    }
}

data class Attestation(val word: Word, val corpusText: CorpusText)

data class StressData(val index: Int, val length: Int)

fun Word.calculateStress(): StressData? {
    val index = stressedPhonemeIndex
    return if (index >= 0) {
        val phonemes = PhonemeIterator(this)
        phonemes.advanceTo(index)
        val isDiphthong = PhonemeClass.diphthong.matchesCurrent(phonemes)
        StressData(phonemes.phonemeToCharacterIndex(index), (if (isDiphthong) 2 else 1))
    } else
        null
}

data class PhonemeDelta(val before: String?, val after: String?) {
    override fun toString(): String {
        return (before ?: "∅") + " -> " + (after ?: "∅")
    }
}

fun getSinglePhonemeDifference(word1: Word, word2: Word): PhonemeDelta? {
    val phonemes1 = PhonemeIterator(word1, mergeDiphthongs = true)
    val phonemes2 = PhonemeIterator(word2, mergeDiphthongs = true)

    if (phonemes1.size == phonemes2.size) {
        var result: PhonemeDelta? = null
        while (true) {
            if (phonemes1.current != phonemes2.current) {
                if (result != null) return null
                result = PhonemeDelta(phonemes1.current, phonemes2.current)
            }
            if (!phonemes1.advance() || !phonemes2.advance()) {
                break
            }
        }
        return result
    }
    if (phonemes1.size == phonemes2.size + 1 || phonemes1.size == phonemes2.size - 1) {
        val commonLength = min(phonemes1.size, phonemes2.size)
        val matchingPrefix = (0..<commonLength).firstOrNull {
            phonemes1[it] != phonemes2[it]
        } ?: commonLength

        if (phonemes1.size + 1 == phonemes2.size &&
            phonemes2.range(matchingPrefix + 1, phonemes2.size) == phonemes1.range(matchingPrefix, phonemes1.size))
        {
            return PhonemeDelta(null, phonemes2[matchingPrefix])
        }
        if (phonemes1.size - 1 == phonemes2.size &&
            phonemes1.range(matchingPrefix + 1, phonemes1.size) == phonemes2.range(matchingPrefix, phonemes2.size))
        {
            return PhonemeDelta(phonemes1[matchingPrefix], null)
        }
    }

    return null
}
