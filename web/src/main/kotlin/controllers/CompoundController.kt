package ru.yole.etymograph.web.controllers

import org.springframework.web.bind.annotation.*
import ru.yole.etymograph.Compound
import ru.yole.etymograph.GraphRepository
import ru.yole.etymograph.web.parseSourceRefs
import ru.yole.etymograph.web.resolveWord

@RestController
@RequestMapping("/{graph}/compound")
class CompoundController {
    data class CompoundParams(
        val compoundId: Int,
        val firstComponentId: Int = -1,
        val source: String,
        val notes: String? = null
    )

    data class UpdateCompoundParams(
        val componentId: Int = -1,
        val markHead: Boolean = false,
    )

    @PostMapping("")
    fun createCompound(repo: GraphRepository, @RequestBody params: CompoundParams) {
        val compoundWord = repo.resolveWord(params.compoundId)
        val componentWord = repo.resolveWord(params.firstComponentId)
        val source = parseSourceRefs(repo, params.source)
        repo.createCompound(compoundWord, componentWord, source, params.notes.nullize())
    }

    @PostMapping("/{id}/add")
    fun addToCompound(repo: GraphRepository, @PathVariable id: Int, @RequestBody params: UpdateCompoundParams) {
        val compound = repo.resolveCompound(id)
        val componentWord = repo.resolveWord(params.componentId)
        compound.components.add(componentWord)
        if (params.markHead) {
            compound.headIndex = compound.components.size - 1
        }
    }

    private fun GraphRepository.resolveCompound(id: Int): Compound {
        return langEntityById(id) as? Compound ?: notFound("No compound with ID $id")
    }

    @PostMapping("/{id}")
    fun editCompound(repo: GraphRepository, @PathVariable id: Int, @RequestBody params: CompoundParams) {
        val compound = repo.resolveCompound(id)
        compound.source = parseSourceRefs(repo, params.source)
        compound.notes = params.notes
    }

    @PostMapping("/{id}/delete")
    fun deleteCompound(repo: GraphRepository, @PathVariable id: Int) {
        val compound = repo.resolveCompound(id)
        repo.deleteCompound(compound)
    }
}
