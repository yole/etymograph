package ru.yole.etymograph.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.yole.etymograph.Publication

data class PublicationViewModel(
    val id: Int,
    val name: String,
    val refId: String
)

data class AddPublicationParameters(
    val name: String?,
    val refId: String?
)

@RestController
class PublicationController(val graphService: GraphService) {
    @GetMapping("/{graph}/publications")
    fun publications(@PathVariable graph: String): List<PublicationViewModel> {
        return graphService.resolveGraph(graph).allPublications().sortedBy { it.refId }.map {
            it.toViewModel()
        }
    }

    private fun Publication.toViewModel() = PublicationViewModel(id, name, refId)

    @GetMapping("/{graph}/publication/{id}")
    fun publication(@PathVariable graph: String, @PathVariable id: Int): PublicationViewModel {
        val publication = graphService.resolveGraph(graph).publicationById(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No publication with ID $id")
        return publication.toViewModel()
    }

    @PostMapping("/{graph}/publications", consumes = ["application/json"])
    @ResponseBody
    fun addPublication(@PathVariable graph: String, @RequestBody params: AddPublicationParameters): PublicationViewModel {
        val name = params.name.nullize() ?: badRequest("Name is required")
        val refId = params.refId.nullize() ?: badRequest("refID is required")
        val publication = graphService.resolveGraph(graph).addPublication(name, refId)
        return publication.toViewModel()
    }

    @PostMapping("/{graph}/publication/{id}", consumes = ["application/json"])
    @ResponseBody
    fun updatePublication(@PathVariable graph: String, @PathVariable id: Int, @RequestBody params: AddPublicationParameters): PublicationViewModel {
        val publication = graphService.resolveGraph(graph).publicationById(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No publication with ID $id")
        publication.name = params.name.nullize() ?: badRequest("Name is required")
        publication.refId = params.refId.nullize() ?: badRequest("refID is required")
        return publication.toViewModel()
    }
}
