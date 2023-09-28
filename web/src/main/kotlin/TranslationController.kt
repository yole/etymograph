package ru.yole.etymograph.web

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class TranslationController(val graphService: GraphService) {
    data class TranslationParams(val corpusTextId: Int, val text: String, val source: String)

    @PostMapping("/translation")
    fun addTranslation(@RequestBody params: TranslationParams) {
        val source = parseSourceRefs(graphService.graph, params.source)
        val corpusText = graphService.resolveCorpusText(params.corpusTextId)
        graphService.graph.addTranslation(corpusText, params.text, source)
    }
}
