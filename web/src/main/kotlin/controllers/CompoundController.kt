package page.yole.etymograph.web.controllers

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import kotlinx.serialization.Serializable
import org.springframework.web.bind.annotation.*
import page.yole.etymograph.Compound
import page.yole.etymograph.Graph
import page.yole.etymograph.KnownClasses
import page.yole.etymograph.OpenCompound
import page.yole.etymograph.web.parseSourceRefs
import page.yole.etymograph.web.resolveWord
import page.yole.etymograph.web.SourceRefViewModel
import page.yole.etymograph.web.toEditableText
import page.yole.etymograph.web.toViewModel

@RestController
@RequestMapping("/{graph}/compound")
class CompoundController {
    data class OpenCompoundParams(
        val componentIds: List<Int>,
        @param:JsonProperty(value = "headIndex", required = true)
        @param:JsonSetter(nulls = Nulls.FAIL)
        val headIndex: Int,
        val gloss: String? = null,
        val fullGloss: String? = null,
        val source: String? = null,
        val notes: String? = null
    )

    @Serializable
    data class OpenCompoundViewModel(
        val id: Int,
        val components: List<WordRefViewModel>,
        val headIndex: Int,
        val gloss: String?,
        val fullGloss: String?,
        val source: List<SourceRefViewModel>,
        val sourceEditableText: String,
        val notes: String?,
        val attestations: List<WordController.AttestationViewModel>
    )

    @GetMapping("/open")
    fun openCompounds(graph: Graph): List<OpenCompoundViewModel> =
        graph.allOpenCompounds().map { it.toViewModel(graph) }

    @GetMapping("/open/{id}")
    fun openCompound(graph: Graph, @PathVariable id: Int): OpenCompoundViewModel =
        graph.resolveOpenCompound(id).toViewModel(graph)

    @PostMapping("/open")
    fun createOpenCompound(graph: Graph, @RequestBody params: OpenCompoundParams): OpenCompoundViewModel {
        validateOpenCompound(params)
        val components = params.componentIds.map { graph.resolveWord(it) }
        val source = parseSourceRefs(graph, params.source)
        return graph.createOpenCompound(components, params.headIndex, params.gloss.nullize(),
            params.fullGloss.nullize(), source, params.notes.nullize()).toViewModel(graph)
    }

    @PostMapping("/open/{id}")
    fun editOpenCompound(graph: Graph, @PathVariable id: Int,
                         @RequestBody params: OpenCompoundParams): OpenCompoundViewModel {
        val compound = graph.resolveOpenCompound(id)
        validateOpenCompound(params)
        val components = params.componentIds.map { graph.resolveWord(it) }
        val source = parseSourceRefs(graph, params.source)
        compound.updateComponents(components, params.headIndex)
        compound.gloss = params.gloss.nullize()
        compound.fullGloss = params.fullGloss.nullize()
        compound.source = source
        compound.notes = params.notes.nullize()
        return compound.toViewModel(graph)
    }

    @PostMapping("/open/{id}/delete")
    fun deleteOpenCompound(graph: Graph, @PathVariable id: Int) {
        graph.deleteOpenCompound(graph.resolveOpenCompound(id))
    }

    private fun validateOpenCompound(params: OpenCompoundParams) {
        if (params.headIndex !in params.componentIds.indices) {
            badRequest("An open compound must have a head component")
        }
        if (params.componentIds.distinct().size != params.componentIds.size) {
            badRequest("An open compound cannot contain the same word more than once")
        }
    }

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

    data class ReplaceCompoundMemberParams(val componentId: Int = -1)

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

    @PostMapping("/{id}/component/{memberIndex}")
    fun replaceCompoundMember(graph: Graph, @PathVariable id: Int, @PathVariable memberIndex: Int,
                              @RequestBody params: ReplaceCompoundMemberParams) {
        val compound = graph.resolveCompound(id)
        graph.replaceCompoundMember(compound, memberIndex, graph.resolveWord(params.componentId))
    }

    @PostMapping("/{id}/component/{memberIndex}/delete")
    fun removeCompoundMember(graph: Graph, @PathVariable id: Int, @PathVariable memberIndex: Int) {
        graph.removeCompoundMember(graph.resolveCompound(id), memberIndex)
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

fun Graph.resolveOpenCompound(id: Int): OpenCompound {
    return langEntityById(id) as? OpenCompound ?: notFound("No open compound with ID $id")
}

fun OpenCompound.toViewModel(graph: Graph): CompoundController.OpenCompoundViewModel =
    CompoundController.OpenCompoundViewModel(id, components.map { it.toRefViewModel() }, headIndex, gloss, fullGloss,
        source.toViewModel(graph), source.toEditableText(graph), notes,
        graph.allCorpusTexts().filter { text -> text.words.any { it.openCompound?.id == id } }.map { text ->
            val lineNumbers = text.mapToLines().filter { line -> line.corpusWords.any { it.openCompound?.id == id } }
                .mapNotNull { it.lineNumber?.takeIf { number -> number.isNotBlank() } }.distinct()
            WordController.AttestationViewModel(text.id, text.title ?: text.text, null, null, lineNumbers)
        })
