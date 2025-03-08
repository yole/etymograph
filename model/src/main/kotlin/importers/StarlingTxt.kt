package ru.yole.etymograph.importers

import ru.yole.etymograph.*
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Path

val wordPattern = Regex("(.+?)( \\[(.+)])? `(.+)'")

fun String.removeMarkup(): String {
    return replace("\\B", "")
        .replace("\\b", "")
        .replace("\\I", "")
        .replace("\\i", "")
}

// Starling seems to be using a weird custom encoding
// https://github.com/rhaver/Starling-cs/blob/master/Visual%20C%23%20Express%202010%20code/StarlingDBF%20Converter/StarlingDecoder.cs
fun String.decodeStarling(): String {
    return replace("\u001Da", "æ")
        .replace("\u001Dt", "þ")
        .replace("\u001Dd", "ð")
        .replace('\u00B0', 'ā')
        .replace('\u00B9', 'ē')
        .replace('\u00D2', 'ō')
        .replace('\u00C5', 'ī')
        .replace('\u00F1', 'ū')
        .replace('Ú', '~')
        .replace('\u00C4', '\u0304') // combining macron
        .replace('\u00DF', '\u0306') // combining breve
        .replace("{U+01E3}", "ǣ") // I think this is an artifact of how the particular file I got was generated, not a Starling thing
        .replace("hw", "hʷ")
}

data class StarlingWord(
    val textVariants: List<String>,
    val classes: String,
    val gloss: String
)

fun parseStarlingWord(word: String): StarlingWord? {
    val m = wordPattern.matchEntire(word) ?: return null
    val text = m.groupValues[1].removeMarkup().decodeStarling()
    val textVariants = if ('~' in text) {
        text.split('~').map { it.trim() }
    }
    else if ('(' in text) {
        val prefix = text.substringBefore('(')
        val suffix = text.substringAfter(')')
        val infix = text.substringAfter('(').substringBefore(')')
        listOf(prefix + suffix, prefix + infix + suffix)
    }
    else if ('/' in text) {
        val pos = text.indexOf('/')
        val beforeSlash = text.substring(0, pos - 1)
        val afterSlash = text.substring(pos + 2)
        listOf(beforeSlash + text[pos-1] + afterSlash, beforeSlash + text[pos+1] + afterSlash)
    } else if (',' in text) {
        text.split(',').map { it.trim() }
    } else {
        listOf(text)
    }
    return StarlingWord(
        textVariants.map {
            it.substringBefore(' ').removePrefix("*")
        },
        m.groupValues[3], m.groupValues[4]
    )
}

fun findWord(repo: GraphRepository, language: Language, starlingWord: StarlingWord, gloss: String): List<Word> {
    val text = starlingWord.textVariants[0]
    val words = repo.wordsByText(language, text)
    if (words.isNotEmpty()) {
        return words.filter { isGlossSimilar(gloss, it.getOrComputeGloss(repo)) || isGlossSimilar(gloss, it.fullGloss) }
    }
    val fuzzyMatches = repo.allWords(language).filter {
        val normText = it.text.replace('ċ', 'c').replace('ġ', 'g')
        if (text.endsWith('-')) {
            normText.startsWith(text.removeSuffix("-"))
        }
        else {
            normText == text
        }
    }
    if (fuzzyMatches.isNotEmpty()) {
        return fuzzyMatches.filter { isGlossSimilar(gloss, it.getOrComputeGloss(repo)) || isGlossSimilar(gloss, it.fullGloss) }
    }
    return emptyList()
}

fun findEtymology(repo: GraphRepository, word: Word, language: Language): Word? {
    var targetWord = word
    while (true) {
        val origin = repo.getLinksFrom(targetWord).singleOrNull { it.type == Link.Origin }?.toEntity as? Word ?: return null
        if (origin.language == language) return origin
        targetWord = origin
    }
}

fun main(args: Array<String>) {
    val ieRepo = JsonGraphRepository.fromJson(Path.of("data/ie"))
    val pgmc = ieRepo.languageByShortName("PGmc")!!
    val oe = ieRepo.languageByShortName("OE")!!

    val starlingFile = File(args[0])
    val lines = starlingFile.readLines(Charset.forName("iso-8859-1")).drop(1)

    var imported = 0

    val kroonen = ieRepo.allPublications().find { it.refId == "Kroonen 2013" }!!
    val sequence = ieRepo.ruleSequenceByName("pgmc-to-oe")!!

    for (line in lines) {
        val (base, translation, page) = line.trim().split('#')
        if (translation.isEmpty()) {
            continue
        }

        val baseWord = parseStarlingWord(base)
        if (baseWord == null) {
            println("Pattern not matched for base word: $line")
            continue
        }
        if (baseWord.textVariants.size > 1) {
            println("Multiple text variants for base word: $line")
            continue
        }

        val translationWord = parseStarlingWord(translation.removePrefix("OE").trim())
        if (translationWord == null) {
            println("Pattern not matched for translation word: $line")
            continue
        }

        val pgmcWords = findWord(ieRepo, pgmc, baseWord, baseWord.gloss)
        if (pgmcWords.size > 1) {
            println("Ambiguous PGmc word: $line")
            continue
        }
        val pgmcWord = pgmcWords.singleOrNull()

        val translationGloss = if (translationWord.gloss == "id.") baseWord.gloss else translationWord.gloss

        val oeWords = findWord(ieRepo, oe, translationWord, translationGloss)
        if (oeWords.size > 1) {
            println("Ambiguous OE word: $line")
            continue
        }
        val oeWord = oeWords.singleOrNull()

        val oeEtymology = oeWord?.let { findEtymology(ieRepo, it, pgmc) }
        val pgmcNew = if (pgmcWord == null) { if (oeEtymology != null) " [<->]" else " [NEW]" } else ""
        val oeNew = if (oeWord == null) " [NEW]" else ""

        if (pgmcWord != null && oeWord != null) {
            continue
        }

        if (pgmcWord == null && oeWord == null) {
            if ("airi" in baseWord.textVariants || "egede" in translationWord.textVariants) {
                continue
            }

            val pgmcNewWord = ieRepo.findOrAddWord(baseWord.textVariants[0], pgmc, baseWord.gloss,
                source = listOf(SourceRef(kroonen.id, page)))
            val oeNewWord = ieRepo.findOrAddWord(translationWord.textVariants[0], oe, translationGloss,
                source = listOf(SourceRef(kroonen.id, page)))
            val link = ieRepo.addLink(oeNewWord, pgmcNewWord, Link.Origin)
            ieRepo.applyRuleSequence(link, sequence)

            println("IMPORT ${baseWord.textVariants[0]} [${baseWord.classes}] '${baseWord.gloss}'$pgmcNew > ${translationWord.textVariants[0]}$oeNew '${translationGloss}'")

            imported++
            if (imported == 5) {
                break
            }
        }
        else if (pgmcWord == null && oeEtymology != null) {
            val pgmcNewWord = ieRepo.findOrAddWord(baseWord.textVariants[0], pgmc, null,
                source = listOf(SourceRef(kroonen.id, page)))
            ieRepo.addLink(pgmcNewWord, oeEtymology, Link.Variation)

            println("VARIANT ${baseWord.textVariants[0]} [${baseWord.classes}] '${baseWord.gloss}'$pgmcNew > ${translationWord.textVariants[0]}$oeNew '${translationGloss}'")

            imported++
            if (imported == 5) {
                break
            }

        }
        else {
            println("SKIP ${baseWord.textVariants[0]} [${baseWord.classes}] '${baseWord.gloss}'$pgmcNew > ${translationWord.textVariants[0]}$oeNew '${translationGloss}'")
        }
    }

    ieRepo.save()
}
