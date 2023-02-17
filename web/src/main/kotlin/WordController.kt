package ru.yole.etymograph.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.yole.etymograph.GraphRepository
import ru.yole.etymograph.Word

@RestController
class WordController(val graphService: GraphService) {
    data class LinkWordViewModel(
        val id: Int,
        val text: String,
        val language: String,
        val gloss: String?,
        val ruleIds: List<Int>,
        val ruleNames: List<String>,
        val homonym: Boolean
    )

    data class LinkTypeViewModel(val typeId: String, val type: String, val words: List<LinkWordViewModel>)

    data class AttestationViewModel(val textId: Int, val textTitle: String, val word: String?)

    data class WordViewModel(
        val id: Int,
        val language: String,
        val languageFullName: String,
        val text: String,
        val gloss: String,
        val glossComputed: Boolean,
        val fullGloss: String?,
        val pos: String?,
        val source: String?,
        val notes: String?,
        val attestations: List<AttestationViewModel>,
        val linksFrom: List<LinkTypeViewModel>,
        val linksTo: List<LinkTypeViewModel>
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
        return WordViewModel(
            id,
            language.shortName,
            language.name,
            text,
            getOrComputeGloss(graph) ?: "",
            gloss == null,
            fullGloss,
            pos,
            source,
            notes,
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
                    it.value.map { link ->
                        LinkWordViewModel(link.toWord.id, link.toWord.text, link.toWord.language.shortName,
                            link.toWord.getOrComputeGloss(graph),
                            link.rules.map { it.id },
                            link.rules.map { it.name },
                            graph.isHomonym(link.toWord)
                        )
                    }
                )
            },
            linksTo.map {
                LinkTypeViewModel(
                    it.key.id,
                    it.key.reverseName,
                    it.value.map { link ->
                        LinkWordViewModel(link.fromWord.id, link.fromWord.text, link.fromWord.language.shortName,
                            link.fromWord.getOrComputeGloss(graph),
                            link.rules.map { it.id },
                            link.rules.map { it.name },
                            graph.isHomonym(link.fromWord)
                        )
                    }
                )
            }
        )
    }

    data class AddWordParameters(
        val text: String?,
        val gloss: String?,
        val fullGloss: String?,
        val pos: String?,
        val source: String?,
        val notes: String?
    )

    @PostMapping("/word/{lang}", consumes = ["application/json"])
    @ResponseBody
    fun addWord(@PathVariable lang: String, @RequestBody params: AddWordParameters): WordViewModel {
        val graph = graphService.graph
        val language = graphService.resolveLanguage(lang)
        val text = params.text?.nullize() ?: throw NoWordTextException()

        val word = graph.addWord(
            text, language,
            params.gloss.nullize(),
            params.fullGloss.nullize(),
            params.pos.nullize(),
            params.source.nullize(),
            params.notes.nullize()
        )
        graph.save()
        return word.toViewModel(graph)
    }

    @PostMapping("/word/{id}/update", consumes = ["application/json"])
    @ResponseBody
    fun updateWord(@PathVariable id: Int, @RequestBody params: AddWordParameters): WordViewModel {
        val graph = graphService.graph
        val word = graph.wordById(id) ?: throw NoWordException()
        word.gloss = params.gloss.nullize()
        word.fullGloss = params.fullGloss.nullize()
        word.pos = params.pos.nullize()
        word.source = params.source.nullize()
        word.notes = params.notes.nullize()
        graph.save()
        return word.toViewModel(graph)
    }

    @PostMapping("/word/{id}/delete", consumes = ["application/json"])
    @ResponseBody
    fun deleteWord(@PathVariable id: Int) {
        val graph = graphService.graph
        val word = graph.wordById(id) ?: throw NoWordException()
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

    @GetMapping("/word/{id}/paradigms")
    fun wordParadigms(@PathVariable id: Int): List<WordParadigmModel> {
        val graph = graphService.graph
        val word = graph.wordById(id) ?: throw NoWordException()
        return graph.paradigmsForLanguage(word.language).filter { it.pos == word.pos }.map { paradigm ->
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
    }
}

@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "No such word")
class NoWordException : RuntimeException()

@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Word text not specified")
class NoWordTextException : RuntimeException()

fun String?.nullize() = this?.takeIf { it.trim().isNotEmpty() }
