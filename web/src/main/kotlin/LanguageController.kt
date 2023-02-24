package ru.yole.etymograph.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class LanguageController(val graphService: GraphService) {
    data class PhonemeClassViewModel(
        val name: String,
        val matchingPhonemes: List<String>
    )

    data class LanguageViewModel(
        val name: String,
        val phonemeClasses: List<PhonemeClassViewModel>,
        val letterNormalization: String
    )

    @GetMapping("/language/{lang}")
    fun language(@PathVariable lang: String): LanguageViewModel {
        val language = graphService.resolveLanguage(lang)
        return LanguageViewModel(
            language.name,
            language.phonemeClasses.map { PhonemeClassViewModel(it.name, it.matchingPhonemes) },
            language.letterNormalization.entries.joinToString(", ") { (from, to) -> "$from=$to" }
        )
    }

    data class UpdateLanguageParameters(
        val letterNormalization: String? = null
    )

    @PostMapping("/language/{lang}", consumes = ["application/json"])
    fun updateLanguage(@PathVariable lang: String, @RequestBody params: UpdateLanguageParameters) {
        val language = graphService.resolveLanguage(lang)
        language.letterNormalization = params.letterNormalization?.let { parseLetterNormalization(it) } ?: emptyMap()
        graphService.graph.save()
    }

    private fun parseLetterNormalization(rules: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (pair in rules.split(',')) {
            val (from, to) = pair.trim().split('=', limit = 2)
            result[from] = to
        }
        return result
    }
}