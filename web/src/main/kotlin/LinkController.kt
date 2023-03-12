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
    data class LinkParams(val fromWord: Int = -1, val toWord: Int = -1, val linkType: String = "", val ruleNames: String = "")
    data class ResolvedLinkParams(val fromEntity: LangEntity, val toEntity: LangEntity, val linkType: LinkType)

    @PostMapping("/link")
    fun addLink(@RequestBody params: LinkParams) {
        val graph = graphService.graph
        val (fromEntity, toEntity, linkType) = resolveLinkParams(params)
        val rules = resolveRuleNames(params)

        graph.addLink(fromEntity, toEntity, linkType, rules, null, null)
        graph.save()
    }

    private fun resolveLinkParams(params: LinkParams): ResolvedLinkParams {
        val graph = graphService.graph
        return ResolvedLinkParams(
            graph.langEntityById(params.fromWord)
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No word or rule with ID ${params.fromWord}"),
            graph.langEntityById(params.toWord)
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No word or rule with ID ${params.toWord}"),
            Link.allLinkTypes.find { it.id == params.linkType } ?: throw NoLinkTypeException()
        )
    }

    private fun resolveRuleNames(params: LinkParams): List<Rule> {
        return params.ruleNames
            .takeIf { it.isNotBlank() }
            ?.split(',')
            ?.map { graphService.graph.ruleByName(it.trim()) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No rule named $it") }
            ?: emptyList()
    }

    @PostMapping("/link/update")
    fun updateLink(@RequestBody params: LinkParams) {
        val (fromEntity, toEntity, linkType) = resolveLinkParams(params)

        val graph = graphService.graph
        val link = graph.findLink(fromEntity, toEntity, linkType) ?: throw NoLinkException()
        val rules = resolveRuleNames(params)

        link.rules = rules
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

@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "No such link type")
class NoLinkTypeException : RuntimeException()

@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "No such link")
class NoLinkException : RuntimeException()
