package ru.yole.etymograph.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import ru.yole.etymograph.RuleBranch
import ru.yole.etymograph.UnknownLanguage

@RestController
class RuleController(val graphService: GraphService) {
    data class RuleBranchViewModel(val conditions: List<String>, val instructions: List<String>)
    data class RuleViewModel(val branches: List<RuleBranchViewModel>)

    @GetMapping("/rule/{lang}/{id}")
    fun rule(@PathVariable lang: String, @PathVariable id: String): RuleViewModel {
        val graph = graphService.graph
        val language = graph.languageByShortName(lang)
        if (language == UnknownLanguage) throw NoLanguageException()
        val rule = id.toIntOrNull()?.let { graph.ruleById(it) } ?: throw NoRuleException()
        return RuleViewModel(rule.branches.map { it.toViewModel() })
    }

    private fun RuleBranch.toViewModel(): RuleBranchViewModel {
        return RuleBranchViewModel(
            conditions.map { it.prettyPrint() },
            instructions.map { it.prettyPrint() }
        )
    }
}

@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "No such rule")
class NoRuleException : RuntimeException()
