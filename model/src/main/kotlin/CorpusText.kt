package ru.yole.etymograph

import java.util.*

class CorpusWord(
    val index: Int,
    val text: String,
    val normalizedText: String,
    val segmentedText: String,
    val word: Word?,
    val wordCandidates: List<Word>?,
    val gloss: String?,
    val segmentedGloss: String?,
    val stressIndex: Int?,
    val stressLength: Int?,
    val homonym: Boolean
)

class CorpusTextLine(val corpusWords: List<CorpusWord>)

class CorpusWordAssociation(
    val index: Int,
    var word: Word
)

class CorpusText(
    id: Int,
    text: String,
    var title: String?,
    val language: Language,
    words: List<CorpusWordAssociation> = emptyList(),
    source: List<SourceRef> = emptyList(),
    notes: String? = null
): LangEntity(id, source, notes) {
    private data class WordText(val baseText: String, val normalizedText: String, val index: Int)

    private val _words = words.toMutableList()
    private var _text: String = text

    val words: List<CorpusWordAssociation>
        get() = _words

    fun wordByIndex(index: Int): Word? {
        return _words.find { it.index == index }?.word
    }

    var text: String
        get() = _text
        set(value) {
            val wordMap = mutableMapOf<String, MutableList<Word?>>()
            for (word in iterateWords()) {
                wordMap.getOrPut(word.normalizedText) { arrayListOf() }.add(wordByIndex(word.index))
            }

            _text = value

            _words.clear()
            for (word in iterateWords()) {
                val list = wordMap[word.normalizedText]
                if (!list.isNullOrEmpty()) {
                    val assocWord = list.first()
                    if (assocWord != null) {
                        _words.add(CorpusWordAssociation(word.index, assocWord))
                    }
                    list.removeAt(0)
                }
            }
        }

    fun mapToLines(repo: GraphRepository): List<CorpusTextLine> {
        var currentIndex = 0
        var sentenceStart = true
        return text.split("\n").map { line ->
            val textWords = splitIntoNormalizedWords(line, currentIndex)
            CorpusTextLine(textWords.map { tw ->
                val word = wordByIndex(currentIndex)
                val normalizedText = if (sentenceStart || leadingPunctuation.any { tw.baseText.startsWith(it) })
                    tw.normalizedText
                else
                    restoreCase(tw.normalizedText, tw.baseText)
                sentenceStart = tw.baseText.endsWith('.')
                if (word != null) {
                    val stressData = word.calculateStress(repo)
                    val leadingPunctuation = tw.baseText.takeWhile { it in leadingPunctuation }
                    val trailingPunctuation = tw.baseText.takeLastWhile { it in punctuation }
                    val wordWithSegments = repo.restoreSegments(word)
                    val segmentedText = leadingPunctuation + restoreCase(wordWithSegments.segmentedText(), tw.baseText) + trailingPunctuation
                    val glossWithSegments = wordWithSegments.getOrComputeGloss(repo) ?: word.getOrComputeGloss(repo)
                    val stressIndex = adjustStressIndex(wordWithSegments, stressData?.index)?.plus(leadingPunctuation.length)

                    CorpusWord(currentIndex++, tw.baseText, normalizedText, segmentedText, word, null,
                        word.getOrComputeGloss(repo),
                        glossWithSegments, stressIndex, stressData?.length, repo.isHomonym(word))
                }
                else {
                    val wordCandidates = repo.wordsByText(language, tw.normalizedText)
                    val gloss = wordCandidates.firstOrNull()?.getOrComputeGloss(repo)
                    CorpusWord(currentIndex++, tw.baseText, normalizedText, tw.baseText,null, wordCandidates,
                        gloss, gloss, null, null,false)
                }
            })
        }
    }

    private fun adjustStressIndex(wordWithSegments: Word?, stressIndex: Int?): Int? {
        if (stressIndex == null) return null
        val segments = wordWithSegments?.segments ?: return stressIndex
        var result = stressIndex
        for (segment in segments) {
            if (segment.firstCharacter > 0 && segment.firstCharacter <= stressIndex) {
                result++
            }
        }
        return result
    }

    private fun splitIntoNormalizedWords(line: String, lineStartIndex: Int): List<WordText> {
        var currentIndex = lineStartIndex
        return line.split(' ').map {
            val cleanText = it.trimStart(*leadingPunctuation).trimEnd(*punctuation)
                .replace("[", "").replace("]", "")
            WordText(it, language.normalizeWord(cleanText), currentIndex++)
        }
    }

    fun containsWord(word: Word): Boolean {
        if (_words.any { it.word == word }) return true
        for (wordText in iterateWords()) {
            if (wordText.normalizedText == word.text && _words.getOrNull(wordText.index) == null) return true
        }
        return false
    }

    fun wordCount(): Int = iterateWords().count()

    private fun iterateWords(): Sequence<WordText> {
        var currentIndex = 0
        return sequence {
            for (line in text.split('\n')) {
                for (wordText in splitIntoNormalizedWords(line, currentIndex)) {
                    currentIndex++
                    yield(wordText)
                }
            }
        }
    }

    fun normalizedWordTextAt(index: Int) = iterateWords().elementAt(index).normalizedText

    fun associateWord(index: Int, word: Word) {
        for (assoc in _words) {
            if (assoc.index == index) {
                assoc.word = word
                return
            }
        }
        _words.add(CorpusWordAssociation(index, word))
    }

    fun removeWord(word: Word) {
        _words.removeIf { it.word == word }
    }

    companion object {
        val punctuation = charArrayOf('!', ',', '.', '?', ':', ';', '\"', '\'', '(', ')')
        val leadingPunctuation = charArrayOf('\"', '(', '\'')
    }
}

fun restoreCase(normalizedText: String, baseText: String): String {
    val offset = baseText.takeWhile { it == '"' }.length
    return buildString {
        for ((i, c) in normalizedText.withIndex()) {
            if (i + offset < baseText.length && baseText[i + offset].isUpperCase()) {
                append(c.uppercase(Locale.FRANCE))
            }
            else {
                append(c)
            }
        }
    }
}

class Translation(
    id: Int,
    val corpusText: CorpusText,
    var text: String,
    source: List<SourceRef>,
    notes: String?
) : LangEntity(id, source, notes)
