package ru.yole.etymograph.importers

import org.jdom2.Content
import org.jdom2.Element
import org.jdom2.input.SAXBuilder
import ru.yole.etymograph.CorpusText
import ru.yole.etymograph.GraphRepository
import ru.yole.etymograph.JsonGraphRepository
import ru.yole.etymograph.Language
import ru.yole.etymograph.Link
import ru.yole.etymograph.Word
import ru.yole.etymograph.removePunctuation
import java.io.File
import java.nio.file.Path


fun main(args: Array<String>) {
    val ieRepo = JsonGraphRepository.fromJson(Path.of("data/etymograph-ie"))
    val tlhdigFile = File(args[0])
    val doc = SAXBuilder().build(tlhdigFile)
    val body = doc.rootElement.getChild("body").getChild("div1").getChild("text")
    val header = doc.rootElement.getChild("AOHeader").getChild("docID").text

    importTLHDig(ieRepo, header,body.children)

    ieRepo.save()
}

fun importTLHDig(ieRepo: GraphRepository, title: String, children: List<Element>) {
    val wordTexts = mutableListOf<String>()
    val wordElements = mutableListOf<Element>()
    val text = buildString {
        for (element in children) {
            if (element.name == "lb" && isNotEmpty()) {
                removeSuffix(" ")
                append("\n")
            }
            else if (element.name == "w") {
                val wordText = collectWordText(element)
                if (wordText.isEmpty()) {
                    continue
                }
                val transWords = element.getAttributeValue("trans")?.split(' ')?.size ?: 1
                repeat(transWords) {
                    wordElements.add(element)
                    wordTexts.add(wordText)
                }
                append(wordText)
                repeat(transWords)
                append(" ")
            }
        }
    }

    val hittite = ieRepo.languageByShortName("Hitt")!!
    val corpusText = ieRepo.addCorpusText(text, title, hittite)

    for ((index, wordElement) in wordElements.withIndex()) {
        val wordText = wordTexts[index]
        val cleanText = removePunctuation(wordText)
        if (cleanText.isEmpty()) {
            println("Skipping empty word $wordText")
            continue
        }

        val existingWords = ieRepo.wordsByText(hittite, cleanText, true)
        if (existingWords.isNotEmpty()) {
            println("Skipping existing word $wordText")
            continue
        }
        val trans = wordElement.getAttributeValue("trans")
        if (trans != null && " " in trans) {
            println("Skipping word with space in transcription: $trans")
            continue
        }

        val sylWord = ieRepo.addWord(cleanText, hittite, null, syllabographic = true)
        corpusText.associateWord(index, sylWord)

        if (trans == null) continue
        var transWord = if (trans.none { it.isUpperCase() }) {
            ieRepo.addWord(trans, hittite, gloss = null).also {
                ieRepo.addLink(sylWord, it, Link.Transcription)
            }
        }
        else {
            sylWord
        }

        val mrpSel = wordElement.getAttributeValue("mrp0sel")?.trim()
        if (mrpSel.isNullOrBlank()) continue

        val mrpAttr = wordElement.getAttributeValue("mrp" + mrpSel[0]) ?: continue
        val mrpElements = mrpAttr.split('@')

        val lemma = mrpElements[0]
        val cleanLemma = lemma.removeSuffix("-")
            .replace("=", "")
            .replace("°", "^")
            .convertSubscripts()

        val gloss = mrpElements[1]
        val analysis = mrpElements[2]
        val selectedAnalysis = if (analysis.startsWith('{')) {
            findSelectedAnalysis(analysis, mrpSel.drop(1))
        }
        else {
            analysis
        }

        if (lemma.any { it.isUpperCase() } && '_' in selectedAnalysis) {
            transWord = createAkkadianCompound(sylWord, selectedAnalysis, hittite, ieRepo, transWord)
        }

        val paradigm = mrpElements.getOrNull(3)?.trim()
        if (cleanLemma == trans) {
            if (transWord.gloss == null) {
                transWord.gloss = gloss
            }
        }
        else {
            if ('/' in lemma) {
                println("Skipping lemma with /: $lemma")
                continue
            }
            val lemmaWord = ieRepo.addWord(cleanLemma, hittite, gloss,
                syllabographic = lemma.any { it.isUpperCase() })
            ieRepo.addLink(transWord, lemmaWord, Link.Derived)
        }
    }
}

private fun createAkkadianCompound(
    sylWord: Word,
    selectedAnalysis: String,
    hittite: Language,
    ieRepo: GraphRepository,
    transWord: Word
): Word {
    var tail = sylWord.text
    val elements = selectedAnalysis.split('_')
    val compoundElements = mutableListOf<Word>()
    for (i in elements.size - 1 downTo 1) {
        if ('-' !in tail) break
        val cliticAnalysis = elements[i]
        val cliticText = '_' + tail.substringAfterLast('-')
        tail = tail.substringBeforeLast('-')

        val clitic = findOrAddWordByTextOnly(hittite, cliticText, cliticAnalysis)
        compoundElements.add(0, clitic)
    }

    val headWord = findOrAddWordByTextOnly(hittite, tail, null)
    compoundElements.add(0, headWord)

    ieRepo.createCompound(transWord, compoundElements)
    return headWord
}

private fun findOrAddWordByTextOnly(
    language: Language,
    text: String,
    gloss: String?
): Word = (language.graph.wordsByText(language, text, true).firstOrNull()
    ?: language.graph.addWord(text, language, gloss, syllabographic = true))

private fun collectWordText(element: Element): String {
    return buildString {
        for (c in element.content) {
            if (c.cType == Content.CType.Text) {
                append(c.value)
            } else if (c is Element) {
                when (c.name) {
                    "aGr" -> {
                        append("_")
                        append(collectWordText(c).convertSubscripts())
                    }
                    "sGr" -> append(collectWordText(c).convertSubscripts())
                    "d" -> append("^" + c.text + "^")
                    "del_in" -> append("[")
                    "del_fin" -> append("]")
                    "laes_in" -> append(CorpusText.leftHalfBracket)
                    "laes_fin" -> append(CorpusText.rightHalfBracket)
                }
            }
        }
    }
}

private fun findSelectedAnalysis(mrp: String, key: String): String {
    if (key.isEmpty()) return ""
    val options = mrp.split('}').filter { it.isNotEmpty() }
    for (option in options) {
        val (optionKey, optionValue) = option.removePrefix("{").trim().split("→")
        if (optionKey.trim() == key) {
            return optionValue.trim()
        }
    }
    return ""
}

const val subscriptZero = '₀'
const val subscriptNine = '₉'

private fun String.convertSubscripts(): String {
    return map { c ->
        if (c in subscriptZero..subscriptNine) (c.code - subscriptZero.code + '0'.code).toChar() else c
    }.joinToString("")
}
