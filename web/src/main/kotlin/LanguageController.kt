package ru.yole.etymograph.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import ru.yole.etymograph.*

@RestController
class LanguageController(val graphService: GraphService) {
    data class PhonemeClassViewModel(
        val name: String,
        val matchingPhonemes: List<String>
    )

    data class LanguageViewModel(
        val name: String,
        val shortName: String,
        val diphthongs: List<String>,
        val digraphs: List<String>,
        val phonemeClasses: List<PhonemeClassViewModel>,
        val letterNormalization: String,
        val stressRuleId: Int?,
        val stressRuleName: String?,
        val syllableStructures: List<String>,
        val wordFinals: List<String>,
        val grammaticalCategories: String
    )

    @GetMapping("/language")
    fun indexJson(): List<Language> {
        return graphService.graph.allLanguages().toList()
    }

    @GetMapping("/language/{lang}")
    fun language(@PathVariable lang: String): LanguageViewModel {
        val language = graphService.resolveLanguage(lang)
        return language.toViewModel()
    }

    private fun Language.toViewModel(): LanguageViewModel {
        val stressRule = stressRule?.resolve()
        return LanguageViewModel(
            name,
            shortName,
            diphthongs,
            digraphs,
            phonemeClasses.map { PhonemeClassViewModel(it.name, it.matchingPhonemes) },
            letterNormalization.entries.joinToString(", ") { (from, to) -> "$from=$to" },
            stressRule?.id,
            stressRule?.name,
            syllableStructures,
            wordFinals,
            grammaticalCategories.toEditableText()
        )
    }

    data class UpdateLanguageParameters(
        val name: String? = null,
        val shortName: String? = null,
        val letterNormalization: String? = null,
        val phonemeClasses: String? = null,
        val diphthongs: String? = null,
        val digraphs: String? = null,
        val stressRuleName: String? = null,
        val syllableStructures: String? = null,
        val wordFinals: String? = null,
        val grammaticalCategories: String? = null
    )

    @PostMapping("/languages", consumes = ["application/json"])
    fun addLanguage(@RequestBody params: UpdateLanguageParameters): LanguageViewModel {
        val name = params.name ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Language name must be provided")
        val shortName = params.shortName ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Language short name must be provided")
        val language = Language(name, shortName)
        graphService.graph.addLanguage(language)
        updateLanguageDetails(language, params)
        graphService.graph.save()
        return language.toViewModel()
    }

    @PostMapping("/language/{lang}", consumes = ["application/json"])
    fun updateLanguage(@PathVariable lang: String, @RequestBody params: UpdateLanguageParameters) {
        val language = graphService.resolveLanguage(lang)
        updateLanguageDetails(language, params)
        graphService.graph.save()
    }

    private fun updateLanguageDetails(
        language: Language,
        params: UpdateLanguageParameters
    ) {
        fun parseList(s: String?): List<String> =
            s?.let { it.split(",").map { d -> d.trim() } } ?: emptyList()

        language.letterNormalization = params.letterNormalization?.let { parseLetterNormalization(it) } ?: emptyMap()
        language.phonemeClasses = params.phonemeClasses?.let { parsePhonemeClasses(it) } ?: mutableListOf()
        language.diphthongs = parseList(params.diphthongs)
        language.digraphs = parseList(params.digraphs)
        language.syllableStructures = parseList(params.syllableStructures)
        language.wordFinals = parseList(params.wordFinals)
        language.grammaticalCategories = params.grammaticalCategories?.let { parseGrammaticaLCategories(it) } ?: mutableListOf()

        val stressRule = params.stressRuleName?.let { graphService.resolveRule(it) }
        language.stressRule = stressRule?.let { RuleRef.to(it) }
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

    private fun List<GrammaticalCategory>.toEditableText(): String {
        return joinToString("\n") { gc ->
            val pos = gc.pos.joinToString(", ")
            "${gc.name} ($pos): ${gc.values.joinToString(", ") { it.toEditableText() }}"
        }
    }

    private fun GrammaticalCategoryValue.toEditableText(): String {
        return "$name ($abbreviation)"
    }

    private fun parseGrammaticaLCategories(s: String): MutableList<GrammaticalCategory> {
        return s.trim().split('\n').mapTo(mutableListOf()) { gcLine ->
            val (nameString, valueStrings) = gcLine.trim().split(':', limit = 2)
            val (name, pos) = parseGrammaticaLCategoryName(nameString)
            GrammaticalCategory(
                name,
                pos,
                valueStrings.split(',').map {
                    parseGrammaticaLCategoryValue(it.trim())
                }
            )
        }
    }

    private fun parseGrammaticaLCategoryName(s: String): Pair<String, List<String>> {
        val p = parenthesized.matchEntire(s)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unrecognized grammatical category name format $s")
        return p.groupValues[1] to p.groupValues[2].split(',').map { it.trim() }
    }

    private fun parseGrammaticaLCategoryValue(s: String): GrammaticalCategoryValue {
        val p = parenthesized.matchEntire(s)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unrecognized grammatical category value format $s")
        return GrammaticalCategoryValue(p.groupValues[1], p.groupValues[2])
    }

    companion object {
        val parenthesized = Regex("(.+)\\s+\\((.+)\\)")
    }
}
