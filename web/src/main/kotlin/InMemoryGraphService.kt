package ru.yole.etymograph.web

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.eclipse.jgit.api.Git
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ru.yole.etymograph.*
import ru.yole.etymograph.web.controllers.badRequest
import ru.yole.etymograph.web.controllers.notFound
import java.nio.file.Path
import kotlin.io.path.*

abstract class GraphService {
    abstract fun allGraphs(): List<GraphRepository>
    abstract fun resolveGraph(name: String): GraphRepository
    abstract fun canWrite(graphId: String, email: String): Boolean
    abstract fun getEditableGraphs(email: String): List<String>
    abstract fun cloneGraph(repoUrl: String): GraphRepository
}

fun GraphService.graphByRequestPath(requestPath: String): GraphRepository? {
    val graphId = requestPath.removePrefix("/").substringBefore('/')
    return allGraphs().find { it.id == graphId }
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
        val name: String,
        val path: String,
        val writers: List<String> = emptyList()
    )

    private data class RegisteredGraph(
        val repository: GraphRepository,
        val name: String,
        val path: String,
        val writers: MutableSet<String>
    )

    @Value("\${etymograph.path:}")
    private var graphPath = ""

    private val registryFile = Path.of(graphRegistryPath)
    private val registryDir = registryFile.toAbsolutePath().parent ?: Path.of(".").toAbsolutePath()
    private val json = Json { prettyPrint = true }

    private val registeredGraphs: MutableMap<String, RegisteredGraph> by lazy {
        linkedMapOf<String, RegisteredGraph>().apply {
            if (graphPath != "") {
                for (graphName in graphPath.split(',')) {
                    val path = Path.of(graphName)
                    val graph = JsonGraphRepository.fromJson(path)
                    put(graph.id, RegisteredGraph(graph, graph.name, path.toString(), mutableSetOf()))
                }
            }

            if (!registryFile.exists()) return@apply
            val registry = json.decodeFromString<GraphRegistryData>(registryFile.readText())

            for (entry in registry.graphs) {
                val repositoryPath = resolveGraphPath(entry.path)
                val graph = JsonGraphRepository.fromJson(repositoryPath)
                if (graph.id != entry.id) {
                    throw IllegalStateException(
                        "Graph ID mismatch for ${entry.path}: registry has ${entry.id}, graph.json has ${graph.id}"
                    )
                }
                if (graph.name != entry.name) {
                    throw IllegalStateException(
                        "Graph name mismatch for ${entry.path}: registry has ${entry.name}, graph.json has ${graph.name}"
                    )
                }
                if (containsKey(entry.id)) {
                    throw IllegalStateException("Duplicate graph ID ${entry.id} in registry")
                }
                put(entry.id, RegisteredGraph(graph, entry.name, entry.path, entry.writers.toMutableSet()))
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

    @OptIn(ExperimentalPathApi::class)
    override fun cloneGraph(repoUrl: String): GraphRepository {
        val clonePath = nextClonePath(repoUrl)
        try {
            Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(clonePath.toFile())
                .call()
                .close()

            val graph = JsonGraphRepository.fromJson(clonePath)
            if (registeredGraphs.containsKey(graph.id)) {
                clonePath.deleteRecursively()
                badRequest("Graph with ID ${graph.id} already exists")
            }

            val storedPath = registryDir.relativize(clonePath).toString()
            registeredGraphs[graph.id] = RegisteredGraph(graph, graph.name, storedPath, mutableSetOf())
            saveRegistry()
            return graph
        } catch (e: Exception) {
            if (clonePath.exists()) {
                clonePath.deleteRecursively()
            }
            throw e
        }
    }

    override fun canWrite(graphId: String, email: String): Boolean {
        return registeredGraphs[graphId]?.writers?.contains(email) == true
    }

    override fun getEditableGraphs(email: String): List<String> {
        return registeredGraphs.values.filter { email in it.writers }.map { it.repository.id }
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
                    registeredGraph.name,
                    registeredGraph.path,
                    registeredGraph.writers.sorted()
                )
            }
        )
        registryFile.createParentDirectories().writeText(json.encodeToString(registry))
    }

    private fun nextClonePath(repoUrl: String): Path {
        val repoName = repoUrl.substringAfterLast('/').removeSuffix(".git").ifBlank { "graph" }
        val sanitizedRepoName = repoName.map { c ->
            when {
                c.isLetterOrDigit() || c == '-' || c == '_' || c == '.' -> c
                else -> '-'
            }
        }.joinToString("").trim('-').ifBlank { "graph" }

        var counter = 1
        var candidate = registryDir.resolve(sanitizedRepoName)
        while (candidate.exists()) {
            counter++
            candidate = registryDir.resolve("$sanitizedRepoName-$counter")
        }
        return candidate
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
