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
    data class PhonemeTableCellViewModel(
        val phonemes: List<PhonemeViewModel>
    )

    data class PhonemeTableRowViewModel(
        val title: String,
        val cells: List<PhonemeTableCellViewModel>
    )

    data class PhonemeTableViewModel(
        val title: String,
        val columnTitles: List<String>,
        val rows: List<PhonemeTableRowViewModel>
    )

    data class LanguageViewModel(
        val name: String,
        val shortName: String,
        val reconstructed: Boolean,
        val diphthongs: List<String>,
        val phonemes: List<PhonemeTableViewModel>,
        val stressRuleId: Int?,
        val stressRuleName: String?,
        val phonotacticsRuleId: Int?,
        val phonotacticsRuleName: String?,
        val orthographyRuleId: Int?,
        val orthographyRuleName: String?,
        val syllableStructures: List<String>,
        val grammaticalCategories: String,
        val wordClasses: String
    )

    @GetMapping("/language")
    fun indexJson(): List<Language> {
        return graphService.graph.allLanguages().sortedBy { it.name }.toList()
    }

    @GetMapping("/language/{lang}")
    fun language(@PathVariable lang: String): LanguageViewModel {
        val language = graphService.resolveLanguage(lang)
        return language.toViewModel()
    }

    private fun Language.toViewModel(): LanguageViewModel {
        val stressRule = stressRule?.resolve()
        val phonotacticsRule = phonotacticsRule?.resolve()
        val orthographyRule = orthographyRule?.resolve()
        return LanguageViewModel(
            name,
            shortName,
            reconstructed,
            diphthongs,
            buildPhonemeTables().map { table ->
                PhonemeTableViewModel(table.title, table.columnTitles, table.rows.map { row ->
                    PhonemeTableRowViewModel(row.title, row.columns.map { cell ->
                        PhonemeTableCellViewModel(cell.phonemes.map { it.toViewModel(graphService.graph, this)})
                    })
                })
            },
            stressRule?.id,
            stressRule?.name,
            phonotacticsRule?.id,
            phonotacticsRule?.name,
            orthographyRule?.id,
            orthographyRule?.name,
            syllableStructures,
            grammaticalCategories.toEditableText(),
            wordClasses.toEditableText()
        )
    }

    data class UpdateLanguageParameters(
        val name: String? = null,
        val shortName: String? = null,
        val reconstructed: Boolean? = null,
        val phonemes: String? = null,
        val diphthongs: String? = null,
        val stressRuleName: String? = null,
        val phonotacticsRuleName: String? = null,
        val orthographyRuleName: String? = null,
        val syllableStructures: String? = null,
        val grammaticalCategories: String? = null,
        val wordClasses: String? = null
    )

    @PostMapping("/languages", consumes = ["application/json"])
    fun addLanguage(@RequestBody params: UpdateLanguageParameters): LanguageViewModel {
        val name = params.name ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Language name must be provided")
        val shortName = params.shortName ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Language short name must be provided")
        val language = Language(name, shortName)
        graphService.graph.addLanguage(language)
        updateLanguageDetails(language, params)
        return language.toViewModel()
    }

    @PostMapping("/language/{lang}", consumes = ["application/json"])
    fun updateLanguage(@PathVariable lang: String, @RequestBody params: UpdateLanguageParameters) {
        val language = graphService.resolveLanguage(lang)
        updateLanguageDetails(language, params)
    }

    data class CopyPhonemesParams(val fromLang: String = "")

    @PostMapping("/language/{lang}/copyPhonemes", consumes = ["application/json"])
    fun copyPhonemes(@PathVariable lang: String, @RequestBody params: CopyPhonemesParams) {
        val toLanguage = graphService.resolveLanguage(lang)
        val fromLanguage = graphService.resolveLanguage(params.fromLang)
        for (phoneme in fromLanguage.phonemes) {
            if (toLanguage.phonemes.none { phoneme.graphemes.intersect(it.graphemes).isNotEmpty() }) {
                graphService.graph.addPhoneme(toLanguage, phoneme.graphemes, phoneme.sound, phoneme.classes)
            }
        }
    }

    private fun updateLanguageDetails(
        language: Language,
        params: UpdateLanguageParameters
    ) {
        if (params.reconstructed != null) {
            language.reconstructed = params.reconstructed
        }

        language.diphthongs = parseList(params.diphthongs)
        language.syllableStructures = parseList(params.syllableStructures)
        language.grammaticalCategories = params.grammaticalCategories.nullize()?.let { parseWordCategories(it) } ?: mutableListOf()
        language.wordClasses = params.wordClasses.nullize()?.let { parseWordCategories(it) } ?: mutableListOf()

        language.stressRule = parseRuleRef(params.stressRuleName)
        language.phonotacticsRule = parseRuleRef(params.phonotacticsRuleName)
        language.orthographyRule = parseRuleRef(params.orthographyRuleName)
    }

    private fun parseRuleRef(name: String?): RuleRef? {
        val stressRule = name?.nullize()?.let { graphService.resolveRule(it) }
        return stressRule?.let { RuleRef.to(it) }
    }

    private fun List<WordCategory>.toEditableText(): String {
        return joinToString("\n") { gc ->
            val pos = gc.pos.joinToString(", ")
            "${gc.name} ($pos): ${gc.values.joinToString(", ") { it.toEditableText() }}"
        }
    }

    private fun WordCategoryValue.toEditableText(): String {
        return if (name == abbreviation) name else "$name ($abbreviation)"
    }

    private fun parseWordCategories(s: String): MutableList<WordCategory> {
        return s.trim().split('\n').mapTo(mutableListOf()) { gcLine ->
            val (nameString, valueStrings) = gcLine.trim().split(':', limit = 2)
            val (name, pos) = parseWordCategoryName(nameString)
            WordCategory(
                name,
                pos,
                valueStrings.split(',').map {
                    parseWordCategoryValue(it.trim())
                }
            )
        }
    }

    private fun parseWordCategoryName(s: String): Pair<String, List<String>> {
        val p = parenthesized.matchEntire(s)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unrecognized grammatical category name format $s")
        return p.groupValues[1] to p.groupValues[2].split(',').map { it.trim() }
    }

    private fun parseWordCategoryValue(s: String): WordCategoryValue {
        val p = parenthesized.matchEntire(s)
            ?: return WordCategoryValue(s, s)
        return WordCategoryValue(p.groupValues[1], p.groupValues[2])
    }

    companion object {
        val parenthesized = Regex("(.+)\\s+\\((.+)\\)")
    }
}

fun parseList(s: String?): List<String> =
    s?.takeIf { it.isNotBlank() }?.let { it.split(",").map { d -> d.trim() } } ?: emptyList()
