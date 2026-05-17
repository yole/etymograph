package ru.yole.etymograph.web

import kotlinx.serialization.json.Json
import org.eclipse.jgit.api.Git
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.yole.etymograph.web.controllers.GraphController
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class GraphControllerTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun cloneAddsGraphToRegistry() {
        val registryDir = Files.createTempDirectory("graph-controller-test")
        val registryFile = registryDir.resolve("graphs.json")
        registryFile.writeText("""{"graphs":[]}""")
        val originRepo = createOriginRepo(findProjectRoot().resolve("data/etymograph-jrrt"))

        val graphService = InMemoryGraphService(registryFile.toString())
        val controller = GraphController(graphService, false)

        val graph = controller.clone(GraphController.CloneGraphParams(originRepo.toUri().toString()))

        assertEquals("jrrt", graph.id)
        assertEquals("Tolkien's Languages", graph.name)
        assertEquals(listOf("jrrt"), graphService.allGraphs().map { it.id })

        val persistedRegistry = json.decodeFromString<InMemoryGraphService.GraphRegistryData>(registryFile.readText())
        assertEquals(1, persistedRegistry.graphs.size)
        assertEquals("jrrt", persistedRegistry.graphs.single().id)
        assertEquals("Tolkien's Languages", persistedRegistry.graphs.single().name)
        assertTrue(registryDir.resolve(persistedRegistry.graphs.single().path).resolve("graph.json").exists())
    }

    private fun createOriginRepo(sourceGraphDir: Path): Path {
        val originDir = Files.createTempDirectory("graph-origin-repo")
        copyDirectory(sourceGraphDir, originDir)
        Git.init().setDirectory(originDir.toFile()).call().use { git ->
            git.add().addFilepattern(".").call()
            git.commit().setMessage("Initial graph").call()
        }
        return originDir
    }

    private fun copyDirectory(source: Path, target: Path) {
        Files.walk(source).use { paths ->
            paths.forEach { path ->
                val relativePath = source.relativize(path)
                val targetPath = target.resolve(relativePath.toString())
                if (Files.isDirectory(path)) {
                    Files.createDirectories(targetPath)
                } else {
                    Files.createDirectories(targetPath.parent)
                    Files.copy(path, targetPath)
                }
            }
        }
    }

    private fun findProjectRoot(): Path {
        val configuredRoot = System.getProperty("etymograph.projectRoot")
        if (configuredRoot != null && Path.of(configuredRoot).resolve("data/etymograph-jrrt/graph.json").exists()) {
            return Path.of(configuredRoot)
        }
        throw IllegalStateException("Cannot find project root")
    }
}
