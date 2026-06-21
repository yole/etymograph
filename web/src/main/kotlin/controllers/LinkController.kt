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
    fun addLink(graph: Graph, @RequestBody params: LinkParams) {
        val (fromEntity, toEntity, linkType) = resolveLinkParams(graph, params)
        val rules = resolveRuleNames(graph, params)
        val source = parseSourceRefs(graph, params.source)

        graph.addLink(fromEntity, toEntity, linkType, rules, null, source, params.notes.nullize())
    }

    data class RuleLinkParams(
        val fromEntity: Int = -1,
        val toRuleName: String = "",
        val linkType: String = "",
        val source: String = "",
        val notes: String? = null
    )

    @PostMapping("/rule")
    fun addRuleLink(graph: Graph, @RequestBody params: RuleLinkParams) {
        val fromEntity = graph.resolveEntity(params.fromEntity)
        val toRule = graph.resolveRule(params.toRuleName)
        val linkType = resolveLinkType(params.linkType)
        val source = parseSourceRefs(graph, params.source)

        graph.addLink(fromEntity, toRule, linkType, emptyList(), null, source, params.notes.nullize())
    }

    private fun resolveLinkParams(graph: Graph, params: LinkParams): ResolvedLinkParams {
        return ResolvedLinkParams(
            graph.resolveEntity(params.fromEntity),
            graph.resolveEntity(params.toEntity),
            resolveLinkType(params.linkType),
        )
    }

    private fun resolveLinkType(linkType: String) =
        (Link.allLinkTypes.find { it.id == linkType }
            ?: badRequest("No link type '$linkType'"))

    private fun resolveRuleNames(graph: Graph, params: LinkParams): List<Rule> {
        return params.ruleNames
            .takeIf { it.isNotBlank() }
            ?.split(',')
            ?.map { graph.resolveRule(it) }
            ?: emptyList()
    }

    @PostMapping("/update")
    fun updateLink(graph: Graph, @RequestBody params: LinkParams) {
        val (fromEntity, toEntity, linkType) = resolveLinkParams(graph, params)

        val link = graph.findLink(fromEntity, toEntity, linkType)
            ?: badRequest("No such link")
        val rules = resolveRuleNames(graph, params)

        if (link.rules != rules) {
            link.sequence = null
        }
        link.rules = rules
        link.source = parseSourceRefs(graph, params.source)
        link.notes = params.notes.nullize()
    }

    @PostMapping("/delete")
    fun deleteLink(graph: Graph, @RequestBody params: LinkParams): Boolean {
        val (fromWord, toWord, linkType) = resolveLinkParams(graph, params)

        return (graph.deleteLink(fromWord, toWord, linkType) || graph.deleteLink(toWord, fromWord, linkType))
    }

    @PostMapping("/refreshSequence")
    fun refreshLinkSequence(graph: Graph, @RequestBody params: LinkParams) {
        val (fromEntity, toEntity, linkType) = resolveLinkParams(graph, params)

        val link = graph.findLink(fromEntity, toEntity, linkType)
            ?: badRequest("No such link")

        link.sequence?.let {
            graph.applyRuleSequence(link, it)
        }
    }
}
