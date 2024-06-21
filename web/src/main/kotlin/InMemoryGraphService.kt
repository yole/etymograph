package ru.yole.etymograph.web

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ru.yole.etymograph.*
import ru.yole.etymograph.web.controllers.badRequest
import ru.yole.etymograph.web.controllers.notFound
import java.nio.file.Path

abstract class GraphService {
    abstract fun allGraphs(): List<GraphRepository>
    abstract fun resolveGraph(name: String): GraphRepository
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

fun GraphRepository.resolveCorpusText(id: Int?): CorpusText {
    if (id == null) badRequest("Corpus text ID not specified")
    return corpusTextById(id) ?: notFound("No corpus text with ID $id")
}

fun GraphRepository.resolveRule(id: Int): Rule {
    return ruleById(id) ?: notFound("No rule with ID $id")
}

fun GraphRepository.resolveRule(name: String): Rule {
    return ruleByName(name) ?: notFound("No rule with name $name")
}

fun GraphRepository.resolveEntity(id: Int): LangEntity {
    return langEntityById(id) ?: notFound("No word or rule with ID $id")
}

fun GraphRepository.resolveRuleSequence(id: Int): RuleSequence {
    return langEntityById(id) as? RuleSequence ?: notFound("No sequence with ID $id")
}