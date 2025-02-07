package ru.yole.etymograph.web.controllers

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.yole.etymograph.*
import ru.yole.etymograph.web.parseSourceRefs
import ru.yole.etymograph.web.resolveEntity
import ru.yole.etymograph.web.resolveRule

@RestController
@RequestMapping("/{graph}/link")
class LinkController {
    data class LinkParams(
        val fromEntity: Int = -1,
        val toEntity: Int = -1,
        val linkType: String = "",
        val ruleNames: String = "",
        val source: String = "",
        val notes: String? = null
    )

    data class ResolvedLinkParams(val fromEntity: LangEntity, val toEntity: LangEntity, val linkType: LinkType)

    @PostMapping("")
    fun addLink(repo: GraphRepository, @RequestBody params: LinkParams) {
        val (fromEntity, toEntity, linkType) = resolveLinkParams(repo, params)
        val rules = resolveRuleNames(repo, params)
        val source = parseSourceRefs(repo, params.source)

        repo.addLink(fromEntity, toEntity, linkType, rules, null, source, params.notes.nullize())
    }

    data class RuleLinkParams(
        val fromEntity: Int = -1,
        val toRuleName: String = "",
        val linkType: String = "",
        val source: String = "",
        val notes: String? = null
    )

    @PostMapping("/rule")
    fun addRuleLink(repo: GraphRepository, @RequestBody params: RuleLinkParams) {
        val fromEntity = repo.resolveEntity(params.fromEntity)
        val toRule = repo.resolveRule(params.toRuleName)
        val linkType = resolveLinkType(params.linkType)
        val source = parseSourceRefs(repo, params.source)

        repo.addLink(fromEntity, toRule, linkType, emptyList(), null, source, params.notes.nullize())
    }

    private fun resolveLinkParams(repo: GraphRepository, params: LinkParams): ResolvedLinkParams {
        return ResolvedLinkParams(
            repo.resolveEntity(params.fromEntity),
            repo.resolveEntity(params.toEntity),
            resolveLinkType(params.linkType),
        )
    }

    private fun resolveLinkType(linkType: String) =
        (Link.allLinkTypes.find { it.id == linkType }
            ?: badRequest("No link type '$linkType'"))

    private fun resolveRuleNames(repo: GraphRepository, params: LinkParams): List<Rule> {
        return params.ruleNames
            .takeIf { it.isNotBlank() }
            ?.split(',')
            ?.map { repo.resolveRule(it) }
            ?: emptyList()
    }

    @PostMapping("/update")
    fun updateLink(repo: GraphRepository, @RequestBody params: LinkParams) {
        val (fromEntity, toEntity, linkType) = resolveLinkParams(repo, params)

        val link = repo.findLink(fromEntity, toEntity, linkType)
            ?: badRequest("No such link")
        val rules = resolveRuleNames(repo, params)

        link.rules = rules
        link.source = parseSourceRefs(repo, params.source)
        link.notes = params.notes.nullize()
    }

    @PostMapping("/delete")
    fun deleteLink(repo: GraphRepository, @RequestBody params: LinkParams): Boolean {
        val (fromWord, toWord, linkType) = resolveLinkParams(repo, params)

        return (repo.deleteLink(fromWord, toWord, linkType) || repo.deleteLink(toWord, fromWord, linkType))
    }
}
