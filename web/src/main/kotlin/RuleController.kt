package ru.yole.etymograph.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import ru.yole.etymograph.Rule
import ru.yole.etymograph.RuleBranch
import ru.yole.etymograph.UnknownLanguage

@RestController
class RuleController(val graphService: GraphService) {
    data class RuleBranchViewModel(val conditions: List<String>, val instructions: List<String>)
    data class RuleViewModel(
        val id: Int,
        val fromLang: String,
        val toLang: String,
        val prettyText: String,
        val addedCategories: String?,
        val source: String?,
        val branches: List<RuleBranchViewModel>
    )

    @GetMapping("/rules")
    fun rules(): List<RuleViewModel> {
        return graphService.graph.allRules().map { it.toViewModel() }
    }

    @GetMapping("/rule/{id}")
    fun rule(@PathVariable id: String): RuleViewModel {
        val graph = graphService.graph
        val rule = id.toIntOrNull()?.let { graph.ruleById(it) } ?: throw NoRuleException()
        return rule.toViewModel()
    }

    private fun Rule.toViewModel(): RuleViewModel {
        return RuleViewModel(
            id, fromLanguage.shortName, toLanguage.shortName,
            prettyPrint(),
            addedCategories,
            source,
            branches.map { it.toViewModel() })
    }

    private fun RuleBranch.toViewModel(): RuleBranchViewModel {
        return RuleBranchViewModel(
            conditions.map { it.prettyPrint() },
            instructions.map { it.prettyPrint() }
        )
    }

    data class UpdateRuleParameters(
        val fromLang: String,
        val toLang: String,
        val text: String,
        val addedCategories: String? = null,
        val source: String? = null
    )

    @PostMapping("/rule", consumes = ["application/json"])
    @ResponseBody
    fun newRule(@RequestBody params: UpdateRuleParameters): RuleViewModel {
        val graph = graphService.graph
        val fromLanguage = graph.languageByShortName(params.fromLang)
        if (fromLanguage == UnknownLanguage) throw NoLanguageException()
        val toLanguage = graph.languageByShortName(params.toLang)
        if (toLanguage == UnknownLanguage) throw NoLanguageException()

        val branches = Rule.parseBranches(params.text) { cls -> graph.characterClassByName(fromLanguage, cls) }
        val rule = graph.addRule(fromLanguage, toLanguage, branches, params.addedCategories, params.source, null)
        graph.save()
        return rule.toViewModel()
    }

    @PostMapping("/rule/{id}", consumes = ["application/json"])
    @ResponseBody
    fun updateRule(@PathVariable id: String, @RequestBody params: UpdateRuleParameters) {
        val graph = graphService.graph
        val rule = id.toIntOrNull()?.let { graph.ruleById(it) } ?: throw NoRuleException()
        rule.branches = Rule.parseBranches(params.text) { cls -> graph.characterClassByName(rule.fromLanguage, cls) }
        graph.save()
    }
}

@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "No such rule")
class NoRuleException : RuntimeException()
