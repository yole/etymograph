package ru.yole.etymograph.importers

import ru.yole.etymograph.*
import java.io.File
import java.io.Reader
import java.nio.file.Path
import javax.xml.namespace.QName
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.events.StartElement

val nonWordSpeechParts = setOf("phrase", "phonetics", "phoneme", "phonetic-group", "phonetic-rule", "grammar", "text", "suf")

fun processEldamoWords(reader: Reader, callback: (word: String, language: String, speech: String) -> Unit) {
    val xmlReader = XMLInputFactory.newFactory().createXMLEventReader(reader)
    while (xmlReader.hasNext()) {
        val tag = xmlReader.next()
        if (tag !is StartElement) continue
        if (tag.name.localPart == "word") {
            val v = tag.getAttributeByName(QName.valueOf("v")).value
            val l = tag.getAttributeByName(QName.valueOf("l")).value
            val speech = tag.getAttributeByName(QName.valueOf("speech")).value
            callback(v, l, speech)
        }
    }
}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: EldamoReader <path>")
        return
    }
    val repo = JsonGraphRepository.fromJson(Path.of("jrrt.json"))
    val q = repo.languageByShortName("Q")!!
    val vowels = q.phonemeClassByName(PhonemeClass.vowelClassName)!!
    val syllableStructures = mutableMapOf<String, String>()
    val codas = mutableMapOf<String, String>()
    val codaCounts = mutableMapOf<String, Int>()
    processEldamoWords(File(args[0]).reader()) { word, language, speech ->
        if (language == "q" && speech !in nonWordSpeechParts && "(" !in word && ' ' !in word) {
            val qWord = Word(-1, word.trimEnd('¹', '²', '³', '⁴', '-').replace('ē', 'é'), q)
            val phonemes = PhonemeIterator(qWord, null)
            val syllables = breakIntoSyllables(qWord)
            for (syllable in syllables) {
                val structure = analyzeSyllableStructure(vowels, phonemes, syllable)
                if (structure !in syllableStructures) {
                    syllableStructures[structure] = word
                }
                val coda = phonemes[syllable.endIndex-1]
                if (coda !in codas) {
                    codas[coda] = word
                }
                if (coda == "f") {
                    println("f: $word")
                }
                val count = codaCounts.getOrDefault(coda, 0)
                codaCounts[coda] = count+1
            }
        }
    }
    for ((syllableStructure, example) in syllableStructures) {
        println("$syllableStructure: $example")
    }
    for ((coda, example) in codas) {
        println("Coda: $coda in $example, total ${codaCounts[coda]}")
    }
}
