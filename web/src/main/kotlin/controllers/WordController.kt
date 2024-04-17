package ru.yole.etymograph.web.controllers

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.yole.etymograph.*
import ru.yole.etymograph.web.*

data class WordRefViewModel(
    val id: Int,
    val text: String,
    val language: String,
    val displayLanguage: String,
    val gloss: String?,
    val homonym: Boolean,
    val reconstructed: Boolean
)

data class ParseCandidateViewModel(
    val text: String,
    val categories: String,
    val ruleNames: List<String>,
    val pos: String?,
    val wordId: Int?
)

fun Word.toRefViewModel(graph: GraphRepository) =
    WordRefViewModel(
        id, text, language.shortName,
        if (reconstructed) "pre-" + language.shortName else language.shortName,
        getOrComputeGloss(graph),
        graph.isHomonym(this),
        reconstructed || language.reconstructed
    )

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
        val notes: String?,
        val suggestedSequences: List<RuleSequenceViewModel>
    )

    data class LinkTypeViewModel(val typeId: String, val type: String, val words: List<LinkWordViewModel>)

    data class AttestationViewModel(val textId: Int, val textTitle: String, val word: String?)

    data class CompoundComponentsViewModel(
        val compoundId: Int,
        val components: List<WordRefViewModel>,
        val source: List<SourceRefViewModel>,
        val notes: String?
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
        val languageReconstructed: Boolean,
        val text: String,
        val gloss: String,
        val glossComputed: Boolean,
        val fullGloss: String?,
        val pos: String?,
        val classes: List<String>,
        val reconstructed: Boolean,
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
        val compound: Boolean,
        val suggestedDeriveSequences: List<RuleSequenceViewModel>
    )

    @GetMapping("/{graph}/word/{lang}/{text}")
    fun wordJson(@PathVariable graph: String, @PathVariable lang: String, @PathVariable text: String): List<WordViewModel> {
        val repo = graphService.resolveGraph(graph)

        val language = graphService.resolveLanguage(graph, lang)

        val words = repo.wordsByText(language, text)
        if (words.isEmpty())
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No word with text $text")

        return words.map { it.toViewModel(repo) }
    }

    @GetMapping("/{graph}/word/{lang}/{text}/{id}")
    fun singleWordJson(@PathVariable graph: String, @PathVariable lang: String, @PathVariable text: String, @PathVariable id: Int): WordViewModel {
        return graphService.resolveWord(graph, id).toViewModel(graphService.resolveGraph(graph))
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
            language.reconstructed,
            text,
            computedGloss ?: "",
            gloss == null,
            fullGloss,
            pos,
            classes,
            reconstructed,
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
                    compound.source.toViewModel(graph),
                    compound.notes
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
            graph.isCompound(this),
            graph.suggestDeriveRuleSequences(this).map {
                RuleSequenceViewModel(it.name, it.id)
            }
        )
    }

    data class AddWordParameters(
        val text: String?,
        val gloss: String?,
        val fullGloss: String?,
        val posClasses: String?,
        val reconstructed: Boolean?,
        val source: String?,
        val notes: String?
    )

    @PostMapping("/{graph}/word/{lang}", consumes = ["application/json"])
    @ResponseBody
    fun addWord(@PathVariable graph: String, @PathVariable lang: String, @RequestBody params: AddWordParameters): WordViewModel {
        val repo = graphService.resolveGraph(graph)
        val language = graphService.resolveLanguage(graph, lang)
        val text = params.text?.nullize() ?: badRequest("No word text specified")

        val (pos, classes) = parseWordClasses(language, params.posClasses)

        val word = repo.findOrAddWord(
            text, language,
            params.gloss.nullize(),
            params.fullGloss.nullize(),
            pos,
            classes,
            params.reconstructed ?: false,
            parseSourceRefs(repo, params.source),
            params.notes.nullize()
        )
        return word.toViewModel(repo)
    }

    private fun parseWordClasses(language: Language, posClasses: String?): Pair<String?, List<String>> {
        val posClassList = posClasses.orEmpty().split(' ')
        val pos = posClassList.firstOrNull().nullize()
        val classes = posClassList.drop(1)
        if (pos != null) {
            for (cls in classes) {
                val (wc, _) = language.findWordClass(cls) ?: badRequest("Unknown word class '$cls'")
                if (pos !in wc.pos) {
                    badRequest("Word class '$cls' does not apply to POS '$pos'")
                }
            }
        }
        return pos to classes
    }

    @PostMapping("/{graph}/word/{id}/update", consumes = ["application/json"])
    @ResponseBody
    fun updateWord(@PathVariable graph: String, @PathVariable id: Int, @RequestBody params: AddWordParameters): WordViewModel {
        val repo = graphService.resolveGraph(graph)
        val word = repo.wordById(id) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No word with ID $id")
        val text = params.text
        if (text != null && text != word.text) {
            repo.updateWordText(word, text)
        }

        val (pos, classes) = parseWordClasses(word.language, params.posClasses)

        word.gloss = params.gloss.nullize()
        word.fullGloss = params.fullGloss.nullize()
        word.pos = pos
        word.classes = classes
        if (params.reconstructed != null) {
            word.reconstructed = params.reconstructed
        }
        word.source = parseSourceRefs(repo, params.source)
        word.notes = params.notes.nullize()
        return word.toViewModel(repo)
    }

    @PostMapping("/{graph}/word/{id}/delete", consumes = ["application/json"])
    @ResponseBody
    fun deleteWord(@PathVariable graph: String, @PathVariable id: Int) {
        val repo = graphService.resolveGraph(graph)
        val word = repo.wordById(id) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No word with ID $id")
        repo.deleteWord(word)
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

    @GetMapping("/{graph}/word/{id}/paradigms")
    fun wordParadigms(@PathVariable graph: String, @PathVariable id: Int): WordParadigmListModel {
        val repo = graphService.resolveGraph(graph)
        val word = graphService.resolveWord(graph, id)
        val paradigmModels = repo.paradigmsForLanguage(word.language).filter { word.pos in it.pos }.map { paradigm ->
            val generatedParadigm = paradigm.generate(word, repo)
            val substitutedParadigm = generatedParadigm.map { colWords ->
                colWords.map { cellWords ->
                    cellWords?.map { alt ->
                        WordParadigmWordModel(alt.word.toRefViewModel(repo), alt.rule?.id)
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

    @PostMapping("/{graph}/word/{id}/paradigm", consumes = ["application/json"])
    fun updateParadigm(@PathVariable graph: String, @PathVariable id: Int, @RequestBody paradigm: UpdateParadigmParameters) {
        val repo = graphService.resolveGraph(graph)
        val word = graphService.resolveWord(graph, id)
        val gloss = word.glossOrNP() ?: badRequest("Trying to update paradigm for unglossed word ${word.text}")
        val derivedWordLinks = repo.getLinksTo(word).filter { it.type == Link.Derived && it.fromEntity is Word }
        for ((ruleIdAny, textAny) in paradigm.items) {
            val ruleId = ruleIdAny as Int
            val text = textAny as String
            val rule = graphService.resolveRule(graph, ruleId)
            val existingLink = derivedWordLinks.find { it.rules == listOf(rule) }
            if (existingLink != null) {
                // TODO what if word is used in corpus texts?
                repo.updateWordText(existingLink.fromEntity as Word, text)
            }
            else {
                val newGloss = rule.applyCategories(gloss)
                val newWord = repo.findOrAddWord(text, word.language, newGloss)
                repo.addLink(newWord, word, Link.Derived, listOf(rule), emptyList(), null)
                newWord.gloss = null
            }
        }
    }

    data class DeriveThroughSequenceParams(val sequenceId: Int = -1)

    @PostMapping("/{graph}/word/{id}/derive", consumes = ["application/json"])
    fun derive(@PathVariable graph: String, @PathVariable id: Int, @RequestBody params: DeriveThroughSequenceParams): WordViewModel {
        val word = graphService.resolveWord(graph, id)
        val sequence = graphService.resolveRuleSequence(graph, params.sequenceId)
        val repo = graphService.resolveGraph(graph)
        val newWord = repo.deriveThroughRuleSequence(word, sequence)
        return (newWord ?: word).toViewModel(repo)
    }

    data class WordSequenceParams(val sequence: String = "", val source: String = "")
    data class WordSequenceResults(
        val words: List<WordRefViewModel>,
        val ruleIds: List<Int>
    )

    @PostMapping("/{graph}/wordSequence")
    fun addWordSequence(@PathVariable graph: String, @RequestBody params: WordSequenceParams): WordSequenceResults {
        val stepText = params.sequence
        val repo = graphService.resolveGraph(graph)
        val source = parseSourceRefs(repo, params.source)
        val steps = stepText.split('>').map { it.trim() }
        var lastGloss: String? = null
        var lastWord: Word? = null
        val resultWords = mutableListOf<WordRefViewModel>()
        val resultRules = mutableListOf<Int>()

        for (step in steps) {
            val match = sequenceStepPattern.matchEntire(step)
                ?: badRequest("Can't parse sequence step: $step")
            val language = graphService.resolveLanguage(graph, match.groupValues[1])

            var gloss: String? = match.groupValues[3].trim(' ', '\'')
            if (!gloss.isNullOrEmpty()) {
                lastGloss = gloss
            }
            else {
                gloss = lastGloss
            }

            var reconstructed = false
            var text = match.groupValues[2]
            if (text.startsWith("*")) {
                text = text.removePrefix("*")
                if (!language.reconstructed) {
                    reconstructed = true
                }
            }

            val word = repo.findOrAddWord(text, language, gloss, source = source,
                reconstructed = reconstructed)
            resultWords.add(word.toRefViewModel(repo))
            if (lastWord != null) {
                val link = repo.addLink(word, lastWord, Link.Origin, emptyList(), source, null)
                val ruleSequence = repo.ruleSequencesForLanguage(word.language)
                    .singleOrNull { it.fromLanguage == lastWord!!.language }
                if (ruleSequence != null) {
                    repo.applyRuleSequence(link, ruleSequence)
                    resultRules.addAll(link.rules.map { it.id })
                }
            }
            lastWord = word
        }
        return WordSequenceResults(resultWords, resultRules)
    }

    companion object {
        val sequenceStepPattern = Regex("(\\w+) (\\S+)(\\s+'.+')?")
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
        link.notes,
        suggestedSequences(graph, link)
    )
}

fun buildIntermediateSteps(graph: GraphRepository, link: Link): List<String> {
    if (link.type != Link.Origin || link.rules.size <= 1) return emptyList()
    var word = link.toEntity as Word
    val result = mutableListOf<String>()
    for (rule in link.rules) {
        word = rule.apply(word, graph)
        result.add(word.text)
    }
    return result
}

fun suggestedSequences(graph: GraphRepository, link: Link): List<WordController.RuleSequenceViewModel> {
    if (link.type != Link.Origin || link.rules.isNotEmpty()) return emptyList()
    val word = link.fromEntity as Word
    val baseWord = link.toEntity as Word
    return graph.ruleSequencesForLanguage(word.language).filter { it.fromLanguage == baseWord.language }.map {
        WordController.RuleSequenceViewModel(it.name, it.id)
    }
}

fun badRequest(message: String): Nothing =
    throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)

fun notFound(message: String): Nothing =
    throw ResponseStatusException(HttpStatus.NOT_FOUND, message)

fun String?.nullize() = this?.takeIf { it.trim().isNotEmpty() }

fun ParseCandidate.toViewModel(): ParseCandidateViewModel =
    ParseCandidateViewModel(
        text,
        categories,
        rules.map { it.name },
        pos,
        word?.id
    )
