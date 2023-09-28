package ru.yole.etymograph.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.yole.etymograph.*

data class WordRefViewModel(
    val id: Int,
    val text: String,
    val language: String,
    val gloss: String?,
    val homonym: Boolean
)

data class ParseCandidateViewModel(
    val text: String,
    val categories: String,
    val ruleNames: List<String>,
    val pos: String?,
    val wordId: Int?
)

fun Word.toRefViewModel(graph: GraphRepository) =
    WordRefViewModel(id, text, language.shortName, getOrComputeGloss(graph), graph.isHomonym(this))

@RestController
class WordController(val graphService: GraphService) {
    data class LinkWordViewModel(
        val word: WordRefViewModel,
        val ruleIds: List<Int>,
        val ruleNames: List<String>,
    )

    data class LinkTypeViewModel(val typeId: String, val type: String, val words: List<LinkWordViewModel>)

    data class AttestationViewModel(val textId: Int, val textTitle: String, val word: String?)

    data class CompoundComponentsViewModel(val compoundId: Int, val components: List<WordRefViewModel>)

    data class WordViewModel(
        val id: Int,
        val language: String,
        val languageFullName: String,
        val text: String,
        val gloss: String,
        val glossComputed: Boolean,
        val fullGloss: String?,
        val pos: String?,
        val classes: List<String>,
        val source: List<SourceRefViewModel>,
        val sourceEditableText: String,
        val notes: String?,
        val parseCandidates: List<ParseCandidateViewModel>,
        val attestations: List<AttestationViewModel>,
        val linksFrom: List<LinkTypeViewModel>,
        val linksTo: List<LinkTypeViewModel>,
        val compounds: List<WordRefViewModel>,
        val components: List<CompoundComponentsViewModel>,
        val stressIndex: Int?,
        val stressLength: Int?,
        val compound: Boolean
    )

    @GetMapping("/word/{lang}/{text}")
    fun wordJson(@PathVariable lang: String, @PathVariable text: String): List<WordViewModel> {
        val graph = graphService.graph

        val language = graphService.resolveLanguage(lang)

        val words = graph.wordsByText(language, text)
        if (words.isEmpty())
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No word with text $text")

        return words.map { it.toViewModel(graph) }
    }

    @GetMapping("/word/{lang}/{text}/{id}")
    fun singleWordJson(@PathVariable lang: String, @PathVariable text: String, @PathVariable id: Int): WordViewModel {
        return graphService.resolveWord(id).toViewModel(graphService.graph)
    }

    private fun Word.toViewModel(graph: GraphRepository): WordViewModel {
        val linksFrom = graph.getLinksFrom(this).groupBy { it.type }
        val linksTo = graph.getLinksTo(this).groupBy { it.type }
        val attestations = graph.findAttestations(this)

        val stressData = calculateStress()

        val computedGloss = getOrComputeGloss(graph)
        return WordViewModel(
            id,
            language.shortName,
            language.name,
            text,
            computedGloss ?: "",
            gloss == null,
            fullGloss,
            pos,
            classes,
            source.toViewModel(graph),
            source.toEditableText(graph),
            notes,
            if (computedGloss == null && linksFrom[Link.Derived].isNullOrEmpty())
                graph.findParseCandidates(this).map { it.toViewModel() }
            else
                emptyList(),
            attestations.map { attestation ->
                AttestationViewModel(
                    attestation.corpusText.id,
                    attestation.corpusText.title ?: attestation.corpusText.text,
                    attestation.word.text.takeIf { it != text }
                )
            },
            linksFrom.map {
                LinkTypeViewModel(
                    it.key.id,
                    it.key.name,
                    it.value.filter { link -> link.toEntity is Word }.map { link ->
                        val toWord = link.toEntity as Word
                        LinkWordViewModel(toWord.toRefViewModel(graph),
                            link.rules.map { it.id },
                            link.rules.map { it.name }
                        )
                    }
                )
            },
            linksTo.map {
                LinkTypeViewModel(
                    it.key.id,
                    it.key.reverseName,
                    it.value.filter { link -> link.fromEntity is Word }.map { link ->
                        val fromWord = link.fromEntity as Word
                        LinkWordViewModel(fromWord.toRefViewModel(graph),
                            link.rules.map { it.id },
                            link.rules.map { it.name }
                        )
                    }
                )
            },
            graph.findCompoundsByComponent(this).map { it.compoundWord.toRefViewModel(graph) },
            graph.findComponentsByCompound(this).map { compound ->
                CompoundComponentsViewModel(compound.id, compound.components.map { it.toRefViewModel(graph) })
            },
            stressData?.index,
            stressData?.length,
            graph.isCompound(this)
        )
    }

    data class AddWordParameters(
        val text: String?,
        val gloss: String?,
        val fullGloss: String?,
        val posClasses: String?,
        val source: String?,
        val notes: String?
    )

    @PostMapping("/word/{lang}", consumes = ["application/json"])
    @ResponseBody
    fun addWord(@PathVariable lang: String, @RequestBody params: AddWordParameters): WordViewModel {
        val graph = graphService.graph
        val language = graphService.resolveLanguage(lang)
        val text = params.text?.nullize() ?: throw NoWordTextException()

        val posClassList = params.posClasses.orEmpty().split(' ')

        val word = graph.findOrAddWord(
            text, language,
            params.gloss.nullize(),
            params.fullGloss.nullize(),
            posClassList.firstOrNull().nullize(),
            posClassList.drop(1),
            parseSourceRefs(graph, params.source),
            params.notes.nullize()
        )
        graph.save()
        return word.toViewModel(graph)
    }

    @PostMapping("/word/{id}/update", consumes = ["application/json"])
    @ResponseBody
    fun updateWord(@PathVariable id: Int, @RequestBody params: AddWordParameters): WordViewModel {
        val graph = graphService.graph
        val word = graph.wordById(id) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No word with ID $id")
        val text = params.text
        if (text != null && text != word.text) {
            graph.updateWordText(word, text)
        }

        val posClassList = params.posClasses.orEmpty().split(' ')

        word.gloss = params.gloss.nullize()
        word.fullGloss = params.fullGloss.nullize()
        word.pos = posClassList.firstOrNull().nullize()
        word.classes = posClassList.drop(1)
        word.source = parseSourceRefs(graph, params.source)
        word.notes = params.notes.nullize()
        graph.save()
        return word.toViewModel(graph)
    }

    @PostMapping("/word/{id}/delete", consumes = ["application/json"])
    @ResponseBody
    fun deleteWord(@PathVariable id: Int) {
        val graph = graphService.graph
        val word = graph.wordById(id) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No word with ID $id")
        graph.deleteWord(word)
        graph.save()
    }

    data class WordParadigmWordModel(
        val word: String,
        val wordId: Int
    )

    data class WordParadigmModel(
        val title: String,
        val rowTitles: List<String>,
        val columnTitles: List<String>,
        val cells: List<List<List<WordParadigmWordModel>>>
    )

    data class WordParadigmListModel(
        val word: String,
        val language: String,
        val languageFullName: String,
        val paradigms: List<WordParadigmModel>
    )

    @GetMapping("/word/{id}/paradigms")
    fun wordParadigms(@PathVariable id: Int): WordParadigmListModel {
        val graph = graphService.graph
        val word = graphService.resolveWord(id)
        val paradigmModels = graph.paradigmsForLanguage(word.language).filter { it.pos == word.pos }.map { paradigm ->
            val generatedParadigm = paradigm.generate(word, graph)
            val substitutedParadigm = generatedParadigm.map { colWords ->
                colWords.map { cellWords ->
                    cellWords?.map { cellWord ->
                        WordParadigmWordModel(cellWord.text, cellWord.id)
                    } ?: emptyList()
                }
            }
            WordParadigmModel(
                paradigm.name,
                paradigm.rowTitles,
                paradigm.columns.map { col -> col.title },
                substitutedParadigm
            )
        }
        return WordParadigmListModel(word.text, word.language.shortName, word.language.name, paradigmModels)
    }
}

@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Word text not specified")
class NoWordTextException : RuntimeException()

fun String?.nullize() = this?.takeIf { it.trim().isNotEmpty() }

fun ParseCandidate.toViewModel(): ParseCandidateViewModel =
    ParseCandidateViewModel(
        text,
        rules.fold("") { t, rule -> t + rule.addedCategories },
        rules.map { it.name },
        pos,
        word?.id
    )
