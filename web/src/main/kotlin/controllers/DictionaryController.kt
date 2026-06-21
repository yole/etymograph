package page.yole.etymograph.web.controllers

import kotlinx.serialization.Serializable
import org.springframework.web.bind.annotation.*
import page.yole.etymograph.Language
import page.yole.etymograph.Graph
import page.yole.etymograph.WordKind
import page.yole.etymograph.web.resolveLanguage

@RestController
@RequestMapping("/{graph}/dictionary")
class DictionaryController {
    @Serializable
    data class DictionaryWordViewModel(
        val ref: WordRefViewModel,
        val fullGloss: String?,
        val pos: String?,
    )

    data class DictionaryViewModel(
        val language: String,
        val languageFullName: String,
        val languageSyllabographic: Boolean,
        val words: List<DictionaryWordViewModel>,
        val wordsByLetter: Map<String, List<DictionaryWordViewModel>>? = null
    )

    @GetMapping("/{lang}")
    fun dictionary(
        graph: Graph,
        @PathVariable lang: String,
        @RequestParam(required = false) letter: String?
    ): DictionaryViewModel {
        return loadDictionary(graph, lang, WordKind.NORMAL, letter)
    }

    @GetMapping("/{lang}/compounds")
    fun dictionaryCompound(
        graph: Graph,
        @PathVariable lang: String,
        @RequestParam(required = false) letter: String?
    ): DictionaryViewModel {
        return loadDictionary(graph, lang, WordKind.COMPOUND, letter)
    }

    @GetMapping("/{lang}/names")
    fun dictionaryNames(
        graph: Graph,
        @PathVariable lang: String,
        @RequestParam(required = false) letter: String?
    ): DictionaryViewModel {
        return loadDictionary(graph, lang, WordKind.NAME, letter)
    }

    @GetMapping("/{lang}/reconstructed")
    fun dictionaryReconstructed(
        graph: Graph,
        @PathVariable lang: String,
        @RequestParam(required = false) letter: String?
    ): DictionaryViewModel {
        return loadDictionary(graph, lang, WordKind.RECONSTRUCTED, letter)
    }

    @GetMapping("/{lang}/all")
    fun allWords(
        graph: Graph,
        @PathVariable lang: String,
        @RequestParam(required = false) letter: String?
    ): DictionaryViewModel {
        return loadDictionary(graph, lang, null, letter)
    }

    private fun loadDictionary(graph: Graph, lang: String, wordKind: WordKind?, letter: String?): DictionaryViewModel {
        val language = graph.resolveLanguage(lang)
        val words = if (wordKind == null)
            graph.allWords(language)
        else
            graph.filteredWords(language, wordKind)

        val mapped = words.map {
            DictionaryWordViewModel(
                it.toRefViewModel(),
                it.fullGloss,
                it.getOrComputePOS()
            )
        }

        val filtered = letter?.let { l ->
            val key = normalizeLetter(l)
            mapped.filter { groupName(language, it) == key }
        } ?: mapped

        return DictionaryViewModel(language.shortName, language.name, language.syllabographic,
            filtered, groupWords(language, filtered))
    }

    private fun groupName(language: Language, word: DictionaryWordViewModel): String {
        val syllabograms = word.ref.syllabogramSequence?.syllabograms
        if (syllabograms != null) {
            val firstNonDeterminative = syllabograms.dropWhile { it.type.isDeterminative }.firstOrNull()
            if (firstNonDeterminative != null && firstNonDeterminative.type.isLogogram) {
                return firstNonDeterminative.text[0].toString()
            }
        }
        val firstGrapheme = language.orthoPhonemeLookup.nextPhoneme(word.ref.text, 0)
        return normalizeLetter(firstGrapheme)
    }

    private fun normalizeLetter(input: String): String {
        if (input.isEmpty()) return "#"
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return "#"
        return input
    }

    private fun groupWords(language: Language, words: List<DictionaryWordViewModel>): Map<String, List<DictionaryWordViewModel>> {
        val comparator = compareBy<String> { it.lowercase() }
            .thenBy { it == it.uppercase() && it != it.lowercase() }
            .thenBy { it }
        return words.groupBy { groupName(language, it) }
            .mapValues { (_, list) ->
                list.sortedWith(compareBy({ it.ref.text.lowercase() }, { it.ref.id }))
            }
            .toSortedMap(comparator)
    }
}
