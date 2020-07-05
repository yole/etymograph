package ru.yole.etymograph

class CorpusTextLine(val pairs: List<Pair<String, Word?>>)

class CorpusText(
    val id: Int,
    val text: String,
    val title: String?,
    val language: Language,
    val words: List<Word>,
    source: String?,
    notes: String?
): LangEntity(source, notes) {
    fun mapToLines(): List<CorpusTextLine> {
        return text.split("\n").map { line ->
            val textWords = line.split(' ')
            CorpusTextLine(textWords.map { textWord ->
                textWord to words.find { word -> word.text.equals(textWord.trimEnd('!', ',', '.'), true) }
            })
        }
    }
}
