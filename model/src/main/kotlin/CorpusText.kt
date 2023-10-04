package ru.yole.etymograph

class CorpusWord(val index: Int, val text: String, val normalizedText: String, val word: Word?, val gloss: String?, val homonym: Boolean)

class CorpusTextLine(val corpusWords: List<CorpusWord>)

class CorpusText(
    id: Int,
    text: String,
    var title: String?,
    val language: Language,
    words: List<Word?>,
    source: List<SourceRef>,
    notes: String?
): LangEntity(id, source, notes) {
    private data class WordText(val baseText: String, val normalizedText: String, val index: Int)

    private val _words = words.toMutableList()
    private var _text: String = text

    var text: String
        get() = _text
        set(value) {
            val wordMap = mutableMapOf<String, MutableList<Word?>>()
            for (word in iterateWords()) {
                wordMap.getOrPut(word.normalizedText) { arrayListOf() }.add(_words.getOrNull(word.index))
            }

            _text = value

            _words.clear()
            for (word in iterateWords()) {
                val list = wordMap[word.normalizedText]
                if (list.isNullOrEmpty()) {
                    _words.add(null)
                }
                else {
                    _words.add(list.first())
                    list.removeAt(0)
                }
            }
            _words.dropLastWhile { it == null }
        }

    val words: List<Word?>
        get() = _words

    fun mapToLines(repo: GraphRepository): List<CorpusTextLine> {
        var currentIndex = 0
        return text.split("\n").map { line ->
            val textWords = splitIntoNormalizedWords(line, currentIndex)
            CorpusTextLine(textWords.map { tw ->
                val word = _words.getOrNull(currentIndex)
                if (word != null) {
                    CorpusWord(currentIndex++, tw.baseText, tw.normalizedText, word, word.getOrComputeGloss(repo), repo.isHomonym(word))
                }
                else {
                    val gloss = repo.wordsByText(language, tw.normalizedText).firstOrNull()?.getOrComputeGloss(repo)
                    CorpusWord(currentIndex++, tw.baseText, tw.normalizedText, null, gloss, false)
                }
            })
        }
    }

    private fun splitIntoNormalizedWords(line: String, lineStartIndex: Int): List<WordText> {
        var currentIndex = lineStartIndex
        return line.split(' ').map {
            val cleanText = it.trimEnd(*punctuation).replace("[", "").replace("]", "")
            WordText(it, language.normalizeWord(cleanText), currentIndex++)
        }
    }

    fun containsWord(word: Word): Boolean {
        if (word in _words) return true
        for (wordText in iterateWords()) {
            if (wordText.normalizedText == word.text && _words.getOrNull(wordText.index) == null) return true
        }
        return false
    }

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

    fun associateWord(index: Int, word: Word) {
        while (_words.size <= index) {
            _words.add(null)
        }
        _words[index] = word
    }

    fun removeWord(word: Word) {
        _words.replaceAll { if (it?.id == word.id) null else it }
    }

    companion object {
        val punctuation = charArrayOf('!', ',', '.', '?', ':', ';')
    }
}

class Translation(
    id: Int,
    val corpusText: CorpusText,
    var text: String,
    source: List<SourceRef>,
    notes: String?
) : LangEntity(id, source, notes)
