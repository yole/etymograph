package ru.yole.etymograph.web

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.yole.etymograph.*
import java.nio.file.Path

abstract class GraphService {
    abstract val graph: GraphRepository

    fun resolveLanguage(lang: String): Language {
        return graph.languageByShortName(lang)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No language with short name $lang")
    }

    fun resolveWord(id: Int): Word {
        return graph.wordById(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No word with ID $id")
    }

    fun resolveRule(name: String): Rule {
        return graph.ruleByName(name.trim())
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No rule named '$name'")
    }
}

@Service
class InMemoryGraphService: GraphService() {
    override val graph = JsonGraphRepository.fromJson(Path.of("jrrt.json"))
}
