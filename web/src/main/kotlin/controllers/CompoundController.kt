package page.yole.etymograph.web.controllers

import org.springframework.web.bind.annotation.*
import page.yole.etymograph.Compound
import page.yole.etymograph.Graph
import page.yole.etymograph.KnownClasses
import page.yole.etymograph.web.parseSourceRefs
import page.yole.etymograph.web.resolveWord

@RestController
@RequestMapping("/{graph}/compound")
class CompoundController {
    data class CompoundParams(
        val compoundId: Int,
        val firstComponentId: Int = -1,
        val head: Int? = null,
        val source: String? = null,
        val notes: String? = null
    )

    data class UpdateCompoundParams(
        val componentId: Int = -1,
        val markHead: Boolean = false,
    )

    @PostMapping("")
    fun createCompound(graph: Graph, @RequestBody params: CompoundParams) {
        val compoundWord = graph.resolveWord(params.compoundId)
        val componentWord = graph.resolveWord(params.firstComponentId)
        val source = parseSourceRefs(graph, params.source)
        graph.createCompound(compoundWord, listOf(componentWord), null, source, params.notes.nullize())
    }

    @PostMapping("/{id}/add")
    fun addToCompound(graph: Graph, @PathVariable id: Int, @RequestBody params: UpdateCompoundParams) {
        val compound = graph.resolveCompound(id)
        val componentWord = graph.resolveWord(params.componentId)
        graph.addToCompound(compound, componentWord)
        if (params.markHead && KnownClasses.clitic !in componentWord.classes) {
            compound.headIndex = compound.components.size - 1
        }
    }

    @PostMapping("/{id}")
    fun editCompound(graph: Graph, @PathVariable id: Int, @RequestBody params: CompoundParams) {
        val compound = graph.resolveCompound(id)
        compound.source = parseSourceRefs(graph, params.source)
        compound.notes = params.notes
        if (params.head != null) {
            compound.headIndex = compound.components.indexOfFirst { it.id == params.head }.takeIf { it >= 0 }
        }
    }

    @PostMapping("/{id}/delete")
    fun deleteCompound(graph: Graph, @PathVariable id: Int) {
        val compound = graph.resolveCompound(id)
        graph.deleteCompound(compound)
    }
}

fun Graph.resolveCompound(id: Int): Compound {
    return langEntityById(id) as? Compound ?: notFound("No compound with ID $id")
}
