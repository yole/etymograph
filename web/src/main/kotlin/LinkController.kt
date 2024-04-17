package ru.yole.etymograph.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.yole.etymograph.*

@RestController
class LinkController(val graphService: GraphService) {
    data class LinkParams(
        val fromEntity: Int = -1,
        val toEntity: Int = -1,
        val linkType: String = "",
        val ruleNames: String = "",
        val source: String = "",
        val notes: String? = null
    )

    data class ResolvedLinkParams(val fromEntity: LangEntity, val toEntity: LangEntity, val linkType: LinkType)

    @PostMapping("/{graph}/link")
    fun addLink(@PathVariable graph: String, @RequestBody params: LinkParams) {
        val repo = graphService.resolveGraph(graph)
        val (fromEntity, toEntity, linkType) = resolveLinkParams(graph, params)
        val rules = resolveRuleNames(graph, params)
        val source = parseSourceRefs(repo, params.source)

        repo.addLink(fromEntity, toEntity, linkType, rules, source, params.notes.nullize())
    }

    data class RuleLinkParams(
        val fromEntity: Int = -1,
        val toRuleName: String = "",
        val linkType: String = "",
        val source: String = "",
        val notes: String? = null
    )

    @PostMapping("/{graph}/link/rule")
    fun addRuleLink(@PathVariable graph: String, @RequestBody params: RuleLinkParams) {
        val repo = graphService.resolveGraph(graph)
        val fromEntity = graphService.resolveEntity(graph, params.fromEntity)
        val toRule = graphService.resolveRule(graph, params.toRuleName)
        val linkType = resolveLinkType(params.linkType)
        val source = parseSourceRefs(repo, params.source)

        repo.addLink(fromEntity, toRule, linkType, emptyList(), source, params.notes.nullize())
    }

    private fun resolveLinkParams(graph: String, params: LinkParams): ResolvedLinkParams {
        return ResolvedLinkParams(
            graphService.resolveEntity(graph, params.fromEntity),
            graphService.resolveEntity(graph, params.toEntity),
            resolveLinkType(params.linkType),
        )
    }

    private fun resolveLinkType(linkType: String) =
        (Link.allLinkTypes.find { it.id == linkType }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No link type '$linkType'"))

    private fun resolveRuleNames(graph: String, params: LinkParams): List<Rule> {
        return params.ruleNames
            .takeIf { it.isNotBlank() }
            ?.split(',')
            ?.map { graphService.resolveRule(graph, it) }
            ?: emptyList()
    }

    @PostMapping("/{graph}/link/update")
    fun updateLink(@PathVariable graph: String, @RequestBody params: LinkParams) {
        val (fromEntity, toEntity, linkType) = resolveLinkParams(graph, params)

        val repo = graphService.resolveGraph(graph)
        val link = repo.findLink(fromEntity, toEntity, linkType)
            ?: badRequest("No such link")
        val rules = resolveRuleNames(graph, params)

        link.rules = rules
        link.source = parseSourceRefs(repo, params.source)
        link.notes = params.notes.nullize()
    }

    @PostMapping("/{graph}/link/delete")
    fun deleteLink(@PathVariable graph: String, @RequestBody params: LinkParams): Boolean {
        val repo = graphService.resolveGraph(graph)
        val (fromWord, toWord, linkType) = resolveLinkParams(graph, params)

        return (repo.deleteLink(fromWord, toWord, linkType) || repo.deleteLink(toWord, fromWord, linkType))
    }
}
