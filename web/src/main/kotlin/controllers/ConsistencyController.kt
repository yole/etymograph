package page.yole.etymograph.web.controllers

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import page.yole.etymograph.*
import page.yole.etymograph.web.resolveLanguage

@RestController
class ConsistencyController {
    data class ConsistencyIssueGroupViewModel(
        val type: String,
        val issues: List<RichText>
    )

    data class ConsistencyViewModel(
        val language: String,
        val languageFullName: String,
        val issueGroups: List<ConsistencyIssueGroupViewModel>
    )

    @GetMapping("/{graph}/consistency/{lang}")
    fun consistency(graph: Graph, @PathVariable lang: String): ConsistencyViewModel {
        val language = graph.resolveLanguage(lang)
        val groups = consistencyCheckers.mapNotNull { checker ->
            val issues = buildList {
                checker.check(graph, language) { add(it.toRichText()) }
            }
            if (issues.isEmpty()) null else ConsistencyIssueGroupViewModel(checker.name, issues)
        }
        return ConsistencyViewModel(language.shortName, language.name, groups)
    }

    private fun ConsistencyCheckerIssue.toRichText(): RichText {
        return RichText(description.fragments.map { fragment ->
            when (val target = entity) {
                is Word -> if (fragment.linkType == "word") fragment.copy(
                    linkData = urlKey(target.language, target.text, target.syllabographic) ?: target.text.lowercase()
                ) else fragment
                is CorpusText -> fragment.copy(linkType = "corpusText", linkId = target.id)
                is Rule -> fragment.copy(linkType = "rule", linkId = target.id)
                else -> error("Unsupported consistency issue entity: $target")
            }
        })
    }
}
