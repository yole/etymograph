package ru.yole.etymograph.web.controllers

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.yole.etymograph.*
import ru.yole.etymograph.importers.Wiktionary
import ru.yole.etymograph.web.*

const val explicitStressMark = 'ˈ'

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
class WordController {
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
        val sourceEditableText: String,
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
        val textWithExplicitStress: String,
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
    fun wordJson(repo: GraphRepository, @PathVariable lang: String, @PathVariable text: String): List<WordViewModel> {
        val language = repo.resolveLanguage(lang)

        val words = repo.wordsByText(language, text)
        if (words.isEmpty())
            notFound("No word with text $text")

        return words.map { it.toViewModel(repo) }
    }

    @GetMapping("/{graph}/word/{lang}/{text}/{id}")
    fun singleWordJson(repo: GraphRepository, @PathVariable lang: String, @PathVariable text: String, @PathVariable id: Int): WordViewModel {
        return repo.resolveWord(id).toViewModel(repo)
    }

    private fun Word.toViewModel(graph: GraphRepository): WordViewModel {
        val linksFrom = graph.getLinksFrom(this).groupBy { it.type }
        val linksTo = graph.getLinksTo(this).groupBy { it.type }
        val ruleLinks = (linksFrom.values.flatten() + linksTo.values.flatten())
            .filter { it.fromEntity is Rule || it.toEntity is Rule }
        val attestations = graph.findAttestations(this)

        val stressData = calculateStress(graph)
        val textWithExplicitStress = if (explicitStress && stressData != null)
            text.substring(0, stressData.index) + explicitStressMark + text.substring(stressData.index)
        else
            text

        val computedGloss = getOrComputeGloss(graph)
        return WordViewModel(
            id,
            language.shortName,
            language.name,
            language.reconstructed,
            text,
            textWithExplicitStress,
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
                    compound.source.toEditableText(graph),
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
        val pos: String?,
        val classes: String?,
        val reconstructed: Boolean?,
        val source: String?,
        val notes: String?
    )

    @PostMapping("/{graph}/word/{lang}", consumes = ["application/json"])
    @ResponseBody
    fun addWord(repo: GraphRepository, @PathVariable lang: String, @RequestBody params: AddWordParameters): WordViewModel {
        val language = repo.resolveLanguage(lang)
        val (text, stressedPhonemeIndex) = parseStress(params.text?.nullize() ?: badRequest("No word text specified"),
            repo, language)

        val classes = parseWordClasses(language, params.pos, params.classes)

        val word = repo.findOrAddWord(
            text, language,
            params.gloss.nullize(),
            params.fullGloss.nullize(),
            params.pos.nullize(),
            classes,
            params.reconstructed ?: false,
            parseSourceRefs(repo, params.source),
            params.notes.nullize()
        )
        if (stressedPhonemeIndex != null) {
            word.stressedPhonemeIndex = stressedPhonemeIndex
            word.explicitStress = true
        }
        return word.toViewModel(repo)
    }

    private fun parseStress(text: String, repo: GraphRepository, language: Language): Pair<String, Int?> {
        val explicitStressIndex = text.indexOf(explicitStressMark)
        if (explicitStressIndex >= 0) {
            val textWithoutStress = text.removeRange(explicitStressIndex, explicitStressIndex + 1)
            val phonemes = PhonemeIterator(textWithoutStress, language, repo)
            return textWithoutStress to phonemes.characterToPhonemeIndex(explicitStressIndex)
        }
        return text to null
    }

    private fun parseWordClasses(language: Language, pos: String?, classes: String?): List<String> {
        if (classes.isNullOrBlank()) return emptyList()
        val posClassList = classes.split(' ')
        for (cls in posClassList) {
            val (wc, _) = language.findWordClass(cls) ?: badRequest("Unknown word class '$cls'")
            if (pos !in wc.pos) {
                badRequest("Word class '$cls' does not apply to POS '$pos'")
            }
        }
        return posClassList
    }

    @PostMapping("/{graph}/word/{id}/update", consumes = ["application/json"])
    @ResponseBody
    fun updateWord(repo: GraphRepository, @PathVariable id: Int, @RequestBody params: AddWordParameters): WordViewModel {
        val word = repo.resolveWord(id)
        if (params.text != null) {
            val (text, stressedPhonemeIndex) = parseStress(params.text, repo, word.language)
            if (text != word.text) {
                repo.updateWordText(word, text)
            }
            if (stressedPhonemeIndex != null) {
                word.stressedPhonemeIndex = stressedPhonemeIndex
                word.explicitStress = true
            }
        }

        val classes = parseWordClasses(word.language, params.pos, params.classes)

        word.gloss = params.gloss.nullize()
        word.fullGloss = params.fullGloss.nullize()
        word.pos = params.pos.nullize()
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
    fun deleteWord(repo: GraphRepository, @PathVariable id: Int) {
        val word = repo.resolveWord(id)
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
    fun wordParadigms(repo: GraphRepository, @PathVariable id: Int): WordParadigmListModel {
        val word = repo.resolveWord(id)
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
    fun updateParadigm(repo: GraphRepository, @PathVariable id: Int, @RequestBody paradigm: UpdateParadigmParameters) {
        val word = repo.resolveWord(id)
        val gloss = word.glossOrNP() ?: badRequest("Trying to update paradigm for unglossed word ${word.text}")
        val derivedWordLinks = repo.getLinksTo(word).filter { it.type == Link.Derived && it.fromEntity is Word }
        for ((ruleIdAny, textAny) in paradigm.items) {
            val ruleId = ruleIdAny as Int
            val text = textAny as String
            val rule = repo.resolveRule(ruleId)
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
    fun derive(repo: GraphRepository, @PathVariable id: Int, @RequestBody params: DeriveThroughSequenceParams): WordViewModel {
        val word = repo.resolveWord(id)
        val sequence = repo.resolveRuleSequence(params.sequenceId)
        val newWord = repo.deriveThroughRuleSequence(word, sequence)
        return (newWord ?: word).toViewModel(repo)
    }

    data class LookupResult(val status: String?)

    @PostMapping("/{graph}/word/{id}/lookup", consumes = ["application/json"])
    fun lookup(repo: GraphRepository, @PathVariable id: Int): LookupResult {
        val word = repo.resolveWord(id)
        val dictionary = Wiktionary()
        val status = augmentWordWithDictionary(repo, dictionary, word)
        return LookupResult(status)
    }

    data class WordSequenceParams(val sequence: String = "", val source: String = "")
    data class WordSequenceResults(
        val words: List<WordRefViewModel>,
        val ruleIds: List<Int>
    )

    @PostMapping("/{graph}/wordSequence")
    fun addWordSequence(repo: GraphRepository, @RequestBody params: WordSequenceParams): WordSequenceResults {
        val stepText = params.sequence
        val source = parseSourceRefs(repo, params.source)
        val steps = stepText.split('>').map { it.trim() }
        if (steps.size < 2) {
            badRequest("Need at least one step in the sequence")
        }
        var lastGloss: String? = null
        var lastWord: Word? = null
        val resultWords = mutableListOf<WordRefViewModel>()
        val resultRules = mutableListOf<Int>()

        for (step in steps) {
            val match = sequenceStepPattern.matchEntire(step)
                ?: badRequest("Can't parse sequence step: $step")
            val language = repo.resolveLanguage(match.groupValues[1])

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
                val existingLink = repo.findLink(word, lastWord, Link.Origin)
                if (existingLink == null) {
                    val link = repo.addLink(word, lastWord, Link.Origin, emptyList(), source, null)
                    val ruleSequence = repo.ruleSequencesForLanguage(word.language)
                        .singleOrNull { it.fromLanguage == lastWord!!.language }
                    if (ruleSequence != null) {
                        repo.applyRuleSequence(link, ruleSequence)
                        resultRules.addAll(link.rules.map { it.id })
                    }
                }
                else {
                    resultRules.addAll(existingLink.rules.map { it.id })
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
    val steps = if (link.type == Link.Origin)
        buildIntermediateSteps(graph, link).map { it.result }.takeIf { it.size > 1 } ?: emptyList()
    else
        emptyList()
    return WordController.LinkWordViewModel(
        toWord.toRefViewModel(graph),
        link.rules.map { it.id },
        link.rules.map { it.name },
        steps,
        link.source.toViewModel(graph),
        link.source.toEditableText(graph),
        link.notes,
        suggestedSequences(graph, link)
    )
}

class RuleStepData(val result: String, val rule: Rule, val matchedBranches: Set<RuleBranch>)

fun buildIntermediateSteps(graph: GraphRepository, link: Link): List<RuleStepData> {
    var word = link.toEntity as Word
    val result = mutableListOf<RuleStepData>()
    val trace = RuleTrace()
    for (rule in link.rules) {
        val newWord = rule.apply(word, graph, trace)
        val matchedBranches = trace.findMatchedBranches(rule, word).ifEmpty { trace.findMatchedBranches(rule, newWord) }
        word = newWord
        result.add(RuleStepData(word.text, rule, matchedBranches))
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
