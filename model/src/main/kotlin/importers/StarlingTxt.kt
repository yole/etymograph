package ru.yole.etymograph.importers

import ru.yole.etymograph.*
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
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
        .replace("kw", "kʷ")
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

class StarlingImporter(
    private val repo: GraphRepository,
    private val fromLang: Language,
    private val toLang: Language,
    private val skipList: Collection<String>,
    private val sourcePub: Int,
    private val sequence: RuleSequence
) {
    var imported = 0

    private fun findWord(language: Language, starlingWord: StarlingWord, gloss: String): List<Word> {
        for (text in starlingWord.textVariants) {
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
        }
        return emptyList()
    }

    private fun findEtymology(word: Word): Word? {
        var targetWord = word
        while (true) {
            val origin = repo.getLinksFrom(targetWord).singleOrNull { it.type == Link.Origin }?.toEntity as? Word ?: return null
            if (origin.language == fromLang) return origin
            targetWord = origin
        }
    }

    private fun findDerivation(word: Word): Word? {
        var targetWord = word
        while (true) {
            val origin = repo.getLinksTo(targetWord).singleOrNull { it.type == Link.Origin }?.fromEntity as? Word ?: return null
            if (origin.language == toLang) return origin
            targetWord = origin
        }
    }

    fun createWordWithVariants(baseWord: StarlingWord, language: Language, source: List<SourceRef>, gloss: String? = null): Word {
        val newWord = repo.findOrAddWord(baseWord.textVariants[0], language, gloss ?: baseWord.gloss,
            source = source)

        for (variant in baseWord.textVariants.drop(1)) {
            if (variant != baseWord.textVariants[0] && variant.isNotBlank()) {
                val pgmcVariant = repo.findOrAddWord(variant, language, null,
                    source = source)
                repo.addLink(pgmcVariant, newWord, Link.Variation)
            }
        }
        return newWord
    }

    fun importLine(line: String) {
        var importLogged = false
        val (base, translation, page) = line.replace("%Á", "`").trim().split('#')
        if (translation.isEmpty()) {
            return

        }

        val baseWord = parseStarlingWord(base)
        if (baseWord == null) {
            println("Pattern not matched for base word: $line")
            return
        }

        val translationWord = parseStarlingWord(translation.removePrefix("OE").trim())
        if (translationWord == null) {
            println("Pattern not matched for translation word: $line")
            return
        }

        val pgmcWords = findWord(fromLang, baseWord, baseWord.gloss)
        if (pgmcWords.size > 1) {
            println("Ambiguous PGmc word: ${pgmcWords.joinToString(", ")} in $line")
            return
        }
        val pgmcWord = pgmcWords.singleOrNull()

        val translationGloss = if (translationWord.gloss == "id.") baseWord.gloss else translationWord.gloss

        val oeWords = findWord(toLang, translationWord, translationGloss)
        if (oeWords.size > 1) {
            println("Ambiguous OE word: ${oeWords.joinToString(", ")} in $line")
            return
        }
        val oeWord = oeWords.singleOrNull()

        val oeEtymology = oeWord?.let { findEtymology(it) }
        val pgmcNew = if (pgmcWord == null) { if (oeEtymology != null) " [<->]" else " [NEW]" } else ""
        val oeNew = if (oeWord == null) " [NEW]" else ""

        if (baseWord.textVariants.any { it in skipList } || translationWord.textVariants.any { it in skipList }) {
            return
        }

        if (pgmcWord != null && oeWord != null) {
            return
        }

        val source = listOf(SourceRef(sourcePub, page))

        if (pgmcWord == null && oeWord == null) {
            val pgmcNewWord = createWordWithVariants(baseWord, fromLang, source)
            val oeNewWord = createWordWithVariants(translationWord, toLang, source, translationGloss)
            val link = repo.addLink(oeNewWord, pgmcNewWord, Link.Origin, source = source)
            repo.applyRuleSequence(link, sequence)
        }
        else if (pgmcWord == null && oeEtymology != null) {
            val pgmcNewWord = repo.findOrAddWord(baseWord.textVariants[0], fromLang, null,
                source = source)
            if (pgmcNewWord.id != oeEtymology.id) {
                repo.addLink(pgmcNewWord, oeEtymology, Link.Variation)
                val link = repo.addLink(oeWord, pgmcNewWord, Link.Origin, source = source)
                repo.applyRuleSequence(link, sequence)
                println("VARIANT ${baseWord.textVariants[0]} [${baseWord.classes}] '${baseWord.gloss}'$pgmcNew > ${translationWord.textVariants[0]}$oeNew '${translationGloss}'")
                importLogged = true
            }
            else {
                println("GLOSS-MISMATCH ${baseWord.textVariants[0]} [${baseWord.classes}] '${baseWord.gloss}'$pgmcNew > ${translationWord.textVariants[0]}$oeNew '${translationGloss}'")
                return
            }
        }
        else if (pgmcWord == null) {
            val pgmcNewWord = createWordWithVariants(baseWord, fromLang, source)

            val link = repo.addLink(oeWord!!, pgmcNewWord, Link.Origin, source = source)
            repo.applyRuleSequence(link, sequence)
        }
        else {
            val oeDerivation = findDerivation(pgmcWord)
            if (oeDerivation != null) {
                val oeVariant = repo.findOrAddWord(translationWord.textVariants[0], toLang,
                    translationGloss, source = source)
                repo.addLink(oeVariant, oeDerivation, Link.Variation)
                val link = repo.addLink(oeVariant, pgmcWord, Link.Origin, source = source)
                repo.applyRuleSequence(link, sequence)
                println("VARIANT ${baseWord.textVariants[0]} [${baseWord.classes}] '${baseWord.gloss}'$pgmcNew > ${translationWord.textVariants[0]}$oeNew '${translationGloss}'")
                importLogged = true
            }
            else {
                println("SKIP ${baseWord.textVariants[0]} [${baseWord.classes}] '${baseWord.gloss}'$pgmcNew > ${translationWord.textVariants[0]}$oeNew '${translationGloss}'")
                return
            }
        }

        if (!importLogged) {
            println("IMPORT ${baseWord.textVariants[0]} [${baseWord.classes}] '${baseWord.gloss}'$pgmcNew > ${translationWord.textVariants[0]}$oeNew '${translationGloss}'")
        }

        imported++
    }
}

fun main(args: Array<String>) {
    val ieRepo = JsonGraphRepository.fromJson(Path.of("data/ie"))
    val pgmc = ieRepo.languageByShortName("PGmc")!!
    val oe = ieRepo.languageByShortName("OE")!!

    val starlingFile = File(args[0])
    val lines = starlingFile.readLines(Charset.forName("iso-8859-1")).drop(1)

    val skiplist = if (args.size > 1) Files.readAllLines(Path.of(args[1])) else emptyList()
    val maxImport = if (args.size > 2) args[2].toIntOrNull() else null

    val kroonen = ieRepo.allPublications().find { it.refId == "Kroonen 2013" }!!
    val sequence = ieRepo.ruleSequenceByName("pgmc-to-oe")!!

    val importer = StarlingImporter(ieRepo, pgmc, oe, skiplist, kroonen.id, sequence)

    for (line in lines) {
        importer.importLine(line)
        if (importer.imported == maxImport) {
            break
        }
    }

    ieRepo.save()
}
