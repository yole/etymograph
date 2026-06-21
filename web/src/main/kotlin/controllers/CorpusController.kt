package ru.yole.etymograph.web.controllers

import kotlinx.serialization.Serializable
import org.springframework.web.bind.annotation.*
import ru.yole.etymograph.*
import ru.yole.etymograph.web.*
import ru.yole.etymograph.web.controllers.CorpusController.TranslationViewModel

@RestController
@RequestMapping("/{graph}/corpus")
class CorpusController {
    data class CorpusLangTextViewModel(val id: Int, val title: String)
    data class CorpusLangViewModel(
        val language: String,
        val languageFullName: String,
        val corpusTexts: List<CorpusLangTextViewModel>
    )

    @GetMapping("")
    fun allCorpusTexts(graph: Graph): List<CorpusLangTextViewModel> {
        return graph.allCorpusTexts()
            .sortedBy { it.title }
            .map { it.toLangViewModel() }
    }

    @GetMapping("/{lang}")
    fun langIndexJson(graph: Graph, @PathVariable lang: String): CorpusLangViewModel {
        val language = graph.resolveLanguage(lang)
        return CorpusLangViewModel(
            language.shortName,
            language.name,
            graph.corpusTextsInLanguage(language)
                .sortedBy { it.title }
                .map { it.toLangViewModel() }
        )
    }

    private fun CorpusText.toLangViewModel() =
        CorpusLangTextViewModel(id, title ?: text)

    @Serializable
    data class CorpusWordCandidateViewModel(val id: Int, val gloss: String?)

    @Serializable
    data class CorpusWordViewModel(
        val index: Int,
        val text: String,  // CorpusWord.segmentedText
        val normalizedText: String, // CorpusWord.normalizedText
        val syllabogramSequence: SyllabogramSequence?,
        val gloss: String,
        val contextGloss: String? = null,
        val wordId: Int? = null,
        val wordText: String? = null,
        val wordUrlKey: String? = null,
        val wordCandidates: List<CorpusWordCandidateViewModel>? = null,
        val stressIndex: Int? = null,
        val stressLength: Int? = null,
        val homonym: Boolean = false,
    )

    @Serializable
    data class TranslationViewModel(
        val id: Int,
        val text: String,
        val source: List<SourceRefViewModel>,
        val sourceEditableText: String,
        val anchorStartIndex: Int? = null,
        val anchorEndIndex: Int? = null
    )

    @Serializable
    data class CorpusLineViewModel(val words: List<CorpusWordViewModel>)

    @Serializable
    data class CorpusTextViewModel(
        val id: Int,
        val title: String,
        val language: String,
        val languageFullName: String,
        val syllabographic: Boolean,
        val text: String,
        val lines: List<CorpusLineViewModel>,
        val source: List<SourceRefViewModel>,
        val sourceEditableText: String,
        val notes: String?,
        val translations: List<TranslationViewModel>
    )

    @GetMapping("/text/{id}")
    fun textJson(graph: Graph, @PathVariable id: Int): CorpusTextViewModel {
        return graph.resolveCorpusText(id).toViewModel()
    }

    private fun CorpusText.toViewModel(): CorpusTextViewModel {
        val graph = language.graph
        return CorpusTextViewModel(
            id,
            title ?: "Untitled",
            language.shortName,
            language.name,
            language.syllabographic,
            text,
            mapToLines().map { line ->
                CorpusLineViewModel(line.corpusWords.map { cw ->
                    val wordUrlKey = (cw.word?.text ?: cw.wordCandidates?.firstOrNull()?.text)?.let {
                        urlKey(language, it, cw.word?.syllabographic ?: language.syllabographic)
                    }
                    CorpusWordViewModel(cw.index,
                        cw.segmentedText,
                        cw.normalizedText,
                        cw.syllabogramSequence,
                        cw.segmentedGloss ?: "",
                        cw.contextGloss,
                        cw.word?.id ?: cw.wordCandidates?.firstOrNull()?.id?.takeIf { wordUrlKey != null },
                        cw.word?.text, wordUrlKey,
                        cw.wordCandidates?.map { CorpusWordCandidateViewModel(it.id, it.getOrComputeGloss()) },
                        cw.stressIndex, cw.stressLength, cw.homonym)
                })
            },
            source.toViewModel(graph),
            source.toEditableText(graph),
            notes,
            graph.translationsForText(this).map {
                translationToViewModel(it, graph)
            }
        )
    }

    data class CorpusTextParams(val title: String = "", val text: String = "", val source: String = "", val notes: String = "")

    @PostMapping("/{lang}/new", consumes = ["application/json"])
    @ResponseBody
    fun newText(graph: Graph, @PathVariable lang: String, @RequestBody params: CorpusTextParams): CorpusTextViewModel {
        val language = graph.resolveLanguage(lang)
        val text = graph.addCorpusText(
            params.text, params.title.nullize(), language,
            parseSourceRefs(graph, params.source), params.notes.nullize()
        )
        return text.toViewModel()
    }

    @PostMapping("/text/{id}")
    fun editText(graph: Graph, @PathVariable id: Int, @RequestBody params: CorpusTextParams) {
        val corpusText = graph.resolveCorpusText(id)
        val oldToNewWordIndices = corpusText.updateText(params.text)
        graph.translationsForText(corpusText).forEach {
            it.remapAnchor(oldToNewWordIndices)
        }
        corpusText.title = params.title.nullize()
        corpusText.source = parseSourceRefs(graph, params.source)
        corpusText.notes = params.notes.nullize()
    }

    data class AssociateWordParameters(
        val index: Int,
        val wordId: Int = -1,
        val contextGloss: String? = null
    )

    @PostMapping("/text/{id}/associate")
    fun associateWord(graph: Graph, @PathVariable id: Int, @RequestBody params: AssociateWordParameters) {
        val corpusText = graph.resolveCorpusText(id)
        val word = graph.resolveWord(params.wordId)
        corpusText.associateWord(params.index, word, params.contextGloss)
    }

    @PostMapping("/text/{id}/lockAssociations")
    fun lockWordAssociations(graph: Graph, @PathVariable id: Int) {
        val corpusText = graph.resolveCorpusText(id)
        corpusText.lockWordAssociations()
    }

    data class AlternativeViewModel(val gloss: String, val wordId: Int, val ruleId: Int)

    @GetMapping("/text/{id}/alternatives/{index}")
    fun requestAlternatives(graph: Graph, @PathVariable id: Int, @PathVariable index: Int): List<AlternativeViewModel> {
        val corpusText = graph.resolveCorpusText(id)
        val word = corpusText.wordByIndex(index)
        val wordText = word?.text ?: corpusText.normalizedWordTextAt(index)
        val results = Alternatives.request(corpusText.language, wordText, word)
        return results.map {
            AlternativeViewModel(it.gloss, it.word.id, it.rule?.id ?: -1)
        }
    }

    data class AcceptAlternativeParameters(val index: Int, val wordId: Int, val ruleId: Int)

    @PostMapping("/text/{id}/accept")
    fun acceptAlternative(graph: Graph, @PathVariable id: Int, @RequestBody params: AcceptAlternativeParameters) {
        val corpusText = graph.resolveCorpusText(id)
        val word = graph.resolveWord(params.wordId)
        val rule = if (params.ruleId == -1) null else graph.resolveRule(params.ruleId)

        Alternatives.accept(corpusText, params.index, word, rule)
    }
}

fun translationToViewModel(t: Translation, graph: Graph): TranslationViewModel =
    TranslationViewModel(
        t.id,
        t.text,
        t.source.toViewModel(graph),
        t.source.toEditableText(graph),
        t.anchorStartIndex,
        t.anchorEndIndex
    )
