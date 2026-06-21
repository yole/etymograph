package ru.yole.etymograph.web.controllers

import org.springframework.web.bind.annotation.*
import ru.yole.etymograph.*
import ru.yole.etymograph.web.parseSourceRefs
import ru.yole.etymograph.web.resolveLanguage
import ru.yole.etymograph.web.resolveRule

@RestController
class ParadigmController {
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
        val editableText: String,
        val preRule: RuleRefViewModel?,
        val postRule: RuleRefViewModel?
    )

    @GetMapping("/{graph}/paradigms")
    fun allParadigms(graph: Graph): List<ParadigmViewModel> {
        return graph.allParadigms().map { it.toViewModel() }
    }

    private fun Paradigm.toViewModel() =
        ParadigmViewModel(
            id, name,
            language.shortName, language.name, pos, rowTitles,
            columns.map { it.toViewModel(rowTitles.size) },
            toEditableText(),
            preRule?.toRefViewModel(),
            postRule?.toRefViewModel()
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
    fun paradigm(graph: Graph, @PathVariable id: Int): ParadigmViewModel {
        val paradigmById = graph.resolveParadigm(id)
        return paradigmById.toViewModel()
    }

    private fun Graph.resolveParadigm(id: Int): Paradigm =
        paradigmById(id) ?: notFound("No paradigm with ID $id")

    data class UpdateParadigmParameters(
        val name: String,
        val pos: String,
        val text: String,
        val preRuleName: String? = null,
        val postRuleName: String? = null
    )

    @PostMapping("/{graph}/paradigms/{lang}", consumes = ["application/json"])
    @ResponseBody
    fun newParadigm(graph: Graph, @PathVariable lang: String, @RequestBody params: UpdateParadigmParameters): ParadigmViewModel {
        val language = graph.resolveLanguage(lang)

        val p = graph.addParadigm(params.name, language, parseList(params.pos))
        p.parse(params.text, graph::ruleByName)
        p.preRule = params.preRuleName.nullize()?.let { graph.resolveRule(it) }
        p.postRule = params.postRuleName.nullize()?.let { graph.resolveRule(it) }
        return p.toViewModel()
    }

    @PostMapping("/{graph}/paradigm/{id}", consumes = ["application/json"])
    @ResponseBody
    fun updateParadigm(graph: Graph, @PathVariable id: Int, @RequestBody params: UpdateParadigmParameters) {
        val paradigm = graph.resolveParadigm(id)
        paradigm.parse(params.text, graph::ruleByName)
        paradigm.name = params.name
        paradigm.pos = parseList(params.pos)
        paradigm.preRule = params.preRuleName.nullize()?.let { graph.resolveRule(it) }
        paradigm.postRule = params.postRuleName.nullize()?.let { graph.resolveRule(it) }
    }

    @PostMapping("/{graph}/paradigm/{id}/delete")
    @ResponseBody
    fun deleteParadigm(graph: Graph, @PathVariable id: Int) {
        val paradigm = graph.resolveParadigm(id)
        graph.deleteParadigm(paradigm)
    }

    data class GenerateParadigmParameters(
        val name: String,
        val lang: String,
        val pos: String,
        val addedCategories: String?,
        val prefix: String,
        val rows: String,
        val columns: String,
        val endings: String?,
        val source: String?
    )

    @PostMapping("/{graph}/paradigm/generate")
    @ResponseBody
    fun generateParadigm(graph: Graph, @RequestBody params: GenerateParadigmParameters): ParadigmViewModel {
        val language = graph.resolveLanguage(params.lang)
        val source = parseSourceRefs(graph, params.source)
        val paradigm = generateParadigm(
            language, params.name,
            params.pos.split(",").map { it.trim() },
            params.rows.split(",").map { it.trim() },
            params.columns.split(",").map { it.trim() },
            params.prefix, params.addedCategories.orEmpty(),
            params.endings.orEmpty().split(",").map { it.trim() },
            source
        )

        return paradigm.toViewModel()
    }
}
