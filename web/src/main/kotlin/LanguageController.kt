package ru.yole.etymograph.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import ru.yole.etymograph.PhonemeClass
import ru.yole.etymograph.RuleRef

@RestController
class LanguageController(val graphService: GraphService) {
    data class PhonemeClassViewModel(
        val name: String,
        val matchingPhonemes: List<String>
    )

    data class LanguageViewModel(
        val name: String,
        val diphthongs: List<String>,
        val digraphs: List<String>,
        val phonemeClasses: List<PhonemeClassViewModel>,
        val letterNormalization: String,
        val stressRuleName: String?,
        val syllableStructures: List<String>
    )

    @GetMapping("/language/{lang}")
    fun language(@PathVariable lang: String): LanguageViewModel {
        val language = graphService.resolveLanguage(lang)
        val stressRule = language.stressRule?.resolve()
        return LanguageViewModel(
            language.name,
            language.diphthongs,
            language.digraphs,
            language.phonemeClasses.map { PhonemeClassViewModel(it.name, it.matchingPhonemes) },
            language.letterNormalization.entries.joinToString(", ") { (from, to) -> "$from=$to" },
            stressRule?.name,
            language.syllableStructures
        )
    }

    data class UpdateLanguageParameters(
        val letterNormalization: String? = null,
        val phonemeClasses: String? = null,
        val diphthongs: String? = null,
        val digraphs: String? = null,
        val stressRuleName: String? = null,
        val syllableStructures: String? = null
    )

    @PostMapping("/language/{lang}", consumes = ["application/json"])
    fun updateLanguage(@PathVariable lang: String, @RequestBody params: UpdateLanguageParameters) {
        fun parseList(s: String?): List<String> =
            s?.let { it.split(",").map { d -> d.trim() } } ?: emptyList()

        val language = graphService.resolveLanguage(lang)
        language.letterNormalization = params.letterNormalization?.let { parseLetterNormalization(it) } ?: emptyMap()
        language.phonemeClasses = params.phonemeClasses?.let { parsePhonemeClasses(it) } ?: mutableListOf()
        language.diphthongs = parseList(params.diphthongs)
        language.digraphs = parseList(params.digraphs)
        language.syllableStructures = parseList(params.syllableStructures)

        val stressRule = params.stressRuleName?.let { graphService.resolveRule(it) }
        language.stressRule = stressRule?.let { RuleRef.to(it) }

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

    private fun parsePhonemeClasses(s: String): MutableList<PhonemeClass> {
        return s.split('\n').map { cls ->
            val (name, phonemes) = cls.split(':')
            PhonemeClass(name.trim(), phonemes.split(',').map { it.trim() })
        }.toMutableList()
    }
}
