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
                val dictionaryWords = dictionary.lookup(language, token.lemma)
                lemmaWords = dictionaryWords.result.map {
                    findOrCreateWordFromDictionary(graph, it)
                }
            }
        }

        if (lemmaWords.size > 1) {
            for (lemmaWord in lemmaWords) {
                createWordForForm(lemmaWord, token.form, token.categories)
            }
        }
        else if (lemmaWords.size == 1) {
            val formWord = createWordForForm(lemmaWords.single(), token.form, token.categories)
            corpusText.associateWord(index + relativeIndex, formWord)
        }
    }
    return corpusText
}

fun createWordForForm(lemmaWord: Word, form: String, categories: List<String>): Word {
    val graph = lemmaWord.graph
    val categories = mapCategoryValues(lemmaWord, categories)
    if (categories.isEmpty() || categories.all { isDefaultCategoryValue(lemmaWord.language, it) }) {
        return lemmaWord
    }
    val rule = findMatchingRule(lemmaWord.language, categories)

    println("INT: ${lemmaWord.text} '${lemmaWord.gloss}', form ${form}, morphology ${categories}, rule: ${rule?.name ?: "none found"}")

    val glossWithCategories = (lemmaWord.gloss ?: "?") + categories.joinToString("") { ".$it" }
    if (rule != null) {
        val existingFormWord = graph.getLinksTo(lemmaWord)
            .find { it.type == Link.Derived && it.rules == listOf(rule) }
            ?.fromEntity as? Word
        if (existingFormWord != null) {
            return existingFormWord
        }
    }

    val newWord = graph.findOrAddWord(form, lemmaWord.language, glossWithCategories)
    if (rule != null) {
        graph.addLink(newWord, lemmaWord, Link.Derived, listOf(rule))
        newWord.gloss = null
    }

    return newWord
}

fun findMatchingRule(language: Language, categories: Set<String>): Rule? {
    return language.graph.allRules().find { it.fromLanguage == language && it.addsCategories(categories) }
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

private fun Rule.addsCategories(categories: Set<String>): Boolean {
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

