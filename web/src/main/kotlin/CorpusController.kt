package ru.yole.etymograph.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import ru.yole.etymograph.CorpusText
import ru.yole.etymograph.Language
import ru.yole.etymograph.parser.CorpusTextSectionParser

@RestController
@CrossOrigin(origins = ["http://localhost:3000"])
class CorpusController(val graphService: GraphService) {
    @GetMapping("/corpus")
    fun indexJson(): List<Language> {
        return graphService.graph.allLanguages().toList()
    }

    data class CorpusLangTextViewModel(val id: Int, val title: String)
    data class CorpusLangViewModel(val language: Language, val corpusTexts: List<CorpusLangTextViewModel>)

    @GetMapping("/corpus/{lang}")
    fun langIndexJson(@PathVariable lang: String): CorpusLangViewModel {
        val language = graphService.resolveLanguage(lang)
        return CorpusLangViewModel(
            language,
            graphService.graph.corpusTextsInLanguage(language).map { CorpusLangTextViewModel(it.id, it.title ?: it.text) }
        )
    }

    data class CorpusWordViewModel(val text: String, val gloss: String, val wordText: String?)
    data class CorpusLineViewModel(val words: List<CorpusWordViewModel>)
    data class CorpusTextViewModel(
        val id: Int,
        val title: String,
        val language: String,
        val languageFullName: String,
        val lines: List<CorpusLineViewModel>
    )

    @GetMapping("/corpus/text/{id}")
    fun textJson(@PathVariable id: Int): CorpusTextViewModel {
        val text = graphService.graph.corpusTextById(id) ?: throw NoCorpusTextException()
        return text.toViewModel()
    }

    private fun CorpusText.toViewModel(): CorpusTextViewModel {
        return CorpusTextViewModel(
            id,
            title ?: "Untitled",
            language.shortName,
            language.name,
            mapToLines(graphService.graph).map {
                CorpusLineViewModel(it.corpusWords.map { cw ->
                    CorpusWordViewModel(cw.text, cw.gloss ?: "", cw.word?.text)
                })
            }
        )
    }

    data class CorpusParams(val text: String = "")

    @PostMapping("/corpus/{lang}/new", consumes = ["application/json"])
    @ResponseBody
    fun newText(@PathVariable lang: String, @RequestBody params: CorpusParams): CorpusTextViewModel {
        val repo = graphService.graph
        val language = graphService.resolveLanguage(lang)
        val parser = CorpusTextSectionParser(repo, language)
        val text = parser.parseText(params.text)
        repo.save()
        return text.toViewModel()
    }
}

@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "No such corpus text")
class NoCorpusTextException : RuntimeException()
