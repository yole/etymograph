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

    data class RuleLinkViewModel(
        val toRuleId: Int,
        val toRuleName: String
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
        val addedCategoryDisplayNames: String?,
        val fromPOS: String?,
        val toPOS: String?,
        val source: List<SourceRefViewModel>,
        val sourceEditableText: String,
        val notes: String?,
        val paradigmId: Int?,
        val paradigmName: String?,
        val preInstructions: List<String>,
        val branches: List<RuleBranchViewModel>,
        val links: List<RuleLinkViewModel>,
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

    @GetMapping("/rules")
    fun allRules(): List<RuleViewModel> {
        return graphService.graph.allRules().map { it.toViewModel() }
    }

    @GetMapping("/rules/{lang}")
    fun rules(@PathVariable lang: String): RuleListViewModel {
        val language = graphService.resolveLanguage(lang)

        val ruleGroups = mutableMapOf<String, MutableList<RuleViewModel>>()

        val paradigms = graphService.graph.paradigmsForLanguage(language)
        val paradigmRules = paradigms.associate { it.name to it.collectAllRules() }
        val allParadigmRules = paradigmRules.values.flatten().toSet()

        for (paradigmRule in paradigmRules.entries) {
            ruleGroups
                .getOrPut("Grammar: ${paradigmRule.key}") { mutableListOf() }
                .addAll(paradigmRule.value.map { it.toViewModel() })
        }

        for (rule in graphService.graph.allRules().filter { it.toLanguage == language }) {
            if (rule in allParadigmRules) continue
            val categoryName = if (rule.isPhonemic()) "Phonetics" else "Grammar: Other"
            ruleGroups.getOrPut(categoryName) { mutableListOf() }.add(rule.toViewModel())
        }

        return RuleListViewModel(
            language.name,
            ruleGroups.entries.map { RuleGroupViewModel(it.key, it.value) }
        )
    }

    @GetMapping("/rule/{id}")
    fun rule(@PathVariable id: Int): RuleViewModel {
        return graphService.resolveRule(id).toViewModel()
    }

    private fun Rule.toViewModel(): RuleViewModel {
        val graph = graphService.graph
        val paradigm = graph.paradigmForRule(this)
        val links = (graph.getLinksFrom(this).map { it.toEntity } +
                graph.getLinksTo(this).map { it.fromEntity })
            .filterIsInstance<Rule>().toList()
        return RuleViewModel(
            id, name,
            fromLanguage.shortName, toLanguage.shortName,
            fromLanguage.name, toLanguage.name,
            toSummaryText(),
            toEditableText(),
            addedCategories,
            replacedCategories,
            toReadableCategories(fromLanguage, addedCategories),
            fromPOS,
            toPOS,
            source.toViewModel(graph),
            source.toEditableText(graph),
            notes.nullize(),
            paradigm?.id,
            paradigm?.name,
            logic.preInstructions.map { it.toEditableText() },
            logic.branches.map { it.toViewModel(isUnconditional()) },
            links.map { RuleLinkViewModel(it.id, it.name) },
            graph.findRuleExamples(this).map { link ->
                val fromWord = link.fromEntity as Word
                val toWord = link.toEntity as Word
                RuleExampleViewModel(
                    fromWord.toRefViewModel(graph),
                    toWord.toRefViewModel(graph),
                    link.rules.fold(toWord) { w, r -> r.apply(w, graph) }.text
                        .takeIf { !toWord.language.isNormalizedEqual(it, fromWord.text) },
                    link.rules.map { it.name }
                )
            }
        )
    }

    private fun toReadableCategories(language: Language, addedCategories: String?): String? {
        if (addedCategories == null) return null
        val gcValues = parseCategoryValues(language, addedCategories)
        return gcValues.joinToString(", ") { (category, value) ->
            if (category == null || value == null) "?" else "${category.name}: ${value.name}"
        }
    }

    private fun RuleBranch.toViewModel(isUnconditional: Boolean): RuleBranchViewModel {
        return RuleBranchViewModel(
            if (isUnconditional) "" else condition.toEditableText(),
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
        val fromPOS: String? = null,
        val toPOS: String? = null,
        val source: String? = null,
        val notes: String? = null
    )

    @PostMapping("/rule", consumes = ["application/json"])
    @ResponseBody
    fun newRule(@RequestBody params: UpdateRuleParameters): RuleViewModel {
        val graph = graphService.graph
        val fromLanguage = graphService.resolveLanguage(params.fromLang)
        val toLanguage = graphService.resolveLanguage(params.toLang)

        if (graph.ruleByName(params.name) != null) {
            badRequest("Rule named '${params.name} already exists")
        }

        val logic = try {
            Rule.parseBranches(params.text, parseContext(fromLanguage, toLanguage))
        } catch (e: RuleParseException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message, e)
        }

        val rule = graph.addRule(
            params.name,
            fromLanguage,
            toLanguage,
            logic,
            params.addedCategories.nullize(),
            params.replacedCategories.nullize(),
            params.fromPOS.nullize(),
            params.toPOS.nullize(),
            parseSourceRefs(graph, params.source),
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
    fun updateRule(@PathVariable id: Int, @RequestBody params: UpdateRuleParameters): RuleViewModel {
        val fromLanguage = graphService.resolveLanguage(params.fromLang)
        val toLanguage = graphService.resolveLanguage(params.toLang)
        val graph = graphService.graph
        val rule = graphService.resolveRule(id)

        if (rule.name != params.name && graph.ruleByName(params.name) != null) {
            badRequest("Rule named '${params.name} already exists")
        }

        rule.name = params.name
        rule.fromLanguage = fromLanguage
        rule.toLanguage = toLanguage
        rule.logic = Rule.parseBranches(params.text, parseContext(rule.fromLanguage, rule.toLanguage))
        rule.notes = params.notes
        rule.addedCategories = params.addedCategories.nullize()
        rule.replacedCategories = params.replacedCategories.nullize()
        rule.fromPOS = params.fromPOS.nullize()
        rule.toPOS = params.toPOS.nullize()
        rule.source = parseSourceRefs(graph, params.source)
        graph.save()
        return rule.toViewModel()
    }

    @PostMapping("/rule/{id}/delete", consumes = ["application/json"])
    @ResponseBody
    fun deleteRule(@PathVariable id: Int) {
        val graph = graphService.graph
        val rule = graph.ruleById(id) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No rule with ID $id")
        graph.deleteRule(rule)
        graph.save()
    }
}
