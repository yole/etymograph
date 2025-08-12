package ru.yole.etymograph.web.controllers

import org.springframework.web.bind.annotation.*
import ru.yole.etymograph.GraphRepository
import ru.yole.etymograph.Language
import ru.yole.etymograph.WordKind
import ru.yole.etymograph.web.resolveLanguage
import java.text.Normalizer

@RestController
@RequestMapping("/{graph}/dictionary")
class DictionaryController {
    data class DictionaryWordViewModel(val id: Int, val text: String, val gloss: String, val fullGloss: String?, val homonym: Boolean, val pos: String?)
    data class DictionaryViewModel(val language: Language, val words: List<DictionaryWordViewModel>, val wordsByLetter: Map<String, List<DictionaryWordViewModel>>? = null)

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
                it.id, it.text,
                it.getOrComputeGloss(repo) ?: "", it.fullGloss,
                repo.isHomonym(it),
                it.getOrComputePOS(repo)
            )
        }

        val filtered = letter?.let { l ->
            val key = normalizeLetter(l)
            mapped.filter { normalizeLetter(it.text) == key }
        } ?: mapped

        return DictionaryViewModel(language, filtered, groupWords(filtered))
    }

    private fun normalizeLetter(input: String): String {
        if (input.isEmpty()) return "#"
        val first = input.trim().ifEmpty { return "#" }[0].toString()
        val normalized = Normalizer.normalize(first, Normalizer.Form.NFKD)
        // Remove combining marks
        val base = normalized.codePoints()
            .filter {
                val t = Character.getType(it)
                t != Character.NON_SPACING_MARK.toInt() && t != Character.COMBINING_SPACING_MARK.toInt()
            }
            .toArray()
        val ch = if (base.isNotEmpty()) base[0] else first.codePointAt(0)
        return if (Character.isLetter(ch)) String(Character.toChars(Character.toUpperCase(ch))) else "#"
    }

    private fun groupWords(words: List<DictionaryWordViewModel>): Map<String, List<DictionaryWordViewModel>> {
        return words.groupBy { normalizeLetter(it.text) }
            .mapValues { (_, list) ->
                list.sortedWith(compareBy<DictionaryWordViewModel>({ it.text.lowercase() }, { it.id }))
            }
            .toSortedMap()
    }
}
