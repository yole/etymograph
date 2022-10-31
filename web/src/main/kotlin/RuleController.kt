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
        val branches: List<RuleBranchViewModel>
    )

    @GetMapping("/rule/{id}")
    fun rule(@PathVariable id: String): RuleViewModel {
        val graph = graphService.graph
        val rule = id.toIntOrNull()?.let { graph.ruleById(it) } ?: throw NoRuleException()
        return RuleViewModel(rule.id, rule.fromLanguage.shortName, rule.toLanguage.shortName, rule.prettyPrint(),
            rule.branches.map { it.toViewModel() })
    }

    private fun RuleBranch.toViewModel(): RuleBranchViewModel {
        return RuleBranchViewModel(
            conditions.map { it.prettyPrint() },
            instructions.map { it.prettyPrint() }
        )
    }

    data class UpdateRuleParameters(val fromLang: String, val toLang: String, val text: String)

    @PostMapping("/rule", consumes = ["application/json"])
    @ResponseBody
    fun newRule(@RequestBody params: UpdateRuleParameters) {
        val graph = graphService.graph
        val fromLanguage = graph.languageByShortName(params.fromLang)
        if (fromLanguage == UnknownLanguage) throw NoLanguageException()
        val toLanguage = graph.languageByShortName(params.toLang)
        if (toLanguage == UnknownLanguage) throw NoLanguageException()

        val branches = Rule.parseBranches(params.text) { cls -> graph.characterClassByName(fromLanguage, cls) }
        graph.addRule(fromLanguage, toLanguage, branches, null, null, null)
        graph.save()
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
