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
    data class CompoundParams(val compoundId: Int, val firstComponentId: Int = -1)
    data class UpdateCompoundParams(val componentId: Int = -1)

    @PostMapping("/compound")
    fun createCompound(@RequestBody params: CompoundParams) {
        val compoundWord = graphService.resolveWord(params.compoundId)
        val componentWord = graphService.resolveWord(params.firstComponentId)
        graphService.graph.createCompound(compoundWord, componentWord, emptyList(), null)
        graphService.graph.save()
    }

    @PostMapping("/compound/{id}/add")
    fun addToCompound(@PathVariable id: Int, @RequestBody params: UpdateCompoundParams) {
        val compound = graphService.graph.langEntityById(id) as? Compound
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No compound with ID $id")
        val componentWord = graphService.resolveWord(params.componentId)
        compound.components.add(componentWord)
        graphService.graph.save()
    }
}
