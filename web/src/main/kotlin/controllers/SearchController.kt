package ru.yole.etymograph.web.controllers

import org.springframework.web.bind.annotation.*
import ru.yole.etymograph.Graph
import ru.yole.etymograph.Word
import ru.yole.etymograph.removeDiacritics
import java.util.*

@RestController
@RequestMapping("/{graph}/search")
class SearchController {
    enum class SearchMode { auto, exact, prefix }

    data class SearchResponse(
        val totalExact: Int,
        val totalPrefix: Int,
        val matches: List<WordRefViewModel>,
        val usedMode: String
    )

    @GetMapping("")
    fun search(
        repo: Graph,
        @RequestParam q: String,
        @RequestParam(required = false, defaultValue = "50") limit: Int,
        @RequestParam(required = false, defaultValue = "0") offset: Int,
        @RequestParam(required = false, defaultValue = "auto") mode: SearchMode
    ): SearchResponse {
        val query = q.trim()
        if (query.isEmpty()) {
            return SearchResponse(0, 0, emptyList(), if (mode == SearchMode.prefix) "prefix" else "exact")
        }

        val normQ = query.lowercase(Locale.FRANCE).removeDiacritics()

        val allWords = repo.allLanguages().flatMap { l -> repo.allWords(l) }

        val exactMatches = allWords.filter { w -> w.text.lowercase(Locale.FRANCE).removeDiacritics() == normQ }

        if (mode != SearchMode.prefix && exactMatches.isNotEmpty()) {
            val page = matchesToViewModel(exactMatches, offset, limit)
            return SearchResponse(exactMatches.size, 0, page, "exact")
        }

        val prefixMatches = allWords.filter { w -> w.text.lowercase(Locale.FRANCE).removeDiacritics().startsWith(normQ) }
        val totalPrefix = prefixMatches.size
        val page = matchesToViewModel(prefixMatches, offset, limit)
        return SearchResponse(0, totalPrefix, page, "prefix")
    }

    private fun matchesToViewModel(
        matches: List<Word>,
        offset: Int,
        limit: Int
    ): List<WordRefViewModel> {
        val page = matches
            .sortedWith(compareBy({ it.language.shortName }, { it.text.lowercase(Locale.ROOT) }, { it.id }))
            .drop(offset.coerceAtLeast(0))
            .take(limit.coerceAtLeast(0))
            .map { it.toRefViewModel() }
        return page
    }

}
