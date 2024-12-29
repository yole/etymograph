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
    fun phonemes(repo: GraphRepository): List<PhonemeViewModel> {
        return repo.allLanguages().flatMap { language ->
            language.phonemes.map { it.toViewModel(repo, language) }
        }
    }

    @GetMapping("/{graph}/phoneme/{id}")
    fun phoneme(repo: GraphRepository, @PathVariable id: Int): PhonemeViewModel {
        val phoneme = repo.resolvePhoneme(id)
        val lang = findLanguage(repo, phoneme, id)
        return phoneme.toViewModel(repo, lang)
    }

    private fun findLanguage(repo: GraphRepository, phoneme: Phoneme, id: Int): Language {
        return repo.allLanguages().find { phoneme in it.phonemes }
            ?: badRequest("Phoneme with id $id is not associated with any language")
    }

    private fun GraphRepository.resolvePhoneme(id: Int): Phoneme = (langEntityById(id) as? Phoneme
        ?: badRequest("Phoneme with id $id not found"))

    data class UpdatePhonemeParameters(
        val graphemes: String,
        val sound: String,
        val classes: String,
        val historical: Boolean = false,
        val source: String? = null,
        val notes: String? = null
    )

    @PostMapping("/{graph}/phonemes/{lang}", consumes = ["application/json"])
    fun addPhoneme(repo: GraphRepository, @PathVariable lang: String, @RequestBody params: UpdatePhonemeParameters): PhonemeViewModel {
        val language = repo.resolveLanguage(lang)

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
        val phoneme = repo.addPhoneme(
            language,
            graphemes,
            sound,
            classes,
            params.historical,
            parseSourceRefs(repo, params.source),
            params.notes
        )
        return phoneme.toViewModel(repo, language)
    }

    @PostMapping("/{graph}/phoneme/{id}", consumes = ["application/json"])
    fun updatePhoneme(repo: GraphRepository, @PathVariable id: Int, @RequestBody params: UpdatePhonemeParameters) {
        val phoneme = repo.resolvePhoneme(id)
        phoneme.graphemes = parseList(params.graphemes)
        phoneme.sound = params.sound.nullize()
        phoneme.classes = parseClasses(params)
        phoneme.historical = params.historical
        phoneme.source = parseSourceRefs(repo, params.source)
        phoneme.notes = params.notes
    }

    private fun parseClasses(params: UpdatePhonemeParameters) =
        params.classes.trim().takeIf { it.isNotEmpty() }?.split(' ')?.toSet() ?: emptySet()

    @PostMapping("/{graph}/phoneme/{id}/delete")
    fun deletePhoneme(repo: GraphRepository, @PathVariable id: Int) {
        val phoneme = repo.resolvePhoneme(id)
        val language = findLanguage(repo, phoneme, id)
        repo.deletePhoneme(language, phoneme)
    }

    data class ComparePhonemesParameters(
        val toPhoneme: String = ""
    )

    data class ComparePhonemesResult(
        val message: String
    )

    @PostMapping("/{graph}/phoneme/{id}/compare", consumes = ["application/json"])
    fun comparePhonemes(repo: GraphRepository, @PathVariable id: Int, @RequestBody params: ComparePhonemesParameters): ComparePhonemesResult {
        val phoneme = repo.resolvePhoneme(id)
        val language = findLanguage(repo, phoneme, id)
        val targetPhoneme = language.phonemes.find { it.effectiveSound == params.toPhoneme }
            ?: badRequest("Phoneme with sound ${params.toPhoneme} not found")

        val result = language.comparePhonemes(phoneme, targetPhoneme)
        return ComparePhonemesResult(result.joinToString())
    }
}

fun Phoneme.toViewModel(graph: GraphRepository, language: Language): PhonemeViewModel {
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
            PhonemeRuleViewModel(it.id, it.name, it.toSummaryText(graph))
        }
    )
}
