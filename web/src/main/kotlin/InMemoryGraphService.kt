package ru.yole.etymograph.web

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ru.yole.etymograph.*
import ru.yole.etymograph.web.controllers.badRequest
import ru.yole.etymograph.web.controllers.notFound
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

abstract class GraphService {
    abstract fun allGraphs(): List<GraphRepository>
    abstract fun resolveGraph(name: String): GraphRepository
}

@Service
class InMemoryGraphService(
    @param:Value("\${etymograph.graphRegistryPath:data/graphs.json}")
    private val graphRegistryPath: String
) : GraphService() {
    @Serializable
    data class GraphRegistryData(val graphs: List<GraphRegistryEntry>)

    @Serializable
    data class GraphRegistryEntry(
        val id: String,
        val path: String,
        val writers: List<String> = emptyList()
    )

    private data class RegisteredGraph(
        val repository: GraphRepository,
        val path: String,
        val writers: MutableSet<String>
    )

    private val registryFile = Path.of(graphRegistryPath)
    private val registryDir = registryFile.toAbsolutePath().parent ?: Path.of(".").toAbsolutePath()
    private val json = Json { prettyPrint = true }

    private val registeredGraphs: Map<String, RegisteredGraph> by lazy {
        val registry = json.decodeFromString<GraphRegistryData>(registryFile.readText())
        buildMap {
            for (entry in registry.graphs) {
                val repositoryPath = resolveGraphPath(entry.path)
                val graph = JsonGraphRepository.fromJson(repositoryPath)
                if (graph.id != entry.id) {
                    throw IllegalStateException(
                        "Graph ID mismatch for ${entry.path}: registry has ${entry.id}, graph.json has ${graph.id}"
                    )
                }
                put(entry.id, RegisteredGraph(graph, entry.path, entry.writers.toMutableSet()))
            }
        }
    }

    private fun resolveGraphPath(path: String): Path {
        val resolvedPath = Path.of(path)
        return if (resolvedPath.isAbsolute) resolvedPath else registryDir.resolve(resolvedPath).normalize()
    }

    override fun allGraphs(): List<GraphRepository> = registeredGraphs.values.map { it.repository }

    override fun resolveGraph(name: String): GraphRepository =
        registeredGraphs[name]?.repository ?: notFound("No graph with ID $name")

    fun canWrite(graphId: String, email: String?): Boolean {
        if (email == null) return false
        return registeredGraphs[graphId]?.writers?.contains(email) == true
    }

    fun writers(graphId: String): Set<String> {
        return registeredGraphs[graphId]?.writers?.toSet() ?: notFound("No graph with ID $graphId")
    }

    fun updateWriters(graphId: String, writers: Set<String>) {
        val graph = registeredGraphs[graphId] ?: notFound("No graph with ID $graphId")
        graph.writers.clear()
        graph.writers.addAll(writers.sorted())
        saveRegistry()
    }

    fun saveRegistry() {
        val registry = GraphRegistryData(
            registeredGraphs.values.map { registeredGraph ->
                GraphRegistryEntry(
                    registeredGraph.repository.id,
                    registeredGraph.path,
                    registeredGraph.writers.sorted()
                )
            }
        )
        registryFile.createParentDirectories().writeText(json.encodeToString(registry))
    }
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
