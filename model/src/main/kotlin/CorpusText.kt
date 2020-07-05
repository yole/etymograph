package ru.yole.etymograph

class CorpusWord(val text: String, val word: Word?, val gloss: String?)

class CorpusTextLine(val corpusWords: List<CorpusWord>)

class CorpusText(
    val id: Int,
    val text: String,
    val title: String?,
    val language: Language,
    val words: List<Word>,
    source: String?,
    notes: String?
): LangEntity(source, notes) {
    fun mapToLines(repo: GraphRepository): List<CorpusTextLine> {
        return text.split("\n").map { line ->
            val textWords = line.split(' ')
            CorpusTextLine(textWords.map { textWord ->
                val word = words.find { word -> word.text.equals(textWord.trimEnd('!', ',', '.'), true) }
                CorpusWord(textWord, word, word?.getOrComputeGloss(repo))
            })
        }
    }
}
