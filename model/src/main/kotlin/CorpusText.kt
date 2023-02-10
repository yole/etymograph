package ru.yole.etymograph

import java.util.*

class CorpusWord(val text: String, val word: Word?, val gloss: String?)

class CorpusTextLine(val corpusWords: List<CorpusWord>)

class CorpusText(
    val id: Int,
    val text: String,
    val title: String?,
    val language: Language,
    val words: MutableList<Word>,
    source: String?,
    notes: String?
): LangEntity(source, notes) {
    fun mapToLines(repo: GraphRepository): List<CorpusTextLine> {
        return text.split("\n").map { line ->
            val textWords = splitIntoNormalizedWords(line)
            CorpusTextLine(textWords.map { textWord ->
                val word = words.find { word -> word.text == textWord }
                if (word != null) {
                    CorpusWord(textWord, word, word.getOrComputeGloss(repo))
                }
                else {
                    CorpusWord(textWord, null, repo.wordsByText(language, textWord).firstOrNull()?.getOrComputeGloss(repo))
                }
            })
        }
    }

    private fun splitIntoNormalizedWords(line: String): List<String> {
        return line.split(' ').map { it.trimEnd('!', ',', '.', '?').lowercase(Locale.FRANCE) }
    }

    fun containsWord(word: Word): Boolean {
        if (word in words) return true
        return text.split("\n").any { word.text in splitIntoNormalizedWords(it) }
    }
}
