package page.yole.etymograph.importers

import org.jdom2.Content
import org.jdom2.Element
import org.jdom2.input.SAXBuilder
import page.yole.etymograph.CorpusText
import page.yole.etymograph.Graph
import page.yole.etymograph.JsonGraph
import page.yole.etymograph.KnownPartsOfSpeech
import page.yole.etymograph.Language
import page.yole.etymograph.Link
import page.yole.etymograph.Rule
import page.yole.etymograph.Word
import page.yole.etymograph.findMatchingRule
import page.yole.etymograph.isAnyGlossSimilar
import page.yole.etymograph.removeDiacritics
import page.yole.etymograph.removePunctuation
import java.io.File
import java.nio.file.Path


fun main(args: Array<String>) {
    val ieRepo = JsonGraph.fromJson(Path.of("data/etymograph-ie"))
    val tlhdigFile = File(args[0])
    val doc = SAXBuilder().build(tlhdigFile)
    val body = doc.rootElement.getChild("body").getChild("div1").getChild("text")
    val header = doc.rootElement.getChild("AOHeader").getChild("docID").text

    importTLHDig(ieRepo, header,body.children)

    ieRepo.save()
}

fun importTLHDig(graph: Graph, title: String, children: List<Element>) {
    class TlhDigWord(val text: String, val trans: String, val element: Element?)

    val wordElements = mutableListOf<TlhDigWord>()
    val text = buildString {
        for (element in children) {
            if (element.name == "lb" && isNotEmpty()) {
                removeSuffix(" ")
                append("\n")
            }
            else if (element.name == "w") {
                val wordText = collectWordText(element)
                    .replace("wa", "u̯a")
                    .replace("ia", "i̯a")
                    .replace("IA", "I̯A")
                if (wordText.isEmpty()) {
                    continue
                }
                val textWords = wordText.split(' ')
                val transWords = element.getAttributeValue("trans")?.split(' ') ?: textWords
                for(i in 0..<textWords.size-1) {
                    wordElements.add(TlhDigWord(textWords[i], transWords[i], null))
                }
                wordElements.add(TlhDigWord(textWords.last(), transWords.last(), element))
                append(wordText)
                append(" ")
            }
        }
    }

    val hittite = graph.languageByShortName("Hitt")!!
    val corpusText = graph.addCorpusText(text, title, hittite)

    for ((index, w) in wordElements.withIndex()) {
        val wordText = w.text
        val wordElement = w.element
        val cleanText = removePunctuation(wordText)
        if (cleanText.isEmpty()) {
            println("Skipping empty word $wordText")
            continue
        }
        if ('x' in cleanText) {
            println("Skipping damaged word $wordText")
            continue
        }

        val existingWords = graph.wordsByText(hittite, cleanText, true)
        if (existingWords.isNotEmpty()) {
            println("Skipping existing word $wordText")
            continue
        }
        if (w.element == null) {
            println("Skipping Akkadian preposition")
            continue
        }
        val trans = w.trans
            .replace("y", "i̯")

        val sylWord = graph.findOrAddWord(cleanText, hittite, null, syllabographic = true)
        corpusText.associateWord(index, sylWord)

        var transWord = if (trans.none { it.isUpperCase() }) {
            graph.addWord(trans, hittite, gloss = null).also {
                graph.addLink(sylWord, it, Link.Transcription)
            }
        }
        else {
            sylWord
        }

        val mrpSel = wordElement.getAttributeValue("mrp0sel")
            ?.trim()
            ?.split(' ')
            ?.maxBy { it.length }
        if (mrpSel.isNullOrBlank()) continue

        val mrpAttr = wordElement.getAttributeValue("mrp" + mrpSel[0]) ?: continue
        val mrpElements = mrpAttr.split('@', limit = 4)

        val lemma = mrpElements[0]
        var cleanLemma = lemma
            .substringBefore('/')
            .substringBefore(',')
            .replace("(", "")
            .replace(")", "")
            .replace("y", "i̯")
            .replace("IA", "I̯A")
            .trimEnd { it.isDigit() }
            .removeSuffix("-")
            .replace("=", "")
            .replace("°", "^")
            .convertSubscripts()

        val gloss = mrpElements[1]
        val analysis = mrpElements[2]
        var selectedAnalysis = if (analysis.startsWith('{')) {
            findSelectedAnalysis(analysis, mrpSel.drop(1))
        }
        else {
            analysis
        }

        if (lemma.any { it.isUpperCase() } && '_' in selectedAnalysis) {
            val oldText = transWord.text
            transWord = createAkkadianCompound(sylWord, selectedAnalysis, transWord)
            selectedAnalysis = selectedAnalysis.substringBefore('_')
            val tail = oldText.drop(transWord.text.length).replace("_", "")
            if (cleanLemma.endsWith(tail)) {
                cleanLemma = cleanLemma.removeSuffix(tail)
            }
        }

        val paradigmWithEnclitics = mrpElements[3].substringBeforeLast('@')
        if ("+=" in paradigmWithEnclitics) {
            val enclitics = paradigmWithEnclitics.substringAfter("+=").trim()
            transWord = createEncliticCompound(transWord, enclitics)
        }

        val posMarkers = mutableListOf<String>()
        val rules = if (selectedAnalysis.isNotEmpty()) {
            findRulesByMrp(selectedAnalysis, hittite, posMarkers)
        }
        else {
            emptyList()
        }

        if (cleanLemma.normalizeSpelling() == transWord.text.normalizeSpelling()) {
            if (transWord.gloss == null) {
                transWord.gloss = gloss
            }
            if (transWord.pos == null) {
                transWord.pos = posMarkers.singleOrNull()
            }
        }
        else {
            val matchingWord = graph.wordsByText(hittite, cleanLemma)
                .firstOrNull { isAnyGlossSimilar(it, gloss) }

            val lemmaWord = matchingWord ?: graph.findOrAddWord(cleanLemma, hittite, gloss,
                pos = posMarkers.singleOrNull() ?: rules.firstOrNull()?.fromPOS?.firstOrNull(),
                syllabographic = lemma.any { it.isUpperCase() })

            graph.addLink(transWord, lemmaWord, Link.Derived,
                rules = rules)
        }
    }
}

private fun String.normalizeSpelling(): String = this
    .replace("da", "ta")
    .replace("bu", "pu")


private fun findRulesByMrp(mrp: String, hittite: Language, posMarkers: MutableList<String>): List<Rule> {
    val analysis = mrp.removeSuffix("(UNM)").removeSuffix("(ABBR)").removePrefix("…:")
    val categories = analysis.split('.')
    val categorySet = mutableSetOf<String>()
    for (cat in categories) {
        if (cat.first().isDigit()) {
            categorySet.add(cat.take(1))
            categorySet.add(cat.drop(1))
        }
        else {
            val posMarker = posMap[cat]
            if (posMarker != null) {
                posMarkers.add(posMarker)
            }
            else if (cat !in ignoreCategories) {
                categorySet.add(cat)
            }
        }
    }

    if (categorySet.isEmpty()) {
        return emptyList()
    }

    val step1Rule = findMatchingRule(hittite, setOf(categorySet.first()))
    if (step1Rule != null) {
        val step2Rule = findMatchingRule(hittite, categorySet.drop(1).toSet())
        if (step2Rule != null) {
            return listOf(step1Rule, step2Rule)
        }
    }

    findMatchingRule(hittite, categorySet)?.let { return listOf(it) }
    categorySet.removeAll(optionalIgnoreCategories)
    return findMatchingRule(hittite, categorySet).also {
        if (it == null) println("No rule for mrp $mrp")
    }?.let { listOf(it) } ?: emptyList()
}

private val posMap = mapOf(
    "PNm" to KnownPartsOfSpeech.properName.abbreviation,
    "GN" to KnownPartsOfSpeech.properName.abbreviation,
    "ADV" to "ADV"
)

private val ignoreCategories = setOf("LUW||HITT", "HITT")
private val optionalIgnoreCategories = setOf("C", "N", "PRS")

private fun createEncliticCompound(word: Word, enclitics: String): Word {
    val (encliticText, encliticAnalysis) = enclitics.trimEnd('@').split('@', limit = 2)
    val encliticTexts = encliticText.split('=')
    val encliticAnalyses = encliticAnalysis.split('=')

    var tail = word.text
    val compoundElements = mutableListOf<Word>()
    for (i in encliticTexts.size - 1 downTo 0) {
        val clitic = findOrAddWordByTextOnly(word.language, encliticTexts[i], encliticAnalyses[i])
        val len = longestCommonTail(tail.removeDiacritics(), encliticTexts[i])
        if (len == 0) break
        tail = tail.substring(0, tail.length - len).removeSuffix("-")
        compoundElements.add(0, clitic)
    }

    val headWord = findOrAddWordByTextOnly(word.language, tail, null)
    compoundElements.add(0, headWord)

    word.graph.createCompound(word, compoundElements)
    return headWord
}

private fun longestCommonTail(s1: String, s2: String): Int =
    s1.reversed().zip(s2.reversed()).takeWhile { (c1, c2) -> c1 == c2 }.count()

private fun createAkkadianCompound(
    sylWord: Word,
    selectedAnalysis: String,
    transWord: Word
): Word {
    var tail = sylWord.text
    val elements = selectedAnalysis.split('_')
    val compoundElements = mutableListOf<Word>()
    for (i in elements.size - 1 downTo 1) {
        if ('-' !in tail) break
        val cliticAnalysis = elements[i]
        val cliticTail = tail.substringAfterLast('-')
        val cliticText = if (cliticTail.startsWith('_')) cliticTail else "_$cliticTail"
        tail = tail.substringBeforeLast('-')

        val clitic = findOrAddWordByTextOnly(sylWord.language, cliticText, cliticAnalysis, syllabographic = true)
        compoundElements.add(0, clitic)
    }

    val headWord = findOrAddWordByTextOnly(sylWord.language, tail, null, syllabographic = true)
    compoundElements.add(0, headWord)

    sylWord.graph.createCompound(transWord, compoundElements)
    return headWord
}

private fun findOrAddWordByTextOnly(
    language: Language,
    text: String,
    gloss: String?,
    syllabographic: Boolean = false
): Word = (language.graph.wordsByText(language, text, syllabographic).firstOrNull()
    ?: language.graph.addWord(text, language, gloss, syllabographic = syllabographic))

private fun collectWordText(element: Element): String {
    return buildString {
        for (c in element.content) {
            if (c.cType == Content.CType.Text) {
                append(c.value)
            } else if (c is Element) {
                when (c.name) {
                    "aGr" -> {
                        val akkText = collectWordText(c).convertSubscripts()
                        if (akkText.startsWith("-")) {
                            append("-_")
                            append(akkText.drop(1))
                        }
                        else {
                            append("_")
                            append(akkText)
                        }
                    }
                    "sGr" -> append(collectWordText(c).convertSubscripts())
                    "d" -> append("^" + collectWordText(c) + "^")
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
        val (optionKey, optionValue) = option.trim().removePrefix("{").trim().split("→")
        if (optionKey.trim() == key) {
            return optionValue.trim()
        }
    }
    println("No matching analysis for $key in $mrp")
    return ""
}

const val subscriptZero = '₀'
const val subscriptNine = '₉'

private fun String.convertSubscripts(): String {
    return map { c ->
        if (c in subscriptZero..subscriptNine) (c.code - subscriptZero.code + '0'.code).toChar() else c
    }.joinToString("")
}
