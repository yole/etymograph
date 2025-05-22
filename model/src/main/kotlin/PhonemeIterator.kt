package ru.yole.etymograph

import kotlin.math.abs
import kotlin.math.sign

open class OrdinalTable(private val items: List<Pair<String, Int>>) {
    fun toString(i: Int): String {
        return items.find { it.second == i }?.first ?: i.toString()
    }

    fun parse(s: String): Pair<Int, String>? {
        for (ordinal in items) {
            if (s.startsWith(ordinal.first)) {
                return ordinal.second to s.removePrefix(ordinal.first).trim()
            }
        }
        return null
    }

    fun parse(buffer: ParseBuffer): Int? {
        for (ordinal in items) {
            if (buffer.consume(ordinal.first)) {
                return ordinal.second
            }
        }
        return null
    }

    fun parseMatch(match: MatchResult, index: Int): Int {
        return match.groupValues[index].takeIf { it.isNotEmpty() }?.trim()?.let {
            parse(it)?.first
        } ?: 0
    }

    fun toPattern(): String {
        return "(" + items.joinToString("|") { it.first + "\\s+" } + ")"
    }
}

object Ordinals : OrdinalTable(
    listOf(
        "last" to -1,
        "second to last" to -2,
        "third to last" to -3,
        "first" to 1,
        "second" to 2,
        "third" to 3,
    )
) {
    fun toAbsoluteIndex(index: Int, size: Int): Int =
        if (index < 0) size + index else index - 1

    fun <T> at(items: List<T>, index: Int): T? {
        return items.getOrNull(toAbsoluteIndex(index, items.size))
    }
}

object RelativeOrdinals : OrdinalTable(
    listOf(
        "previous" to -1,
        "next" to 1,
        "second next" to 2,
        "second previous" to -2,
        "third next" to 3,
        "third previous" to -3
    )
)

class SeekTarget(val index: Int, val phonemeClass: PhonemeClass?, val relative: Boolean = false) {
    fun toEditableText(): String {
        val targetSound = phonemeClass?.name ?: "sound"
        val indexAsString = if (relative) RelativeOrdinals.toString(index) else Ordinals.toString(index)
        return "$indexAsString $targetSound"
    }

    fun toRichText(): RichText {
        return (if (relative) RelativeOrdinals.toString(index) else Ordinals.toString(index)).rich(true) +
            " ".rich() +
            (phonemeClass?.toRichText() ?: "sound".rich())
    }

    companion object {
        fun parse(s: String, language: Language): SeekTarget {
            var relative = false
            val (index, phonemeClassName) = Ordinals.parse(s)
                ?: RelativeOrdinals.parse(s)?.also { relative = true }
                ?: throw RuleParseException("Cannot parse seek target $s")

            val phonemeClass = if (phonemeClassName == "sound")
                null
            else {
                language.phonemeClassByName(phonemeClassName)
                    ?: throw RuleParseException("Unknown phoneme class '$phonemeClassName'")
            }
            return SeekTarget(index, phonemeClass, relative)
        }

        fun parse(buffer: ParseBuffer, language: Language): SeekTarget? {
            var relative = true
            val index = RelativeOrdinals.parse(buffer)
                ?: Ordinals.parse(buffer)?.also { relative = false }
                ?: return null

            val targetPhonemeClass = buffer.parsePhonemeClass(language, true)
            return SeekTarget(index, targetPhonemeClass, relative)
        }
    }
}

class PhonemeIterator {
    val language: Language
    private val word: Word?
    private val repo: GraphRepository?
    private val phonemes: MutableList<String>
    private val resultPhonemes: MutableList<String>
    private var phonemeIndex = 0
    private var phonemeToResultIndexMap: IntArray
    private var indexMapStack: MutableList<IntArray>? = null
    private var atEnd: Boolean = false

    constructor(word: Word, repo: GraphRepository?, resultPhonemic: Boolean? = null, mergeDiphthongs: Boolean = false) : this(
        if (word.isPhonemic) word.text else word.normalizedText.trimEnd('-'),
        word.language,
        repo,
        word,
        word.isPhonemic,
        resultPhonemic,
        mergeDiphthongs
    )

    constructor(
        text: String,
        language: Language,
        repo: GraphRepository?,
        word: Word? = null,
        phonemic: Boolean = false,
        resultPhonemic: Boolean? = null,
        mergeDiphthongs: Boolean = false
    ) {
        this.language = language
        this.word = word
        this.repo = repo

        val sourcePhonemes = mutableListOf<String>()
        resultPhonemes = mutableListOf()
        val lookup = if (phonemic) this.language.phonoPhonemeLookup else this.language.orthoPhonemeLookup

        lookup.iteratePhonemes(text) { startIndex, endIndex, phoneme ->
            val normalizedResultText = if (resultPhonemic ?: phonemic) phoneme?.sound else phoneme?.graphemes?.first()
            val normalizedText = if (phonemic) phoneme?.sound else phoneme?.graphemes?.first()
            if (mergeDiphthongs && sourcePhonemes.size > 0 && sourcePhonemes.last() + (phoneme?.sound ?: text.substring(startIndex, endIndex)) in language.diphthongs) {
                sourcePhonemes[sourcePhonemes.size - 1] = sourcePhonemes[sourcePhonemes.size - 1] + (normalizedText ?: text.substring(startIndex, endIndex))
                resultPhonemes[resultPhonemes.size - 1] = resultPhonemes[resultPhonemes.size - 1] + (normalizedResultText ?: text.substring(startIndex, endIndex))
            }
            else {
                sourcePhonemes.add(normalizedText ?: text.substring(startIndex, endIndex))
                resultPhonemes.add(normalizedResultText ?: text.substring(startIndex, endIndex))
            }
        }

        phonemes = sourcePhonemes
        phonemeToResultIndexMap = IntArray(phonemes.size) { it }
    }

    private constructor(
        phonemes: List<String>,
        resultPhonemes: MutableList<String>,
        language: Language,
        repo: GraphRepository?,
        word: Word?,
        phonemeToResultIndexMap: IntArray
    ) {
        this.phonemes = phonemes.toMutableList()
        this.resultPhonemes = resultPhonemes
        this.language = language
        this.repo = repo
        this.word = word
        this.phonemeToResultIndexMap = phonemeToResultIndexMap
    }

    val current: String get() = phonemes[phonemeIndex]
    val last: String? get() = phonemes.lastOrNull()
    val size: Int get() = phonemes.size
    val index: Int get() = phonemeIndex

    operator fun get(index: Int): String = phonemes[index]
    fun range(fromIndex: Int, toIndex: Int) = phonemes.subList(fromIndex, toIndex)

    fun atRelative(relativeIndex: Int): String? = phonemes.getOrNull(phonemeIndex + relativeIndex)

    fun clone(): PhonemeIterator {
        return PhonemeIterator(phonemes, resultPhonemes, language, repo, word, phonemeToResultIndexMap).also {
            it.phonemeIndex = phonemeIndex
            it.atEnd = atEnd
        }
    }

    fun commit() {
        if (indexMapStack == null) {
            indexMapStack = mutableListOf()
        }
        indexMapStack!!.add(phonemeToResultIndexMap)
        var newIndex = phonemeIndex
        while (newIndex < phonemeToResultIndexMap.size - 1 && phonemeToResultIndexMap[newIndex] == -1) {
            newIndex++
        }
        while (newIndex > 0 && phonemeToResultIndexMap[newIndex] == -1) {
            newIndex--
        }
        val index = phonemeToResultIndexMap[newIndex]
        phonemes.clear()
        phonemes.addAll(resultPhonemes)
        phonemeIndex = index
        phonemeToResultIndexMap = IntArray(phonemes.size) { it }
    }

    fun advanceTo(index: Int): Boolean {
        if (index < 0) {
            throw IllegalArgumentException("Can't advance to negative index")
        }
        if (index >= phonemes.size) {
            return false
        }
        phonemeIndex = index
        atEnd = false
        return true
    }

    fun advance(): Boolean {
        return advanceBy(1)
    }

    fun advanceBy(relativeIndex: Int): Boolean {
        val newIndex = phonemeIndex + relativeIndex
        if (newIndex >= 0 && newIndex < phonemes.size) {
            phonemeIndex = newIndex
            atEnd = false
            return true
        }
        else if (newIndex >= phonemes.size) {
            atEnd = true
        }
        return false
    }

    fun advanceToClass(phonemeClass: PhonemeClass, relativeIndex: Int): Boolean {
        var count = abs(relativeIndex)
        while (advanceBy(relativeIndex.sign)) {
            if (phonemeClass.matchesCurrent(this) && --count == 0) {
                return true
            }
        }
        return false
    }

    fun catchUp(it: PhonemeIterator) {
        phonemeIndex = it.index
        atEnd = it.atEnd()
    }

    fun seek(seekTarget: SeekTarget): Boolean {
        if (seekTarget.relative) {
            return if (seekTarget.phonemeClass != null)
                advanceToClass(seekTarget.phonemeClass, seekTarget.index)
            else
                advanceBy(seekTarget.index)
        }

        val targetPhonemeClass = seekTarget.phonemeClass
        if (targetPhonemeClass == null) {
            val absIndex = Ordinals.toAbsoluteIndex(seekTarget.index, phonemes.size)
            if (absIndex >= phonemes.size) return false
            advanceTo(absIndex)
            return true
        }

        val matchingIndexes = mutableListOf<Int>()
        for (targetPhonemeIndex in phonemes.indices) {
            if (phonemes[targetPhonemeIndex] in targetPhonemeClass.matchingPhonemes) {
                matchingIndexes.add(targetPhonemeIndex)
            }
        }
        val result = Ordinals.at(matchingIndexes, seekTarget.index) ?: return false
        phonemeIndex = result
        atEnd = false
        return true
    }

    fun replace(s: String) {
        val resultIndex = phonemeToResultIndexMap[phonemeIndex]
        if (resultIndex >= 0) {
            resultPhonemes[resultIndex] = s
        }
    }

    fun replaceAtRelative(relativeIndex: Int, s: String) {
        val resultIndex = phonemeToResultIndexMap[phonemeIndex + relativeIndex]
        if (resultIndex >= 0) {
            resultPhonemes[resultIndex] = s
        }
    }

    fun deleteAtRelative(relativeIndex: Int) {
        val targetIndex = phonemeIndex + relativeIndex
        if (targetIndex !in phonemeToResultIndexMap.indices) {
            return
        }
        val resultIndex = phonemeToResultIndexMap[targetIndex]
        if (resultIndex >= 0) {
            resultPhonemes.removeAt(resultIndex)
            phonemeToResultIndexMap[targetIndex] = -1
            for (i in targetIndex+1..<phonemes.size) {
                phonemeToResultIndexMap[i]--
            }
        }
    }

    fun insertAtRelative(relativeIndex: Int, s: String) {
        val targetIndex = phonemeIndex + relativeIndex
        val resultIndex = if (targetIndex == phonemeToResultIndexMap.size)
            phonemeToResultIndexMap.last() + 1
        else
            phonemeToResultIndexMap[targetIndex]
        resultPhonemes.add(resultIndex, s)
        for (i in targetIndex..<phonemes.size) {
            phonemeToResultIndexMap[i]++
        }
    }

    fun result(): String {
        return resultPhonemes.joinToString("")
    }

    fun mapIndex(index: Int): Int {
        val i = indexMapStack?.fold(index) { i, map -> applyIndexMap(i, map) } ?: index
        return applyIndexMap(i, phonemeToResultIndexMap)
    }

    fun mapNextValidIndex(index: Int): Int {
        val i = indexMapStack?.fold(index) { i, map -> applyIndexMap(i, map, true) } ?: index
        return applyIndexMap(i, phonemeToResultIndexMap, true)
    }

    private fun applyIndexMap(index: Int, map: IntArray, nextValid: Boolean = false): Int {
        if (index < 0) {
            return -1
        }
        if (index == map.size) {
            return map[index - 1] + 1
        }
        if (nextValid) {
            var validIndex = index
            while (validIndex < map.size - 1 && map[validIndex] == -1) {
                validIndex++
            }
            return map[validIndex]
        }
        return map[index]
    }

    fun atBeginning(): Boolean = phonemeIndex == 0
    fun atEnd(): Boolean = atEnd

    fun findMatchInRange(start: Int, end: Int, phonemeClass: PhonemeClass): Int? {
        for (i in start until end) {
            advanceTo(i)
            if (phonemeClass.matchesCurrent(this)) {
                return i
            }
        }
        return null
    }

    fun phonemeToCharacterIndex(phonemeIndex: Int): Int {
        return phonemes.subList(0, phonemeIndex).sumOf { it.length }
    }

    fun characterToPhonemeIndex(characterIndex: Int): Int {
        var length = 0
        for ((index, phoneme) in phonemes.withIndex()) {
            if (length >= characterIndex) {
                return index
            }
            length += phoneme.length
        }
        throw IndexOutOfBoundsException("Character index $index outside of phoneme index range")
    }

    val stressedPhonemeIndex: Int?
        get() = word?.calcStressedPhonemeIndex(repo)

    val syllables: List<Syllable>?
        get() = word?.let { breakIntoSyllables(it) }

    val segments: List<WordSegment>?
        get() = word?.segments
}

