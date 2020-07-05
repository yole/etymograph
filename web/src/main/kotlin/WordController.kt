package ru.yole.etymograph.web

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseStatus
import ru.yole.etymograph.UnknownLanguage

@Controller
class WordController(val graphService: GraphService) {
    @GetMapping("/word/{lang}/{text}")
    fun word(@PathVariable lang: String, @PathVariable text: String, model: Model): String {
        val graph = graphService.graph
        val language = graph.languageByShortName(lang)
        if (language == UnknownLanguage) throw NoLanguageException()

        val words = graph.wordsByText(language, text)
        if (words.isEmpty()) throw NoWordException()

        val word = words.single()
        model.addAttribute("word", word)
        val linksFrom = graph.getLinksFrom(word).groupBy { it.type }
        val linksTo = graph.getLinksTo(word).groupBy { it.type }
        model.addAttribute("linksFrom", linksFrom)
        model.addAttribute("linksTo", linksTo)
        return "word/index"
    }
}


@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "No such word")
class NoWordException : RuntimeException()
