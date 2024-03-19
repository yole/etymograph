package ru.yole.etymograph.web

import org.springframework.web.bind.annotation.*
import ru.yole.etymograph.GraphRepository
import ru.yole.etymograph.Language
import ru.yole.etymograph.Phoneme

data class PhonemeViewModel(
    val id: Int,
    val languageShortName: String,
    val languageFullName: String,
    val graphemes: List<String>,
    val sound: String,
    val classes: String,
    val source: List<SourceRefViewModel>,
    val sourceEditableText: String,
    val notes: String?
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
        val source: String? = null,
        val notes: String? = null
    )

    @PostMapping("/phonemes/{lang}", consumes = ["application/json"])
    fun addPhoneme(@PathVariable lang: String, @RequestBody params: UpdatePhonemeParameters): PhonemeViewModel {
        val language = graphService.resolveLanguage(lang)
        val phoneme = graphService.graph.addPhoneme(
            language,
            parseList(params.graphemes),
            params.sound.nullize(),
            params.classes.split(' ').toSet(),
            parseSourceRefs(graphService.graph, params.source),
            params.notes
        )
        graphService.graph.save()
        return phoneme.toViewModel(graphService.graph, language)
    }

    @PostMapping("/phoneme/{id}", consumes = ["application/json"])
    fun updatePhoneme(@PathVariable id: Int, @RequestBody params: UpdatePhonemeParameters) {
        val phoneme = resolvePhoneme(id)
        phoneme.graphemes = parseList(params.graphemes)
        phoneme.sound = params.sound.nullize()
        phoneme.classes = params.classes.split(' ').toSet()
        phoneme.source = parseSourceRefs(graphService.graph, params.source)
        phoneme.notes = params.notes
        graphService.graph.save()
    }

    @PostMapping("/phoneme/{id}/delete", consumes = ["application/json"])
    fun deletePhoneme(@PathVariable id: Int) {
        val phoneme = resolvePhoneme(id)
        val language = findLanguage(phoneme, id)
        graphService.graph.deletePhoneme(language, phoneme)
        graphService.graph.save()
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
        source.toViewModel(graph),
        source.toEditableText(graph),
        notes
    )
}
