package ru.yole.etymograph.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import ru.yole.etymograph.*

@RestController
class ParadigmController(val graphService: GraphService) {
    data class ParadigmCellViewModel(
        val ruleNames: List<String>,
        val ruleSummaries: List<String>,
        val ruleIds: List<Int>
    )

    data class ParadigmColumnViewModel(
        val title: String,
        val cells: List<ParadigmCellViewModel>
    )

    data class ParadigmViewModel(
        val id: Int,
        val name: String,
        val language: String,
        val pos: String,
        val rowTitles: List<String>,
        val columns: List<ParadigmColumnViewModel>,
        val editableText: String
    )

    @GetMapping("/paradigms/{lang}")
    fun paradigms(@PathVariable lang: String): List<ParadigmViewModel> {
        val language = graphService.graph.languageByShortName(lang)
        if (language == UnknownLanguage) throw NoLanguageException()

        return graphService.graph.paradigmsForLanguage(language).map {
            it.toViewModel()
        }
    }

    private fun Paradigm.toViewModel() =
        ParadigmViewModel(id, name, language.shortName, pos, rowTitles, columns.map { it.toViewModel(rowTitles.size) }, toEditableText() )

    private fun ParadigmColumn.toViewModel(rows: Int) =
        ParadigmColumnViewModel(title, (0 until rows).map {
            cells.getOrNull(it)?.toViewModel() ?: ParadigmCellViewModel(emptyList(), emptyList(),  emptyList())
        })

    private fun ParadigmCell.toViewModel() =
        ParadigmCellViewModel(rules.map { it.name }, rules.map { it.toSummaryText() }, rules.map { it.id })

    @GetMapping("/paradigm/{id}")
    fun paradigm(@PathVariable id: Int): ParadigmViewModel {
        val paradigmById = graphService.graph.paradigmById(id) ?: throw NoParadigmException()
        return paradigmById.toViewModel()
    }

    data class UpdateParadigmParameters(
        val name: String,
        val pos: String,
        val text: String
    )

    @PostMapping("/paradigms/{lang}", consumes = ["application/json"])
    @ResponseBody
    fun newParadigm(@PathVariable lang: String, @RequestBody params: UpdateParadigmParameters): ParadigmViewModel {
        val graph = graphService.graph
        val language = graph.languageByShortName(lang)
        if (language == UnknownLanguage) throw NoLanguageException()

        val p = graph.addParadigm(params.name, language, params.pos)
        p.parse(params.text, graph::ruleByName)
        graph.save()
        return p.toViewModel()
    }

    @PostMapping("/paradigm/{id}", consumes = ["application/json"])
    @ResponseBody
    fun updateParadigm(@PathVariable id: Int, @RequestBody params: UpdateParadigmParameters) {
        val graph = graphService.graph
        val paradigm = graph.paradigmById(id) ?: throw NoParadigmException()
        paradigm.parse(params.text, graph::ruleByName)
        graph.save()
    }
}

@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "No such paradigm")
class NoParadigmException : RuntimeException()
