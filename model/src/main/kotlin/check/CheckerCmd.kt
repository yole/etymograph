package ru.yole.etymograph.check

import ru.yole.etymograph.ConsistencyCheckerIssue
import ru.yole.etymograph.JsonGraphRepository
import ru.yole.etymograph.Language
import ru.yole.etymograph.consistencyCheckers
import java.nio.file.Path

fun printIssue(issue: ConsistencyCheckerIssue) {
    println(issue.description)
}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: CheckerCmd <repo dir> [<language>]")
        return
    }
    val repo = JsonGraphRepository.fromJson(Path.of(args[0]))

    val language = args.getOrNull(1)?.let {
        repo.languageByShortName(args[1]) ?: run {
            println("No language with ID ${args[1]}")
            return
        }
    }
    if (language == null) {
        for (l in repo.allLanguages()) {
            checkLanguage(repo, l)
        }
    }
    else {
        checkLanguage(repo, language)
    }
}

private fun checkLanguage(repo: JsonGraphRepository, language: Language) {
    for (consistencyChecker in consistencyCheckers) {
        consistencyChecker.check(repo, language, ::printIssue)
    }
}
