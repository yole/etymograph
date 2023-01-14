package ru.yole.etymograph.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.yole.etymograph.*

@RestController
class RuleController(val graphService: GraphService) {
    data class RuleBranchViewModel(val conditions: List<String>, val instructions: List<String>)
    data class RuleViewModel(
        val id: Int,
        val name: String,
        val fromLang: String,
        val toLang: String,
        val summaryText: String,
        val editableText: String,
        val addedCategories: String?,
        val replacedCategories: String?,
        val source: String?,
        val branches: List<RuleBranchViewModel>
    )

    @GetMapping("/rules")
    fun rules(): List<RuleViewModel> {
        return graphService.graph.allRules().map { it.toViewModel() }
    }

    @GetMapping("/rule/{id}")
    fun rule(@PathVariable id: Int): RuleViewModel {
        return resolveRule(id).toViewModel()
    }

    private fun Rule.toViewModel(): RuleViewModel {
        return RuleViewModel(
            id, name, fromLanguage.shortName, toLanguage.shortName,
            toSummaryText(),
            toEditableText(),
            addedCategories,
            replacedCategories,
            source.nullize(),
            branches.map { it.toViewModel() })
    }

    private fun RuleBranch.toViewModel(): RuleBranchViewModel {
        return RuleBranchViewModel(
            conditions.map { it.toEditableText() },
            instructions.map { it.toEditableText() }
        )
    }

    data class UpdateRuleParameters(
        val name: String,
        val fromLang: String,
        val toLang: String,
        val text: String,
        val addedCategories: String? = null,
        val replacedCategories: String? = null,
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

        val branches = try {
            Rule.parseBranches(params.text) { cls -> graph.characterClassByName(fromLanguage, cls) }
        }
        catch (e: RuleParseException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message, e)
        }

        val rule = graph.addRule(
            params.name,
            fromLanguage,
            toLanguage,
            branches,
            params.addedCategories,
            params.replacedCategories,
            params.source,
            null
        )
        graph.save()
        return rule.toViewModel()
    }

    @PostMapping("/rule/{id}", consumes = ["application/json"])
    @ResponseBody
    fun updateRule(@PathVariable id: Int, @RequestBody params: UpdateRuleParameters) {
        val graph = graphService.graph
        val rule = resolveRule(id)
        rule.branches = Rule.parseBranches(params.text) { cls -> graph.characterClassByName(rule.fromLanguage, cls) }
        graph.save()
    }

    private fun resolveRule(id: Int): Rule {
        return graphService.graph.ruleById(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No rule with ID $id")
    }
}
