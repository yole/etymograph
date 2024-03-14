package ru.yole.etymograph

import java.util.*

class WordSegment(
    val firstCharacter: Int,
    val length: Int,
    val category: String?,
    val sourceRule: Rule?,
    val clitic: Boolean = false
)

class Word(
    id: Int,
    var text: String,
    val language: Language,
    var gloss: String? = null,
    var fullGloss: String? = null,
    var pos: String? = null,
    var classes: List<String> = emptyList(),
    source: List<SourceRef> = emptyList(),
    notes: String? = null
) : LangEntity(id, source, notes) {
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

    val normalizedText: String get() = language.normalizeWord(text)
    val normalized: Word get() = derive(normalizedText)

    var isPhonemic: Boolean = false

    fun asPhonemic(): Word {
        if (isPhonemic) return this
        val phonemicText = buildString {
            language.orthoPhonemeLookup.iteratePhonemes(text.lowercase(Locale.FRANCE)) { s, phoneme ->
                append(phoneme?.sound ?: phoneme?.graphemes?.first() ?: s)
            }
        }
        return derive(phonemicText, phonemic = true)
    }

    fun asOrthographic(): Word {
        if (!isPhonemic) return this
        val orthoRule = language.orthographyRule?.resolve()
        val orthoText: String
        if (orthoRule != null) {
            val it = PhonemeIterator(this, resultPhonemic = false)
            while (true) {
                orthoRule.applyToPhoneme(this, it)
                if (!it.advance()) break
            }
            orthoText = it.result()
        }
        else {
            orthoText = buildString {
                language.phonoPhonemeLookup.iteratePhonemes(text) { s, phoneme ->
                    append(phoneme?.graphemes?.get(0) ?: s)
                }
            }
        }
        return derive(orthoText, phonemic = false)
    }

    fun derive(text: String, newSegment: WordSegment? = null, newClasses: List<String>? = null, phonemic: Boolean? = false): Word {
        val sourceSegments = segments
        return if (this.text == text && newClasses == null && phonemic == null)
            this
        else
            Word(-1, text, language, gloss, fullGloss, pos, newClasses ?: classes).also {
                it.stressedPhonemeIndex = stressedPhonemeIndex
                it.segments = appendSegments(sourceSegments, newSegment)
                if (phonemic != null) it.isPhonemic = phonemic
            }
    }

    var stressedPhonemeIndex: Int = -1

    fun calcStressedPhonemeIndex(): Int {
        if (stressedPhonemeIndex < 0) {
            // graph is used only for retrieving links of word and we don't need this for stress
            language.stressRule?.resolve()?.apply(this, InMemoryGraphRepository.EMPTY)
        }
        return stressedPhonemeIndex
    }

    var segments: List<WordSegment>? = null

    fun getOrComputeGloss(graph: GraphRepository): String? {
        gloss?.let { return it }
        val variationOf = graph.getLinksFrom(this).singleOrNull { it.type == Link.Variation && it.toEntity is Word }
        if (variationOf != null) {
            return (variationOf.toEntity as Word).getOrComputeGloss(graph)
        }
        val compound = graph.findComponentsByCompound(this).firstOrNull()
        if (compound != null) {
            return compound.components.joinToString("-") {
                it.getOrComputeGloss(graph)?.substringBefore(", ") ?: "?"
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
        if (pos == "NP") {
            return text.replaceFirstChar { it.uppercase(Locale.FRANCE) }
        }
        return null
    }

    fun glossOrNP(): String? =
        gloss ?: if (pos == "NP") text.replaceFirstChar { c -> c.uppercase(Locale.FRANCE) } else null

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

    companion object {
        fun appendSegments(sourceSegments: List<WordSegment>?, newSegment: WordSegment?): List<WordSegment>? {
            if (newSegment == null) {
                return sourceSegments
            }
            if (sourceSegments == null) {
                return listOf(newSegment)
            }
            return normalizeSegments(sourceSegments + newSegment)
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
    val index = calcStressedPhonemeIndex()
    return if (index >= 0) {
        val phonemes = PhonemeIterator(this)
        phonemes.advanceTo(index)
        val isDiphthong = PhonemeClass.diphthong.matchesCurrent(phonemes)
        StressData(phonemes.phonemeToCharacterIndex(index), (if (isDiphthong) 2 else 1))
    } else
        null
}
