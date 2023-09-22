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
    @GetMapping("/publications")
    fun publications(): List<PublicationViewModel> {
        return graphService.graph.allPublications().sortedBy { it.refId }.map {
            it.toViewModel()
        }
    }

    private fun Publication.toViewModel() = PublicationViewModel(id, name, refId)

    @GetMapping("/publication/{id}")
    fun publication(@PathVariable id: Int): PublicationViewModel {
        val publication = graphService.graph.publicationById(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No publication with ID $id")
        return publication.toViewModel()
    }

    @PostMapping("/publications", consumes = ["application/json"])
    @ResponseBody
    fun addPublication(@RequestBody params: AddPublicationParameters): PublicationViewModel {
        val name = params.name.nullize() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required")
        val refId = params.refId.nullize() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "refID is required")
        val publication = graphService.graph.addPublication(name, refId)
        graphService.graph.save()
        return publication.toViewModel()
    }

    @PostMapping("/publication/{id}", consumes = ["application/json"])
    @ResponseBody
    fun updatePublication(@PathVariable id: Int, @RequestBody params: AddPublicationParameters): PublicationViewModel {
        val publication = graphService.graph.publicationById(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No publication with ID $id")
        publication.name = params.name.nullize() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required")
        publication.refId = params.refId.nullize() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "refID is required")
        graphService.graph.save()
        return publication.toViewModel()
    }
}
