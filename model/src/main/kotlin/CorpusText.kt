package ru.yole.etymograph

class CorpusWord(val index: Int, val text: String, val word: Word?, val gloss: String?, val homonym: Boolean)

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
    private data class WordText(val baseText: String, val normalizedText: String)

    private val _words = words.toMutableList()
    private var _text: String = text

    var text: String
        get() = _text
        set(value) {
            val wordMap = mutableMapOf<String, MutableList<Word?>>()
            var currentIndex = 0
            for (word in iterateWords()) {
                wordMap.getOrPut(word.normalizedText) { arrayListOf() }.add(_words.getOrNull(currentIndex++))
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
            val textWords = splitIntoNormalizedWords(line)
            CorpusTextLine(textWords.map { (textWord, _) ->
                val word = _words.getOrNull(currentIndex)
                if (word != null) {
                    CorpusWord(currentIndex++, textWord, word, word.getOrComputeGloss(repo), repo.isHomonym(word))
                }
                else {
                    val gloss = repo.wordsByText(language, textWord).firstOrNull()?.getOrComputeGloss(repo)
                    CorpusWord(currentIndex++, textWord, null, gloss, false)
                }
            })
        }
    }

    private fun splitIntoNormalizedWords(line: String): List<WordText> {
        return line.split(' ').map { WordText(it, language.normalizeWord(it.trimEnd(*punctuation))) }
    }

    fun containsWord(word: Word): Boolean {
        if (word in _words) return true
        if (_words.none { w -> w != null && w.text == word.text && w.id != word.id }) {
            for (wordText in iterateWords()) {
                if (wordText.normalizedText == word.text) return true
            }
        }
        return false
    }

    private fun iterateWords(): Sequence<WordText> {
        return sequence {
            for (line in text.split('\n')) {
                for (wordText in splitIntoNormalizedWords(line)) {
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
    val text: String,
    source: List<SourceRef>,
    notes: String?
) : LangEntity(id, source, notes)
