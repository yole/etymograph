package ru.yole.etymograph.web

import org.springframework.web.bind.annotation.*
import ru.yole.etymograph.GraphRepository
import ru.yole.etymograph.Language
import ru.yole.etymograph.Phoneme
import ru.yole.etymograph.RuleSequence

data class PhonemeRuleViewModel(
    val id: Int,
    val name: String,
    val summary: String
)

data class PhonemeRuleGroupViewModel(
    val title: String,
    val rules: List<PhonemeRuleViewModel>
)

data class PhonemeViewModel(
    val id: Int,
    val languageShortName: String,
    val languageFullName: String,
    val graphemes: List<String>,
    val sound: String,
    val classes: String,
    val historical: Boolean,
    val source: List<SourceRefViewModel>,
    val sourceEditableText: String,
    val notes: String?,
    val relatedRules: List<PhonemeRuleGroupViewModel>
)

@RestController
class PhonemeController(val graphService: GraphService) {
    @GetMapping("/phonemes")
    fun phonemes(): List<PhonemeViewModel> {
        return graphService.graph.allLanguages().flatMap { language ->
            language.phonemes.map { it.toViewModel(graphService.graph, language) }
        }
    }

    @GetMapping("/phoneme/{id}")
    fun phoneme(@PathVariable id: Int): PhonemeViewModel {
        val phoneme = resolvePhoneme(id)
        val lang = findLanguage(phoneme, id)
        return phoneme.toViewModel(graphService.graph, lang)
    }

    private fun findLanguage(phoneme: Phoneme, id: Int): Language {
        return graphService.graph.allLanguages().find { phoneme in it.phonemes }
            ?: badRequest("Phoneme with id $id is not associated with any language")
    }

    private fun resolvePhoneme(id: Int) = (graphService.graph.langEntityById(id) as? Phoneme
        ?: badRequest("Phoneme with id $id not found"))

    data class UpdatePhonemeParameters(
        val graphemes: String,
        val sound: String,
        val classes: String,
        val historical: Boolean = false,
        val source: String? = null,
        val notes: String? = null
    )

    @PostMapping("/phonemes/{lang}", consumes = ["application/json"])
    fun addPhoneme(@PathVariable lang: String, @RequestBody params: UpdatePhonemeParameters): PhonemeViewModel {
        val language = graphService.resolveLanguage(lang)

        val graphemes = parseList(params.graphemes)
        for (phoneme in language.phonemes) {
            val existingGraphemes = graphemes.intersect(phoneme.graphemes.toSet())
            if (existingGraphemes.isNotEmpty()) {
                badRequest("Duplicate graphemes $existingGraphemes")
            }
        }

        val phoneme = graphService.graph.addPhoneme(
            language,
            graphemes,
            params.sound.nullize(),
            parseClasses(params),
            params.historical,
            parseSourceRefs(graphService.graph, params.source),
            params.notes
        )
        return phoneme.toViewModel(graphService.graph, language)
    }

    @PostMapping("/phoneme/{id}", consumes = ["application/json"])
    fun updatePhoneme(@PathVariable id: Int, @RequestBody params: UpdatePhonemeParameters) {
        val phoneme = resolvePhoneme(id)
        phoneme.graphemes = parseList(params.graphemes)
        phoneme.sound = params.sound.nullize()
        phoneme.classes = parseClasses(params)
        phoneme.historical = params.historical
        phoneme.source = parseSourceRefs(graphService.graph, params.source)
        phoneme.notes = params.notes
    }

    private fun parseClasses(params: UpdatePhonemeParameters) =
        params.classes.trim().split(' ').toSet()

    @PostMapping("/phoneme/{id}/delete", consumes = ["application/json"])
    fun deletePhoneme(@PathVariable id: Int) {
        val phoneme = resolvePhoneme(id)
        val language = findLanguage(phoneme, id)
        graphService.graph.deletePhoneme(language, phoneme)
    }
}

fun Phoneme.toViewModel(graph: GraphRepository, language: Language): PhonemeViewModel {
    return PhonemeViewModel(
        id,
        language.shortName,
        language.name,
        graphemes,
        sound ?: "",
        classes.joinToString(" "),
        historical,
        source.toViewModel(graph),
        source.toEditableText(graph),
        notes,
        findRelatedRules(graph, language, this)
    )
}

fun findRelatedRules(graph: GraphRepository, language: Language, phoneme: Phoneme): List<PhonemeRuleGroupViewModel> {
    val seqFromLanguage = graph.ruleSequencesFromLanguage(language)
    val seqToLanguage = graph.ruleSequencesForLanguage(language)

    val developmentGroups = seqFromLanguage.groupBy { it.toLanguage }.map { (language, sequences) ->
        buildPhonemeRuleGroup(graph, "Development: ${language.name}", phoneme, sequences)
    }
    val groups = if (seqToLanguage.isNotEmpty()) {
        listOf(buildPhonemeRuleGroup(graph, "Origin", phoneme, seqToLanguage)) + developmentGroups
    }
    else {
        developmentGroups
    }
    return groups.filter { it.rules.isNotEmpty() }
}

fun buildPhonemeRuleGroup(graph: GraphRepository, title: String, phoneme: Phoneme, sequences: List<RuleSequence>): PhonemeRuleGroupViewModel {
    val rules = sequences.flatMapTo(mutableSetOf()) {
        it.resolveRules(graph).filter { rule ->
            rule.refersToPhoneme(phoneme)
        }
    }
    return PhonemeRuleGroupViewModel(
        title,
        rules.map {
            PhonemeRuleViewModel(it.id, it.name, it.toSummaryText())
        }
    )
}
