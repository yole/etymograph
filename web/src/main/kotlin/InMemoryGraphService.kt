package ru.yole.etymograph.web

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.yole.etymograph.*
import ru.yole.etymograph.web.controllers.badRequest
import ru.yole.etymograph.web.controllers.notFound
import java.nio.file.Path

abstract class GraphService {
    abstract fun allGraphs(): List<GraphRepository>
    abstract fun resolveGraph(name: String): GraphRepository

    fun resolveLanguage(graph: String, lang: String): Language {
        return resolveGraph(graph).languageByShortName(lang)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No language with short name $lang")
    }

    fun resolveWord(graph: String, id: Int): Word {
        return resolveGraph(graph).wordById(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No word with ID $id")
    }

    fun resolveEntity(graph: String, id: Int): LangEntity {
        return resolveGraph(graph).langEntityById(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No word or rule with ID $id")
    }

    fun resolveRule(graph: String, name: String): Rule {
        return resolveGraph(graph).ruleByName(name.trim())
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No rule named '$name'")
    }

    fun resolveRule(graph: String, id: Int): Rule {
        return resolveGraph(graph).ruleById(id)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No rule with ID '$id'")
    }

    fun resolveRuleSequence(graph: String, id: Int) = (resolveGraph(graph).langEntityById(id) as? RuleSequence
        ?: badRequest("No sequence with ID $id"))

    fun resolveCorpusText(graph: String, id: Int): CorpusText {
        return resolveGraph(graph).corpusTextById(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No corpus text with ID $id")
    }
}

@Service
class InMemoryGraphService: GraphService() {
    @Value("\${etymograph.path}")
    private var graphPath = ""

    private val graphs: Map<String, GraphRepository> by lazy {
        val map = mutableMapOf<String, GraphRepository>()
        for (graphName in graphPath.split(',')) {
            val graph = JsonGraphRepository.fromJson(Path.of(graphName))
            map[graph.id] = graph
        }
        map
    }

    override fun allGraphs(): List<GraphRepository> = graphs.values.toList()
    override fun resolveGraph(name: String): GraphRepository =
        graphs[name] ?: notFound("No graph with ID $name")
}

fun GraphRepository.resolveLanguage(lang: String): Language {
    return languageByShortName(lang)
        ?: notFound("No language with short name $lang")
}

fun GraphRepository.resolveWord(id: Int): Word {
    return wordById(id) ?: notFound("No word with ID $id")
}

fun GraphRepository.resolveCorpusText(id: Int): CorpusText {
    return corpusTextById(id) ?: notFound("No corpus text with ID $id")
}

fun GraphRepository.resolveRule(id: Int): Rule {
    return ruleById(id) ?: notFound("No rule with ID $id")
}
