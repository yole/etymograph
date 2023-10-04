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

    @PostMapping("/translation")
    fun addTranslation(@RequestBody params: TranslationParams) {
        val graph = graphService.graph
        val source = parseSourceRefs(graph, params.source)
        val corpusTextId = params.corpusTextId
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Corpus text ID not specified")
        val corpusText = graphService.resolveCorpusText(params.corpusTextId)
        graph.addTranslation(corpusText, params.text, source)
        graph.save()
    }

    @PostMapping("/translations/{id}")
    fun editTranslation(@PathVariable id: Int, @RequestBody params: TranslationParams) {
        val graph = graphService.graph
        val source = parseSourceRefs(graph, params.source)
        val translation = graph.langEntityById(id) as? Translation
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No translation with ID $id")
        translation.text = params.text
        translation.source = source
        graph.save()
    }
}
