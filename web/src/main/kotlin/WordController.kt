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

    data class LinkWordViewModel(val text: String, val language: String, val ruleId: Int?)
    data class LinkTypeViewModel(val type: String, val words: List<LinkWordViewModel>)
    data class WordViewModel(
        val id: Int,
        val language: String,
        val text: String,
        val gloss: String,
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
            linksFrom.map {
                LinkTypeViewModel(
                    it.key.name,
                    it.value.map { link -> LinkWordViewModel(link.toWord.text, link.toWord.language.shortName, link.rule?.id) })
            },
            linksTo.map {
                LinkTypeViewModel(
                    it.key.reverseName,
                    it.value.map { link -> LinkWordViewModel(link.fromWord.text, link.fromWord.language.shortName, link.rule?.id) })
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

    data class AddWordParameters(val text: String, val gloss: String, val source: String)

    @PostMapping("/word/{lang}", consumes = ["application/json"])
    @ResponseBody
    fun addWord(@PathVariable lang: String, @RequestBody params: AddWordParameters): WordViewModel {
        val graph = graphService.graph
        val language = graph.languageByShortName(lang)
        if (language == UnknownLanguage) throw NoLanguageException()

        val word = graph.addWord(params.text, language, params.gloss, params.source, null)
        graph.save()
        return word.toViewModel(graph)
    }
}

@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "No such word")
class NoWordException : RuntimeException()
