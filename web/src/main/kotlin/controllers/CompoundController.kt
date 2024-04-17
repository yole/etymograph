package ru.yole.etymograph.web.controllers

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import ru.yole.etymograph.Compound
import ru.yole.etymograph.web.GraphService
import ru.yole.etymograph.web.parseSourceRefs

@RestController
class CompoundController(val graphService: GraphService) {
    data class CompoundParams(
        val compoundId: Int,
        val firstComponentId: Int = -1,
        val source: String,
        val notes: String? = null
    )

    data class UpdateCompoundParams(val componentId: Int = -1)

    @PostMapping("/{graph}/compound")
    fun createCompound(@PathVariable graph: String, @RequestBody params: CompoundParams) {
        val compoundWord = graphService.resolveWord(graph, params.compoundId)
        val componentWord = graphService.resolveWord(graph, params.firstComponentId)
        val repo = graphService.resolveGraph(graph)
        val source = parseSourceRefs(repo, params.source)
        repo.createCompound(compoundWord, componentWord, source, params.notes.nullize())
    }

    @PostMapping("/{graph}/compound/{id}/add")
    fun addToCompound(@PathVariable graph: String, @PathVariable id: Int, @RequestBody params: UpdateCompoundParams) {
        val compound = resolveCompound(graph, id)
        val componentWord = graphService.resolveWord(graph, params.componentId)
        compound.components.add(componentWord)
    }

    private fun resolveCompound(graph: String, id: Int): Compound {
        return graphService.resolveGraph(graph).langEntityById(id) as? Compound
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No compound with ID $id")
    }

    @PostMapping("/{graph}/compound/{id}")
    fun editCompound(@PathVariable graph: String, @PathVariable id: Int, @RequestBody params: CompoundParams) {
        val compound = resolveCompound(graph, id)
        compound.source = parseSourceRefs(graphService.resolveGraph(graph), params.source)
        compound.notes = params.notes
    }

    @PostMapping("/{graph}/compound/{id}/delete")
    fun deleteCompound(@PathVariable graph: String, @PathVariable id: Int) {
        val repo = graphService.resolveGraph(graph)
        val compound = resolveCompound(graph, id)
        repo.deleteCompound(compound)
    }
}
