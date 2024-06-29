package ru.yole.etymograph

interface Dictionary {
    fun lookup(language: Language, word: String): List<Word>
}

fun augmentWithDictionary(repo: GraphRepository, language: Language, dictionary: Dictionary) {
    for (word in repo.dictionaryWords(language)) {
        val result = augmentWordWithDictionary(dictionary, word)
        if (result != null) {
            println(result)
        }
    }
}

fun augmentWordWithDictionary(dictionary: Dictionary, word: Word): String? {
    val lookupResult = dictionary.lookup(word.language, word.text)
    if (lookupResult.isEmpty()) {
        return "Found no matching word for ${word.text}"
    } else if (lookupResult.size > 1) {
        val bestMatch = tryFindBestMatch(word, lookupResult)
        if (bestMatch != null) {
            augmentWord(word, bestMatch)
        } else {
            return "Found multiple matching words for ${word.text}: " +
                    lookupResult.joinToString(", ") { it.source.first().refText }
        }
    } else {
        augmentWord(word, lookupResult.single())
    }
    return null
}

private fun tryFindBestMatch(word: Word, dictionaryWords: List<Word>): Word? {
    var candidates = dictionaryWords
    if (word.pos != null) {
        candidates = candidates.filter { it.pos == word.pos }
        if (candidates.size == 1) {
            return candidates[0]
        }
    }
    val gloss = word.gloss
    if (!gloss.isNullOrEmpty()) {
        val glossWords = wordSet(gloss)
        candidates = candidates.filter { candidate ->
            val dictGloss = candidate.gloss
            if (dictGloss.isNullOrEmpty())
                false
            else
                wordSet(dictGloss)
                    .intersect(glossWords)
                    .isNotEmpty()
        }
        if (candidates.size == 1) {
            return candidates[0]
        }
    }
    return null
}

private fun wordSet(gloss: String): Set<String> =
    gloss.lowercase()
        .split(' ')
        .map { it.trimEnd(',', ';', ' ', '!', '.') }
        .filter { it != "to" && it != "the" && it != "a" }
        .toSet()

fun augmentWord(word: Word, dictionaryWord: Word) {
    if (word.pos == null && dictionaryWord.pos != null) {
        word.pos = dictionaryWord.pos
    }
    val newClasses = dictionaryWord.classes.filter { it !in word.classes }
    if (newClasses.isNotEmpty()) {
        word.classes += newClasses
    }
    if (word.fullGloss == null) {
        val fullGloss = dictionaryWord.fullGloss
        if (fullGloss != null && fullGloss.length <= 200) {
            word.fullGloss = fullGloss
        }
    }
    val newSources = dictionaryWord.source.filter { it !in word.source }
    if (newSources.isNotEmpty()) {
        word.source += newSources
    }
}
