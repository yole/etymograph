package ru.yole.etymograph

interface Dictionary {
    fun lookup(repo: GraphRepository, language: Language, word: String): List<DictionaryWord>
}

data class DictionaryRelatedWord(val linkType: LinkType, val relatedWord: DictionaryWord)

data class DictionaryWord(
    val text: String,
    val language: Language,
    val gloss: String?,
    val fullGloss: String?,
    val pos: String?,
    val classes: List<String>,
    val source: String,
    var reconstructed: Boolean = false,
    val relatedWords: MutableList<DictionaryRelatedWord> = mutableListOf(),
    val compoundComponents: MutableList<DictionaryWord> = mutableListOf()
)

fun augmentWithDictionary(repo: GraphRepository, language: Language, dictionary: Dictionary) {
    for (word in repo.dictionaryWords(language)) {
        val result = augmentWordWithDictionary(repo, dictionary, word)
        if (result != null) {
            println(result)
        }
    }
}

fun augmentWordWithDictionary(repo: GraphRepository, dictionary: Dictionary, word: Word): String? {
    val lookupResult = dictionary.lookup(repo, word.language, word.text)
    if (lookupResult.isEmpty()) {
        return "Found no matching word for ${word.text}"
    } else if (lookupResult.size > 1) {
        val bestMatch = tryFindBestMatch(word, lookupResult)
        if (bestMatch != null) {
            augmentWord(repo, word, bestMatch)
        } else {
            return "Found multiple matching words for ${word.text}: " +
                    lookupResult.joinToString(", ") { it.source }
        }
    } else {
        augmentWord(repo, word, lookupResult.single())
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

fun findOrCreateWordFromDictionary(
    repo: GraphRepository,
    word: DictionaryWord,
): Word {
    return repo.findOrAddWord(
        word.text, word.language, word.gloss, word.fullGloss, word.pos, word.classes, word.reconstructed,
        listOf(SourceRef(null, word.source))
    )
}

fun augmentWord(repo: GraphRepository, word: Word, dictionaryWord: DictionaryWord) {
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

    for (relatedDictionaryWord in dictionaryWord.relatedWords) {
        if (repo.getLinksFrom(word).filter { it.type == relatedDictionaryWord.linkType }.isEmpty()) {
            val relatedWord = findOrCreateWordFromDictionary(repo, relatedDictionaryWord.relatedWord)
            repo.addLink(word, relatedWord, relatedDictionaryWord.linkType, emptyList(), emptyList(), null)
        }
    }

    if (repo.findCompoundsByCompoundWord(word).isEmpty() && dictionaryWord.compoundComponents.isNotEmpty()) {
        val componentWords = dictionaryWord.compoundComponents.map {
            findOrCreateWordFromDictionary(repo, it)
        }
        val compound = repo.createCompound(word, componentWords.first())
        compound.components.addAll(componentWords.drop(1))
    }
}
