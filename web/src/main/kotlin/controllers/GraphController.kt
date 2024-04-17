package ru.yole.etymograph.web.controllers

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import ru.yole.etymograph.web.GraphService

@RestController
class GraphController(val graphService: GraphService) {
    data class GraphViewModel(val id: String, val name: String)

    @GetMapping("/graphs")
    fun list(): List<GraphViewModel> {
        return graphService.allGraphs().map { GraphViewModel(it.id, it.name) }
    }
}
