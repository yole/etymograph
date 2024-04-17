package ru.yole.etymograph.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import ru.yole.etymograph.Translation

@RestController
class TranslationController(val graphService: GraphService) {
    data class TranslationParams(val corpusTextId: Int? = null, val text: String, val source: String)

    @PostMapping("/{graph}/translation")
    fun addTranslation(@PathVariable graph: String, @RequestBody params: TranslationParams): CorpusController.TranslationViewModel {
        val repo = graphService.resolveGraph(graph)
        val source = parseSourceRefs(repo, params.source)
        val corpusTextId = params.corpusTextId
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Corpus text ID not specified")
        val corpusText = graphService.resolveCorpusText(graph, corpusTextId)
        val translation = repo.addTranslation(corpusText, params.text, source)
        return translationToViewModel(translation, repo)
    }

    @PostMapping("/{graph}/translations/{id}")
    fun editTranslation(@PathVariable graph: String, @PathVariable id: Int, @RequestBody params: TranslationParams): CorpusController.TranslationViewModel {
        val repo = graphService.resolveGraph(graph)
        val source = parseSourceRefs(repo, params.source)
        val translation = repo.langEntityById(id) as? Translation
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No translation with ID $id")
        translation.text = params.text
        translation.source = source
        return translationToViewModel(translation, repo)
    }
}
