package ru.yole.etymograph.web.controllers

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import ru.yole.etymograph.*
import ru.yole.etymograph.web.GraphService

@RestController
class ParadigmController(val graphService: GraphService) {
    data class ParadigmCellViewModel(
        val alternativeRuleNames: List<String>,
        val alternativeRuleSummaries: List<String>,
        val alternativeRuleIds: List<Int?>
    )

    data class ParadigmColumnViewModel(
        val title: String,
        val cells: List<ParadigmCellViewModel>
    )

    data class ParadigmViewModel(
        val id: Int,
        val name: String,
        val language: String,
        val languageFullName: String,
        val pos: List<String>,
        val rowTitles: List<String>,
        val columns: List<ParadigmColumnViewModel>,
        val editableText: String
    )

    data class ParadigmListViewModel(
        val langFullName: String,
        val paradigms: List<ParadigmViewModel>
    )

    @GetMapping("/{graph}/paradigms")
    fun allParadigms(@PathVariable graph: String): List<ParadigmViewModel> {
        return graphService.resolveGraph(graph).allParadigms().map { it.toViewModel() }
    }

    @GetMapping("/{graph}/paradigms/{lang}")
    fun paradigms(@PathVariable graph: String, @PathVariable lang: String): ParadigmListViewModel {
        val language = graphService.resolveLanguage(graph, lang)

        val paradigms = graphService.resolveGraph(graph).paradigmsForLanguage(language).map {
            it.toViewModel()
        }
        return ParadigmListViewModel(language.name, paradigms)
    }

    private fun Paradigm.toViewModel() =
        ParadigmViewModel(id, name,
            language.shortName, language.name, pos, rowTitles,
            columns.map { it.toViewModel(rowTitles.size) },
            toEditableText()
        )

    private fun ParadigmColumn.toViewModel(rows: Int) =
        ParadigmColumnViewModel(title, (0 until rows).map {
            cells.getOrNull(it)?.toViewModel() ?: ParadigmCellViewModel(emptyList(), emptyList(), emptyList())
        })

    private fun ParadigmCell.toViewModel() =
        ParadigmCellViewModel(
            ruleAlternatives.map { it?.name ?: "." },
            ruleAlternatives.map { it?.toSummaryText() ?: "" },
            ruleAlternatives.map { it?.id },
        )

    @GetMapping("/{graph}/paradigm/{id}")
    fun paradigm(@PathVariable graph: String, @PathVariable id: Int): ParadigmViewModel {
        val paradigmById = graphService.resolveGraph(graph).paradigmById(id) ?: throw NoParadigmException()
        return paradigmById.toViewModel()
    }

    data class UpdateParadigmParameters(
        val name: String,
        val pos: String,
        val text: String
    )

    @PostMapping("/{graph}/paradigms/{lang}", consumes = ["application/json"])
    @ResponseBody
    fun newParadigm(@PathVariable graph: String, @PathVariable lang: String, @RequestBody params: UpdateParadigmParameters): ParadigmViewModel {
        val repo = graphService.resolveGraph(graph)
        val language = graphService.resolveLanguage(graph, lang)

        val p = repo.addParadigm(params.name, language, parseList(params.pos))
        p.parse(params.text, repo::ruleByName)
        return p.toViewModel()
    }

    @PostMapping("/{graph}/paradigm/{id}", consumes = ["application/json"])
    @ResponseBody
    fun updateParadigm(@PathVariable graph: String, @PathVariable id: Int, @RequestBody params: UpdateParadigmParameters) {
        val repo = graphService.resolveGraph(graph)
        val paradigm = repo.paradigmById(id) ?: throw NoParadigmException()
        paradigm.parse(params.text, repo::ruleByName)
        paradigm.name = params.name
        paradigm.pos = parseList(params.pos)
    }

    @PostMapping("/{graph}/paradigm/{id}/delete")
    @ResponseBody
    fun deleteParadigm(@PathVariable graph: String, @PathVariable id: Int) {
        val repo = graphService.resolveGraph(graph)
        val paradigm = repo.paradigmById(id) ?: throw NoParadigmException()
        repo.deleteParadigm(paradigm)
    }
}

@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "No such paradigm")
class NoParadigmException : RuntimeException()
