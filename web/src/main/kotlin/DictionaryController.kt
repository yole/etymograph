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

    @GetMapping("/dictionary/{lang}")
    fun dictionary(@PathVariable lang: String): DictionaryViewModel {
        return loadDictionary(lang, WordKind.NORMAL)
    }

    @GetMapping("/dictionary/{lang}/compounds")
    fun dictionaryCompound(@PathVariable lang: String): DictionaryViewModel {
        return loadDictionary(lang, WordKind.COMPOUND)
    }

    @GetMapping("/dictionary/{lang}/names")
    fun dictionaryNames(@PathVariable lang: String): DictionaryViewModel {
        return loadDictionary(lang, WordKind.NAME)
    }

    @GetMapping("/dictionary/{lang}/reconstructed")
    fun dictionaryReconstructed(@PathVariable lang: String): DictionaryViewModel {
        return loadDictionary(lang, WordKind.RECONSTRUCTED)
    }

    @GetMapping("/dictionary/{lang}/all")
    fun allWords(@PathVariable lang: String): DictionaryViewModel {
        return loadDictionary(lang, null)
    }

    private fun loadDictionary(lang: String, wordKind: WordKind?): DictionaryViewModel {
        val language = graphService.resolveLanguage(lang)
        val words = if (wordKind == null)
            graphService.graph.allWords(language)
        else
            graphService.graph.filteredWords(language, wordKind)
        return DictionaryViewModel(language, words.map {
            DictionaryWordViewModel(
                it.id, it.text,
                it.getOrComputeGloss(graphService.graph) ?: "", it.fullGloss,
                graphService.graph.isHomonym(it)
            )
        })
    }
}
