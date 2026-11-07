package page.yole.etymograph.web.controllers

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import page.yole.etymograph.Graph
import page.yole.etymograph.Translation
import page.yole.etymograph.web.parseSourceRefs
import page.yole.etymograph.web.resolveCorpusText

@RestController
class TranslationController {
    data class TranslationParams(
        val corpusTextId: Int? = null,
        val text: String,
        val source: String? = null,
        val anchorStartIndex: Int? = null,
        val anchorEndIndex: Int? = null
    )

    @PostMapping("/{graph}/translation")
    fun addTranslation(graph: Graph, @RequestBody params: TranslationParams): CorpusController.TranslationViewModel {
        val source = parseSourceRefs(graph, params.source)
        val corpusText = graph.resolveCorpusText(params.corpusTextId)
        val wordCount = corpusText.wordCount()
        val anchorStartIndex = params.anchorStartIndex
        val anchorEndIndex = params.anchorEndIndex
        if (anchorStartIndex != null && (anchorStartIndex < 0 || anchorStartIndex >= wordCount)) {
            badRequest("Anchor index $anchorStartIndex is out of bounds")
        }
        if (anchorEndIndex != null &&
            (anchorStartIndex == null || anchorEndIndex <= anchorStartIndex || anchorEndIndex > wordCount)) {
            badRequest("Invalid translation anchor range")
        }

        val translation = graph.addTranslation(corpusText, params.text, source)
        if (anchorStartIndex != null) {
            val translations = graph.translationsForText(corpusText)
            val nextAnchorStartIndex = anchorEndIndex ?: translations
                .asSequence()
                .mapNotNull { it.anchorStartIndex }
                .filter { it > anchorStartIndex }
                .minOrNull()
                ?: wordCount

            translation.anchorStartIndex = anchorStartIndex
            translation.anchorEndIndex = nextAnchorStartIndex

            translations
                .filter { it.id != translation.id }
                .forEach {
                    val start = it.anchorStartIndex ?: return@forEach
                    val end = it.anchorEndIndex ?: return@forEach
                    if (anchorStartIndex in (start + 1)..<end) {
                        it.anchorEndIndex = anchorStartIndex
                    }
                }
        }
        return translationToViewModel(translation, graph)
    }

    @PostMapping("/{graph}/translations/{id}")
    fun editTranslation(graph: Graph, @PathVariable id: Int, @RequestBody params: TranslationParams): CorpusController.TranslationViewModel {
        val source = parseSourceRefs(graph, params.source)
        val translation = graph.langEntityById(id) as? Translation
            ?: notFound("No translation with ID $id")
        translation.text = params.text
        translation.source = source
        return translationToViewModel(translation, graph)
    }

    @PostMapping("/{graph}/translations/{id}/delete")
    fun deleteTranslation(graph: Graph, @PathVariable id: Int) {
        val translation = graph.langEntityById(id) as? Translation
            ?: notFound("No translation with ID $id")
        graph.deleteTranslation(translation)
    }
}
