package ru.yole.etymograph.web

import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import ru.yole.etymograph.Language
import ru.yole.etymograph.UnknownLanguage

@RestController
@CrossOrigin(origins = ["http://localhost:3000"])
class DictionaryController(val graphService: GraphService) {
    data class DictionaryWordViewModel(val text: String, val gloss: String)
    data class DictionaryViewModel(val language: Language, val words: List<DictionaryWordViewModel>)

    @GetMapping("/dictionary/{lang}")
    fun dictionary(@PathVariable lang: String): DictionaryViewModel {
        val graph = graphService.graph
        val language = graph.languageByShortName(lang)
        if (language == UnknownLanguage) throw NoLanguageException()
        val words = graph.dictionaryWords(language)
        return DictionaryViewModel(language, words.map { DictionaryWordViewModel(it.text, it.gloss!!) })
    }
}
