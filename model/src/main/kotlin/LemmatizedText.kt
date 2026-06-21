package ru.yole.etymograph

class LemmatizedToken(val form: String, val lemma: String, val pos: String, val categories: List<String>)

class LemmatizedWord(val form: String, val tokens: List<LemmatizedToken>)

class LemmatizedText(val text: String, val words: List<LemmatizedWord>)

fun importLemmatizedText(language: Language, dictionary: Dictionary, title: String, text: LemmatizedText): CorpusText {
    val graph = language.graph
    var relativeIndex = 0
    val corpusText = graph.corpusTextsInLanguage(language).find { it.title == title }
        ?.also { corpusText ->
            relativeIndex = corpusText.wordCount()
            corpusText.text += "\n${text.text}"
        }
        ?: graph.addCorpusText(text.text, title, language)
    for ((index, word) in text.words.withIndex()) {
        val token = word.tokens.singleOrNull() ?: continue
        var lemmaWords = graph.wordsByTextFuzzy(language, token.lemma)
            .filter {
                graph.getLinksFrom(it).none { link -> link.type == Link.Variation } &&
                it.grammaticalCategorySuffix() == null
            }
        if (lemmaWords.isEmpty()) {
            if (token.pos == "proper noun") {
                val baseWord = graph.findOrAddWord(token.lemma, language, null, pos = "NP")
                lemmaWords = listOf(baseWord)
            }
            else {
                val dictionaryWords = dictionary.lookup(graph, language, token.lemma)
                lemmaWords = dictionaryWords.result.map {
                    findOrCreateWordFromDictionary(graph, it)
                }
            }
        }

        if (lemmaWords.size > 1) {
            for (lemmaWord in lemmaWords) {
                createWordForForm(lemmaWord, graph, token)
            }
        }
        else if (lemmaWords.size == 1) {
            val formWord = createWordForForm(lemmaWords.single(), graph, token)
            corpusText.associateWord(index + relativeIndex, formWord)
        }
    }
    return corpusText
}

private fun createWordForForm(lemmaWord: Word, repo: Graph, word: LemmatizedToken): Word {
    val categories = mapCategoryValues(lemmaWord, word.categories)
    if (categories.isEmpty() || categories.all { isDefaultCategoryValue(lemmaWord.language, it) }) {
        return lemmaWord
    }
    val rule = findMatchingRule(lemmaWord, categories)

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
        repo.addLink(newWord, lemmaWord, Link.Derived, listOf(rule))
        newWord.gloss = null
    }

    return newWord
}

fun findMatchingRule(word: Word, categories: Set<String>): Rule? {
    return word.graph.allRules().find { it.fromLanguage == word.language && it.addsCategories(categories) }
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
        val parsedRuleCategory = parsedRuleCategories.find {
            it?.value?.abbreviation.equals(category, ignoreCase = true)
        }
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
    return language.grammaticalCategories.any {
        it.values.firstOrNull()?.abbreviation.equals(categoryValue, ignoreCase = true)
    }
}

