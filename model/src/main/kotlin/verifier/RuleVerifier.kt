package page.yole.etymograph.verifier

import page.yole.etymograph.*
import java.nio.file.Path
import kotlin.io.path.outputStream
import kotlin.io.path.readLines

fun processParadigm(word: Word, paradigm: Paradigm, callback: (WordAlternative, Rule) -> Unit) {
    val generatedParadigm = paradigm.generate(word)
    for (row in generatedParadigm) {
        for (col in row.filterNotNull()) {
            for (alt in col) {
                if ("?" in alt.expectedWord.text) continue
                alt.rule?.let { rule ->
                    callback(alt, rule)
                }
            }
        }
    }
}

fun processParadigms(repo: JsonGraph, callback: (Word, String, Rule) -> Unit) {
    for (language in repo.allLanguages()) {
        val paradigms = repo.paradigmsForLanguage(language)
        for (word in repo.dictionaryWords(language)) {
            val applicableParadigms = paradigms.filter { word.pos in it.pos }
            for (applicableParadigm in applicableParadigms) {
                processParadigm(word, applicableParadigm) { alt, rule ->
                    callback(word, alt.expectedWord.text, rule)
                }
            }
        }
    }
}

fun export(repo: JsonGraph, outputPath: String) {
    Path.of(outputPath).outputStream().bufferedWriter().use { outWriter ->
        processParadigms(repo) { word, expected, rule ->
            outWriter.write("${word.id},${rule.name},${word.classes.joinToString(" ")},${expected}\n")
        }
    }
}

data class Key(val wordId: Int, val ruleName: String)

fun verify(repo: JsonGraph, goldPath: String) {
    val gold = mutableMapOf<Key, Pair<String, Set<String>>>()
    for (line in Path.of(goldPath).readLines()) {
        val (id, ruleName, classes, result) = line.split(',')
        val classSet = classes.split(' ').toSet()
        gold[Key(id.toInt(), ruleName)] = result to classSet
    }

    var verified = 0
    processParadigms(repo) { word, expected, rule ->
        val goldData = gold[Key(word.id, rule.name)]
        if (goldData != null && goldData.first != expected && goldData.second == word.classes.toSet()) {
            println("Changed result for rule ${rule.name} on word ${word.text}: previous ${goldData.first}, now $expected")
        }
        else {
            verified++
        }
    }
    println("Successfully verified $verified forms")
}

fun main(args: Array<String>) {
    if (args.size != 3 || (args[0] != "export" && args[0] != "verify")) {
        println("Usage: verifier <export|verify> <repo dir> <result file>")
        return
    }
    val graph = JsonGraph.fromJson(Path.of(args[1]))
    when(args[0]) {
        "export" -> export(graph, args[2])
        "verify" -> verify(graph, args[2])
    }
}
