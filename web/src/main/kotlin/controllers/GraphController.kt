package ru.yole.etymograph.web.controllers

import org.eclipse.jgit.api.Git
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
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
}
