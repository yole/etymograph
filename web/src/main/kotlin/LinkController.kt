package ru.yole.etymograph.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import ru.yole.etymograph.Link
import ru.yole.etymograph.LinkType
import ru.yole.etymograph.Word

@RestController
class LinkController(val graphService: GraphService) {
    data class LinkParams(val fromWord: Int = -1, val toWord: Int = -1, val linkType: String = "", val ruleNames: String)
    data class ResolvedLinkParams(val fromWord: Word, val toWord: Word, val linkType: LinkType)

    @PostMapping("/link")
    fun addLink(@RequestBody params: LinkParams) {
        val graph = graphService.graph
        val (fromWord, toWord, linkType) = resolveLinkParams(params)
        val rules = params.ruleNames.split(',').map { graph.ruleByName(it) ?: throw NoRuleException() }

        graph.addLink(fromWord, toWord, linkType, rules, null, null)
        graph.save()
    }

    private fun resolveLinkParams(params: LinkParams): ResolvedLinkParams {
        val graph = graphService.graph
        return ResolvedLinkParams(
            graph.wordById(params.fromWord) ?: throw NoWordException(),
            graph.wordById(params.toWord) ?: throw NoWordException(),
            Link.allLinkTypes.find { it.id == params.linkType } ?: throw NoLinkTypeException()
        )
    }

    @PostMapping("/link/update")
    fun updateLink(@RequestBody params: LinkParams) {
        val (fromWord, toWord, linkType) = resolveLinkParams(params)

        val graph = graphService.graph
        val link = graph.findLink(fromWord, toWord, linkType) ?: throw NoLinkException()
        val rules = params.ruleNames.split(',').map { graph.ruleByName(it) ?: throw NoRuleException() }

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
