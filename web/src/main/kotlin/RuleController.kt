package ru.yole.etymograph.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.yole.etymograph.*

@RestController
class RuleController(val graphService: GraphService) {
    data class RuleBranchViewModel(val conditions: String, val instructions: List<String>)

    data class RuleExampleViewModel(
        val fromWord: WordRefViewModel,
        val toWord: WordRefViewModel,
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
        val preInstructions: List<String>,
        val branches: List<RuleBranchViewModel>,
        val examples: List<RuleExampleViewModel>
    )

    data class RuleGroupViewModel(
        val groupName: String,
        val rules: List<RuleViewModel>
    )

    data class RuleListViewModel(
        val toLangFullName: String,
        val ruleGroups: List<RuleGroupViewModel>
    )

    @GetMapping("/rules/{lang}")
    fun rules(@PathVariable lang: String): RuleListViewModel {
        val language = graphService.resolveLanguage(lang)

        val ruleGroups = mutableMapOf<String, MutableList<RuleViewModel>>()
        for (rule in graphService.graph.allRules().filter { it.toLanguage == language }) {
            val categoryName = if (rule.isPhonemic()) "Phonetics" else "Grammar"
            ruleGroups.getOrPut(categoryName) { mutableListOf() }.add(rule.toViewModel())
        }

        return RuleListViewModel(
            language.name,
            ruleGroups.entries.map { RuleGroupViewModel(it.key, it.value) }
        )
    }

    @GetMapping("/rule/{id}")
    fun rule(@PathVariable id: Int): RuleViewModel {
        return resolveRule(id).toViewModel()
    }

    private fun Rule.toViewModel(): RuleViewModel {
        val graph = graphService.graph
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
            logic.preInstructions.map { it.toEditableText() },
            logic.branches.map { it.toViewModel() },
            graph.findRuleExamples(this).map { link ->
                val fromWord = link.fromEntity as Word
                val toWord = link.toEntity as Word
                RuleExampleViewModel(
                    fromWord.toRefViewModel(graph),
                    toWord.toRefViewModel(graph),
                    link.rules.fold(toWord) { w, r -> r.apply(w, graph) }.text
                        .takeIf { !isNormalizedEqual(toWord.language, it, fromWord.text) },
                    link.rules.map { it.name }
                )
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

        val logic = try {
            Rule.parseBranches(params.text, parseContext(fromLanguage, toLanguage))
        }
        catch (e: RuleParseException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message, e)
        }

        val rule = graph.addRule(
            params.name,
            fromLanguage,
            toLanguage,
            logic,
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
            RuleRef.to(graphService.resolveRule(it))
        }

    @PostMapping("/rule/{id}", consumes = ["application/json"])
    @ResponseBody
    fun updateRule(@PathVariable id: Int, @RequestBody params: UpdateRuleParameters) {
        val graph = graphService.graph
        val rule = resolveRule(id)
        rule.logic = Rule.parseBranches(params.text, parseContext(rule.fromLanguage, rule.toLanguage))
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
