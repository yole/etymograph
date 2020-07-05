package ru.yole.etymograph.web

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseStatus
import ru.yole.etymograph.UnknownLanguage

@Controller
class CorpusController(val graphService: GraphService) {
    @GetMapping("/corpus")
    fun index(model: Model): String {
        model.addAttribute("languages", graphService.graph.allLanguages())
        return "corpus/index"
    }

    @GetMapping("/corpus/{lang}")
    fun langIndex(@PathVariable lang: String, model: Model): String {
        val language = graphService.graph.languageByShortName(lang)
        if (language == UnknownLanguage) throw NoLanguageException()

        model.addAttribute("language", language)
        model.addAttribute("texts", graphService.graph.corpusTextsInLanguage(language))
        return "corpus/langIndex"
    }

    @GetMapping("/corpus/text/{id}")
    fun text(@PathVariable id: Int, model: Model): String {
        val text = graphService.graph.corpusTextById(id) ?: throw NoCorpusTextException()
        model.addAttribute("text", text)
        model.addAttribute("lines", text.mapToLines())
        return "corpus/text"
    }
}

@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "No such language")
class NoLanguageException : RuntimeException()

@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "No such corpus text")
class NoCorpusTextException : RuntimeException()
