package page.yole.etymograph.web.controllers

import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import page.yole.etymograph.Graph
import page.yole.etymograph.JsonGraph
import page.yole.etymograph.web.GraphGitService
import page.yole.etymograph.web.GraphService
import java.io.File
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists

@RestController
class GraphController(
    val graphService: GraphService,
    @param:org.springframework.beans.factory.annotation.Value("\${etymograph.auth.enabled:false}")
    private val authEnabled: Boolean
) {
    data class GraphViewModel(val id: String, val name: String, val status: String, val canWrite: Boolean)

    @GetMapping("/graphs")
    fun list(@AuthenticationPrincipal principal: OAuth2User?): List<GraphViewModel> {
        val email = principal?.getAttribute<String>("email")
        return graphService.allGraphs().map {
            GraphViewModel(it.id, it.name, GraphGitService.status(it.workTree()),
                !authEnabled || (email != null && graphService.canWrite(it.id, email)))
        }
    }

    fun Graph.workTree(): File {
        val jsonGraph = this as? JsonGraph
            ?: badRequest("Sync Changes is only supported for JSON graph repositories")
        return jsonGraph.path?.toFile()
            ?: badRequest("JSON graph repository path is not specified")
    }

    @PostMapping("/{graph}/syncChanges")
    fun syncChanges(graph: Graph): GraphViewModel {
        val workTree = graph.workTree()
        try {
            GraphGitService.sync(workTree)
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.message ?: "Failed to sync changes", e)
        }

        return GraphViewModel(graph.id, graph.name, GraphGitService.status(workTree), true)
    }

    @PostMapping("/{graph}/revertChanges")
    fun revertChanges(graph: Graph): GraphViewModel {
        val reverted = try {
            GraphGitService.revert(graph.workTree())
            graphService.reload(graph.id)
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.message ?: "Failed to revert changes", e)
        }

        return GraphViewModel(reverted.id, reverted.name, GraphGitService.status(reverted.workTree()), true)
    }

    data class CloneGraphParams(val repoUrl: String = "")

    @OptIn(ExperimentalPathApi::class)
    @PostMapping("/graphs/clone")
    fun clone(@RequestBody params: CloneGraphParams): GraphViewModel {
        if (params.repoUrl.isBlank()) {
            badRequest("Repository URL is not specified")
        }

        val clonePath = graphService.nextClonePath(params.repoUrl)
        val repo = try {
            GraphGitService.clone(params.repoUrl, clonePath.toFile())
            graphService.loadGraph(clonePath)
        }
        catch(e: Exception) {
            if (clonePath.exists()) {
                clonePath.deleteRecursively()
            }
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.message ?: "Failed to clone graph", e)
        }

        return GraphViewModel(repo.id, repo.name, GraphGitService.status(repo.workTree()), true)
    }
}
