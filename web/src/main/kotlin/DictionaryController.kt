package ru.yole.etymograph.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import ru.yole.etymograph.Language
import ru.yole.etymograph.WordKind

@RestController
class DictionaryController(val graphService: GraphService) {
    data class DictionaryWordViewModel(val id: Int, val text: String, val gloss: String, val fullGloss: String?, val homonym: Boolean)
    data class DictionaryViewModel(val language: Language, val words: List<DictionaryWordViewModel>)

    @GetMapping("/{graph}/dictionary/{lang}")
    fun dictionary(@PathVariable graph: String, @PathVariable lang: String): DictionaryViewModel {
        return loadDictionary(graph, lang, WordKind.NORMAL)
    }

    @GetMapping("/{graph}/dictionary/{lang}/compounds")
    fun dictionaryCompound(@PathVariable graph: String, @PathVariable lang: String): DictionaryViewModel {
        return loadDictionary(graph, lang, WordKind.COMPOUND)
    }

    @GetMapping("/{graph}/dictionary/{lang}/names")
    fun dictionaryNames(@PathVariable graph: String, @PathVariable lang: String): DictionaryViewModel {
        return loadDictionary(graph, lang, WordKind.NAME)
    }

    @GetMapping("/{graph}/dictionary/{lang}/reconstructed")
    fun dictionaryReconstructed(@PathVariable graph: String, @PathVariable lang: String): DictionaryViewModel {
        return loadDictionary(graph, lang, WordKind.RECONSTRUCTED)
    }

    @GetMapping("/{graph}/dictionary/{lang}/all")
    fun allWords(@PathVariable graph: String, @PathVariable lang: String): DictionaryViewModel {
        return loadDictionary(graph, lang, null)
    }

    private fun loadDictionary(graph: String, lang: String, wordKind: WordKind?): DictionaryViewModel {
        val repo = graphService.resolveGraph(graph)
        val language = graphService.resolveLanguage(graph, lang)
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
