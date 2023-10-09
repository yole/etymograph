package ru.yole.etymograph.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import ru.yole.etymograph.*

@RestController
class LinkController(val graphService: GraphService) {
    data class LinkParams(
        val fromEntity: Int = -1,
        val toEntity: Int = -1,
        val linkType: String = "",
        val ruleNames: String = "",
        val source: String = ""
    )

    data class ResolvedLinkParams(val fromEntity: LangEntity, val toEntity: LangEntity, val linkType: LinkType)

    @PostMapping("/link")
    fun addLink(@RequestBody params: LinkParams) {
        val graph = graphService.graph
        val (fromEntity, toEntity, linkType) = resolveLinkParams(params)
        val rules = resolveRuleNames(params)
        val source = parseSourceRefs(graph, params.source)

        graph.addLink(fromEntity, toEntity, linkType, rules, source, null)
        graph.save()
    }

    data class RuleLinkParams(val fromEntity: Int = -1, val toRuleName: String = "", val linkType: String = "")

    @PostMapping("/link/rule")
    fun addRuleLink(@RequestBody params: RuleLinkParams) {
        val graph = graphService.graph
        val fromEntity = graphService.resolveEntity(params.fromEntity)
        val toRule = graphService.resolveRule(params.toRuleName)
        val linkType = resolveLinkType(params.linkType)

        graph.addLink(fromEntity, toRule, linkType, emptyList(), emptyList(), null)
        graph.save()
    }

    private fun resolveLinkParams(params: LinkParams): ResolvedLinkParams {
        return ResolvedLinkParams(
            graphService.resolveEntity(params.fromEntity),
            graphService.resolveEntity(params.toEntity),
            resolveLinkType(params.linkType),
        )
    }

    private fun resolveLinkType(linkType: String) =
        (Link.allLinkTypes.find { it.id == linkType }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No link type '$linkType'"))

    private fun resolveRuleNames(params: LinkParams): List<Rule> {
        return params.ruleNames
            .takeIf { it.isNotBlank() }
            ?.split(',')
            ?.map { graphService.resolveRule(it) }
            ?: emptyList()
    }

    @PostMapping("/link/update")
    fun updateLink(@RequestBody params: LinkParams) {
        val (fromEntity, toEntity, linkType) = resolveLinkParams(params)

        val graph = graphService.graph
        val link = graph.findLink(fromEntity, toEntity, linkType) ?: throw NoLinkException()
        val rules = resolveRuleNames(params)

        link.rules = rules
        link.source = parseSourceRefs(graph, params.source)
        graph.save()
    }

    @PostMapping("/link/delete")
    fun deleteLink(@RequestBody params: LinkParams): Boolean {
        val graph = graphService.graph
        val (fromWord, toWord, linkType) = resolveLinkParams(params)

        return (graph.deleteLink(fromWord, toWord, linkType) || graph.deleteLink(toWord, fromWord, linkType)).also {
            graph.save()
        }
    }
}
@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "No such link")
class NoLinkException : RuntimeException()
