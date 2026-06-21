package page.yole.etymograph

data class AlternativeModel(val gloss: String, val word: Word, val rule: Rule?)

object Alternatives {
    fun request(language: Language, wordText: String, word: Word?): List<AlternativeModel> {
        val wordsWithMatchingText = language.graph.wordsByText(language, wordText)
        val allVariants = wordsWithMatchingText.flatMap {
            val gloss = it.getOrComputeGloss()
            if (gloss == null)
                emptyList()
            else {
                val baseWord = if (it === (word ?: wordsWithMatchingText.first()))
                    emptyList()
                else
                    listOf(AlternativeModel(gloss, it, null))
                if (it.glossOrNP() == null && !language.graph.isCompound(it)) {
                    baseWord
                }
                else {
                    baseWord + requestForms(it, gloss)
                }
            }
        }
        return allVariants.associateBy { it.gloss }.values.toList()
    }

    private fun requestForms(word: Word, gloss: String): List<AlternativeModel> {
        val pos = word.getOrComputePOS()
        return word.graph.paradigmsForLanguage(word.language).filter { pos in it.pos }.flatMap { paradigm ->
            val wordParadigm = paradigm.generate(word)
            wordParadigm.flatMap { column ->
                column.flatMap { alts ->
                    alts?.mapNotNull {
                        if (it.word.text == word.text && it.rule != null)
                            AlternativeModel(it.rule.applyCategories(gloss), word, it.rule)
                        else
                            null
                    } ?: emptyList()
                }
            }
        }
    }

    fun accept(corpusText: CorpusText, index: Int, word: Word, rule: Rule?) {
        if (rule == null) {
            corpusText.associateWord(index, word)
        }
        else {
            val gloss = word.glossOrNP()
                ?: (if (word.graph.isCompound(word)) word.getOrComputeGloss() else null)
                ?: throw IllegalArgumentException("Accepting alternative with unglossed word ${word.id}")

            val linkedWord = word.graph.getLinksTo(word).singleOrNull { it.rules == listOf(rule) }?.fromEntity as? Word
            if (linkedWord != null) {
                corpusText.associateWord(index, linkedWord)
            }
            else {
                val newGloss = rule.applyCategories(gloss)
                val newWord = word.graph.findOrAddWord(word.text, word.language, newGloss)
                word.graph.addLink(newWord, word, Link.Derived, listOf(rule))
                newWord.gloss = null

                corpusText.associateWord(index, newWord)
            }
        }
    }
}
