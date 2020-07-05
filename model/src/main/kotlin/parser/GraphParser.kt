package ru.yole.etymograph.parser

import ru.yole.etymograph.InMemoryGraphRepository
import ru.yole.etymograph.Language
import ru.yole.etymograph.Link
import ru.yole.etymograph.Word
import java.io.File
import java.io.FileInputStream
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
    var linkType: String? = null

    fun doneWord() {
        val word = Word(currentWordText!!, language, currentWordGloss, lineSource, null)
        currentWordText = null
        currentWordGloss = null

        repo.addWord(word)
        if (firstWord == null) {
            firstWord = word
        }
        if (prevWord != null && linkType != null) {
            repo.addLink(Link(prevWord!!, word, linkType!!, lineSource, null))
        }
        prevWord = word
    }

    for ((index, token) in line.split(' ').withIndex()) {
        if (index == 0 && token.startsWith('{')) {
            lineSource = token.removePrefix("{").removeSuffix("}")
            continue
        }

        if (token == "<") {
            doneWord()
            linkType = "derived from"
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

fun parseGraph(stream: InputStream): InMemoryGraphRepository {
    val repo = InMemoryGraphRepository()
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
    return repo
}

fun main(args: Array<String>) {
    val repo = parseGraph(FileInputStream(File(args[0])))
    for (corpusText in repo.allCorpusTexts()) {
        println(corpusText.text)
        val lines = corpusText.mapToLines()
        for (line in lines) {
            for ((textWord, word) in line.pairs) {
                println(textWord)
                if (word != null) {
                    println("${word.text}: ${word.gloss}")
                    println("Related words:")
                    for (link in repo.getLinksFrom(word)) {
                        println("${link.type} ${link.toWord.text} (${link.toWord.gloss})")
                    }
                    for (link in repo.getLinksTo(word)) {
                        println("${link.type} ${link.fromWord.text} (${link.fromWord.gloss})")
                    }
                }
            }
        }
    }
}

