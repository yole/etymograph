package ru.yole.etymograph.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.yole.etymograph.*

@RestController
class RuleController(val graphService: GraphService) {
    data class RuleBranchViewModel(val conditions: RichText, val instructions: List<RichText>)

    data class RuleLinkViewModel(
        val toRuleId: Int,
        val toRuleName: String,
        val linkType: String,
        val source: List<SourceRefViewModel>,
        val notes: String?
    )

    data class RuleWordLinkViewModel(
        val toWord: WordRefViewModel,
        val linkType: String,
        val source: List<SourceRefViewModel>,
        val notes: String?
    )

    data class RuleExampleViewModel(
        val fromWord: WordRefViewModel,
        val toWord: WordRefViewModel,
        val expectedWord: String?,
        val allRules: List<RuleLinkViewModel>,
        val ruleResults: List<String>
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
        val phonemic: Boolean,
        val preInstructions: List<RichText>,
        val branches: List<RuleBranchViewModel>,
        val links: List<RuleLinkViewModel>,
        val linkedWords: List<RuleWordLinkViewModel>,
        val examples: List<RuleExampleViewModel>
    )

    data class RuleShortViewModel(
        val id: Int,
        val name: String,
        val summaryText: String
    )

    data class RuleGroupViewModel(
        val groupName: String,
        val rules: List<RuleShortViewModel>,
        val sequenceId: Int? = null,
        val sequenceName: String? = null,
        val sequenceFromLang: String? = null,
        val sequenceToLang: String? = null
    )

    data class RuleListViewModel(
        val toLangFullName: String,
        val ruleGroups: List<RuleGroupViewModel>
    )

    @GetMapping("/rules")
    fun allRules(): List<RuleShortViewModel> {
        return graphService.graph.allRules().map { it.toShortViewModel() }
    }

    @GetMapping("/rules/{lang}")
    fun rules(@PathVariable lang: String): RuleListViewModel {
        val language = graphService.resolveLanguage(lang)

        val ruleGroups = mutableMapOf<String, MutableList<RuleShortViewModel>>()

        val paradigms = graphService.graph.paradigmsForLanguage(language)
        val paradigmRules = paradigms.associate { it.name to it.collectAllRules() }
        val allParadigmRules = paradigmRules.values.flatten().toSet()
        val ruleSeqMap = mutableMapOf<String, RuleSequence>()

        for (paradigmRule in paradigmRules.entries) {
            ruleGroups
                .getOrPut("Grammar: ${paradigmRule.key}") { mutableListOf() }
                .addAll(paradigmRule.value.map { it.toShortViewModel() })
        }

        val sequences = graphService.graph.ruleSequencesForLanguage(language)
        val allSequenceRules = mutableSetOf<Rule>()
        for (sequence in sequences) {
            val groupName = "Phonetics: ${sequence.name}"
            val group = ruleGroups.getOrPut(groupName) { mutableListOf() }
            ruleSeqMap[groupName] = sequence
            val rules = sequence.ruleIds.map { graphService.graph.langEntityById(it)!! }
            allSequenceRules.addAll(rules.filterIsInstance<Rule>())
            group.addAll(rules.map { it.toShortViewModel() })
        }

        for (rule in graphService.graph.allRules().filter { it.toLanguage == language }) {
            if (rule in allParadigmRules || rule in allSequenceRules) continue
            val categoryName = if (rule.isPhonemic()) "Phonetics" else "Grammar: Other"
            ruleGroups.getOrPut(categoryName) { mutableListOf() }.add(rule.toShortViewModel())
        }

        return RuleListViewModel(
            language.name,
            ruleGroups.entries.map {
                val seq = ruleSeqMap[it.key]
                RuleGroupViewModel(it.key, it.value, seq?.id, seq?.name,
                    seq?.fromLanguage?.shortName, seq?.toLanguage?.shortName)
            }
        )
    }

    @GetMapping("/rule/{id}")
    fun rule(@PathVariable id: Int): RuleViewModel {
        return graphService.resolveRule(id).toViewModel()
    }

    private fun LangEntity.toShortViewModel(): RuleShortViewModel {
        return when (this) {
            is Rule -> RuleShortViewModel(id, name, toSummaryText())
            is RuleSequence -> RuleShortViewModel(id, "sequence: $name", "")
            else -> throw IllegalStateException("Unknown entity type")
        }
    }

    private fun Rule.toViewModel(): RuleViewModel {
        val graph = graphService.graph
        val paradigm = graph.paradigmForRule(this)
        val links = (graph.getLinksFrom(this).map { it to it.toEntity } +
                graph.getLinksTo(this).map { it to it.fromEntity })
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
            isPhonemic(),
            logic.preInstructions.map { it.toRichText() },
            logic.branches.map { it.toViewModel(isUnconditional()) },
            links.mapNotNull { (link, langEntity) ->
                val rule = langEntity as? Rule
                rule?.let {
                    RuleLinkViewModel(it.id, it.name, link.type.id, link.source.toViewModel(graph), link.notes)
                }
            },
            links.mapNotNull { (link, langEntity) ->
                val word = langEntity as? Word
                word?.let {
                    RuleWordLinkViewModel(
                        word.toRefViewModel(graph),
                        link.type.id, link.source.toViewModel(graph), link.notes
                    )
                }
            },
            graph.findRuleExamples(this).map { link ->
                exampleToViewModel(link, graph)
            }
        )
    }

    private fun exampleToViewModel(link: Link, graph: GraphRepository): RuleExampleViewModel {
        val fromWord = link.fromEntity as Word
        val toWord = link.toEntity as Word
        return RuleExampleViewModel(
            fromWord.toRefViewModel(graph),
            toWord.toRefViewModel(graph),
            link.applyRules(toWord, graph).asOrthographic()
                .takeIf { !fromWord.language.isNormalizedEqual(it, fromWord) }?.text,
            link.rules.map { RuleLinkViewModel(it.id, it.name, link.type.id, link.source.toViewModel(graph), link.notes) },
            buildIntermediateSteps(graph, link)
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
            if (isUnconditional) RichText(emptyList()) else condition.toRichText(),
            instructions.map { it.toRichText() }
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
        if (' ' in params.name) {
            badRequest("Rule names may not contain spaces")
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
        if (' ' in params.name) {
            badRequest("Rule names may not contain spaces")
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
        return rule.toViewModel()
    }

    @PostMapping("/rule/{id}/delete", consumes = ["application/json"])
    @ResponseBody
    fun deleteRule(@PathVariable id: Int) {
        val graph = graphService.graph
        val rule = graph.ruleById(id) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No rule with ID $id")
        graph.deleteRule(rule)
    }

    data class UpdateSequenceParams(
        val name: String,
        val fromLang: String,
        val toLang: String,
        val ruleNames: String
    )

    @PostMapping("/rule/sequence", consumes = ["application/json"])
    fun newSequence(@RequestBody params: UpdateSequenceParams) {
        val graph = graphService.graph
        val (fromLanguage, toLanguage, rules) = resolveUpdateSequenceParams(params)
        graph.addRuleSequence(params.name, fromLanguage, toLanguage, rules)
    }

    private fun resolveUpdateSequenceParams(params: UpdateSequenceParams): Triple<Language, Language, List<LangEntity>> {
        val fromLanguage = graphService.resolveLanguage(params.fromLang)
        val toLanguage = graphService.resolveLanguage(params.toLang)
        val rules = params.ruleNames.split('\n').filter { it.isNotBlank() }.map {
            val name = it.trim()
            if (name.startsWith("sequence:")) {
                val sequenceName = name.removePrefix("sequence:").trim()
                graphService.graph.ruleSequenceByName(sequenceName)
                    ?: badRequest("Cannot find rule sequence $sequenceName")
            }
            else {
                graphService.resolveRule(name)
            }
        }
        return Triple(fromLanguage, toLanguage, rules)
    }

    @PostMapping("/rule/sequence/{id}", consumes = ["application/json"])
    fun updateSequence(@PathVariable id: Int, @RequestBody params: UpdateSequenceParams) {
        val sequence = graphService.resolveRuleSequence(id)
        val (fromLanguage, toLanguage, rules) = resolveUpdateSequenceParams(params)
        sequence.name = params.name
        sequence.fromLanguage = fromLanguage
        sequence.toLanguage = toLanguage
        sequence.ruleIds = rules.map { it.id }
    }

    data class ApplySequenceParams(
        val linkFromId: Int,
        val linkToId: Int
    )

    @PostMapping("/rule/sequence/{id}/apply", consumes = ["application/json"])
    @ResponseBody
    fun applySequence(@PathVariable id: Int, @RequestBody params: ApplySequenceParams): WordController.LinkWordViewModel {
        val sequence = graphService.resolveRuleSequence(id)
        val fromEntity = graphService.resolveWord(params.linkFromId)
        val toEntity = graphService.resolveWord(params.linkToId)
        val graph = graphService.graph
        val link = graph.findLink(fromEntity, toEntity, Link.Origin)
            ?: badRequest("No Derived link from ${params.linkFromId} to ${params.linkToId}")
        graph.applyRuleSequence(link, sequence)
        return linkToViewModel(link, graph, true)
    }
}
