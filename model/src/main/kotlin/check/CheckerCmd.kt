package page.yole.etymograph.check

import page.yole.etymograph.ConsistencyCheckerIssue
import page.yole.etymograph.JsonGraph
import page.yole.etymograph.Language
import page.yole.etymograph.consistencyCheckers
import java.nio.file.Path

fun printIssue(issue: ConsistencyCheckerIssue) {
    println(issue.description)
}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: CheckerCmd <repo dir> [<language>]")
        return
    }
    val graph = JsonGraph.fromJson(Path.of(args[0]))

    val language = args.getOrNull(1)?.let {
        graph.languageByShortName(args[1]) ?: run {
            println("No language with ID ${args[1]}")
            return
        }
    }
    if (language == null) {
        for (l in graph.allLanguages()) {
            checkLanguage(graph, l)
        }
    }
    else {
        checkLanguage(graph, language)
    }
}

private fun checkLanguage(repo: JsonGraph, language: Language) {
    for (consistencyChecker in consistencyCheckers) {
        consistencyChecker.check(repo, language, ::printIssue)
    }
}
