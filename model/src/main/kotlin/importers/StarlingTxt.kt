package ru.yole.etymograph.importers

import java.io.File
import java.nio.charset.Charset

val wordPattern = Regex("(.+) \\[(.+)] `(.+)'")

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
        .replace('\u00B0', 'ā')
        .replace('\u00B9', 'ē')
        .replace('\u00D2', 'ō')
        .replace('\u00C5', 'ī')
        .replace('\u00F1', 'ū')
        .replace('\u00C4', '\u0304') // combining macron
        .replace('\u00DF', '\u0306') // combining breve
        .replace("{U+01E3}", "ǣ") // I think this is an artifact of how the particular file I got was generated, not a Starling thing
}

fun main(args: Array<String>) {
    val starlingFile = File(args[0])
    val lines = starlingFile.readLines(Charset.forName("iso-8859-1")).drop(1)
    for (line in lines) {
        val (base, translation, page) = line.trim().split('#')
        if (translation.isEmpty()) {
            continue
        }

        val m = wordPattern.matchEntire(base)
        if (m == null) {
            println("Pattern not matched for base word: $line")
            continue
        }

        val mt = wordPattern.matchEntire(translation.removePrefix("OE").trim())
        if (mt == null) {
            println("Pattern not matched for translation: $line")
            continue
        }

        val baseWord = m.groupValues[1].removeMarkup().decodeStarling()
        if (baseWord.startsWith("*aitra-")) {
            println("here")
        }
        val translationWord = mt.groupValues[1].removeMarkup().decodeStarling()
        println("$baseWord > $translationWord")
    }
}
