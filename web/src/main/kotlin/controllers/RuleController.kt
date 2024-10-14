package ru.yole.etymograph.web.controllers

import org.springframework.web.bind.annotation.*
import ru.yole.etymograph.*
import ru.yole.etymograph.web.*

@RestController
class RuleController {
    data class RuleBranchViewModel(
        val conditions: RichText,
        val instructions: List<RichText>,
        val comment: String?,
        val examples: List<RuleExampleViewModel>
    )

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
        val fromPOS: List<String>,
        val toPOS: String?,
        val source: List<SourceRefViewModel>,
        val sourceEditableText: String,
        val notes: String?,
        val paradigmId: Int?,
        val paradigmName: String?,
        val phonemic: Boolean,
        val preInstructions: List<RichText>,
        val branches: List<RuleBranchViewModel>,
        val postInstructions: List<RichText>,
        val links: List<RuleLinkViewModel>,
        val linkedWords: List<RuleWordLinkViewModel>,
        val orphanExamples: List<RuleExampleViewModel>
    )

    data class RuleShortViewModel(
        val id: Int,
        val name: String,
        val toLang: String,
        val summaryText: String,
        val optional: Boolean
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

    @GetMapping("/{graph}/rules")
    fun allRules(repo: GraphRepository): List<RuleShortViewModel> {
        return repo.allRules().map { it.toShortViewModel() }
    }

    @GetMapping("/{graph}/rules/{lang}")
    fun rules(repo: GraphRepository, @PathVariable lang: String): RuleListViewModel {
        val language = repo.resolveLanguage(lang)

        val ruleGroups = mutableMapOf<String, MutableList<RuleShortViewModel>>()

        val paradigms = repo.paradigmsForLanguage(language)
        val paradigmRules = paradigms.associate { it.name to it.collectAllRules() }
        val allParadigmRules = paradigmRules.values.flatten().toSet()
        val ruleSeqMap = mutableMapOf<String, RuleSequence>()

        for (paradigmRule in paradigmRules.entries) {
            ruleGroups
                .getOrPut("Grammar: ${paradigmRule.key}") { mutableListOf() }
                .addAll(paradigmRule.value.map { it.toShortViewModel() })
        }

        val sequences = repo.ruleSequencesForLanguage(language)
        val allSequenceRules = mutableSetOf<Rule>()
        for (sequence in sequences) {
            val groupName = "Phonetics: ${sequence.name}"
            val group = ruleGroups.getOrPut(groupName) { mutableListOf() }
            ruleSeqMap[groupName] = sequence
            for (step in sequence.steps) {
                val rule = repo.langEntityById(step.ruleId)!!
                if (rule is Rule) {
                    allSequenceRules.add(rule)
                }
                group.add(rule.toShortViewModel(step.optional))
            }
        }

        for (rule in repo.allRules().filter { it.toLanguage == language }) {
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

    @GetMapping("/{graph}/rule/{id}")
    fun rule(repo: GraphRepository, @PathVariable id: Int): RuleViewModel {
        return repo.resolveRule(id).toViewModel(repo)
    }

    private fun LangEntity.toShortViewModel(optional: Boolean = false): RuleShortViewModel {
        return when (this) {
            is Rule -> RuleShortViewModel(id, name, toLanguage.shortName, toSummaryText(), optional)
            is RuleSequence -> RuleShortViewModel(id, "sequence: $name", toLanguage.shortName, "", optional)
            else -> throw IllegalStateException("Unknown entity type")
        }
    }

    private class RuleExampleData(val link: Link, val steps: List<RuleStepData>, val branches: Set<RuleBranch>)

    private fun Rule.toViewModel(repo: GraphRepository): RuleViewModel {
        val paradigm = repo.paradigmForRule(this)
        val links = (repo.getLinksFrom(this).map { it to it.toEntity } +
                repo.getLinksTo(this).map { it to it.fromEntity })
        val examples = repo.findRuleExamples(this).map { link ->
            val steps = buildIntermediateSteps(repo, link)
            RuleExampleData(link, steps, steps.find { it.rule == this }?.matchedBranches ?: mutableSetOf())
        }
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
            source.toViewModel(repo),
            source.toEditableText(repo),
            notes.nullize(),
            paradigm?.id,
            paradigm?.name,
            isPhonemic(),
            logic.preInstructions.map { it.toRichText() },
            logic.branches.map { branch ->
                branch.toViewModel(isUnconditional(), examples.filter { branch in it.branches }, repo)
            },
            logic.postInstructions.map { it.toRichText() },
            links.mapNotNull { (link, langEntity) ->
                val rule = langEntity as? Rule
                rule?.let {
                    RuleLinkViewModel(it.id, it.name, link.type.id, link.source.toViewModel(repo), link.notes)
                }
            },
            links.mapNotNull { (link, langEntity) ->
                val word = langEntity as? Word
                word?.let {
                    RuleWordLinkViewModel(
                        word.toRefViewModel(repo),
                        link.type.id, link.source.toViewModel(repo), link.notes
                    )
                }
            },
            examples.filter { it.branches.isEmpty() }.map { exampleToViewModel(it, repo) }
        )
    }

    private fun exampleToViewModel(example: RuleExampleData, graph: GraphRepository): RuleExampleViewModel {
        val link = example.link
        val fromWord = link.fromEntity as Word
        val toWord = link.toEntity as Word
        return RuleExampleViewModel(
            fromWord.toRefViewModel(graph),
            toWord.toRefViewModel(graph),
            link.applyRules(toWord, graph).asOrthographic()
                .takeIf { !fromWord.language.isNormalizedEqual(it, fromWord) }?.text,
            link.rules.map { RuleLinkViewModel(it.id, it.name, link.type.id, link.source.toViewModel(graph), link.notes) },
            example.steps.map { it.result }
        )
    }

    private fun toReadableCategories(language: Language, addedCategories: String?): String? {
        if (addedCategories == null) return null
        val gcValues = parseCategoryValues(language, addedCategories)
        return gcValues.joinToString(", ") { categoryValue ->
            if (categoryValue == null) "?" else "${categoryValue.category.name}: ${categoryValue.value.name}"
        }
    }

    private fun RuleBranch.toViewModel(isUnconditional: Boolean, examples: List<RuleExampleData>, repo: GraphRepository): RuleBranchViewModel {
        return RuleBranchViewModel(
            if (isUnconditional) RichText(emptyList()) else condition.toRichText(),
            instructions.map { it.toRichText() },
            comment,
            examples.map { exampleToViewModel(it, repo) }
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

    @PostMapping("/{graph}/rule", consumes = ["application/json"])
    @ResponseBody
    fun newRule(repo: GraphRepository, @RequestBody params: UpdateRuleParameters): RuleViewModel {
        val fromLanguage = repo.resolveLanguage(params.fromLang)
        val toLanguage = repo.resolveLanguage(params.toLang)

        if (repo.ruleByName(params.name) != null) {
            badRequest("Rule named '${params.name} already exists")
        }
        if (' ' in params.name) {
            badRequest("Rule names may not contain spaces")
        }

        val logic = try {
            Rule.parseBranches(params.text, parseContext(repo, fromLanguage, toLanguage))
        } catch (e: RuleParseException) {
            badRequest(e.message ?: "Cannot parse rule")
        }

        val rule = repo.addRule(
            params.name,
            fromLanguage,
            toLanguage,
            logic,
            params.addedCategories.nullize(),
            params.replacedCategories.nullize(),
            parsePOSList(params.fromPOS, fromLanguage),
            params.toPOS.nullize(),
            parseSourceRefs(repo, params.source),
            params.notes
        )
        return rule.toViewModel(repo)
    }

    private fun parseContext(repo: GraphRepository, fromLanguage: Language, toLanguage: Language): RuleParseContext =
        RuleParseContext(fromLanguage, toLanguage) {
            RuleRef.to(repo.resolveRule(it))
        }

    @PostMapping("/{graph}/rule/{id}", consumes = ["application/json"])
    @ResponseBody
    fun updateRule(repo: GraphRepository, @PathVariable id: Int, @RequestBody params: UpdateRuleParameters): RuleViewModel {
        val fromLanguage = repo.resolveLanguage(params.fromLang)
        val toLanguage = repo.resolveLanguage(params.toLang)
        val rule = repo.resolveRule(id)

        if (rule.name != params.name && repo.ruleByName(params.name) != null) {
            badRequest("Rule named '${params.name} already exists")
        }
        if (' ' in params.name) {
            badRequest("Rule names may not contain spaces")
        }

        rule.name = params.name
        rule.fromLanguage = fromLanguage
        rule.toLanguage = toLanguage
        rule.logic = Rule.parseBranches(params.text, parseContext(repo, rule.fromLanguage, rule.toLanguage))
        rule.notes = params.notes
        rule.addedCategories = params.addedCategories.nullize()
        rule.replacedCategories = params.replacedCategories.nullize()
        rule.fromPOS = parsePOSList(params.fromPOS, fromLanguage)
        rule.toPOS = params.toPOS.nullize()
        rule.source = parseSourceRefs(repo, params.source)
        return rule.toViewModel(repo)
    }

    @PostMapping("/{graph}/rule/{id}/delete", consumes = ["application/json"])
    @ResponseBody
    fun deleteRule(repo: GraphRepository, @PathVariable id: Int) {
        val rule = repo.resolveRule(id)
        repo.deleteRule(rule)
    }

    data class RuleSequenceViewModel(
        val name: String,
        val fromLang: String,
        val toLang: String
    )

    data class UpdateSequenceParams(
        val name: String,
        val fromLang: String,
        val toLang: String,
        val ruleNames: String
    )

    @PostMapping("/{graph}/rule/sequence", consumes = ["application/json"])
    fun newSequence(repo: GraphRepository, @RequestBody params: UpdateSequenceParams): RuleSequenceViewModel {
        val (fromLanguage, toLanguage, rules) = resolveUpdateSequenceParams(repo, params)
        val sequence = repo.addRuleSequence(params.name, fromLanguage, toLanguage, rules)
        return sequence.toViewModel()
    }

    private fun RuleSequence.toViewModel() = RuleSequenceViewModel(
        name, fromLanguage.shortName, toLanguage.shortName
    )

    private fun resolveUpdateSequenceParams(
        repo: GraphRepository,
        params: UpdateSequenceParams
    ): Triple<Language, Language, List<RuleSequenceStep>> {
        val fromLanguage = repo.resolveLanguage(params.fromLang)
        val toLanguage = repo.resolveLanguage(params.toLang)
        val rules = params.ruleNames.split('\n').filter { it.isNotBlank() }.map {
            var name = it.trim()
            val optional = name.endsWith("?")
            name = name.removeSuffix("?")
            val rule = if (name.startsWith("sequence:")) {
                val sequenceName = name.removePrefix("sequence:").trim()
                repo.ruleSequenceByName(sequenceName)
                    ?: badRequest("Cannot find rule sequence $sequenceName")
            }
            else {
                repo.resolveRule(name)
            }
            RuleSequenceStep(rule, optional)
        }
        return Triple(fromLanguage, toLanguage, rules)
    }

    @PostMapping("/{graph}/rule/sequence/{id}", consumes = ["application/json"])
    fun updateSequence(repo: GraphRepository, @PathVariable id: Int, @RequestBody params: UpdateSequenceParams): RuleSequenceViewModel {
        val sequence = repo.resolveRuleSequence(id)
        val (fromLanguage, toLanguage, steps) = resolveUpdateSequenceParams(repo, params)
        sequence.name = params.name
        sequence.fromLanguage = fromLanguage
        sequence.toLanguage = toLanguage
        sequence.steps = steps.map { RuleSequenceStepRef(it.rule.id, it.optional) }
        return sequence.toViewModel()
    }

    data class ApplySequenceParams(
        val linkFromId: Int,
        val linkToId: Int
    )

    @PostMapping("/{graph}/rule/sequence/{id}/apply", consumes = ["application/json"])
    @ResponseBody
    fun applySequence(repo: GraphRepository, @PathVariable id: Int, @RequestBody params: ApplySequenceParams): WordController.LinkWordViewModel {
        val sequence = repo.resolveRuleSequence(id)
        val fromEntity = repo.resolveWord(params.linkFromId)
        val toEntity = repo.resolveWord(params.linkToId)
        val link = repo.findLink(fromEntity, toEntity, Link.Origin)
            ?: badRequest("No Derived link from ${params.linkFromId} to ${params.linkToId}")
        repo.applyRuleSequence(link, sequence)
        return linkToViewModel(link, repo, true)
    }

    data class RuleTraceParams(
        val word: String = "",
        val reverse: Boolean = false
    )

    data class RuleTraceResult(
        val trace: String,
        val result: String
    )

    @PostMapping("/{graph}/rule/{id}/trace", consumes = ["application/json"])
    fun trace(repo: GraphRepository, @PathVariable id: Int, @RequestBody params: RuleTraceParams): RuleTraceResult {
        val rule = repo.resolveRule(id)
        if (params.word.isBlank()) {
            badRequest("Cannot trace rule for empty word")
        }
        val existingWord = repo.wordsByText(rule.fromLanguage, params.word).singleOrNull()
        val word = existingWord ?: Word(-1, params.word, rule.fromLanguage)
        val trace = RuleTrace()
        val result = if (params.reverse)
            rule.reverseApply(word, repo, trace).joinToString(", ")
        else
            rule.apply(word, repo, trace).text

        return RuleTraceResult(trace.log(), result)
    }
}

fun parsePOSList(pos: String?, language: Language): List<String> {
    val posList = parseList(pos)
    for (posEntry in posList) {
        if (!language.pos.any { it.abbreviation == posEntry }) {
            badRequest("Unknown POS '$posEntry'")
        }
    }
    return posList
}
