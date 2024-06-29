package ru.yole.etymograph

class LemmatizedWord(val form: String, val lemma: String, val pos: String, val categories: List<String>)

class LemmatizedText(val text: String, val words: List<LemmatizedWord>)

fun importLemmatizedText(repo: GraphRepository, language: Language, dictionary: Dictionary, title: String, text: LemmatizedText) {
    var relativeIndex = 0
    val corpusText = repo.corpusTextsInLanguage(language).find { it.title == title }
        ?.also { corpusText ->
            relativeIndex = corpusText.wordCount()
            corpusText.text += "\n${text.text}"
        }
        ?: repo.addCorpusText(text.text, title, language)
    for ((index, word) in text.words.withIndex()) {
        var lemmaWords = repo.wordsByTextFuzzy(language, word.lemma)
            .filter {
                repo.getLinksFrom(it).none { link -> link.type == Link.Variation } &&
                it.grammaticalCategorySuffix(repo) == null
            }
        if (lemmaWords.isEmpty()) {
            if (word.pos == "proper noun") {
                val baseWord = repo.findOrAddWord(word.lemma, language, null, pos = "NP")
                lemmaWords = listOf(baseWord)
            }
            else {
                val dictionaryWords = dictionary.lookup(language, word.lemma)
                lemmaWords = dictionaryWords.map {
                    repo.findOrAddWord(word.lemma, language, it.gloss, it.fullGloss, it.pos, it.classes, it.reconstructed, it.source, it.notes)
                }
            }
        }

        if (lemmaWords.size > 1) {
            for (lemmaWord in lemmaWords) {
                createWordForForm(lemmaWord, repo, word)
            }
        }
        else if (lemmaWords.size == 1) {
            val formWord = createWordForForm(lemmaWords.single(), repo, word)
            corpusText.associateWord(index + relativeIndex, formWord)
        }
    }
}

private fun createWordForForm(lemmaWord: Word, repo: GraphRepository, word: LemmatizedWord): Word {
    val categories = mapCategoryValues(lemmaWord, word.categories)
    if (categories.isEmpty() || categories.all { isDefaultCategoryValue(lemmaWord.language, it) }) {
        return lemmaWord
    }
    val rule = findMatchingRule(repo, lemmaWord, categories)

    println("INT: ${lemmaWord.text} '${lemmaWord.gloss}', form ${word.form}, morphology ${word.categories}, rule: ${rule?.name ?: "none found"}")

    val glossWithCategories = (lemmaWord.gloss ?: "?") + categories.joinToString("") { ".$it" }
    if (rule != null) {
        val existingFormWord = repo.getLinksTo(lemmaWord)
            .find { it.type == Link.Derived && it.rules == listOf(rule) }
            ?.fromEntity as? Word
        if (existingFormWord != null) {
            return existingFormWord
        }
    }

    val newWord = repo.findOrAddWord(word.form, lemmaWord.language, glossWithCategories)
    if (rule != null) {
        repo.addLink(newWord, lemmaWord, Link.Derived, listOf(rule), emptyList(), null)
        newWord.gloss = null
    }

    return newWord
}

fun findMatchingRule(repo: GraphRepository, word: Word, categories: Set<String>): Rule? {
    return repo.allRules().find { it.fromLanguage == word.language && it.addsCategories(categories) }
}

private fun mapCategoryValues(word: Word, categories: List<String>): Set<String> {
    val allCategoryValues = word.language.grammaticalCategories
        .filter { word.pos in it.pos }
        .flatMap { it.values }
    val addedCategories = mutableSetOf<String>()
    for (category in categories) {
        val catValue = allCategoryValues.find { it.name.lowercase() == category }?.abbreviation
        if (catValue != null) {
            addedCategories += catValue
        }
    }
    return addedCategories
}

fun Rule.addsCategories(categories: Set<String>): Boolean {
    val ruleCategories = addedCategories ?: return false
    val parsedRuleCategories = parseCategoryValues(fromLanguage, ruleCategories).toMutableList()
    for (category in categories) {
        val parsedRuleCategory = parsedRuleCategories.find { it.second?.abbreviation == category }
        if (parsedRuleCategory != null) {
            parsedRuleCategories.remove(parsedRuleCategory)
        }
        else if (!isDefaultCategoryValue(fromLanguage, category)) {
            return false
        }
    }
    return parsedRuleCategories.isEmpty()
}

fun isDefaultCategoryValue(language: Language, categoryValue: String): Boolean {
    return language.grammaticalCategories.any { it.values.firstOrNull()?.abbreviation == categoryValue }
}

