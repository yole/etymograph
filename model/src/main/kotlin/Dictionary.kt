package ru.yole.etymograph

interface Dictionary {
    fun lookup(language: Language, word: String): List<DictionaryWord>
}

data class DictionaryWord(
    val gloss: String?,
    val fullGloss: String?,
    val pos: String?,
    val classes: List<String>,
    val source: String,
    var reconstructed: Boolean = false
)

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
                    lookupResult.joinToString(", ") { it.source }
        }
    } else {
        augmentWord(word, lookupResult.single())
    }
    return null
}

private fun tryFindBestMatch(word: Word, dictionaryWords: List<DictionaryWord>): DictionaryWord? {
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

fun augmentWord(word: Word, dictionaryWord: DictionaryWord) {
    if (word.pos == null && dictionaryWord.pos != null) {
        word.pos = dictionaryWord.pos
    }
    val newClasses = dictionaryWord.classes.filter { it !in word.classes }
    if (newClasses.isNotEmpty()) {
        word.classes += newClasses
    }
    if (word.gloss == null) {
        word.gloss = dictionaryWord.gloss
    }
    if (word.fullGloss == null) {
        val fullGloss = dictionaryWord.fullGloss
        if (fullGloss != null && fullGloss.length <= 200) {
            word.fullGloss = fullGloss
        }
    }

    if (word.source.none { it.refText == dictionaryWord.source }) {
        word.source += SourceRef(null, dictionaryWord.source)
    }
}
