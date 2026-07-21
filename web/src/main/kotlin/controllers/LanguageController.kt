package page.yole.etymograph.web.controllers

import kotlinx.serialization.Serializable
import org.springframework.web.bind.annotation.*
import page.yole.etymograph.*
import page.yole.etymograph.web.resolveLanguage
import page.yole.etymograph.web.resolveRule
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

    @Serializable
    data class WordCategoryValueViewModel(
        val name: String,
        val abbreviation: String
    )

    @Serializable
    data class WordCategoryViewModel(
        val name: String,
        val pos: List<String>,
        val values: List<WordCategoryValueViewModel>
    )

    data class LanguageViewModel(
        val name: String,
        val shortName: String,
        val protoLanguageShortName: String?,
        val reconstructed: Boolean,
        val syllabographic: Boolean,
        val diphthongs: List<String>,
        val phonemes: List<PhonemeTableViewModel>,
        val stressRuleId: Int?,
        val stressRuleName: String?,
        val phonotacticsRuleId: Int?,
        val phonotacticsRuleName: String?,
        val pronunciationRuleId: Int?,
        val pronunciationRuleName: String?,
        val orthographyRule: RuleRefViewModel?,
        val syllableStructures: List<String>,
        val pos: String,
        val grammaticalCategories: String,
        val wordClasses: String,
        val dictionarySettings: String?,
        val accentTypes: List<String>
    )

    @Serializable
    data class LanguageShortViewModel(
        val name: String,
        val shortName: String,
        val protoLanguageShortName: String? = null,
        val reconstructed: Boolean = false,
        val pos: List<WordCategoryValueViewModel>,
        val grammaticalCategories: List<WordCategoryViewModel>,
        val wordClasses: List<WordCategoryViewModel>,
        val dictionaries: List<String>,
        val hasReconstructedWords: Boolean = false,
        var descendantLanguages: List<LanguageShortViewModel>? = null
    )

    @GetMapping("/{graph}/language")
    fun indexJson(graph: Graph): List<LanguageShortViewModel> {
        return graph.allLanguages().sortedBy { it.name }.map { lang ->
            lang.toShortViewModel()
        }
    }

    @GetMapping("/{graph}/languages")
    fun treeJson(graph: Graph): List<LanguageShortViewModel> {
        val languages = graph.allLanguages()
        val shortViewModels = languages.associateBy(
            { it.shortName },
            { it.toShortViewModel() }
        ).toMutableMap()

        // Build a map from protoLanguageShortName to list of descendant languages
        val protoToDescendants = mutableMapOf<String, MutableList<LanguageShortViewModel>>()
        for (lang in languages) {
            val proto = lang.protoLanguage?.shortName
            if (proto != null) {
                protoToDescendants.getOrPut(proto) { mutableListOf() }.add(shortViewModels[lang.shortName]!!)
            }
        }

        // Set descendantLanguages for each language
        for ((protoShortName, descendants) in protoToDescendants) {
            shortViewModels[protoShortName]?.descendantLanguages = descendants.sortedBy { it.name }
        }

        // Return only top-level languages (those with no proto language)
        return languages
            .filter { it.protoLanguage == null }
            .map { shortViewModels[it.shortName]!! }
            .sortedBy { it.name }
    }

    private fun Language.toShortViewModel(): LanguageShortViewModel =
        LanguageShortViewModel(
            name,
            shortName,
            protoLanguage?.shortName,
            reconstructed,
            pos.map { WordCategoryValueViewModel(it.name, it.abbreviation) },
            grammaticalCategories.map {
                WordCategoryViewModel(it.name, it.pos, it.values.map {
                    WordCategoryValueViewModel(it.name, it.abbreviation)
                })
            },
            wordClasses.map {
                WordCategoryViewModel(it.name, it.pos, it.values.map {
                    WordCategoryValueViewModel(it.name, it.abbreviation)
                })  
            },
            if ("wiktionary-id" in (dictionarySettings ?: "")) listOf("wiktionary") else emptyList(),
            graph.allWords(this).any { it.reconstructed },
            null
        )
    
    @GetMapping("/{graph}/language/{lang}")
    fun language(graph: Graph, @PathVariable lang: String): LanguageViewModel {
        val language = graph.resolveLanguage(lang)
        return language.toViewModel()
    }

    private fun Language.toViewModel(): LanguageViewModel {
        val stressRule = stressRule?.resolve()
        val phonotacticsRule = phonotacticsRule?.resolve()
        val pronunciationRule = pronunciationRule?.resolve()
        val orthographyRule = orthographyRule?.resolve()
        return LanguageViewModel(
            name,
            shortName,
            protoLanguage?.shortName,
            reconstructed,
            syllabographic,
            diphthongs,
            PhonemeTable.build(phonemes).map { table ->
                PhonemeTableViewModel(table.title, table.columnTitles, table.rows.map { row ->
                    PhonemeTableRowViewModel(row.title, row.columns.map { cell ->
                        PhonemeTableCellViewModel(cell.phonemes.map { it.toViewModel(this)})
                    })
                })
            },
            stressRule?.id,
            stressRule?.name,
            phonotacticsRule?.id,
            phonotacticsRule?.name,
            pronunciationRule?.id,
            pronunciationRule?.name,
            orthographyRule?.toRefViewModel(),
            syllableStructures,
            pos.valuesToEditableText(),
            grammaticalCategories.toEditableText(),
            wordClasses.toEditableText(),
            dictionarySettings,
            accentTypes.map { it.name }
        )
    }

    data class UpdateLanguageParameters(
        val name: String? = null,
        val shortName: String? = null,
        val protoLanguageShortName: String? = null,
        val reconstructed: Boolean? = null,
        val syllabographic: Boolean? = null,
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
        val dictionarySettings: String? = null,
        val accentTypes: List<String>? = null
    )

    @PostMapping("/{graph}/languages", consumes = ["application/json"])
    fun addLanguage(graph: Graph, @RequestBody params: UpdateLanguageParameters): LanguageViewModel {
        val name = params.name.takeIf { !it.isNullOrBlank() } ?: badRequest("Language name must be provided")
        val shortName = params.shortName.takeIf { !it.isNullOrBlank() } ?: badRequest("Language short name must be provided")
        val language = graph.addLanguage(name, shortName)
        updateLanguageDetails(language, params)
        return language.toViewModel()
    }

    @PostMapping("/{graph}/language/{lang}", consumes = ["application/json"])
    fun updateLanguage(graph: Graph, @PathVariable lang: String, @RequestBody params: UpdateLanguageParameters) {
        val language = graph.resolveLanguage(lang)
        updateLanguageDetails(language, params)
    }

    data class CopyPhonemesParams(val fromLang: String = "")

    @PostMapping("/{graph}/language/{lang}/copyPhonemes", consumes = ["application/json"])
    fun copyPhonemes(graph: Graph, @PathVariable lang: String, @RequestBody params: CopyPhonemesParams) {
        val toLanguage = graph.resolveLanguage(lang)
        val fromLanguage = graph.resolveLanguage(params.fromLang)
        for (phoneme in fromLanguage.phonemes) {
            if (toLanguage.phonemes.none { phoneme.graphemes.intersect(it.graphemes).isNotEmpty() }) {
                graph.addPhoneme(toLanguage, phoneme.graphemes, phoneme.sound, phoneme.classes)
            }
        }
    }

    data class InputAssistGraphemeViewModel(val text: String, val languages: List<String>)
    data class InputAssistViewModel(val graphemes: List<InputAssistGraphemeViewModel>)

    @GetMapping("/{graph}/inputAssist")
    fun inputAssist(graph: Graph): InputAssistViewModel {
        val graphemes = mutableMapOf<String, MutableList<String>>()
        for (language in graph.allLanguages()) {
            for (phoneme in language.phonemes) {
                val grapheme = phoneme.graphemes.firstOrNull() ?: continue
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
        language: Language,
        params: UpdateLanguageParameters
    ) {
        val gr = language.graph
        if (params.reconstructed != null) {
            language.reconstructed = params.reconstructed
        }

        if (params.syllabographic != null) {
            language.syllabographic = params.syllabographic
        }

        language.protoLanguage = params.protoLanguageShortName?.let { gr.resolveLanguage(it) }

        language.diphthongs = parseList(params.diphthongs)
        language.syllableStructures = parseList(params.syllableStructures)
        val pos = params.pos.nullize()?.let { parseWordCategoryValues(it) } ?: mutableListOf()
        val wordClasses = params.wordClasses.nullize()?.let { parseWordCategories(it) } ?: mutableListOf()
        validateUniqueWordClassAndPosAbbreviations(pos, wordClasses)
        language.pos = pos
        language.grammaticalCategories = params.grammaticalCategories.nullize()?.let { parseWordCategories(it) } ?: mutableListOf()
        language.wordClasses = wordClasses

        language.stressRule = parseRuleRef(gr, params.stressRuleName)
        language.phonotacticsRule = parseRuleRef(gr, params.phonotacticsRuleName)
        language.pronunciationRule = parseRuleRef(gr, params.pronunciationRuleName)
        language.orthographyRule = parseRuleRef(gr, params.orthographyRuleName)

        language.dictionarySettings = params.dictionarySettings
        language.accentTypes = params.accentTypes?.mapTo(mutableSetOf()) {
            AccentType.valueOf(it)
        } ?: mutableSetOf()
    }

    private fun validateUniqueWordClassAndPosAbbreviations(
        pos: List<WordCategoryValue>,
        wordClasses: List<WordCategory>
    ) {
        val abbreviations = mutableSetOf<String>()
        val duplicateAbbreviation = (pos.asSequence() + wordClasses.asSequence().flatMap { it.values.asSequence() })
            .firstOrNull { !abbreviations.add(it.abbreviation) }
            ?.abbreviation
        if (duplicateAbbreviation != null) {
            badRequest("Duplicate word class or part of speech abbreviation '$duplicateAbbreviation'")
        }
    }

    private fun parseRuleRef(graph: Graph, name: String?): RuleRef? {
        val rule = name?.nullize()?.let { graph.resolveRule(it) }
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
