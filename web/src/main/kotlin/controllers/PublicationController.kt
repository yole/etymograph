package ru.yole.etymograph.web.controllers

import org.springframework.web.bind.annotation.*
import ru.yole.etymograph.GraphRepository
import ru.yole.etymograph.Publication

data class PublicationViewModel(
    val id: Int,
    val name: String,
    val author: String?,
    val date: String?,
    val publisher: String?,
    val refId: String
)

data class AddPublicationParameters(
    val name: String?,
    val refId: String?,
    val author: String?,
    val date: String?,
    val publisher: String?,
)

@RestController
class PublicationController {
    @GetMapping("/{graph}/publications")
    fun publications(repo: GraphRepository): List<PublicationViewModel> {
        return repo.allPublications().sortedBy { it.refId }.map {
            it.toViewModel()
        }
    }

    private fun Publication.toViewModel() = PublicationViewModel(id, name, author, date, publisher, refId)

    @GetMapping("/{graph}/publication/{id}")
    fun publication(repo: GraphRepository, @PathVariable id: Int): PublicationViewModel {
        val publication = repo.resolvePublication(id)
        return publication.toViewModel()
    }

    private fun GraphRepository.resolvePublication(id: Int): Publication = (publicationById(id)
        ?: notFound("No publication with ID $id"))

    @PostMapping("/{graph}/publications", consumes = ["application/json"])
    @ResponseBody
    fun addPublication(repo: GraphRepository, @RequestBody params: AddPublicationParameters): PublicationViewModel {
        val name = params.name.nullize() ?: badRequest("Name is required")
        val refId = params.refId.nullize() ?: badRequest("refID is required")
        val publication = repo.addPublication(name, refId)
        updatePublicationParameters(publication, params)
        return publication.toViewModel()
    }

    private fun updatePublicationParameters(publication: Publication, params: AddPublicationParameters) {
        publication.author = params.author.nullize()
        publication.date = params.date.nullize()
        publication.publisher = params.publisher.nullize()
    }

    @PostMapping("/{graph}/publication/{id}", consumes = ["application/json"])
    @ResponseBody
    fun updatePublication(repo: GraphRepository, @PathVariable id: Int, @RequestBody params: AddPublicationParameters): PublicationViewModel {
        val publication = repo.resolvePublication(id)
        publication.name = params.name.nullize() ?: badRequest("Name is required")
        publication.refId = params.refId.nullize() ?: badRequest("refID is required")
        updatePublicationParameters(publication, params)
        return publication.toViewModel()
    }
}
