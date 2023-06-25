package ru.yole.etymograph

class WordSegment(
    val firstCharacter: Int,
    val length: Int,
    val category: String?,
    val sourceRule: Rule?
)

class Word(
    id: Int,
    var text: String,
    val language: Language,
    var gloss: String? = null,
    var fullGloss: String? = null,
    var pos: String? = null,
    source: String? = null,
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

    fun derive(text: String, newSegment: WordSegment? = null): Word {
        val sourceSegments = segments
        return if (this.text == text)
            this
        else
            Word(-1, text, language, gloss, fullGloss, pos).apply {
                segments = appendSegments(sourceSegments, newSegment)
            }
    }

    var stressedPhonemeIndex: Int = -1

    var segments: List<WordSegment>? = null

    fun getOrComputeGloss(graph: GraphRepository): String? {
        gloss?.let { return it }
        val components = graph.getLinksFrom(this).filter { it.type == Link.Agglutination && it.toEntity is Word }
        if (components.isNotEmpty()) {
            return components.joinToString("-") {
                (it.toEntity as Word).getOrComputeGloss(graph)?.substringBefore(", ") ?: "?"
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
        return null
    }

    fun baseWordLink(graph: GraphRepository): Link? =
        graph.getLinksFrom(this).singleOrNull { it.type == Link.Derived && it.toEntity is Word }

    fun segmentedText(): String {
        if (segments.isNullOrEmpty()) return text
        return buildString {
            var index = 0
            for (segment in segments!!) {
                if (index < segment.firstCharacter) {
                    append(text.substring(index, segment.firstCharacter))
                }
                index = segment.firstCharacter
                append("-")
            }
            append(text.substring(index))
        }
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
                        last.sourceRule
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
    if (stressedPhonemeIndex < 0) {
        // graph is used only for retrieving links of word and we don't need this for stress
        language.stressRule?.resolve()?.apply(this, InMemoryGraphRepository.EMPTY)
    }
    return if (stressedPhonemeIndex >= 0) {
        val phonemes = PhonemeIterator(this)
        phonemes.advanceTo(stressedPhonemeIndex)
        val isDiphthong = PhonemeClass.diphthong.matchesCurrent(phonemes)
        StressData(phonemes.phonemeToCharacterIndex(stressedPhonemeIndex), (if (isDiphthong) 2 else 1))
    } else
        null
}
