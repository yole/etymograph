package ru.yole.etymograph.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import ru.yole.etymograph.UnknownLanguage

@RestController
class LanguageController(val graphService: GraphService) {
    data class PhonemeClassViewModel(
        val name: String,
        val matchingPhonemes: List<String>
    )

    data class LanguageViewModel(
        val name: String,
        val phonemeClasses: List<PhonemeClassViewModel>
    )

    @GetMapping("/language/{lang}")
    fun language(@PathVariable lang: String): LanguageViewModel {
        val language = graphService.graph.languageByShortName(lang)
        if (language == UnknownLanguage) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No language with short name $lang")
        }
        return LanguageViewModel(
            language.name,
            language.phonemeClasses.map { PhonemeClassViewModel(it.name, it.matchingPhonemes) }
        )
    }
}
