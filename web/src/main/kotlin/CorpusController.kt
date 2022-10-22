package ru.yole.etymograph.web

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.ResponseStatus
import ru.yole.etymograph.CorpusText
import ru.yole.etymograph.Language
import ru.yole.etymograph.UnknownLanguage

@Controller
@CrossOrigin(origins = ["http://localhost:3000"])
class CorpusController(val graphService: GraphService) {
    @GetMapping("/corpus")
    fun index(model: Model): String {
        model.addAttribute("languages", graphService.graph.allLanguages())
        return "corpus/index"
    }

    @GetMapping("/corpus", produces = ["application/json"])
    @ResponseBody
    fun indexJson(): List<Language> {
        return graphService.graph.allLanguages().toList()
    }

    @GetMapping("/corpus/{lang}")
    fun langIndex(@PathVariable lang: String, model: Model): String {
        val language = graphService.graph.languageByShortName(lang)
        if (language == UnknownLanguage) throw NoLanguageException()

        model.addAttribute("language", language)
        model.addAttribute("texts", graphService.graph.corpusTextsInLanguage(language))
        return "corpus/langIndex"
    }

    data class CorpusLangTextViewModel(val id: Int, val title: String)
    data class CorpusLangViewModel(val language: Language, val corpusTexts: List<CorpusLangTextViewModel>)

    @GetMapping("/corpus/{lang}", produces = ["application/json"])
    @ResponseBody
    fun langIndexJson(@PathVariable lang: String): CorpusLangViewModel {
        val language = graphService.graph.languageByShortName(lang)
        if (language == UnknownLanguage) throw NoLanguageException()

        return CorpusLangViewModel(
            language,
            graphService.graph.corpusTextsInLanguage(language).map { CorpusLangTextViewModel(it.id, it.title ?: "Untitled") }
        )
    }

    @GetMapping("/corpus/text/{id}")
    fun text(@PathVariable id: Int, model: Model): String {
        val text = graphService.graph.corpusTextById(id) ?: throw NoCorpusTextException()
        model.addAttribute("text", text)
        model.addAttribute("lines", text.mapToLines(graphService.graph))
        return "corpus/text"
    }

    data class CorpusWordViewModel(val text: String, val gloss: String)
    data class CorpusLineViewModel(val words: List<CorpusWordViewModel>)
    data class CorpusTextViewModel(val title: String, val language: String, val lines: List<CorpusLineViewModel>)

    @GetMapping("/corpus/text/{id}", produces = ["application/json"])
    @ResponseBody
    fun textJson(@PathVariable id: Int): CorpusTextViewModel {
        val text = graphService.graph.corpusTextById(id) ?: throw NoCorpusTextException()
        return CorpusTextViewModel(
            text.title ?: "Untitled",
            text.language.shortName,
            text.mapToLines(graphService.graph).map {
                CorpusLineViewModel(it.corpusWords.map { cw ->
                    CorpusWordViewModel(cw.text, cw.gloss ?: "")
                })
            }
        )
    }
}

@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "No such language")
class NoLanguageException : RuntimeException()

@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "No such corpus text")
class NoCorpusTextException : RuntimeException()
