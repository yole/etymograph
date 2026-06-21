package page.yole.etymograph.web.controllers

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.Status
import org.eclipse.jgit.transport.RemoteRefUpdate
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
import page.yole.etymograph.web.GraphService

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
            GraphViewModel(it.id, it.name, it.status(), !authEnabled || (email != null && graphService.canWrite(it.id, email)))
        }
    }

    @PostMapping("/{graph}/syncChanges")
    fun syncChanges(graph: Graph): GraphViewModel {
        val jsonGraph = graph as? JsonGraph
            ?: badRequest("Sync Changes is only supported for JSON graph repositories")
        val workTree = jsonGraph.path?.toFile()
            ?: badRequest("JSON graph repository path is not specified")

        try {
            Git.open(workTree).use { git ->
                val status = git.status().call()
                val unversionedPaths = status.unversionedPaths()
                if (unversionedPaths.isNotEmpty()) {
                    val add = git.add()
                    unversionedPaths.forEach(add::addFilepattern)
                    add.call()
                }
                git.add().addFilepattern(".").setUpdate(true).call()
                git.commit().setMessage("Sync changes").call()
                val pushResults = git.push().call()
                val failedUpdates = pushResults
                    .flatMap { it.remoteUpdates }
                    .filter { it.status !in successfulPushStatuses }
                if (failedUpdates.isNotEmpty()) {
                    val details = failedUpdates.joinToString(", ") { update ->
                        "${update.remoteName}: ${update.status}"
                    }
                    throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to push changes: $details")
                }
            }
        } catch (e: ResponseStatusException) {
            throw e
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.message ?: "Failed to sync changes", e)
        }

        return GraphViewModel(graph.id, graph.name, graph.status(), true)
    }

    data class CloneGraphParams(val repoUrl: String = "")

    @PostMapping("/graphs/clone")
    fun clone(@RequestBody params: CloneGraphParams): GraphViewModel {
        if (params.repoUrl.isBlank()) {
            badRequest("Repository URL is not specified")
        }

        val repo = try {
            graphService.cloneGraph(params.repoUrl)
        } catch (e: ResponseStatusException) {
            throw e
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.message ?: "Failed to clone graph", e)
        }

        return GraphViewModel(repo.id, repo.name, repo.status(), true)
    }

    private fun Graph.status(): String {
        val jsonGraphRepository = this as? JsonGraph ?: return ""
        val workTree = jsonGraphRepository.path?.toFile() ?: return ""
        return runCatching {
            Git.open(workTree).use { git ->
                val status = git.status().call()
                val changedFiles = buildSet {
                    addAll(status.added)
                    addAll(status.changed)
                    addAll(status.modified)
                    addAll(status.unversionedPaths())
                }
                "${changedFiles.size} changed files"
            }
        }.getOrDefault("")
    }

    private fun Status.unversionedPaths(): Set<String> {
        val untrackedFiles = untracked
        return buildSet {
            addAll(untrackedFiles)
            addAll(untrackedFolders.filter { folder ->
                untrackedFiles.none { file -> file == folder || file.startsWith("$folder/") }
            })
        }
    }

    companion object {
        private val successfulPushStatuses = setOf(
            RemoteRefUpdate.Status.OK,
            RemoteRefUpdate.Status.UP_TO_DATE,
            RemoteRefUpdate.Status.NON_EXISTING
        )
    }
}
