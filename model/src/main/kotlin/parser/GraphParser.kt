package ru.yole.etymograph.parser

import ru.yole.etymograph.*

fun parseWordChain(repo: GraphRepository, line: String, language: Language): Word {
    var currentWordText: String? = null
    var currentWordGloss: String? = null
    var firstWord: Word? = null
    var prevWord: Word? = null
    var lineSource: String? = null
    var linkType: LinkType? = null
    var linkAssociative = false

    fun doneWord() {
        val word = repo.findOrAddWord(currentWordText!!, language, currentWordGloss, null, null, lineSource, null)
        currentWordText = null
        currentWordGloss = null

        if (firstWord == null) {
            firstWord = word
        }
        prevWord?.let { prevWord ->
            if (linkType != null) {
                repo.addLink(prevWord, word, linkType!!, repo.findMatchingRule(prevWord, word)?.let { listOf(it) } ?: emptyList(), lineSource, null)
            }
        }
        if (!linkAssociative) {
            prevWord = word
        }
    }

    for ((index, token) in line.split(' ').withIndex()) {
        if (index == 0 && token.startsWith('{')) {
            lineSource = token.removePrefix("{").removeSuffix("}")
            continue
        }

        if (token == "<") {
            doneWord()
            linkType = Link.Derived
            linkAssociative = false
        }
        else if (token == "+" || token == "<+") {
            doneWord()
            linkType = Link.Agglutination
            linkAssociative = true
        }
        else if (currentWordText == null) {
            currentWordText = token
        }
        else if (currentWordGloss == null) {
            currentWordGloss = token
        }
        else {
            currentWordGloss += " $token"
        }
    }
    doneWord()
    return firstWord!!
}
