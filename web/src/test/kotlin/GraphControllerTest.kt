package page.yole.etymograph.web

import kotlinx.serialization.json.Json
import org.eclipse.jgit.api.Git
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import page.yole.etymograph.Graph
import page.yole.etymograph.JsonGraph
import page.yole.etymograph.web.controllers.GraphController
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
        val originRepo = createMinimalGraphRepo()

        val graphService = InMemoryGraphService(registryFile.toString())
        val controller = GraphController(graphService, false)

        val graph = controller.clone(GraphController.CloneGraphParams(originRepo.toUri().toString()))

        assertEquals("test", graph.id)
        assertEquals("Test Graph", graph.name)
        assertEquals(listOf("test"), graphService.allGraphs().map { it.id })

        val persistedRegistry = json.decodeFromString<InMemoryGraphService.GraphRegistryData>(registryFile.readText())
        assertEquals(1, persistedRegistry.graphs.size)
        assertEquals("test", persistedRegistry.graphs.single().id)
        assertEquals("Test Graph", persistedRegistry.graphs.single().name)
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
        val graph = JsonGraph.fromJson(workTree)
        val controller = GraphController(SingleGraphService(graph), false)

        workTree.resolve("foo").createDirectories()
        val unversionedFile = "foo/language.json"
        workTree.resolve(unversionedFile).writeText("Unversioned notes")

        assertEquals("1 changed files", controller.list(null).single().status)

        controller.syncChanges(graph)

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

    @Test
    fun revertChangesReloadsGraphFromDisk() {
        val registryDir = Files.createTempDirectory("graph-controller-revert-test")
        val registryFile = registryDir.resolve("graphs.json")
        registryFile.writeText("""{"graphs":[]}""")
        val originRepo = createMinimalGraphRepo()

        val graphService = InMemoryGraphService(registryFile.toString())
        val controller = GraphController(graphService, false)
        controller.clone(GraphController.CloneGraphParams(originRepo.toUri().toString()))

        val clonedGraph = graphService.allGraphs().single() as JsonGraph
        val workTree = clonedGraph.path!!

        // Make a local modification and an unversioned file
        workTree.resolve("graph.json").writeText(
            """{"id":"test","name":"Modified Graph","languages":[],"links":[]}"""
        )
        val unversionedFile = workTree.resolve("extra.json")
        unversionedFile.writeText("garbage")

        assertEquals("2 changed files", controller.list(null).single().status)

        val reverted = controller.revertChanges(clonedGraph)

        assertEquals("Test Graph", reverted.name)
        assertEquals("0 changed files", reverted.status)
        assertTrue(!unversionedFile.exists())
        assertEquals("Test Graph", graphService.allGraphs().single().name)
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

    private class SingleGraphService(private val graph: Graph) : GraphService() {
        override fun allGraphs(): List<Graph> = listOf(graph)
        override fun resolveGraph(name: String): Graph = graph
        override fun canWrite(graphId: String, email: String): Boolean = true
        override fun getEditableGraphs(email: String): List<String> = listOf(graph.id)
        override fun cloneGraph(repoUrl: String): Graph = graph
        override fun revertChanges(graphId: String): Graph = graph
    }
}
