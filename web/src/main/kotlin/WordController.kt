package ru.yole.etymograph.web

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import ru.yole.etymograph.GraphRepository
import ru.yole.etymograph.UnknownLanguage
import ru.yole.etymograph.Word

@Controller
class WordController(val graphService: GraphService) {
    @GetMapping("/word/{lang}/{text}")
    fun word(@PathVariable lang: String, @PathVariable text: String, model: Model): String {
        val graph = graphService.graph
        val word = findWord(graph, lang, text)
        model.addAttribute("word", word)
        model.addAttribute("gloss", word.getOrComputeGloss(graph))
        val linksFrom = graph.getLinksFrom(word).groupBy { it.type }
        val linksTo = graph.getLinksTo(word).groupBy { it.type }
        model.addAttribute("linksFrom", linksFrom)
        model.addAttribute("linksTo", linksTo)
        return "word/index"
    }

    data class LinkWordViewModel(val id: Int, val text: String, val language: String, val ruleIds: List<Int>)
    data class LinkTypeViewModel(val typeId: String, val type: String, val words: List<LinkWordViewModel>)
    data class WordViewModel(
        val id: Int,
        val language: String,
        val text: String,
        val gloss: String,
        val glossComputed: Boolean,
        val pos: String?,
        val source: String?,
        val notes: String?,
        val linksFrom: List<LinkTypeViewModel>,
        val linksTo: List<LinkTypeViewModel>
    )

    @GetMapping("/word/{lang}/{text}", produces = ["application/json"])
    @ResponseBody
    fun wordJson(@PathVariable lang: String, @PathVariable text: String): WordViewModel {
        val graph = graphService.graph
        val word = findWord(graph, lang, text)
        return word.toViewModel(graph)
    }

    private fun Word.toViewModel(graph: GraphRepository): WordViewModel {
        val linksFrom = graph.getLinksFrom(this).groupBy { it.type }
        val linksTo = graph.getLinksTo(this).groupBy { it.type }
        return WordViewModel(
            id,
            language.shortName,
            text,
            getOrComputeGloss(graph) ?: "",
            gloss == null,
            pos,
            source,
            notes,
            linksFrom.map {
                LinkTypeViewModel(
                    it.key.id,
                    it.key.name,
                    it.value.map { link ->
                        LinkWordViewModel(link.toWord.id, link.toWord.text, link.toWord.language.shortName, link.rules.map { it.id })
                    }
                )
            },
            linksTo.map {
                LinkTypeViewModel(
                    it.key.id,
                    it.key.reverseName,
                    it.value.map { link ->
                        LinkWordViewModel(link.fromWord.id, link.fromWord.text, link.fromWord.language.shortName, link.rules.map { it.id })
                    }
                )
            }
        )
    }

    private fun findWord(
        graph: GraphRepository,
        lang: String,
        text: String
    ): Word {
        val language = graph.languageByShortName(lang)
        if (language == UnknownLanguage) throw NoLanguageException()

        val words = graph.wordsByText(language, text)
        if (words.isEmpty()) throw NoWordException()
        return words.single()
    }

    data class AddWordParameters(
        val text: String?,
        val gloss: String?,
        val pos: String?,
        val source: String?,
        val notes: String?
    )

    @PostMapping("/word/{lang}", consumes = ["application/json"])
    @ResponseBody
    fun addWord(@PathVariable lang: String, @RequestBody params: AddWordParameters): WordViewModel {
        val graph = graphService.graph
        val language = graph.languageByShortName(lang)
        if (language == UnknownLanguage) throw NoLanguageException()
        val text = params.text?.nullize() ?: throw NoWordTextException()

        val word = graph.addWord(
            text, language,
            params.gloss.nullize(),
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

    data class WordParadigmCellModel(
        val word: String,
        val wordId: Int
    )

    data class WordParadigmModel(
        val title: String,
        val rowTitles: List<String>,
        val columnTitles: List<String>,
        val cells: List<List<WordParadigmCellModel>>
    )

    @GetMapping("/word/{id}/paradigms", produces = ["application/json"])
    @ResponseBody
    fun wordParadigms(@PathVariable id: Int): List<WordParadigmModel> {
        val graph = graphService.graph
        val word = graph.wordById(id) ?: throw NoWordException()
        return graph.paradigmsForLanguage(word.language).filter { it.pos == word.pos }.map { paradigm ->
            val generatedParadigm = paradigm.generate(word)
            val substitutedParadigm = generatedParadigm.map { colWords ->
                colWords.map { cellWord ->
                    val actualWord = cellWord?.let { graph.substituteKnownWord(word, it) }
                    WordParadigmCellModel(actualWord?.text ?: "", actualWord?.id ?: -1)
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
