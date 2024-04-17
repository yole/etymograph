package ru.yole.etymograph.web.controllers

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.yole.etymograph.GraphRepository
import ru.yole.etymograph.Language
import ru.yole.etymograph.WordKind
import ru.yole.etymograph.web.GraphService
import ru.yole.etymograph.web.resolveLanguage

@RestController
@RequestMapping("/{graph}/dictionary")
class DictionaryController(val graphService: GraphService) {
    data class DictionaryWordViewModel(val id: Int, val text: String, val gloss: String, val fullGloss: String?, val homonym: Boolean)
    data class DictionaryViewModel(val language: Language, val words: List<DictionaryWordViewModel>)

    @GetMapping("/{lang}")
    fun dictionary(repo: GraphRepository, @PathVariable lang: String): DictionaryViewModel {
        return loadDictionary(repo, lang, WordKind.NORMAL)
    }

    @GetMapping("/{lang}/compounds")
    fun dictionaryCompound(repo: GraphRepository, @PathVariable lang: String): DictionaryViewModel {
        return loadDictionary(repo, lang, WordKind.COMPOUND)
    }

    @GetMapping("/{lang}/names")
    fun dictionaryNames(repo: GraphRepository, @PathVariable lang: String): DictionaryViewModel {
        return loadDictionary(repo, lang, WordKind.NAME)
    }

    @GetMapping("/{lang}/reconstructed")
    fun dictionaryReconstructed(repo: GraphRepository, @PathVariable lang: String): DictionaryViewModel {
        return loadDictionary(repo, lang, WordKind.RECONSTRUCTED)
    }

    @GetMapping("/{lang}/all")
    fun allWords(repo: GraphRepository, @PathVariable lang: String): DictionaryViewModel {
        return loadDictionary(repo, lang, null)
    }

    private fun loadDictionary(repo: GraphRepository, lang: String, wordKind: WordKind?): DictionaryViewModel {
        val language = repo.resolveLanguage(lang)
        val words = if (wordKind == null)
            repo.allWords(language)
        else
            repo.filteredWords(language, wordKind)
        return DictionaryViewModel(language, words.map {
            DictionaryWordViewModel(
                it.id, it.text,
                it.getOrComputeGloss(repo) ?: "", it.fullGloss,
                repo.isHomonym(it)
            )
        })
    }
}
