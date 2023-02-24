package ru.yole.etymograph.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.yole.etymograph.*

@RestController
class RuleController(val graphService: GraphService) {
    data class RuleBranchViewModel(val conditions: String, val instructions: List<String>)

    data class RuleExampleViewModel(
        val fromWord: String,
        val toWord: String,
        val toWordGloss: String?,
        val expectedWord: String?,
        val allRules: List<String>
    )

    data class RuleViewModel(
        val id: Int,
        val name: String,
        val fromLang: String,
        val toLang: String,
        val fromLangFullName: String,
        val toLangFullName: String,
        val summaryText: String,
        val editableText: String,
        val addedCategories: String?,
        val replacedCategories: String?,
        val source: String?,
        val notes: String?,
        val branches: List<RuleBranchViewModel>,
        val examples: List<RuleExampleViewModel>
    )

    data class RuleListViewModel(
        val toLangFullName: String,
        val rules: List<RuleViewModel>
    )

    @GetMapping("/rules/{lang}")
    fun rules(@PathVariable lang: String): RuleListViewModel {
        val language = graphService.resolveLanguage(lang)
        return RuleListViewModel(
            language.name,
            graphService.graph.allRules().filter { it.toLanguage == language }.map { it.toViewModel() }
        )
    }

    @GetMapping("/rule/{id}")
    fun rule(@PathVariable id: Int): RuleViewModel {
        return resolveRule(id).toViewModel()
    }

    private fun Rule.toViewModel(): RuleViewModel {
        return RuleViewModel(
            id, name,
            fromLanguage.shortName, toLanguage.shortName,
            fromLanguage.name, toLanguage.name,
            toSummaryText(),
            toEditableText(),
            addedCategories,
            replacedCategories,
            source.nullize(),
            notes.nullize(),
            branches.map { it.toViewModel() },
            graphService.graph.findRuleExamples(this).map { link ->
                RuleExampleViewModel(
                    link.fromWord.text,
                    link.toWord.text,
                    link.toWord.getOrComputeGloss(graphService.graph),
                    link.rules.fold(link.toWord) { w, r -> r.apply(w) }.text
                        .takeIf { !isNormalizedEqual(link.toWord.language, it, link.fromWord.text) },
                    link.rules.map { it.name })
            }
        )
    }

    private fun isNormalizedEqual(lang: Language, ruleProducedWord: String, attestedWord: String): Boolean {
        return lang.normalizeWord(ruleProducedWord) == lang.normalizeWord(attestedWord)
    }

    private fun RuleBranch.toViewModel(): RuleBranchViewModel {
        return RuleBranchViewModel(
            condition.toEditableText(),
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
        val source: String? = null,
        val notes: String? = null
    )

    @PostMapping("/rule", consumes = ["application/json"])
    @ResponseBody
    fun newRule(@RequestBody params: UpdateRuleParameters): RuleViewModel {
        val graph = graphService.graph
        val fromLanguage = graphService.resolveLanguage(params.fromLang)
        val toLanguage = graphService.resolveLanguage(params.toLang)

        val branches = try {
            Rule.parseBranches(params.text, parseContext(fromLanguage, toLanguage))
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
            params.notes
        )
        graph.save()
        return rule.toViewModel()
    }

    private fun parseContext(fromLanguage: Language, toLanguage: Language): RuleParseContext =
        RuleParseContext(fromLanguage, toLanguage) {
            RuleRef.to(graphService.graph.ruleByName(it) ?: throw RuleParseException("No rule with name $it"))
        }

    @PostMapping("/rule/{id}", consumes = ["application/json"])
    @ResponseBody
    fun updateRule(@PathVariable id: Int, @RequestBody params: UpdateRuleParameters) {
        val graph = graphService.graph
        val rule = resolveRule(id)
        rule.branches = Rule.parseBranches(params.text, parseContext(rule.fromLanguage, rule.toLanguage))
        rule.notes = params.notes
        rule.addedCategories = params.addedCategories.nullize()
        rule.replacedCategories = params.replacedCategories.nullize()
        rule.source = params.source.nullize()
        graph.save()
    }

    private fun resolveRule(id: Int): Rule {
        return graphService.graph.ruleById(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No rule with ID $id")
    }
}
