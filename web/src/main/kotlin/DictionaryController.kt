package ru.yole.etymograph.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import ru.yole.etymograph.Language
import ru.yole.etymograph.Word

@RestController
class DictionaryController(val graphService: GraphService) {
    data class DictionaryWordViewModel(val id: Int, val text: String, val gloss: String)
    data class DictionaryViewModel(val language: Language, val words: List<DictionaryWordViewModel>)

    @GetMapping("/dictionary/{lang}")
    fun dictionary(@PathVariable lang: String): DictionaryViewModel {
        return loadDictionary(lang) { language -> graphService.graph.dictionaryWords(language) }
    }

    @GetMapping("/dictionary/{lang}/compounds")
    fun dictionaryCompound(@PathVariable lang: String): DictionaryViewModel {
        return loadDictionary(lang) { language -> graphService.graph.compoundWords(language) }
    }

    @GetMapping("/dictionary/{lang}/names")
    fun dictionaryNames(@PathVariable lang: String): DictionaryViewModel {
        return loadDictionary(lang) { language -> graphService.graph.nameWords(language) }
    }

    private fun loadDictionary(lang: String, wordLoader: (Language) -> List<Word>): DictionaryViewModel {
        val language = graphService.resolveLanguage(lang)
        val words = wordLoader(language)
        return DictionaryViewModel(language, words.map { DictionaryWordViewModel(it.id, it.text,
            it.getOrComputeGloss(graphService.graph) ?: "") })
    }
}
