package ru.yole.etymograph

class CorpusWord(val index: Int, val text: String, val word: Word?, val gloss: String?, val homonym: Boolean)

class CorpusTextLine(val corpusWords: List<CorpusWord>)

class CorpusText(
    id: Int,
    val text: String,
    val title: String?,
    val language: Language,
    words: List<Word?>,
    source: String?,
    notes: String?
): LangEntity(id, source, notes) {
    private val _words = words.toMutableList()

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

    private fun splitIntoNormalizedWords(line: String): List<Pair<String, String>> {
        return line.split(' ').map { it to language.normalizeWord(it.trimEnd('!', ',', '.', '?', ':')) }
    }

    fun containsWord(word: Word): Boolean {
        if (word in _words) return true
        return text.split("\n").any {
            word.text in splitIntoNormalizedWords(it).map { it.second } &&
                    _words.none { w -> w != null && w.text == word.text && w.id != word.id }
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
}
