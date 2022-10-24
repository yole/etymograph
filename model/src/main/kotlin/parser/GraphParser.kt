package ru.yole.etymograph.parser

import ru.yole.etymograph.*
import java.io.InputStream

abstract class GraphSectionParser(val repo: InMemoryGraphRepository) {
    abstract fun parseLine(line: String)
    open fun done() {}
}

class LanguagesSectionParser(repo: InMemoryGraphRepository) : GraphSectionParser(repo) {
    override fun parseLine(line: String) {
        val shortName = line.substringBefore(':').trim()
        val name = line.substringAfter(':').trim()
        repo.addLanguage(Language(name, shortName))

    }
}

fun parseWordChain(repo: InMemoryGraphRepository, line: String, language: Language): Word {
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

class CorpusTextSectionParser(repo: InMemoryGraphRepository): GraphSectionParser(repo) {
    private var language: Language? = null
    private var title: String? = null
    private var text: String = ""
    private var source: String? = null
    private val words = mutableListOf<Word>()

    override fun parseLine(line: String) {
        if (language == null) {
            language = repo.languageByShortName(line.substringBefore(':'))
            val tail = line.substringAfter(':')
            if (tail.endsWith('}')) {
                source = tail.substringAfterLast('{').trimEnd('}')
                title = tail.substringBeforeLast('{').trim()
            }
            else {
                title = tail
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

    override fun done() {
        repo.addCorpusText(text, title, language!!, words, source, null)
    }
}

fun parseGraph(stream: InputStream, repo: InMemoryGraphRepository) {
    stream.reader().useLines { lines ->
        var currentSection: GraphSectionParser? = null
        for (line in lines) {
            if (line.isBlank()) continue
            if (line.startsWith("[")) {
                currentSection?.done()
                currentSection = when(line.trim()) {
                    "[languages]" -> LanguagesSectionParser(repo)
                    "[corpustext]" -> CorpusTextSectionParser(repo)
                    else -> throw IllegalArgumentException("Unknown section name $line")
                }
            }
            else {
                currentSection?.parseLine(line.trimEnd())
            }
        }
        currentSection?.done()
    }
}
