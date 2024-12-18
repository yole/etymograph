package ru.yole.etymograph.verifier

import ru.yole.etymograph.*
import java.nio.file.Path
import kotlin.io.path.outputStream
import kotlin.io.path.readLines

fun processParadigm(repo: GraphRepository, word: Word, paradigm: Paradigm, callback: (WordAlternative, Rule) -> Unit) {
    val generatedParadigm = paradigm.generate(word, repo)
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

fun processParadigms(repo: JsonGraphRepository, callback: (Word, String, Rule) -> Unit) {
    for (language in repo.allLanguages()) {
        val paradigms = repo.paradigmsForLanguage(language)
        for (word in repo.dictionaryWords(language)) {
            val applicableParadigms = paradigms.filter { word.pos in it.pos }
            for (applicableParadigm in applicableParadigms) {
                processParadigm(repo, word, applicableParadigm) { alt, rule ->
                    callback(word, alt.expectedWord.text, rule)
                }
            }
        }
    }
}

fun export(repo: JsonGraphRepository, outputPath: String) {
    Path.of(outputPath).outputStream().bufferedWriter().use { outWriter ->
        processParadigms(repo) { word, expected, rule ->
            outWriter.write("${word.id},${rule.name},${word.classes.joinToString(" ")},${expected}\n")
        }
    }
}

data class Key(val wordId: Int, val ruleName: String)

fun verify(repo: JsonGraphRepository, goldPath: String) {
    val gold = mutableMapOf<Key, Pair<String, Set<String>>>()
    for (line in Path.of(goldPath).readLines()) {
        val (id, ruleName, classes, result) = line.split(',')
        val classSet = classes.split(' ').toSet()
        gold[Key(id.toInt(), ruleName)] = result to classSet
    }

    processParadigms(repo) { word, expected, rule ->
        val goldData = gold[Key(word.id, rule.name)]
        if (goldData != null && goldData.first != expected && goldData.second == word.classes.toSet()) {
            println("Changed result for rule ${rule.name} on word ${word.text}: previous ${goldData.first}, now $expected")
        }
    }
}

fun main(args: Array<String>) {
    if (args.size != 3 || (args[0] != "export" && args[0] != "verify")) {
        println("Usage: verifier <export|verify> <repo dir> <result file>")
        return
    }
    val repo = JsonGraphRepository.fromJson(Path.of(args[1]))
    when(args[0]) {
        "export" -> export(repo, args[2])
        "verify" -> verify(repo, args[2])
    }
}
