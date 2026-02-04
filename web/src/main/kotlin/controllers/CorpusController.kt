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
    fun allCorpusTexts(repo: GraphRepository): List<CorpusLangTextViewModel> {
        return repo.allCorpusTexts()
            .sortedBy { it.title }
            .map { it.toLangViewModel() }
    }

    @GetMapping("/{lang}")
    fun langIndexJson(repo: GraphRepository, @PathVariable lang: String): CorpusLangViewModel {
        val language = repo.resolveLanguage(lang)
        return CorpusLangViewModel(
            language.shortName,
            language.name,
            repo.corpusTextsInLanguage(language)
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
        val sourceEditableText: String
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
    fun textJson(repo: GraphRepository, @PathVariable id: Int): CorpusTextViewModel {
        return repo.resolveCorpusText(id).toViewModel(repo)
    }

    private fun CorpusText.toViewModel(repo: GraphRepository): CorpusTextViewModel {
        return CorpusTextViewModel(
            id,
            title ?: "Untitled",
            language.shortName,
            language.name,
            language.syllabographic,
            text,
            mapToLines(repo).map { line ->
                CorpusLineViewModel(line.corpusWords.map { cw ->
                    CorpusWordViewModel(cw.index,
                        cw.segmentedText,
                        cw.normalizedText,
                        cw.syllabogramSequence,
                        cw.segmentedGloss ?: "",
                        cw.contextGloss,
                        cw.word?.id, cw.word?.text,
                        cw.wordCandidates?.map { CorpusWordCandidateViewModel(it.id, it.getOrComputeGloss(repo)) },
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

    @PostMapping("/{lang}/new", consumes = ["application/json"])
    @ResponseBody
    fun newText(repo: GraphRepository, @PathVariable lang: String, @RequestBody params: CorpusTextParams): CorpusTextViewModel {
        val language = repo.resolveLanguage(lang)
        val text = repo.addCorpusText(
            params.text, params.title.nullize(), language,
            parseSourceRefs(repo, params.source), params.notes.nullize()
        )
        return text.toViewModel(repo)
    }

    @PostMapping("/text/{id}")
    fun editText(repo: GraphRepository, @PathVariable id: Int, @RequestBody params: CorpusTextParams) {
        val corpusText = repo.resolveCorpusText(id)
        corpusText.text = params.text
        corpusText.title = params.title.nullize()
        corpusText.source = parseSourceRefs(repo, params.source)
        corpusText.notes = params.notes.nullize()
    }

    data class AssociateWordParameters(
        val index: Int,
        val wordId: Int = -1,
        val contextGloss: String? = null
    )

    @PostMapping("/text/{id}/associate")
    fun associateWord(repo: GraphRepository, @PathVariable id: Int, @RequestBody params: AssociateWordParameters) {
        val corpusText = repo.resolveCorpusText(id)
        val word = repo.resolveWord(params.wordId)
        corpusText.associateWord(params.index, word, params.contextGloss)
    }

    @PostMapping("/text/{id}/lockAssociations")
    fun lockWordAssociations(repo: GraphRepository, @PathVariable id: Int) {
        val corpusText = repo.resolveCorpusText(id)
        corpusText.lockWordAssociations(repo)
    }

    data class AlternativeViewModel(val gloss: String, val wordId: Int, val ruleId: Int)

    @GetMapping("/text/{id}/alternatives/{index}")
    fun requestAlternatives(repo: GraphRepository, @PathVariable id: Int, @PathVariable index: Int): List<AlternativeViewModel> {
        val corpusText = repo.resolveCorpusText(id)
        val word = corpusText.wordByIndex(index)
        val wordText = word?.text ?: corpusText.normalizedWordTextAt(index)
        val results = Alternatives.request(repo, corpusText.language, wordText, word)
        return results.map {
            AlternativeViewModel(it.gloss, it.word.id, it.rule?.id ?: -1)
        }
    }

    data class AcceptAlternativeParameters(val index: Int, val wordId: Int, val ruleId: Int)

    @PostMapping("/text/{id}/accept")
    fun acceptAlternative(repo: GraphRepository, @PathVariable id: Int, @RequestBody params: AcceptAlternativeParameters) {
        val corpusText = repo.resolveCorpusText(id)
        val word = repo.resolveWord(params.wordId)
        val rule = if (params.ruleId == -1) null else repo.resolveRule(params.ruleId)

        Alternatives.accept(repo, corpusText, params.index, word, rule)
    }
}

fun translationToViewModel(t: Translation, repo: GraphRepository): TranslationViewModel =
    TranslationViewModel(t.id, t.text, t.source.toViewModel(repo), t.source.toEditableText(repo))
