package ru.yole.etymograph.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.yole.etymograph.*
import java.util.*

@RestController
@CrossOrigin(origins = ["http://localhost:3000"])
class CorpusController(val graphService: GraphService) {
    data class CorpusLangTextViewModel(val id: Int, val title: String)
    data class CorpusLangViewModel(val language: Language, val corpusTexts: List<CorpusLangTextViewModel>)
    data class CorpusListViewModel(val corpusTexts: List<CorpusLangTextViewModel>)

    @GetMapping("/corpus")
    fun indexJson(): CorpusListViewModel {
        return CorpusListViewModel(graphService.graph.allCorpusTexts().sortedBy { it.title }.map { it.toLangViewModel() })
    }

    @GetMapping("/corpus/{lang}")
    fun langIndexJson(@PathVariable lang: String): CorpusLangViewModel {
        val language = graphService.resolveLanguage(lang)
        return CorpusLangViewModel(
            language,
            graphService.graph.corpusTextsInLanguage(language).sortedBy { it.title }.map { it.toLangViewModel() }
        )
    }

    private fun CorpusText.toLangViewModel() =
        CorpusLangTextViewModel(id, title ?: text)

    data class CorpusWordViewModel(
        val index: Int,
        val text: String,  // CorpusWord.segmentedText
        val normalizedText: String, // CorpusWord.normalizedText
        val gloss: String, val wordId: Int?, val wordText: String?,
        val stressIndex: Int?, val stressLength: Int?, val homonym: Boolean,
    )

    data class TranslationViewModel(
        val id: Int,
        val text: String,
        val source: List<SourceRefViewModel>,
        val sourceEditableText: String
    )

    data class CorpusLineViewModel(val words: List<CorpusWordViewModel>)
    data class CorpusTextViewModel(
        val id: Int,
        val title: String,
        val language: String,
        val languageFullName: String,
        val text: String,
        val lines: List<CorpusLineViewModel>,
        val source: List<SourceRefViewModel>,
        val sourceEditableText: String,
        val notes: String?,
        val translations: List<TranslationViewModel>
    )

    @GetMapping("/corpus/text/{id}")
    fun textJson(@PathVariable id: Int): CorpusTextViewModel {
        return graphService.resolveCorpusText(id).toViewModel()
    }

    private fun CorpusText.toViewModel(): CorpusTextViewModel {
        val repo = graphService.graph
        return CorpusTextViewModel(
            id,
            title ?: "Untitled",
            language.shortName,
            language.name,
            text,
            mapToLines(repo).map { line ->
                CorpusLineViewModel(line.corpusWords.map { cw ->
                    CorpusWordViewModel(cw.index,
                        cw.segmentedText,
                        cw.normalizedText,
                        cw.segmentedGloss ?: "",
                        cw.word?.id, cw.word?.text,
                        cw.stressIndex, cw.stressLength, cw.homonym)
                })
            },
            source.toViewModel(repo),
            source.toEditableText(repo),
            notes,
            repo.translationsForText(this).map {
                TranslationViewModel(it.id, it.text, it.source.toViewModel(repo), it.source.toEditableText(repo))
            }
        )
    }

    data class CorpusTextParams(val title: String = "", val text: String = "", val source: String = "", val notes: String = "")

    @PostMapping("/corpus/{lang}/new", consumes = ["application/json"])
    @ResponseBody
    fun newText(@PathVariable lang: String, @RequestBody params: CorpusTextParams): CorpusTextViewModel {
        val repo = graphService.graph
        val language = graphService.resolveLanguage(lang)
        val text = repo.addCorpusText(
            params.text, params.title.nullize(), language, emptyList(),
            parseSourceRefs(repo, params.source), params.notes.nullize()
        )
        repo.save()
        return text.toViewModel()
    }

    @PostMapping("/corpus/text/{id}")
    fun editText(@PathVariable id: Int, @RequestBody params: CorpusTextParams) {
        val corpusText = graphService.resolveCorpusText(id)
        corpusText.text = params.text
        corpusText.title = params.title.nullize()
        corpusText.source = parseSourceRefs(graphService.graph, params.source)
        corpusText.notes = params.notes.nullize()
        graphService.graph.save()
    }

    data class AssociateWordParameters(val index: Int, val wordId: Int = -1)

    @PostMapping("/corpus/text/{id}/associate")
    fun associateWord(@PathVariable id: Int, @RequestBody params: AssociateWordParameters) {
        val corpusText = graphService.resolveCorpusText(id)
        val word = graphService.resolveWord(params.wordId)
        corpusText.associateWord(params.index, word)
        graphService.graph.save()
    }

    data class AlternativeViewModel(val gloss: String, val wordId: Int, val ruleId: Int)

    @GetMapping("/corpus/text/{id}/alternatives/{index}")
    fun requestAlternatives(@PathVariable id: Int, @PathVariable index: Int): List<AlternativeViewModel> {
        val corpusText = graphService.resolveCorpusText(id)
        val word = corpusText.words.getOrNull(index)
        val wordText = word?.text ?: corpusText.normalizedWordTextAt(index)
        val wordsWithMatchingText = graphService.graph.wordsByText(corpusText.language, wordText)
        return wordsWithMatchingText.flatMap {
            val gloss = it.glossOrNP()
            if (gloss == null)
                emptyList()
            else {
                val baseWord = if (it == wordsWithMatchingText.first())
                    emptyList()
                else
                    listOf(AlternativeViewModel(gloss, it.id, -1))
                val alts = graphService.graph.requestAlternatives(it)
                baseWord + alts.map { pc ->
                    val rule = pc.rules.single()
                    AlternativeViewModel(rule.applyCategories(gloss), it.id, rule.id)
                }
            }
        }
    }

    data class AcceptAlternativeParameters(val index: Int, val wordId: Int, val ruleId: Int)

    @PostMapping("/corpus/text/{id}/accept")
    fun acceptAlternative(@PathVariable id: Int, @RequestBody params: AcceptAlternativeParameters) {
        val corpusText = graphService.resolveCorpusText(id)
        val word = graphService.resolveWord(params.wordId)

        if (params.ruleId == -1) {
            corpusText.associateWord(params.index, word)
        }
        else {
            val rule = graphService.resolveRule(params.ruleId)
            val gloss = word.glossOrNP()
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Accepting alternative with unglossed word ${word.id}")

            val linkedWord = graphService.graph.getLinksTo(word).singleOrNull { it.rules == listOf(rule) }?.fromEntity as? Word
            if (linkedWord != null) {
                corpusText.associateWord(params.index, linkedWord)
            }
            else {
                val newGloss = rule.applyCategories(gloss)
                val graph = graphService.graph
                val newWord = graph.findOrAddWord(word.text, word.language, newGloss)
                graph.addLink(newWord, word, Link.Derived, listOf(rule), emptyList(), null)
                newWord.gloss = null

                corpusText.associateWord(params.index, newWord)
            }

        }

        graphService.graph.save()
    }
}
