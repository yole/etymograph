package ru.yole.etymograph.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.yole.etymograph.CorpusText
import ru.yole.etymograph.Language
import ru.yole.etymograph.calculateStress

@RestController
@CrossOrigin(origins = ["http://localhost:3000"])
class CorpusController(val graphService: GraphService) {
    data class CorpusLangTextViewModel(val id: Int, val title: String)
    data class CorpusLangViewModel(val language: Language, val corpusTexts: List<CorpusLangTextViewModel>)
    data class CorpusListViewModel(val corpusTexts: List<CorpusLangTextViewModel>)

    @GetMapping("/corpus")
    fun indexJson(): CorpusListViewModel {
        return CorpusListViewModel(graphService.graph.allCorpusTexts().map { it.toLangViewModel() })
    }

    @GetMapping("/corpus/{lang}")
    fun langIndexJson(@PathVariable lang: String): CorpusLangViewModel {
        val language = graphService.resolveLanguage(lang)
        return CorpusLangViewModel(
            language,
            graphService.graph.corpusTextsInLanguage(language).map { it.toLangViewModel() }
        )
    }

    private fun CorpusText.toLangViewModel() =
        CorpusLangTextViewModel(id, title ?: text)

    data class CorpusWordViewModel(
        val index: Int, val text: String, val gloss: String, val wordId: Int?, val wordText: String?,
        val stressIndex: Int?, val stressLength: Int?
    )

    data class CorpusLineViewModel(val words: List<CorpusWordViewModel>)
    data class CorpusTextViewModel(
        val id: Int,
        val title: String,
        val language: String,
        val languageFullName: String,
        val lines: List<CorpusLineViewModel>,
        val source: String?
    )

    @GetMapping("/corpus/text/{id}")
    fun textJson(@PathVariable id: Int): CorpusTextViewModel {
        return resolveCorpusText(id).toViewModel()
    }

    private fun resolveCorpusText(id: Int): CorpusText {
        return graphService.graph.corpusTextById(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No corpus text with ID $id")
    }

    private fun CorpusText.toViewModel(): CorpusTextViewModel {
        return CorpusTextViewModel(
            id,
            title ?: "Untitled",
            language.shortName,
            language.name,
            mapToLines(graphService.graph).map {
                CorpusLineViewModel(it.corpusWords.map { cw ->
                    val stressData = cw.word?.calculateStress()
                    CorpusWordViewModel(cw.index, cw.text, cw.gloss ?: "", cw.word?.id, cw.word?.text,
                        stressData?.index, stressData?.length)
                })
            },
            source
        )
    }

    data class CorpusParams(val title: String = "", val text: String = "", val source: String = "")

    @PostMapping("/corpus/{lang}/new", consumes = ["application/json"])
    @ResponseBody
    fun newText(@PathVariable lang: String, @RequestBody params: CorpusParams): CorpusTextViewModel {
        val repo = graphService.graph
        val language = graphService.resolveLanguage(lang)
        val text = repo.addCorpusText(params.text, params.title, language, emptyList(), params.source, null)
        repo.save()
        return text.toViewModel()
    }

    data class AssociateWordParameters(val index: Int, val wordId: Int = -1)

    @PostMapping("/corpus/text/{id}/associate")
    fun associateWord(@PathVariable id: Int, @RequestBody params: AssociateWordParameters) {
        val corpusText = resolveCorpusText(id)
        val word = graphService.resolveWord(params.wordId)
        corpusText.associateWord(params.index, word)
        graphService.graph.save()
    }
}
