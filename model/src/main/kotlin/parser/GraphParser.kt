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
        val word = repo.addWord(currentWordText!!, language, currentWordGloss, lineSource, null)
        currentWordText = null
        currentWordGloss = null

        if (firstWord == null) {
            firstWord = word
        }
        prevWord?.let { prevWord ->
            if (linkType != null) {
                repo.addLink(prevWord, word, linkType!!, repo.findMatchingRule(prevWord, word), lineSource, null)
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

class CorpusTextSectionParser(val repo: GraphRepository) {
    private var language: Language? = null
    private var title: String? = null
    private var text: String = ""
    private var source: String? = null
    private val words = mutableListOf<Word>()

    private fun parseLine(line: String) {
        if (language == null) {
            language = repo.languageByShortName(line.substringBefore(':'))
            val tail = line.substringAfter(':')
            val firstLine: String
            if (tail.endsWith('}')) {
                source = tail.substringAfterLast('{').trimEnd('}')
                firstLine = tail.substringBeforeLast('{').trim()
            }
            else {
                firstLine = tail.trim()
            }

            if (firstLine.startsWith('"') && firstLine.endsWith('"')) {
                text += firstLine.removePrefix("\"").removeSuffix("\"")
            }
            else {
                title = firstLine
            }
        }
        else if (!line.startsWith(' ')) {
            if (text.isNotEmpty()) text += "\n"
            text += line
        }
        else {
            words.add(parseWordChain(repo, line.trim(), language!!))
        }
    }

    fun parseText(corpusText: String): CorpusText {
        for (line in corpusText.split('\n')) {
            parseLine(line)
        }
        return repo.addCorpusText(text, title, language!!, words, source, null)
    }
}
