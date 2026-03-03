package ru.yole.etymograph.web.controllers

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import ru.yole.etymograph.GraphRepository
import ru.yole.etymograph.Translation
import ru.yole.etymograph.web.parseSourceRefs
import ru.yole.etymograph.web.resolveCorpusText

@RestController
class TranslationController {
    data class TranslationParams(
        val corpusTextId: Int? = null,
        val text: String,
        val source: String,
        val anchorStartIndex: Int? = null
    )

    @PostMapping("/{graph}/translation")
    fun addTranslation(repo: GraphRepository, @RequestBody params: TranslationParams): CorpusController.TranslationViewModel {
        val source = parseSourceRefs(repo, params.source)
        val corpusText = repo.resolveCorpusText(params.corpusTextId)
        val translation = repo.addTranslation(corpusText, params.text, source)
        params.anchorStartIndex?.let { anchorStartIndex ->
            if (anchorStartIndex < 0 || anchorStartIndex >= corpusText.wordCount()) {
                badRequest("Anchor index $anchorStartIndex is out of bounds")
            }

            val translations = repo.translationsForText(corpusText)
            val nextAnchorStartIndex = translations
                .asSequence()
                .mapNotNull { it.anchorStartIndex }
                .filter { it > anchorStartIndex }
                .minOrNull()
                ?: corpusText.wordCount()

            translation.anchorStartIndex = anchorStartIndex
            translation.anchorEndIndex = nextAnchorStartIndex

            translations
                .filter { it.id != translation.id }
                .forEach {
                    val start = it.anchorStartIndex ?: return@forEach
                    val end = it.anchorEndIndex ?: return@forEach
                    if (start < anchorStartIndex && end > anchorStartIndex) {
                        it.anchorEndIndex = anchorStartIndex
                    }
                }
        }
        return translationToViewModel(translation, repo)
    }

    @PostMapping("/{graph}/translations/{id}")
    fun editTranslation(repo: GraphRepository, @PathVariable id: Int, @RequestBody params: TranslationParams): CorpusController.TranslationViewModel {
        val source = parseSourceRefs(repo, params.source)
        val translation = repo.langEntityById(id) as? Translation
            ?: notFound("No translation with ID $id")
        translation.text = params.text
        translation.source = source
        return translationToViewModel(translation, repo)
    }

    @PostMapping("/{graph}/translations/{id}/delete")
    fun deleteTranslation(repo: GraphRepository, @PathVariable id: Int) {
        val translation = repo.langEntityById(id) as? Translation
            ?: notFound("No translation with ID $id")
        repo.deleteTranslation(translation)
    }
}
