package ru.yole.etymograph.web

import kotlinx.serialization.json.Json
import org.eclipse.jgit.api.Git
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.yole.etymograph.GraphRepository
import ru.yole.etymograph.JsonGraphRepository
import ru.yole.etymograph.web.controllers.GraphController
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
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

    @Test
    fun syncChangesAddsUnversionedFiles() {
        val sourceRepo = createMinimalGraphRepo()
        val originRepo = createBareOriginRepo(sourceRepo)
        val workTree = Files.createTempDirectory("graph-worktree")
        Git.cloneRepository()
            .setURI(originRepo.toUri().toString())
            .setDirectory(workTree.toFile())
            .call()
            .use { configureIdentity(it) }
        val repo = JsonGraphRepository.fromJson(workTree)
        val controller = GraphController(SingleGraphService(repo), false)

        workTree.resolve("foo").createDirectories()
        val unversionedFile = "foo/language.json"
        workTree.resolve(unversionedFile).writeText("Unversioned notes")

        assertEquals("1 changed files", controller.list(null).single().status)

        controller.syncChanges(repo)

        Git.open(workTree.toFile()).use { git ->
            assertTrue(git.status().call().isClean)
            assertTrue(git.log().addPath(unversionedFile).call().iterator().hasNext())
        }
        val pushedClone = Files.createTempDirectory("graph-pushed-clone")
        Git.cloneRepository()
            .setURI(originRepo.toUri().toString())
            .setDirectory(pushedClone.toFile())
            .call()
            .close()
        assertTrue(pushedClone.resolve(unversionedFile).exists())

        assertEquals("0 changed files", controller.list(null).single().status)
    }

    private fun createMinimalGraphRepo(): Path {
        val repoDir = Files.createTempDirectory("graph-source-repo")
        repoDir.resolve("graph.json").writeText(
            """{"id":"test","name":"Test Graph","languages":[],"links":[]}"""
        )
        repoDir.resolve("publications.json").writeText("[]")
        Git.init().setDirectory(repoDir.toFile()).call().use { git ->
            configureIdentity(git)
            git.add().addFilepattern(".").call()
            git.commit().setMessage("Initial graph").call()
        }
        return repoDir
    }

    private fun createBareOriginRepo(sourceRepo: Path): Path {
        val originDir = Files.createTempDirectory("graph-origin-bare")
        Git.cloneRepository()
            .setURI(sourceRepo.toUri().toString())
            .setDirectory(originDir.toFile())
            .setBare(true)
            .call()
            .close()
        return originDir
    }

    private fun createOriginRepo(sourceGraphDir: Path): Path {
        val originDir = Files.createTempDirectory("graph-origin-repo")
        copyDirectory(sourceGraphDir, originDir)
        Git.init().setDirectory(originDir.toFile()).call().use { git ->
            configureIdentity(git)
            git.add().addFilepattern(".").call()
            git.commit().setMessage("Initial graph").call()
        }
        return originDir
    }

    private fun configureIdentity(git: Git) {
        git.repository.config.setString("user", null, "name", "Etymograph Test")
        git.repository.config.setString("user", null, "email", "test@example.com")
        git.repository.config.save()
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

    private class SingleGraphService(private val repo: GraphRepository) : GraphService() {
        override fun allGraphs(): List<GraphRepository> = listOf(repo)
        override fun resolveGraph(name: String): GraphRepository = repo
        override fun canWrite(graphId: String, email: String): Boolean = true
        override fun getEditableGraphs(email: String): List<String> = listOf(repo.id)
        override fun cloneGraph(repoUrl: String): GraphRepository = repo
    }
}
