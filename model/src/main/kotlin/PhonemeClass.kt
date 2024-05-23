package ru.yole.etymograph

open class PhonemeClass(val name: String, var matchingPhonemes: List<String>) {
    open fun matchesCurrent(it: PhonemeIterator): Boolean {
        return it.current in matchingPhonemes
    }

    companion object {
        val diphthong = object : PhonemeClass("diphthong", emptyList()) {
            override fun matchesCurrent(it: PhonemeIterator): Boolean {
                val next = it.atRelative(1)
                if (next != null && it.current + next in it.language.diphthongs) {
                    return true
                }
                val previous = it.atRelative(-1)
                if (previous != null && previous + it.current in it.language.diphthongs) {
                    return true
                }
                return false
            }
        }

        val stressed = object : PhonemeClass("stressed", emptyList()) {
            override fun matchesCurrent(it: PhonemeIterator): Boolean {
                return it.stressedPhonemeIndex == it.index
            }
        }

        val wordInitial = object : PhonemeClass("word-initial", emptyList()) {
            override fun matchesCurrent(it: PhonemeIterator): Boolean {
                return it.index == 0
            }
        }

        val wordFinal = object : PhonemeClass("word-final", emptyList()) {
            override fun matchesCurrent(it: PhonemeIterator): Boolean {
                return it.index == it.size - 1
            }
        }

        val syllableInitial = object : PhonemeClass("syllable-initial", emptyList()) {
            override fun matchesCurrent(it: PhonemeIterator): Boolean {
                val syllables = it.syllables ?: return false
                return syllables.any { s -> it.index == s.startIndex }
            }
        }

        val syllableFinal = object : PhonemeClass("syllable-final", emptyList()) {
            override fun matchesCurrent(it: PhonemeIterator): Boolean {
                val syllables = it.syllables ?: return false
                return syllables.any { s -> it.index == s.endIndex - 1 }
            }
        }

        val geminate = object  : PhonemeClass("geminate", emptyList()) {
            override fun matchesCurrent(it: PhonemeIterator): Boolean {
                return it.current == it.atRelative(1) || it.current == it.atRelative(-1)
            }
        }

        val specialPhonemeClasses = listOf(
            diphthong, stressed, wordInitial, wordFinal, syllableInitial, syllableFinal, geminate
        )

        const val vowelClassName = "vowel"
    }
}

class IntersectionPhonemeClass(name: String, val classList: List<PhonemeClass>)
    : PhonemeClass(name, mergePhonemeClasses(classList)) {
    override fun matchesCurrent(it: PhonemeIterator): Boolean {
        return classList.all { cls -> cls.matchesCurrent(it) }
    }

    companion object {
        private fun mergePhonemeClasses(classList: List<PhonemeClass>): List<String> {
            return classList.fold(emptySet<String>()) { set, cls ->
                if (set.isEmpty())
                    cls.matchingPhonemes.toSet()
                else
                    set.intersect(cls.matchingPhonemes)

            }.toList()
        }
    }
}

class NegatedPhonemeClass(private val baseClass: PhonemeClass)
    : PhonemeClass("non-" + baseClass.name, emptyList())
{
    override fun matchesCurrent(it: PhonemeIterator): Boolean {
        return !baseClass.matchesCurrent(it)
    }
}

class PhonemePattern(val phonemeClass: PhonemeClass?, val literal: String?) {
    fun matchesCurrent(phonemes: PhonemeIterator): Boolean =
        (phonemeClass?.matchesCurrent(phonemes) ?: true) &&
         (literal == null || phonemes.current == literal)

    fun toRichText(): RichText {
        return listOf(
            phonemeClass?.name?.rich(emph = true, tooltip = phonemeClass.matchingPhonemes.takeIf { it.isNotEmpty() }?.joinToString(", ")),
            literal?.let { "'$it'" }?.rich(emph = true)
        ).filterNotNull().joinToRichText(" ") { richText(it) }
    }

    fun toEditableText(): String =
         arrayOf(
            phonemeClass?.name,
            literal?.let { "'$it'" }
        ).filterNotNull().joinToString(" ")

    fun refersToPhoneme(phoneme: Phoneme, phonemic: Boolean): Boolean {
        val phonemeAsString = if (phonemic) phoneme.effectiveSound else phoneme.graphemes[0]
        if (literal != null) {
            return literal == phonemeAsString
        }
        if (phonemeClass != null) {
            return phonemeAsString in phonemeClass.matchingPhonemes
        }
        return false
    }
}

class PhonemeClassList {
    private var classes: List<PhonemeClass> = listOf()

    fun update(phonemes: List<Phoneme>) {
        val phonemeClassMap = mutableMapOf<String, MutableList<String>>()
        for (phoneme in phonemes) {
            for (cls in withImplicit(phoneme.classes)) {
                phonemeClassMap.getOrPut(cls) { mutableListOf() }.add(phoneme.sound ?: phoneme.graphemes[0])
            }
        }
        val oldPhonemeClasses = classes
        classes = phonemeClassMap.map { (name, phonemes) ->
            oldPhonemeClasses.find { it.name == name }?.also { it.matchingPhonemes = phonemes }
                ?: PhonemeClass(name, phonemes)
        }
    }

    private fun withImplicit(classes: Set<String>): Set<String> =
        classes + classes.map { implicitPhonemeClasses[it] ?: emptySet() }.flatten()

    fun findByName(name: String): PhonemeClass? {
        if (' ' in name) {
            val subclassNames = name.split(' ')
            val subclasses = subclassNames.map { findByName(it) ?: return null }
            return IntersectionPhonemeClass(name, subclasses)
        }
        if (name.startsWith("non-")) {
            val baseClass = findByName(name.removePrefix("non-")) ?: return null
            return NegatedPhonemeClass(baseClass)
        }
        return classes.find { it.name == name } ?: PhonemeClass.specialPhonemeClasses.find { it.name == name }
    }
}

private val implicitPhonemeClasses = mapOf(
    "plosive" to setOf("obstruent", "consonant"),
    "stop" to setOf("obstruent", "consonant"),
    "fricative" to setOf("obstruent", "consonant"),
    "affricate" to setOf("obstruent", "consonant"),
    "approximant" to setOf("sonorant", "consonant"),
    "trill" to setOf("sonorant", "consonant"),
    "lateral" to setOf("sonorant", "consonant"),
    "dental" to setOf("coronal"),
    "alveolar" to setOf("coronal"),
    "vowel" to setOf("vocalic")
)
