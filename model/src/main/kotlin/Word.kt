package ru.yole.etymograph

import java.util.*

class WordSegment(
    val firstCharacter: Int,
    val length: Int,
    val category: String?,
    val sourceWord: Word?,
    val sourceRule: Rule?,
    val clitic: Boolean = false
) {
    init {
        if (firstCharacter < 0) {
            throw IllegalArgumentException("Invalid segment start index")
        }
    }

    override fun toString(): String {
        return "[$firstCharacter,$length]"
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
    source: List<SourceRef> = emptyList(),
    notes: String? = null
) : LangEntity(id, source, notes) {
    var stressedPhonemeIndex: Int = -1
    var explicitStress: Boolean = false

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
            stressedPhonemeIndex = -1
        }

    val normalizedText: String get() = language.normalizeWord(text)
    val normalized: Word get() = derive(normalizedText)

    var isPhonemic: Boolean = false

    fun asPhonemic(): Word {
        if (isPhonemic) return this
        val pronunciationRule = language.pronunciationRule?.resolve()
        val phonemicText = if (pronunciationRule != null) {
            val it = PhonemeIterator(this, null, resultPhonemic = true)
            while (true) {
                pronunciationRule.applyToPhoneme(this, it, InMemoryGraphRepository.EMPTY)
                if (!it.advance()) break
            }
            it.result()
        }
        else {
            buildString {
                language.orthoPhonemeLookup.iteratePhonemes(text.lowercase(Locale.FRANCE)) { s, phoneme ->
                    append(phoneme?.sound ?: phoneme?.graphemes?.first() ?: s)
                }
            }
        }
        return derive(phonemicText, phonemic = true)
    }

    fun asOrthographic(): Word {
        if (!isPhonemic) return this
        val orthoRule = language.orthographyRule?.resolve()
        val orthoText: String = if (orthoRule != null) {
            val it = PhonemeIterator(this, null, resultPhonemic = false)
            while (true) {
                orthoRule.applyToPhoneme(this, it, InMemoryGraphRepository.EMPTY)
                if (!it.advance()) break
            }
            it.result()
        }
        else {
            buildString {
                language.phonoPhonemeLookup.iteratePhonemes(text) { s, phoneme ->
                    append(phoneme?.graphemes?.get(0) ?: s)
                }
            }
        }
        return derive(orthoText, phonemic = false)
    }

    fun derive(text: String, id: Int? = null, newSegment: WordSegment? = null, newClasses: List<String>? = null,
               phonemic: Boolean? = null, keepStress: Boolean = true): Word {
        val sourceSegments = segments
        return if (this.text == text && newClasses == null && phonemic == null && id == null)
            this
        else
            Word(id ?: -1, text, language, gloss, fullGloss, pos, newClasses ?: classes).also {
                if (keepStress) {
                    it.stressedPhonemeIndex = stressedPhonemeIndex
                }
                it.segments = appendSegments(sourceSegments, newSegment)
                if (phonemic != null) it.isPhonemic = phonemic
            }
    }

    fun calcStressedPhonemeIndex(repo: GraphRepository?): Int {
        if (stressedPhonemeIndex < 0) {
            val wordWithStress = language.stressRule?.resolve()?.apply(this, repo ?: InMemoryGraphRepository.EMPTY)
            if (wordWithStress != null) {
                stressedPhonemeIndex = wordWithStress.stressedPhonemeIndex
            }
        }
        return stressedPhonemeIndex
    }

    var segments: List<WordSegment>? = null

    fun getOrComputeGloss(graph: GraphRepository): String? {
        gloss?.let { return it }
        val variationOf = getVariationOf(graph)
        if (variationOf != null) {
            return variationOf.getOrComputeGloss(graph)
        }
        val compound = graph.findCompoundsByCompoundWord(this).firstOrNull()
        if (compound != null) {
            return compound.components.joinToString("-") {
                it.getOrComputeGloss(graph)?.substringBefore(", ")?.removePrefix("-")?.removeSuffix("-") ?: "?"
            }
        }
        val derivation = baseWordLink(graph)
        if (derivation != null) {
            if (derivation.rules.any { it.addedCategories != null }) {
                (derivation.toEntity as Word).getOrComputeGloss(graph)?.let { fromGloss ->
                    return derivation.rules.fold(fromGloss) { gloss, rule ->
                        val segment = segments?.any { it.sourceRule == rule }
                        rule.applyCategories(gloss, segment != null)
                    }
                }
            }
        }
        if (pos == KnownPartsOfSpeech.properName.abbreviation) {
            return text.replaceFirstChar { it.uppercase(Locale.FRANCE) }
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

    fun getVariationOf(graph: GraphRepository): Word? =
        graph.getLinksFrom(this).singleOrNull { it.type == Link.Variation && it.toEntity is Word }?.toEntity as Word?

    fun baseWordLink(graph: GraphRepository): Link? =
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
            if (index < 0) {
                println("here")
            }
            append(text.substring(index))
        }
    }

    fun grammaticalCategorySuffix(graph: GraphRepository): String? {
        val gloss = getOrComputeGloss(graph)
        val suffix = gloss?.substringAfterLast('.', "")
        return suffix.takeIf { !it.isNullOrEmpty() && it.all { c -> c.isUpperCase() || c.isDigit() } }
    }

    fun remapSegments(mapper: (WordSegment) -> WordSegment): Word {
        segments = segments?.map(mapper)
        return this
    }

    fun getOrComputePOS(graph: GraphRepository): String? {
        if (pos != null) {
            return pos
        }
        for (compound in graph.findCompoundsByCompoundWord(this)) {
            val head = compound.headIndex?.let { compound.components[it] }
            head?.pos?.let { return it }
        }
        return null
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

        fun normalizeSegments(segments: List<WordSegment>): List<WordSegment> {
            var result = segments
            while (result.size >= 2) {
                val beforeLast = result[result.size - 2]
                val last = result.last()
                if (beforeLast.sourceRule == last.sourceRule &&
                    beforeLast.firstCharacter + beforeLast.length == last.firstCharacter) {
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

fun Word.calculateStress(graph: GraphRepository): StressData? {
    val index = calcStressedPhonemeIndex(graph)
    return if (index >= 0) {
        val phonemes = PhonemeIterator(this, graph)
        phonemes.advanceTo(index)
        val isDiphthong = PhonemeClass.diphthong.matchesCurrent(phonemes)
        StressData(phonemes.phonemeToCharacterIndex(index), (if (isDiphthong) 2 else 1))
    } else
        null
}
