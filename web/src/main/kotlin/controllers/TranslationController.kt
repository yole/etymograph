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
    data class TranslationParams(val corpusTextId: Int? = null, val text: String, val source: String)

    @PostMapping("/{graph}/translation")
    fun addTranslation(repo: GraphRepository, @RequestBody params: TranslationParams): CorpusController.TranslationViewModel {
        val source = parseSourceRefs(repo, params.source)
        val corpusText = repo.resolveCorpusText(params.corpusTextId)
        val translation = repo.addTranslation(corpusText, params.text, source)
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
}
