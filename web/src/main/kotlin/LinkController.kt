package ru.yole.etymograph.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import ru.yole.etymograph.Link

@RestController
class LinkController(val graphService: GraphService) {
    data class LinkParams(val fromWord: Int = -1, val toWord: Int = -1, val linkType: String = "")

    @PostMapping("/link")
    fun addLink(@RequestBody params: LinkParams) {
        val graph = graphService.graph
        val fromWord = graph.wordById(params.fromWord) ?: throw NoWordException()
        val toWord = graph.wordById(params.toWord) ?: throw NoWordException()
        val linkType = Link.allLinkTypes.find { it.id == params.linkType } ?: throw NoLinkTypeException()

        graph.addLink(fromWord, toWord, linkType, emptyList(), null, null)
        graph.save()
    }

    @PostMapping("/link/delete")
    fun deleteLink(@RequestBody params: LinkParams): Boolean {
        val graph = graphService.graph
        val fromWord = graph.wordById(params.fromWord) ?: throw NoWordException()
        val toWord = graph.wordById(params.toWord) ?: throw NoWordException()
        val linkType = Link.allLinkTypes.find { it.id == params.linkType } ?: throw NoLinkTypeException()

        return graph.deleteLink(fromWord, toWord, linkType).also {
            graph.save()
        }
    }
}

@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "No such link type")
class NoLinkTypeException : RuntimeException()
