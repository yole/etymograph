package ru.yole.etymograph.web.controllers

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import ru.yole.etymograph.GraphRepository
import ru.yole.etymograph.JsonGraphRepository
import ru.yole.etymograph.web.GraphService

@RestController
class GraphController(val graphService: GraphService) {
    data class GraphViewModel(val id: String, val name: String, val status: String)

    @GetMapping("/graphs")
    fun list(): List<GraphViewModel> {
        return graphService.allGraphs().map { GraphViewModel(it.id, it.name, it.status()) }
    }

    @PostMapping("/{graph}/syncChanges")
    fun syncChanges(repo: GraphRepository): GraphViewModel {
        val jsonGraphRepository = repo as? JsonGraphRepository
            ?: badRequest("Sync Changes is only supported for JSON graph repositories")
        val workTree = jsonGraphRepository.path?.toFile()
            ?: badRequest("JSON graph repository path is not specified")

        try {
            Git.open(workTree).use { git ->
                val status = git.status().call()
                if (status.untracked.isNotEmpty()) {
                    val add = git.add()
                    status.untracked.forEach(add::addFilepattern)
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

        return GraphViewModel(repo.id, repo.name, repo.status())
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

        return GraphViewModel(repo.id, repo.name, repo.status())
    }

    private fun GraphRepository.status(): String {
        val jsonGraphRepository = this as? JsonGraphRepository ?: return ""
        val workTree = jsonGraphRepository.path?.toFile() ?: return ""
        return runCatching {
            Git.open(workTree).use { git ->
                val status = git.status().call()
                val changedFiles = buildSet {
                    addAll(status.added)
                    addAll(status.changed)
                    addAll(status.modified)
                }
                "${changedFiles.size} changed files"
            }
        }.getOrDefault("")
    }

    companion object {
        private val successfulPushStatuses = setOf(
            RemoteRefUpdate.Status.OK,
            RemoteRefUpdate.Status.UP_TO_DATE,
            RemoteRefUpdate.Status.NON_EXISTING
        )
    }
}
