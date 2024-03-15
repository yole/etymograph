package ru.yole.etymograph.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.yole.etymograph.*

data class WordRefViewModel(
    val id: Int,
    val text: String,
    val language: String,
    val gloss: String?,
    val homonym: Boolean
)

data class ParseCandidateViewModel(
    val text: String,
    val categories: String,
    val ruleNames: List<String>,
    val pos: String?,
    val wordId: Int?
)

fun Word.toRefViewModel(graph: GraphRepository) =
    WordRefViewModel(id, text, language.shortName, getOrComputeGloss(graph), graph.isHomonym(this))

@RestController
class WordController(val graphService: GraphService) {
    data class RuleSequenceViewModel(
        val name: String,
        val id: Int
    )

    data class LinkWordViewModel(
        val word: WordRefViewModel,
        val ruleIds: List<Int>,
        val ruleNames: List<String>,
        val ruleResults: List<String>,
        val source: List<SourceRefViewModel>,
        val sourceEditableText: String,
        val suggestedSequences: List<RuleSequenceViewModel>
    )

    data class LinkTypeViewModel(val typeId: String, val type: String, val words: List<LinkWordViewModel>)

    data class AttestationViewModel(val textId: Int, val textTitle: String, val word: String?)

    data class CompoundComponentsViewModel(
        val compoundId: Int,
        val components: List<WordRefViewModel>,
        val source: List<SourceRefViewModel>
    )

    data class LinkedRuleViewModel(
        val ruleId: Int,
        val ruleName: String,
        val linkType: String,
        val source: List<SourceRefViewModel>,
        val notes: String?
    )

    data class WordViewModel(
        val id: Int,
        val language: String,
        val languageFullName: String,
        val text: String,
        val gloss: String,
        val glossComputed: Boolean,
        val fullGloss: String?,
        val pos: String?,
        val classes: List<String>,
        val source: List<SourceRefViewModel>,
        val sourceEditableText: String,
        val notes: String?,
        val parseCandidates: List<ParseCandidateViewModel>,
        val attestations: List<AttestationViewModel>,
        val linksFrom: List<LinkTypeViewModel>,
        val linksTo: List<LinkTypeViewModel>,
        val compounds: List<WordRefViewModel>,
        val components: List<CompoundComponentsViewModel>,
        val linkedRules: List<LinkedRuleViewModel>,
        val stressIndex: Int?,
        val stressLength: Int?,
        val compound: Boolean
    )

    @GetMapping("/word/{lang}/{text}")
    fun wordJson(@PathVariable lang: String, @PathVariable text: String): List<WordViewModel> {
        val graph = graphService.graph

        val language = graphService.resolveLanguage(lang)

        val words = graph.wordsByText(language, text)
        if (words.isEmpty())
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No word with text $text")

        return words.map { it.toViewModel(graph) }
    }

    @GetMapping("/word/{lang}/{text}/{id}")
    fun singleWordJson(@PathVariable lang: String, @PathVariable text: String, @PathVariable id: Int): WordViewModel {
        return graphService.resolveWord(id).toViewModel(graphService.graph)
    }

    private fun Word.toViewModel(graph: GraphRepository): WordViewModel {
        val linksFrom = graph.getLinksFrom(this).groupBy { it.type }
        val linksTo = graph.getLinksTo(this).groupBy { it.type }
        val ruleLinks = (linksFrom.values.flatten() + linksTo.values.flatten())
            .filter { it.fromEntity is Rule || it.toEntity is Rule }
        val attestations = graph.findAttestations(this)

        val stressData = calculateStress()

        val computedGloss = getOrComputeGloss(graph)
        return WordViewModel(
            id,
            language.shortName,
            language.name,
            text,
            computedGloss ?: "",
            gloss == null,
            fullGloss,
            pos,
            classes,
            source.toViewModel(graph),
            source.toEditableText(graph),
            notes,
            if (computedGloss == null && linksFrom[Link.Derived].isNullOrEmpty())
                graph.findParseCandidates(this).map { it.toViewModel() }
            else
                emptyList(),
            attestations.map { attestation ->
                AttestationViewModel(
                    attestation.corpusText.id,
                    attestation.corpusText.title ?: attestation.corpusText.text,
                    attestation.word.text.takeIf { it != text }
                )
            },
            linksFrom.mapNotNull {
                val wordLinks = it.value.filter { link -> link.toEntity is Word }
                if (wordLinks.isEmpty())
                    null
                else
                    LinkTypeViewModel(
                        it.key.id,
                        it.key.name,
                        wordLinks.map { link ->
                            linkToViewModel(link, graph, true)
                        }
                    )
            },
            linksTo.mapNotNull {
                val wordLinks = it.value.filter { link -> link.fromEntity is Word }
                if (wordLinks.isEmpty())
                    null
                else
                    LinkTypeViewModel(
                        it.key.id,
                        it.key.reverseName,
                        wordLinks.map { link ->
                            linkToViewModel(link, graph, false)
                        }
                    )
            },
            graph.findCompoundsByComponent(this).map { it.compoundWord.toRefViewModel(graph) },
            graph.findComponentsByCompound(this).map { compound ->
                CompoundComponentsViewModel(
                    compound.id,
                    compound.components.map { it.toRefViewModel(graph) },
                    compound.source.toViewModel(graph)
                )
            },
            ruleLinks.map { link ->
                val rule = link.fromEntity as? Rule ?: link.toEntity as Rule
                LinkedRuleViewModel(
                    rule.id, rule.name, link.type.id, link.source.toViewModel(graph), link.notes
                )
            },
            stressData?.index,
            stressData?.length,
            graph.isCompound(this)
        )
    }

    data class AddWordParameters(
        val text: String?,
        val gloss: String?,
        val fullGloss: String?,
        val posClasses: String?,
        val source: String?,
        val notes: String?
    )

    @PostMapping("/word/{lang}", consumes = ["application/json"])
    @ResponseBody
    fun addWord(@PathVariable lang: String, @RequestBody params: AddWordParameters): WordViewModel {
        val graph = graphService.graph
        val language = graphService.resolveLanguage(lang)
        val text = params.text?.nullize() ?: badRequest("No word text specified")

        val (pos, classes) = parseWordClasses(language, params.posClasses)

        val word = graph.findOrAddWord(
            text, language,
            params.gloss.nullize(),
            params.fullGloss.nullize(),
            pos,
            classes,
            parseSourceRefs(graph, params.source),
            params.notes.nullize()
        )
        graph.save()
        return word.toViewModel(graph)
    }

    private fun parseWordClasses(language: Language, posClasses: String?): Pair<String?, List<String>> {
        val posClassList = posClasses.orEmpty().split(' ')
        val pos = posClassList.firstOrNull().nullize()
        val classes = posClassList.drop(1)
        if (pos != null) {
            for (cls in classes) {
                val (wc, wcv) = language.findWordClass(cls)
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown word class '$cls'")
                if (pos !in wc.pos) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Word class '$cls' does not apply to POS '$pos'")
                }
            }
        }
        return pos to classes
    }

    @PostMapping("/word/{id}/update", consumes = ["application/json"])
    @ResponseBody
    fun updateWord(@PathVariable id: Int, @RequestBody params: AddWordParameters): WordViewModel {
        val graph = graphService.graph
        val word = graph.wordById(id) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No word with ID $id")
        val text = params.text
        if (text != null && text != word.text) {
            graph.updateWordText(word, text)
        }

        val (pos, classes) = parseWordClasses(word.language, params.posClasses)

        word.gloss = params.gloss.nullize()
        word.fullGloss = params.fullGloss.nullize()
        word.pos = pos
        word.classes = classes
        word.source = parseSourceRefs(graph, params.source)
        word.notes = params.notes.nullize()
        graph.save()
        return word.toViewModel(graph)
    }

    @PostMapping("/word/{id}/delete", consumes = ["application/json"])
    @ResponseBody
    fun deleteWord(@PathVariable id: Int) {
        val graph = graphService.graph
        val word = graph.wordById(id) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No word with ID $id")
        graph.deleteWord(word)
        graph.save()
    }

    data class WordParadigmWordModel(
        val word: WordRefViewModel,
        val ruleId: Int?
    )

    data class WordParadigmModel(
        val name: String,
        val rowTitles: List<String>,
        val columnTitles: List<String>,
        val cells: List<List<List<WordParadigmWordModel>>>
    )

    data class WordParadigmListModel(
        val word: String,
        val wordId: Int,
        val language: String,
        val languageFullName: String,
        val paradigms: List<WordParadigmModel>
    )

    @GetMapping("/word/{id}/paradigms")
    fun wordParadigms(@PathVariable id: Int): WordParadigmListModel {
        val graph = graphService.graph
        val word = graphService.resolveWord(id)
        val paradigmModels = graph.paradigmsForLanguage(word.language).filter { word.pos in it.pos }.map { paradigm ->
            val generatedParadigm = paradigm.generate(word, graph)
            val substitutedParadigm = generatedParadigm.map { colWords ->
                colWords.map { cellWords ->
                    cellWords?.map { alt ->
                        WordParadigmWordModel(alt.word.toRefViewModel(graph), alt.rule?.id)
                    } ?: emptyList()
                }
            }
            WordParadigmModel(
                paradigm.name,
                paradigm.rowTitles,
                paradigm.columns.map { col -> col.title },
                substitutedParadigm
            )
        }
        return WordParadigmListModel(word.text, word.id, word.language.shortName, word.language.name, paradigmModels)
    }

    data class UpdateParadigmParameters(var items: Array<Array<Any>> = emptyArray())

    @PostMapping("/word/{id}/paradigm", consumes = ["application/json"])
    fun updateParadigm(@PathVariable id: Int, @RequestBody paradigm: UpdateParadigmParameters) {
        val graph = graphService.graph
        val word = graphService.resolveWord(id)
        val gloss = word.glossOrNP() ?: badRequest("Trying to update paradigm for unglossed word ${word.text}")
        val derivedWordLinks = graph.getLinksTo(word).filter { it.type == Link.Derived && it.fromEntity is Word }
        for ((ruleIdAny, textAny) in paradigm.items) {
            val ruleId = ruleIdAny as Int
            val text = textAny as String
            val rule = graphService.resolveRule(ruleId)
            val existingLink = derivedWordLinks.find { it.rules == listOf(rule) }
            if (existingLink != null) {
                // TODO what if word is used in corpus texts?
                graph.updateWordText(existingLink.fromEntity as Word, text)
            }
            else {
                val newGloss = rule.applyCategories(gloss)
                val newWord = graphService.graph.findOrAddWord(text, word.language, newGloss)
                graph.addLink(newWord, word, Link.Derived, listOf(rule), emptyList(), null)
                newWord.gloss = null
            }
        }
        graph.save()
    }
}

fun linkToViewModel(
    link: Link,
    graph: GraphRepository,
    fromSide: Boolean
): WordController.LinkWordViewModel {
    val toWord = if (fromSide) link.toEntity as Word else link.fromEntity as Word
    return WordController.LinkWordViewModel(
        toWord.toRefViewModel(graph),
        link.rules.map { it.id },
        link.rules.map { it.name },
        buildIntermediateSteps(graph, link),
        link.source.toViewModel(graph),
        link.source.toEditableText(graph),
        suggestedSequences(graph, link)
    )
}

fun buildIntermediateSteps(graph: GraphRepository, link: Link): List<String> {
    if (link.type != Link.Derived || link.rules.size <= 1) return emptyList()
    var word = link.toEntity as Word
    val result = mutableListOf<String>()
    for (rule in link.rules) {
        word = rule.apply(word, graph)
        result.add(word.text)
    }
    return result
}

fun suggestedSequences(graph: GraphRepository, link: Link): List<WordController.RuleSequenceViewModel> {
    if (link.type != Link.Derived || link.rules.isNotEmpty()) return emptyList()
    val word = link.fromEntity as Word
    val baseWord = link.toEntity as Word
    return graph.ruleSequencesForLanguage(word.language).filter { it.fromLanguage == baseWord.language }.map {
        WordController.RuleSequenceViewModel(it.name, it.id)
    }
}

fun badRequest(message: String): Nothing =
    throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)

fun String?.nullize() = this?.takeIf { it.trim().isNotEmpty() }

fun ParseCandidate.toViewModel(): ParseCandidateViewModel =
    ParseCandidateViewModel(
        text,
        categories,
        rules.map { it.name },
        pos,
        word?.id
    )
