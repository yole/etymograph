package ru.yole.etymograph

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

    fun derive(text: String): Word = if (this.text == text) this else Word(-1, text, language, gloss, fullGloss, pos)

    var stressedPhonemeIndex: Int = -1

    fun getOrComputeGloss(graph: GraphRepository): String? {
        gloss?.let { return it }
        val components = graph.getLinksFrom(this).filter { it.type == Link.Agglutination && it.toEntity is Word }
        if (components.isNotEmpty()) {
            return components.joinToString("-") {
                (it.toEntity as Word).getOrComputeGloss(graph)?.substringBefore(", ") ?: "?"
            }
        }
        val derivation = graph.getLinksFrom(this).singleOrNull { it.type == Link.Derived && it.toEntity is Word }
        if (derivation != null) {
            if (derivation.rules.any { it.addedCategories != null }) {
                (derivation.toEntity as Word).getOrComputeGloss(graph)?.let { fromGloss ->
                    return derivation.rules.fold(fromGloss) { gloss, rule -> rule.applyCategories(gloss) }
                }
            }
        }
        return null
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
