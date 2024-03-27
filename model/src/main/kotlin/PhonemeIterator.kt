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
        "third next" to 3
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
            (phonemeClass?.name?.rich(true) ?: "sound".rich())
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
    private val phonemes: List<String>
    private val resultPhonemes: MutableList<String>
    private var phonemeIndex = 0
    private val phonemeToResultIndexMap: IntArray
    private val stressCallback: (() -> Int)?

    constructor(word: Word, resultPhonemic: Boolean? = null) : this(
        if (word.isPhonemic) word.text else word.normalizedText.trimEnd('-'),
        word.language,
        word.isPhonemic,
        resultPhonemic,
        { word.calcStressedPhonemeIndex() }
    )

    constructor(
        text: String,
        language: Language,
        phonemic: Boolean = false,
        resultPhonemic: Boolean? = null,
        stressCallback: (() -> Int)? = null
    ) {
        this.language = language
        this.stressCallback = stressCallback

        val sourcePhonemes = mutableListOf<String>()
        resultPhonemes = mutableListOf()
        val lookup = if (phonemic) this.language.phonoPhonemeLookup else this.language.orthoPhonemeLookup

        lookup.iteratePhonemes(text) { phonemeText, phoneme ->
            val normalizedText = if (phonemic) phoneme?.sound else phoneme?.graphemes?.first()
            sourcePhonemes.add(normalizedText ?: phonemeText)

            val normalizedResultText = if (resultPhonemic ?: phonemic) phoneme?.sound else phoneme?.graphemes?.first()
            resultPhonemes.add(normalizedResultText ?: phonemeText)
        }

        phonemes = sourcePhonemes
        phonemeToResultIndexMap = IntArray(phonemes.size) { it }
    }

    private constructor(
        phonemes: List<String>,
        resultPhonemes: MutableList<String>,
        language: Language,
        stressCallback: (() -> Int)? = null,
        phonemeToResultIndexMap: IntArray
    ) {
        this.phonemes = phonemes
        this.resultPhonemes = resultPhonemes
        this.language = language
        this.stressCallback = stressCallback
        this.phonemeToResultIndexMap = phonemeToResultIndexMap
    }

    val current: String get() = phonemes[phonemeIndex]
    val last: String? get() = phonemes.lastOrNull()
    val size: Int get() = phonemes.size
    val index: Int get() = phonemeIndex

    operator fun get(index: Int): String = phonemes[index]
    fun atRelative(relativeIndex: Int): String? = phonemes.getOrNull(phonemeIndex + relativeIndex)

    fun clone(): PhonemeIterator {
        return PhonemeIterator(phonemes, resultPhonemes, language, stressCallback, phonemeToResultIndexMap).also {
            it.phonemeIndex = phonemeIndex
        }
    }

    fun advanceTo(index: Int) {
        phonemeIndex = index
    }

    fun advance(): Boolean {
        return advanceBy(1)
    }

    fun advanceBy(relativeIndex: Int): Boolean {
        val newIndex = phonemeIndex + relativeIndex
        if (newIndex >= 0 && newIndex < phonemes.size) {
            phonemeIndex = newIndex
            return true
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
        val resultIndex = phonemeToResultIndexMap[targetIndex]
        resultPhonemes.add(resultIndex, s)
        for (i in targetIndex..<phonemes.size) {
            phonemeToResultIndexMap[i]++
        }
    }

    fun result(): String {
        return resultPhonemes.joinToString("")
    }

    fun atBeginning(): Boolean = phonemeIndex == 0
    fun atEnd(): Boolean = phonemeIndex == phonemes.size - 1

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

    val stressedPhonemeIndex: Int?
        get() = stressCallback?.invoke()
}

