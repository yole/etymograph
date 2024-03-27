package ru.yole.etymograph.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import ru.yole.etymograph.Compound
import ru.yole.etymograph.SourceRefData

@RestController
class CompoundController(val graphService: GraphService) {
    data class CompoundParams(val compoundId: Int, val firstComponentId: Int = -1, val source: String)
    data class UpdateCompoundParams(val componentId: Int = -1)

    @PostMapping("/compound")
    fun createCompound(@RequestBody params: CompoundParams) {
        val compoundWord = graphService.resolveWord(params.compoundId)
        val componentWord = graphService.resolveWord(params.firstComponentId)
        val graph = graphService.graph
        val source = parseSourceRefs(graph, params.source)
        graph.createCompound(compoundWord, componentWord, source, null)
    }

    @PostMapping("/compound/{id}/add")
    fun addToCompound(@PathVariable id: Int, @RequestBody params: UpdateCompoundParams) {
        val compound = graphService.graph.langEntityById(id) as? Compound
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No compound with ID $id")
        val componentWord = graphService.resolveWord(params.componentId)
        compound.components.add(componentWord)
    }

    @PostMapping("/compound/{id}/delete")
    fun deleteCompound(@PathVariable id: Int) {
        val graph = graphService.graph
        val compound = graph.langEntityById(id) as? Compound
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No compound with ID $id")
        graph.deleteCompound(compound)
    }
}
