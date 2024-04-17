package ru.yole.etymograph.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.yole.etymograph.*
import ru.yole.etymograph.web.CorpusController.TranslationViewModel

@RestController
@CrossOrigin(origins = ["http://localhost:3000"])
class CorpusController(val graphService: GraphService) {
    data class CorpusLangTextViewModel(val id: Int, val title: String)
    data class CorpusLangViewModel(val language: Language, val corpusTexts: List<CorpusLangTextViewModel>)

    @GetMapping("/{graph}/corpus")
    fun indexJson(@PathVariable graph: String): List<CorpusLangTextViewModel> {
        return graphService.resolveGraph(graph).allCorpusTexts()
                .sortedBy { it.title }
                .map { it.toLangViewModel() }
    }

    @GetMapping("/{graph}/corpus/{lang}")
    fun langIndexJson(@PathVariable graph: String, @PathVariable lang: String): CorpusLangViewModel {
        val language = graphService.resolveLanguage(graph, lang)
        return CorpusLangViewModel(
            language,
            graphService.resolveGraph(graph).corpusTextsInLanguage(language)
                .sortedBy { it.title }
                .map { it.toLangViewModel() }
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

    @GetMapping("/{graph}/corpus/text/{id}")
    fun textJson(@PathVariable graph: String, @PathVariable id: Int): CorpusTextViewModel {
        val repo = graphService.resolveGraph(graph)
        return graphService.resolveCorpusText(graph, id).toViewModel(repo)
    }

    private fun CorpusText.toViewModel(repo: GraphRepository): CorpusTextViewModel {
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
                translationToViewModel(it, repo)
            }
        )
    }

    data class CorpusTextParams(val title: String = "", val text: String = "", val source: String = "", val notes: String = "")

    @PostMapping("/{graph}/corpus/{lang}/new", consumes = ["application/json"])
    @ResponseBody
    fun newText(@PathVariable graph: String, @PathVariable lang: String, @RequestBody params: CorpusTextParams): CorpusTextViewModel {
        val repo = graphService.resolveGraph(graph)
        val language = graphService.resolveLanguage(graph, lang)
        val text = repo.addCorpusText(
            params.text, params.title.nullize(), language, emptyList(),
            parseSourceRefs(repo, params.source), params.notes.nullize()
        )
        return text.toViewModel(repo)
    }

    @PostMapping("/{graph}/corpus/text/{id}")
    fun editText(@PathVariable graph: String, @PathVariable id: Int, @RequestBody params: CorpusTextParams) {
        val corpusText = graphService.resolveCorpusText(graph, id)
        val repo = graphService.resolveGraph(graph)
        corpusText.text = params.text
        corpusText.title = params.title.nullize()
        corpusText.source = parseSourceRefs(repo, params.source)
        corpusText.notes = params.notes.nullize()
    }

    data class AssociateWordParameters(val index: Int, val wordId: Int = -1)

    @PostMapping("/{graph}/corpus/text/{id}/associate")
    fun associateWord(@PathVariable graph: String, @PathVariable id: Int, @RequestBody params: AssociateWordParameters) {
        val corpusText = graphService.resolveCorpusText(graph, id)
        val word = graphService.resolveWord(graph, params.wordId)
        corpusText.associateWord(params.index, word)
    }

    data class AlternativeViewModel(val gloss: String, val wordId: Int, val ruleId: Int)

    @GetMapping("/{graph}/corpus/text/{id}/alternatives/{index}")
    fun requestAlternatives(@PathVariable graph: String, @PathVariable id: Int, @PathVariable index: Int): List<AlternativeViewModel> {
        val repo = graphService.resolveGraph(graph)
        val corpusText = graphService.resolveCorpusText(graph, id)
        val word = corpusText.words.getOrNull(index)
        val wordText = word?.text ?: corpusText.normalizedWordTextAt(index)
        val wordsWithMatchingText = repo.wordsByText(corpusText.language, wordText)
        val allVariants = wordsWithMatchingText.flatMap {
            val gloss = it.getOrComputeGloss(repo)
            if (gloss == null)
                emptyList()
            else {
                val baseWord = if (it == wordsWithMatchingText.first())
                    emptyList()
                else
                    listOf(AlternativeViewModel(gloss, it.id, -1))
                if (it.glossOrNP() == null) {
                    baseWord
                }
                else {
                    val alts = repo.requestAlternatives(it)
                    baseWord + alts.map { pc ->
                        val rule = pc.rules.single()
                        AlternativeViewModel(rule.applyCategories(gloss), it.id, rule.id)
                    }
                }
            }
        }
        return allVariants.associateBy { it.gloss }.values.toList()
    }

    data class AcceptAlternativeParameters(val index: Int, val wordId: Int, val ruleId: Int)

    @PostMapping("/{graph}/corpus/text/{id}/accept")
    fun acceptAlternative(@PathVariable graph: String, @PathVariable id: Int, @RequestBody params: AcceptAlternativeParameters) {
        val corpusText = graphService.resolveCorpusText(graph, id)
        val word = graphService.resolveWord(graph, params.wordId)
        val repo = graphService.resolveGraph(graph)

        if (params.ruleId == -1) {
            corpusText.associateWord(params.index, word)
        }
        else {
            val rule = graphService.resolveRule(graph, params.ruleId)
            val gloss = word.glossOrNP()
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Accepting alternative with unglossed word ${word.id}")

            val linkedWord = repo.getLinksTo(word).singleOrNull { it.rules == listOf(rule) }?.fromEntity as? Word
            if (linkedWord != null) {
                corpusText.associateWord(params.index, linkedWord)
            }
            else {
                val newGloss = rule.applyCategories(gloss)
                val newWord = repo.findOrAddWord(word.text, word.language, newGloss)
                repo.addLink(newWord, word, Link.Derived, listOf(rule), emptyList(), null)
                newWord.gloss = null

                corpusText.associateWord(params.index, newWord)
            }

        }
    }
}

fun translationToViewModel(t: Translation, repo: GraphRepository): TranslationViewModel =
    TranslationViewModel(t.id, t.text, t.source.toViewModel(repo), t.source.toEditableText(repo))
