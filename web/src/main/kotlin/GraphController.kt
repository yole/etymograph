package ru.yole.etymograph.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class GraphController(val graphService: GraphService) {
    data class GraphViewModel(val id: String)

    @GetMapping("/graphs")
    fun list(): List<GraphViewModel> {
        return graphService.allGraphs().map { GraphViewModel(it.id) }
    }
}
