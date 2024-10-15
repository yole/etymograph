package ru.yole.etymograph.web.controllers

import org.springframework.web.bind.annotation.*
import ru.yole.etymograph.*
import ru.yole.etymograph.web.resolveLanguage
import ru.yole.etymograph.web.resolveRule
import java.text.Normalizer

@RestController
class LanguageController {
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

    data class WordCategoryValueViewModel(
        val name: String,
        val abbreviation: String
    )

    data class WordCategoryViewModel(
        val name: String,
        val pos: List<String>,
        val values: List<WordCategoryValueViewModel>
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
        val pronunciationRuleId: Int?,
        val pronunciationRuleName: String?,
        val orthographyRuleId: Int?,
        val orthographyRuleName: String?,
        val syllableStructures: List<String>,
        val pos: String,
        val grammaticalCategories: String,
        val wordClasses: String,
        val dictionarySettings: String?
    )

    data class LanguageShortViewModel(
        val name: String,
        val shortName: String,
        val pos: List<WordCategoryValueViewModel>,
        val wordClasses: List<WordCategoryViewModel>,
        val dictionaries: List<String>
    )

    @GetMapping("/{graph}/language")
    fun indexJson(repo: GraphRepository): List<LanguageShortViewModel> {
        return repo.allLanguages().sortedBy { it.name }.map { lang ->
            LanguageShortViewModel(
                lang.name,
                lang.shortName,
                lang.pos.map { WordCategoryValueViewModel(it.name, it.abbreviation) },
                lang.wordClasses.map {
                    WordCategoryViewModel(it.name, it.pos, it.values.map {
                        WordCategoryValueViewModel(it.name, it.abbreviation)
                    })
                },
                if ("wiktionary-id" in (lang.dictionarySettings ?: "")) listOf("wiktionary") else emptyList()
            )
        }
    }

    @GetMapping("/{graph}/language/{lang}")
    fun language(repo: GraphRepository, @PathVariable lang: String): LanguageViewModel {
        val language = repo.resolveLanguage(lang)
        return language.toViewModel(repo)
    }

    private fun Language.toViewModel(repo: GraphRepository): LanguageViewModel {
        val stressRule = stressRule?.resolve()
        val phonotacticsRule = phonotacticsRule?.resolve()
        val pronunciationRule = pronunciationRule?.resolve()
        val orthographyRule = orthographyRule?.resolve()
        return LanguageViewModel(
            name,
            shortName,
            reconstructed,
            diphthongs,
            buildPhonemeTables().map { table ->
                PhonemeTableViewModel(table.title, table.columnTitles, table.rows.map { row ->
                    PhonemeTableRowViewModel(row.title, row.columns.map { cell ->
                        PhonemeTableCellViewModel(cell.phonemes.map { it.toViewModel(repo, this)})
                    })
                })
            },
            stressRule?.id,
            stressRule?.name,
            phonotacticsRule?.id,
            phonotacticsRule?.name,
            pronunciationRule?.id,
            pronunciationRule?.name,
            orthographyRule?.id,
            orthographyRule?.name,
            syllableStructures,
            pos.valuesToEditableText(),
            grammaticalCategories.toEditableText(),
            wordClasses.toEditableText(),
            dictionarySettings
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
        val pronunciationRuleName: String? = null,
        val orthographyRuleName: String? = null,
        val syllableStructures: String? = null,
        val pos: String? = null,
        val grammaticalCategories: String? = null,
        val wordClasses: String? = null,
        val dictionarySettings: String? = null
    )

    @PostMapping("/{graph}/languages", consumes = ["application/json"])
    fun addLanguage(repo: GraphRepository, @RequestBody params: UpdateLanguageParameters): LanguageViewModel {
        val name = params.name.takeIf { !it.isNullOrBlank() } ?: badRequest("Language name must be provided")
        val shortName = params.shortName.takeIf { !it.isNullOrBlank() } ?: badRequest("Language short name must be provided")
        val language = Language(name, shortName)
        repo.addLanguage(language)
        updateLanguageDetails(repo, language, params)
        return language.toViewModel(repo)
    }

    @PostMapping("/{graph}/language/{lang}", consumes = ["application/json"])
    fun updateLanguage(repo: GraphRepository, @PathVariable lang: String, @RequestBody params: UpdateLanguageParameters) {
        val language = repo.resolveLanguage(lang)
        updateLanguageDetails(repo, language, params)
    }

    data class CopyPhonemesParams(val fromLang: String = "")

    @PostMapping("/{graph}/language/{lang}/copyPhonemes", consumes = ["application/json"])
    fun copyPhonemes(repo: GraphRepository, @PathVariable lang: String, @RequestBody params: CopyPhonemesParams) {
        val toLanguage = repo.resolveLanguage(lang)
        val fromLanguage = repo.resolveLanguage(params.fromLang)
        for (phoneme in fromLanguage.phonemes) {
            if (toLanguage.phonemes.none { phoneme.graphemes.intersect(it.graphemes).isNotEmpty() }) {
                repo.addPhoneme(toLanguage, phoneme.graphemes, phoneme.sound, phoneme.classes)
            }
        }
    }

    data class InputAssistGraphemeViewModel(val text: String, val languages: List<String>)
    data class InputAssistViewModel(val graphemes: List<InputAssistGraphemeViewModel>)

    @GetMapping("/{graph}/inputAssist")
    fun inputAssist(repo: GraphRepository): InputAssistViewModel {
        val graphemes = mutableMapOf<String, MutableList<String>>()
        for (language in repo.allLanguages()) {
            for (phoneme in language.phonemes) {
                val grapheme = phoneme.graphemes.first()
                if (grapheme.any { it !in 'a'..'z'}) {
                    val langList = graphemes.getOrPut(grapheme) { mutableListOf() }
                    langList.add(language.shortName)
                }
            }
        }
        graphemes.getOrPut(explicitStressMark.toString()) { mutableListOf() }
        return InputAssistViewModel(
            graphemes
                .map { (text, langs) -> InputAssistGraphemeViewModel(text, langs) }
                .sortedBy { Normalizer.normalize(it.text, Normalizer.Form.NFD) }
        )
    }

    private fun updateLanguageDetails(
        repo: GraphRepository,
        language: Language,
        params: UpdateLanguageParameters
    ) {
        if (params.reconstructed != null) {
            language.reconstructed = params.reconstructed
        }

        language.diphthongs = parseList(params.diphthongs)
        language.syllableStructures = parseList(params.syllableStructures)
        language.pos = params.pos.nullize()?.let { parseWordCategoryValues(it) } ?: mutableListOf()
        language.grammaticalCategories = params.grammaticalCategories.nullize()?.let { parseWordCategories(it) } ?: mutableListOf()
        language.wordClasses = params.wordClasses.nullize()?.let { parseWordCategories(it) } ?: mutableListOf()

        language.stressRule = parseRuleRef(repo, params.stressRuleName)
        language.phonotacticsRule = parseRuleRef(repo, params.phonotacticsRuleName)
        language.pronunciationRule = parseRuleRef(repo, params.pronunciationRuleName)
        language.orthographyRule = parseRuleRef(repo, params.orthographyRuleName)

        language.dictionarySettings = params.dictionarySettings
    }

    private fun parseRuleRef(repo: GraphRepository, name: String?): RuleRef? {
        val rule = name?.nullize()?.let { repo.resolveRule(it) }
        return rule?.let { RuleRef.to(it) }
    }

    private fun List<WordCategory>.toEditableText(): String {
        return joinToString("\n") { gc ->
            val pos = gc.pos.joinToString(", ")
            "${gc.name} ($pos): ${gc.values.valuesToEditableText()}"
        }
    }

    private fun List<WordCategoryValue>.valuesToEditableText(): String =
        joinToString(", ") { it.toEditableText() }

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
                parseWordCategoryValues(valueStrings)
            )
        }
    }

    private fun parseWordCategoryValues(valueStrings: String): MutableList<WordCategoryValue> =
        valueStrings.split(',').map { it.trim() }.mapTo(mutableListOf()) { s ->
            val p = parenthesized.matchEntire(s)
            if (p != null) WordCategoryValue(p.groupValues[1], p.groupValues[2]) else WordCategoryValue(s, s)
        }

    private fun parseWordCategoryName(s: String): Pair<String, List<String>> {
        val p = parenthesized.matchEntire(s)
            ?: badRequest("Unrecognized grammatical category name format $s")
        return p.groupValues[1] to p.groupValues[2].split(',').map { it.trim() }
    }

    companion object {
        val parenthesized = Regex("(.+)\\s+\\((.+)\\)")
    }
}

fun parseList(s: String?): List<String> =
    s?.takeIf { it.isNotBlank() }?.let { it.split(",").map { d -> d.trim() } } ?: emptyList()
