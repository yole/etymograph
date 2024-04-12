package ru.yole.etymograph.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import ru.yole.etymograph.Compound

@RestController
class CompoundController(val graphService: GraphService) {
    data class CompoundParams(
        val compoundId: Int,
        val firstComponentId: Int = -1,
        val source: String,
        val notes: String? = null
    )

    data class UpdateCompoundParams(val componentId: Int = -1)

    @PostMapping("/compound")
    fun createCompound(@RequestBody params: CompoundParams) {
        val compoundWord = graphService.resolveWord(params.compoundId)
        val componentWord = graphService.resolveWord(params.firstComponentId)
        val graph = graphService.graph
        val source = parseSourceRefs(graph, params.source)
        graph.createCompound(compoundWord, componentWord, source, params.notes.nullize())
    }

    @PostMapping("/compound/{id}/add")
    fun addToCompound(@PathVariable id: Int, @RequestBody params: UpdateCompoundParams) {
        val compound = resolveCompound(id)
        val componentWord = graphService.resolveWord(params.componentId)
        compound.components.add(componentWord)
    }

    private fun resolveCompound(id: Int): Compound {
        return graphService.graph.langEntityById(id) as? Compound
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No compound with ID $id")
    }

    @PostMapping("/compound/{id}")
    fun editCompound(@PathVariable id: Int, @RequestBody params: CompoundParams) {
        val compound = resolveCompound(id)
        compound.source = parseSourceRefs(graphService.graph, params.source)
        compound.notes = params.notes
    }

    @PostMapping("/compound/{id}/delete")
    fun deleteCompound(@PathVariable id: Int) {
        val graph = graphService.graph
        val compound = resolveCompound(id)
        graph.deleteCompound(compound)
    }
}
