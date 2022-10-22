package ru.yole.etymograph

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import ru.yole.etymograph.parser.parseGraph
import java.io.File
import java.io.InputStream

class PersistentWord(val id: Int, text: String, language: Language, gloss: String?, source: String?, notes: String?)
    : Word(text, language, gloss, source, notes)

class PersistentRule(val id: Int, fromLanguage: Language, toLanguage: Language, fromPattern: String, toPattern: String,
                     addedCategories: String?, source: String?, notes: String?
) : Rule(fromLanguage, toLanguage,
    fromPattern,
    toPattern, addedCategories, source, notes
)

class PersistentLink(val id: Int, fromWord: Word, toWord: Word, type: LinkType, rule: Rule?, source: String?,
                     notes: String?
): Link(fromWord, toWord, type,
    rule,
    source, notes
)

@Serializable
data class LanguageData(val name: String, val shortName: String)

@Serializable
data class WordData(val id: Int, val text: String, @SerialName("lang") val languageShortName: String, val gloss: String?, val source: String? = null, val notes: String? = null)

@Serializable
data class RuleData(
    val id: Int,
    @SerialName("fromLang") val fromLanguageShortName: String,
    @SerialName("toLang") val toLanguageShortName: String,
    val fromPattern: String, val toPattern: String,
    val addedCategories: String?,
    val source: String? = null,
    val notes: String? = null
)

@Serializable
data class LinkData(
    val id: Int,
    @SerialName("from") val fromWordId: Int,
    @SerialName("to") val toWordId: Int,
    val type: String,
    val ruleId: Int = -1,
    val source: String? = null,
    val notes: String? = null
)

@Serializable
data class CorpusTextData(
    val id: Int, val text: String, val title: String?,
    @SerialName("lang") val languageShortName: String,
    val wordIds: List<Int>,
    val source: String? = null,
    val notes: String? = null
)

@Serializable
data class GraphRepositoryData(
    val languages: List<LanguageData>,
    val words: List<WordData>,
    val rules: List<RuleData>,
    val links: List<LinkData>,
    val corpusTexts: List<CorpusTextData>
)

class JsonGraphRepository : InMemoryGraphRepository() {
    private val allWords = mutableListOf<PersistentWord>()
    private val allRules = mutableListOf<PersistentRule>()
    private val allLinks = mutableListOf<PersistentLink>()

    override fun createWord(text: String, language: Language, gloss: String?, source: String?, notes: String?): Word {
        return PersistentWord(allWords.size, text, language, gloss, source, notes).also {
            allWords.add(it)
        }
    }

    override fun createRule(
        fromLanguage: Language,
        toLanguage: Language,
        fromPattern: String,
        toPattern: String,
        addedCategories: String?,
        source: String?,
        notes: String?
    ): Rule {
        return PersistentRule(allRules.size , fromLanguage, toLanguage, fromPattern, toPattern, addedCategories, source, notes).also {
            allRules.add(it)
        }
    }

    override fun createLink(
        fromWord: Word,
        toWord: Word,
        type: LinkType,
        rule: Rule?,
        source: String?,
        notes: String?
    ): Link {
        return PersistentLink(allLinks.size, fromWord, toWord, type, rule, source, notes).also {
            allLinks.add(it)
        }
    }

    fun toJson(): String {
        val repoData = createGraphRepositoryData()
        return Json { prettyPrint = true }.encodeToString(repoData)
    }

    private fun createGraphRepositoryData(): GraphRepositoryData {
        return GraphRepositoryData(
            languages.values.map { LanguageData(it.name, it.shortName) },
            allWords.map { WordData(it.id, it.text, it.language.shortName, it.gloss, it.source, it.notes) },
            allRules.map { RuleData(it.id, it.fromLanguage.shortName, it.toLanguage.shortName, it.fromPattern, it.toPattern, it.addedCategories, it.source, it.notes) },
            allLinks.map {
                LinkData(
                    it.id, (it.fromWord as PersistentWord).id, (it.toWord as PersistentWord).id,
                    it.type.id, (it.rule as PersistentRule?)?.id ?: -1, it.source, it.notes
                )
            },
            corpus.map {
                CorpusTextData(
                    it.id, it.text, it.title, it.language.shortName,
                    it.words.map { (it as PersistentWord).id }, it.source, it.notes
                )
            }
        )
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun fromJson(stream: InputStream): JsonGraphRepository {
            val data = Json.decodeFromStream<GraphRepositoryData>(stream)
            val result = JsonGraphRepository()
            for (language in data.languages) {
                result.addLanguage(Language(language.name, language.shortName))
            }
            for (word in data.words) {
                result.addWord(word.text, result.languageByShortName(word.languageShortName), word.gloss, word.source, word.notes)
            }
            for (rule in data.rules) {
                result.addRule(
                    result.languageByShortName(rule.fromLanguageShortName),
                    result.languageByShortName(rule.toLanguageShortName),
                    rule.fromPattern, rule.toPattern, rule.addedCategories, rule.source, rule.notes
                )
            }
            for (link in data.links) {
                result.addLink(
                    result.allWords[link.fromWordId],
                    result.allWords[link.toWordId],
                    Link.allLinkTypes.first { it.id == link.type },
                    link.ruleId.takeIf { it >= 0 }?.let { result.allRules[it] },
                    link.source,
                    link.notes
                )
            }
            for (corpusText in data.corpusTexts) {
                result.addCorpusText(
                    corpusText.text,
                    corpusText.title,
                    result.languageByShortName(corpusText.languageShortName),
                    corpusText.wordIds.map { result.allWords[it] },
                    corpusText.source,
                    corpusText.notes
                )
            }
            return result
        }
    }
}

fun main() {
    val repo = JsonGraphRepository()
    File("web/src/main/resources/jrrt.txt").inputStream().use {
        parseGraph(it, repo)
        File("jrrt.json").writeText(repo.toJson())
    }
}
