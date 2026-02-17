package ru.yole.etymograph.web.controllers

import kotlinx.serialization.Serializable
import org.springframework.web.bind.annotation.*
import ru.yole.etymograph.Language
import ru.yole.etymograph.GraphRepository
import ru.yole.etymograph.WordKind
import ru.yole.etymograph.web.resolveLanguage
import java.text.Normalizer

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
        repo: GraphRepository,
        @PathVariable lang: String,
        @RequestParam(required = false) letter: String?
    ): DictionaryViewModel {
        return loadDictionary(repo, lang, WordKind.NORMAL, letter)
    }

    @GetMapping("/{lang}/compounds")
    fun dictionaryCompound(
        repo: GraphRepository,
        @PathVariable lang: String,
        @RequestParam(required = false) letter: String?
    ): DictionaryViewModel {
        return loadDictionary(repo, lang, WordKind.COMPOUND, letter)
    }

    @GetMapping("/{lang}/names")
    fun dictionaryNames(
        repo: GraphRepository,
        @PathVariable lang: String,
        @RequestParam(required = false) letter: String?
    ): DictionaryViewModel {
        return loadDictionary(repo, lang, WordKind.NAME, letter)
    }

    @GetMapping("/{lang}/reconstructed")
    fun dictionaryReconstructed(
        repo: GraphRepository,
        @PathVariable lang: String,
        @RequestParam(required = false) letter: String?
    ): DictionaryViewModel {
        return loadDictionary(repo, lang, WordKind.RECONSTRUCTED, letter)
    }

    @GetMapping("/{lang}/all")
    fun allWords(
        repo: GraphRepository,
        @PathVariable lang: String,
        @RequestParam(required = false) letter: String?
    ): DictionaryViewModel {
        return loadDictionary(repo, lang, null, letter)
    }

    private fun loadDictionary(repo: GraphRepository, lang: String, wordKind: WordKind?, letter: String?): DictionaryViewModel {
        val language = repo.resolveLanguage(lang)
        val words = if (wordKind == null)
            repo.allWords(language)
        else
            repo.filteredWords(language, wordKind)

        val mapped = words.map {
            DictionaryWordViewModel(
                it.toRefViewModel(repo),
                it.fullGloss,
                it.getOrComputePOS(repo)
            )
        }

        val filtered = letter?.let { l ->
            val key = normalizeLetter(l)
            mapped.filter { groupName(repo, language, it) == key }
        } ?: mapped

        return DictionaryViewModel(language.shortName, language.name, language.syllabographic,
            filtered, groupWords(repo, language, filtered))
    }

    private fun groupName(repo: GraphRepository, language: Language, word: DictionaryWordViewModel): String {
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

    private fun groupWords(repo: GraphRepository, language: Language, words: List<DictionaryWordViewModel>): Map<String, List<DictionaryWordViewModel>> {
        val comparator = compareBy<String> { it.lowercase() }
            .thenBy { it == it.uppercase() && it != it.lowercase() }
            .thenBy { it }
        return words.groupBy { groupName(repo, language, it) }
            .mapValues { (_, list) ->
                list.sortedWith(compareBy({ it.ref.text.lowercase() }, { it.ref.id }))
            }
            .toSortedMap(comparator)
    }
}
