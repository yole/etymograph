package ru.yole.etymograph.web.controllers

import org.springframework.web.bind.annotation.*
import ru.yole.etymograph.*
import ru.yole.etymograph.web.resolveLanguage

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
        val editableText: String
    )

    data class ParadigmListViewModel(
        val langFullName: String,
        val paradigms: List<ParadigmViewModel>
    )

    @GetMapping("/{graph}/paradigms")
    fun allParadigms(repo: GraphRepository): List<ParadigmViewModel> {
        return repo.allParadigms().map { it.toViewModel() }
    }

    @GetMapping("/{graph}/paradigms/{lang}")
    fun paradigms(repo: GraphRepository, @PathVariable lang: String): ParadigmListViewModel {
        val language = repo.resolveLanguage(lang)

        val paradigms = repo.paradigmsForLanguage(language).map {
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
    fun paradigm(repo: GraphRepository, @PathVariable id: Int): ParadigmViewModel {
        val paradigmById = repo.resolveParadigm(id)
        return paradigmById.toViewModel()
    }

    private fun GraphRepository.resolveParadigm(id: Int): Paradigm =
        paradigmById(id) ?: notFound("No paradigm with ID $id")

    data class UpdateParadigmParameters(
        val name: String,
        val pos: String,
        val text: String
    )

    @PostMapping("/{graph}/paradigms/{lang}", consumes = ["application/json"])
    @ResponseBody
    fun newParadigm(repo: GraphRepository, @PathVariable lang: String, @RequestBody params: UpdateParadigmParameters): ParadigmViewModel {
        val language = repo.resolveLanguage(lang)

        val p = repo.addParadigm(params.name, language, parseList(params.pos))
        p.parse(params.text, repo::ruleByName)
        return p.toViewModel()
    }

    @PostMapping("/{graph}/paradigm/{id}", consumes = ["application/json"])
    @ResponseBody
    fun updateParadigm(repo: GraphRepository, @PathVariable id: Int, @RequestBody params: UpdateParadigmParameters) {
        val paradigm = repo.resolveParadigm(id)
        paradigm.parse(params.text, repo::ruleByName)
        paradigm.name = params.name
        paradigm.pos = parseList(params.pos)
    }

    @PostMapping("/{graph}/paradigm/{id}/delete")
    @ResponseBody
    fun deleteParadigm(repo: GraphRepository, @PathVariable id: Int) {
        val paradigm = repo.resolveParadigm(id)
        repo.deleteParadigm(paradigm)
    }

    data class GenerateParadigmParameters(
        val name: String,
        val lang: String,
        val pos: String,
        val prefix: String,
        val rows: String,
        val columns: String
    )

    @PostMapping("/{graph}/paradigm/generate")
    @ResponseBody
    fun generateParadigm(repo: GraphRepository, @RequestBody params: GenerateParadigmParameters): ParadigmViewModel {
        val language = repo.resolveLanguage(params.lang)
        val rowList = params.rows.split(',').map { it.trim() }
        val colList = params.columns.split(',').map { it.trim() }

        val paradigm = repo.addParadigm(params.name, language, params.pos.split(',').map { it.trim()} )
        for (rowTitle in rowList) {
            paradigm.addRow(rowTitle)
        }
        for (columnTitle in colList) {
            paradigm.addColumn(columnTitle)
        }

        for ((rowIndex, rowTitle) in rowList.withIndex()) {
            for ((colIndex, columnTitle) in colList.withIndex()) {
                val ruleName = "${params.prefix}-${rowTitle.lowercase()}-${columnTitle.lowercase().replace(" ", "-")}"
                val addedCategories = "." + rowTitle.uppercase() + "." + columnTitle.uppercase().replace(" ", ".")
                val rule = repo.ruleByName(ruleName)
                    ?: repo.addRule(ruleName, language, language, RuleLogic.empty(), addedCategories, null,
                        params.pos)
                paradigm.setRule(rowIndex, colIndex, listOf(rule))
            }
        }

        return paradigm.toViewModel()
    }
}
