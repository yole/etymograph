package ru.yole.etymograph

data class LookupVariant(val text: String, val disambiguation: String)

data class LookupResult(
    val result: List<DictionaryWord>,
    val messages: List<String>,
    val variants: List<LookupVariant> = emptyList()
) {
    companion object {
        val empty = LookupResult(emptyList(), emptyList())
    }
}

interface Dictionary {
    fun lookup(repo: GraphRepository, language: Language, word: String, disambiguation: String? = null): LookupResult
}

data class DictionaryRelatedWord(
    val linkType: LinkType,
    val relatedWord: DictionaryWord,
    val linkDetails: List<String> = emptyList()
)

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
        if (result.message != null) {
            println(result.message)
        }
    }
}

data class AugmentVariant(val text: String, val disambiguation: String?)
data class AugmentResult(val message: String?, val variants: List<AugmentVariant>)

fun augmentWordWithDictionary(
    repo: GraphRepository, dictionary: Dictionary, word: Word,
    disambiguation: String? = null
): AugmentResult {
    val lookupResult = dictionary.lookup(repo, word.language, word.text, disambiguation)
    if (lookupResult.result.isEmpty()) {
        return AugmentResult("Found no matching word for ${word.text}", emptyList())
    } else if (lookupResult.result.size > 1) {
        val bestMatch = selectLookupResult(word, lookupResult, disambiguation)
        if (bestMatch != null) {
            augmentWord(repo, word, bestMatch)
        } else {
            return AugmentResult("Found multiple matching words for ${word.text}",
                lookupResult.result.mapIndexed { index, it ->
                    val pos = it.pos?.let { "$it " } ?: ""
                    AugmentVariant(pos + (it.gloss ?: "?"), "${word.text}:$index")
                })
        }
    } else {
        augmentWord(repo, word, lookupResult.result.single())
    }
    return AugmentResult(
        lookupResult.messages.joinToString("\n").takeIf { it.isNotEmpty() },
        lookupResult.variants.map { AugmentVariant(it.text, it.disambiguation) }
    )
}

private fun selectLookupResult(word: Word, lookupResult: LookupResult, disambiguation: String?): DictionaryWord? {
    if (disambiguation != null && disambiguation.startsWith(word.text + ":")) {
        val index = disambiguation.substringAfterLast(':').toIntOrNull()
        if (index != null && index >= 0 && index < lookupResult.result.size) {
            return lookupResult.result[index]
        }
    }
    return tryFindBestMatch(word, lookupResult.result)
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
        candidates = candidates.filter { candidate -> isGlossSimilar(candidate.gloss, gloss) }
        if (candidates.size == 1) {
            return candidates[0]
        }
    }
    return null
}

private fun isGlossSimilar(candidateGloss: String?, gloss: String?): Boolean {
    return if (candidateGloss.isNullOrEmpty() || gloss.isNullOrEmpty())
        false
    else
        wordSet(candidateGloss)
            .intersect(wordSet(gloss))
            .isNotEmpty()
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
    val existingWords = repo.wordsByText(word.language, word.text)
    return existingWords.find { isGlossSimilar(it.gloss, word.gloss) }
        ?: repo.findOrAddWord(
            word.text, word.language, word.gloss, word.fullGloss, word.pos, word.classes, word.reconstructed,
            listOf(SourceRef(null, word.source)
        )
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
            var rules = emptyList<Rule>()
            if (relatedDictionaryWord.linkDetails.isNotEmpty() && relatedDictionaryWord.linkType == Link.Derived) {
                val rule = findMatchingRule(repo, word, relatedDictionaryWord.linkDetails.toSet())
                if (rule != null) {
                    rules = listOf(rule)
                }
                word.gloss = null
            }
            else if (relatedDictionaryWord.linkType == Link.Variation) {
                word.gloss = null
            }

            repo.addLink(word, relatedWord, relatedDictionaryWord.linkType, rules)
        }
    }

    if (repo.findCompoundsByCompoundWord(word).isEmpty() && dictionaryWord.compoundComponents.isNotEmpty()) {
        val componentWords = dictionaryWord.compoundComponents.map {
            findOrCreateWordFromDictionary(repo, it)
        }
        repo.createCompound(word, componentWords)
    }
}
