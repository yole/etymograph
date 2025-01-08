package ru.yole.etymograph

data class AlternativeModel(val gloss: String, val word: Word, val rule: Rule?)

object Alternatives {
    fun requestAlternativesByText(repo: GraphRepository, language: Language, wordText: String, word: Word?): List<AlternativeModel> {
        val wordsWithMatchingText = repo.wordsByText(language, wordText)
        val allVariants = wordsWithMatchingText.flatMap {
            val gloss = it.getOrComputeGloss(repo)
            if (gloss == null)
                emptyList()
            else {
                val baseWord = if (it == wordsWithMatchingText.first())
                    emptyList()
                else
                    listOf(AlternativeModel(gloss, it, null))
                if (it.glossOrNP() == null && !repo.isCompound(it)) {
                    baseWord
                }
                else {
                    val alts = requestAlternatives(repo, it)
                    baseWord + alts.map { pc ->
                        val rule = pc.rules.single()
                        AlternativeModel(rule.applyCategories(gloss), it, rule)
                    }
                }
            }
        }
        return allVariants.associateBy { it.gloss }.values.toList()
    }

    fun requestAlternatives(repo: GraphRepository, word: Word): List<ParseCandidate> {
        val pos = word.getOrComputePOS(repo)
        return repo.paradigmsForLanguage(word.language).filter { pos in it.pos }.flatMap { paradigm ->
            val wordParadigm = paradigm.generate(word, repo)
            wordParadigm.flatMap { column ->
                column.flatMap { alts ->
                    alts?.mapNotNull {
                        if (it.word.text == word.text && it.rule != null)
                            ParseCandidate(word.text, listOf(it.rule), null, it.word.takeIf { w -> w.id >= 0 })
                        else
                            null
                    } ?: emptyList()
                }
            }
        }
    }
}