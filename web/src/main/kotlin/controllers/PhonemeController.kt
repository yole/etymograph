package ru.yole.etymograph.web.controllers

import org.springframework.web.bind.annotation.*
import ru.yole.etymograph.*
import ru.yole.etymograph.web.*

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
    val implicitClasses: String,
    val features: String,
    val historical: Boolean,
    val source: List<SourceRefViewModel>,
    val sourceEditableText: String,
    val notes: String?,
    val relatedRules: List<PhonemeRuleGroupViewModel>
)

@RestController
class PhonemeController {
    @GetMapping("/{graph}/phonemes")
    fun phonemes(graph: Graph): List<PhonemeViewModel> {
        return graph.allLanguages().flatMap { language ->
            language.phonemes.map { it.toViewModel(language) }
        }
    }

    @GetMapping("/{graph}/phoneme/{id}")
    fun phoneme(graph: Graph, @PathVariable id: Int): PhonemeViewModel {
        val phoneme = graph.resolvePhoneme(id)
        val lang = findLanguage(graph, phoneme, id)
        return phoneme.toViewModel(lang)
    }

    private fun findLanguage(graph: Graph, phoneme: Phoneme, id: Int): Language {
        return graph.allLanguages().find { phoneme in it.phonemes }
            ?: badRequest("Phoneme with id $id is not associated with any language")
    }

    private fun Graph.resolvePhoneme(id: Int): Phoneme = (langEntityById(id) as? Phoneme
        ?: badRequest("Phoneme with id $id not found"))

    data class UpdatePhonemeParameters(
        val graphemes: String,
        val sound: String,
        val classes: String? = null,
        val historical: Boolean = false,
        val source: String? = null,
        val notes: String? = null
    )

    @PostMapping("/{graph}/phonemes/{lang}", consumes = ["application/json"])
    fun addPhoneme(graph: Graph, @PathVariable lang: String, @RequestBody params: UpdatePhonemeParameters): PhonemeViewModel {
        val language = graph.resolveLanguage(lang)

        val graphemes = parseList(params.graphemes)
        for (phoneme in language.phonemes) {
            val existingGraphemes = graphemes.intersect(phoneme.graphemes.toSet())
            if (existingGraphemes.isNotEmpty()) {
                badRequest("Duplicate graphemes $existingGraphemes")
            }
        }

        val sound = params.sound.nullize()
        val classes = parseClasses(params).ifEmpty {
            val effectiveSound = sound ?: graphemes.first()
            defaultPhonemeClasses[effectiveSound] ?: emptySet()
        }
        val phoneme = graph.addPhoneme(
            language,
            graphemes,
            sound,
            classes,
            params.historical,
            parseSourceRefs(graph, params.source),
            params.notes
        )
        return phoneme.toViewModel(language)
    }

    @PostMapping("/{graph}/phoneme/{id}", consumes = ["application/json"])
    fun updatePhoneme(graph: Graph, @PathVariable id: Int, @RequestBody params: UpdatePhonemeParameters) {
        val phoneme = graph.resolvePhoneme(id)
        phoneme.graphemes = parseList(params.graphemes)
        phoneme.sound = params.sound.nullize()
        phoneme.classes = parseClasses(params)
        phoneme.historical = params.historical
        phoneme.source = parseSourceRefs(graph, params.source)
        phoneme.notes = params.notes
        val language = findLanguage(graph, phoneme, id)
        language.updatePhonemes()
    }

    private fun parseClasses(params: UpdatePhonemeParameters) =
        params.classes.orEmpty().trim().takeIf { it.isNotEmpty() }?.split(' ')?.toSet() ?: emptySet()

    @PostMapping("/{graph}/phoneme/{id}/delete")
    fun deletePhoneme(graph: Graph, @PathVariable id: Int) {
        val phoneme = graph.resolvePhoneme(id)
        val language = findLanguage(graph, phoneme, id)
        graph.deletePhoneme(language, phoneme)
    }

    data class ComparePhonemesParameters(
        val toPhoneme: String = ""
    )

    data class ComparePhonemesResult(
        val message: String
    )

    @PostMapping("/{graph}/phoneme/{id}/compare", consumes = ["application/json"])
    fun comparePhonemes(graph: Graph, @PathVariable id: Int, @RequestBody params: ComparePhonemesParameters): ComparePhonemesResult {
        val phoneme = graph.resolvePhoneme(id)
        val language = findLanguage(graph, phoneme, id)
        val targetPhoneme = language.phonemes.find { it.effectiveSound == params.toPhoneme }
            ?: badRequest("Phoneme with sound ${params.toPhoneme} not found")

        val result = language.comparePhonemes(phoneme, targetPhoneme)
        return ComparePhonemesResult(result.joinToString())
    }
}

fun Phoneme.toViewModel(language: Language): PhonemeViewModel {
    val features = language.phonemeFeatures(this)
    return PhonemeViewModel(
        id,
        language.shortName,
        language.name,
        graphemes,
        sound ?: "",
        classes.joinToString(" "),
        (implicitPhonemeClasses(classes) - features).joinToString(" "),
        features.joinToString( " "),
        historical,
        source.toViewModel(language.graph),
        source.toEditableText(language.graph),
        notes,
        findRelatedRules(language, this)
    )
}

fun findRelatedRules(language: Language, phoneme: Phoneme): List<PhonemeRuleGroupViewModel> {
    val graph = language.graph
    val seqFromLanguage = graph.ruleSequencesFromLanguage(language)
    val seqToLanguage = graph.ruleSequencesForLanguage(language)

    val developmentGroups = seqFromLanguage.groupBy { it.toLanguage }.map { (language, sequences) ->
        buildPhonemeRuleGroup("Development: ${language.name}", phoneme, sequences)
    }
    val groups = if (seqToLanguage.isNotEmpty()) {
        listOf(buildPhonemeRuleGroup("Origin", phoneme, seqToLanguage)) + developmentGroups
    }
    else {
        developmentGroups
    }
    return groups.filter { it.rules.isNotEmpty() }
}

fun buildPhonemeRuleGroup(title: String, phoneme: Phoneme, sequences: List<RuleSequence>): PhonemeRuleGroupViewModel {
    val rules = sequences.flatMapTo(mutableSetOf()) {
        it.resolveRules().filter { rule ->
            rule.logic.refersToLangEntity(phoneme)
        }
    }
    return PhonemeRuleGroupViewModel(
        title,
        rules.map {
            PhonemeRuleViewModel(it.id, it.name, it.toSummaryText())
        }
    )
}
