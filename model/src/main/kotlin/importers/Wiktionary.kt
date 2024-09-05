package ru.yole.etymograph.importers

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.yole.etymograph.*
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Path
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

@Serializable
data class WiktionaryPageData(val source: String)

val wiktionaryPosNames = setOf("Noun", "Adjective", "Verb", "Article", "Determiner", "Pronoun", "Adverb", "Conjunction")

abstract class WiktionarySection {
    fun parse(lines: LineBuffer) {
        var subsection: String? = null
        while (true) {
            val line = lines.peek() ?: return
            if (line.startsWith("==") && !line.startsWith("====")) {
                return
            }
            lines.next()

            if (line.startsWith("====")) {
                subsection = line.trimStart('=').trimEnd('=')
            }
            else if (subsection == null) {
                parseSectionLine(line)
            }
        }
    }

    protected abstract fun parseSectionLine(line: String)

    protected fun filterTemplates(s: String): String {
        return s.replace(templateRegex) { mr ->
            val templateData = mr.groupValues[1].split('|')
            processTemplate(templateData[0].trim(), templateData.drop(1).map { it.trim() })
        }
    }

    protected open fun processTemplate(name: String, parameters: List<String>): String {
        return ""
    }

    companion object {
        val templateRegex = Regex("\\{\\{(.+)}}")
    }
}

class WiktionaryPosSection(
    val pos: String,
    private val dictionarySettings: Map<String, List<String>>
): WiktionarySection() {
    val senses = mutableListOf<String>()
    val classes = mutableListOf<String>()

    override fun parseSectionLine(line: String) {
        for ((key, value) in dictionarySettings) {
            if (key in line) {
                classes.addAll(value)
            }
        }

        if (line.startsWith("# ")) {
            senses.add(filterTemplates(line.removePrefix("# ").replace("[[", "").replace("]]", "")).trim())
        }
    }
}

data class InheritedWordTemplate(val language: String, val word: String)

class WiktionaryEtymologySection : WiktionarySection() {
    val inheritedWords = mutableListOf<InheritedWordTemplate>()

    override fun parseSectionLine(line: String) {
        filterTemplates(line)
    }

    override fun processTemplate(name: String, parameters: List<String>): String {
        if (name == "inh") {
            inheritedWords.add(InheritedWordTemplate(parameters[1], parameters[2]))
        }
        return ""
    }
}

class WiktionaryPage(source: String) {
    val lines = LineBuffer(source)
    val posSections = mutableListOf<WiktionaryPosSection>()
    var etymologySection: WiktionaryEtymologySection? = null

    private fun seekToLanguage(language: String): Boolean {
        while (true) {
            val line = lines.next() ?: return false
            if (line == "==$language==") {
                return true
            }
        }
    }

    fun parse(language: String, dictionarySettings: Map<String, List<String>>): Boolean {
        if (!seekToLanguage(language)) return false
        var currentSection: String? = null
        while (true) {
            val line = lines.next() ?: break
            if (line.startsWith("===")) {
                currentSection = line.trimStart('=').trimEnd('=')
                if (currentSection in wiktionaryPosNames) {
                    posSections.add(WiktionaryPosSection(currentSection, dictionarySettings).apply { parse(lines) })
                }
                else if (currentSection == "Etymology") {
                    etymologySection = WiktionaryEtymologySection().apply { parse(lines) }
                }
            }
            else if (line.startsWith("==")) {
                break
            }
        }
        return true
    }
}

open class Wiktionary : Dictionary {
    private fun parseDictionarySettings(settings: String?): Map<String, List<String>> {
        if (settings == null) return emptyMap()
        return settings
            .split('\n')
            .mapNotNull { it.trim().takeIf { line -> line.isNotEmpty() } }
            .mapNotNull { parseDictionarySettingsLine(it) }
            .toMap()
    }

    private fun parseDictionarySettingsLine(line: String): Pair<String, List<String>>? {
        val keyValue = line.split(':')
        if (keyValue.size != 2) return null
        return keyValue[0].trim() to keyValue[1].split(',').map { it.trim() }
    }

    override fun lookup(repo: GraphRepository, language: Language, word: String): List<DictionaryWord> {
        val normalizedWord = word.removeDiacritics()
        val source = loadWiktionaryPageSource(language, normalizedWord) ?: return emptyList()
        val wiktionaryPage = WiktionaryPage(source)
        if (!wiktionaryPage.parse(language.name, parseDictionarySettings(language.dictionarySettings))) {
            return emptyList()
        }
        return wiktionaryPage.posSections.map { section ->
            DictionaryWord(language, section.senses.first(), section.senses.joinToString("; "),
                pos = language.pos.find { it.name == section.pos }?.abbreviation,
                classes = section.classes,
                source = "https://en.wiktionary.org/wiki/${langPrefix(language)}$normalizedWord#${language.name.replace(' ', '_')}")
        }
    }

    protected open fun loadWiktionaryPageSource(language: Language, normalizedWord: String): String? {
        val pageJson = loadWiktionaryPage(language, normalizedWord) ?: return null
        val pageData = json.decodeFromString<WiktionaryPageData>(pageJson)
        return pageData.source
    }

    protected open fun loadWiktionaryPage(language: Language, title: String): String? {
        val langPrefix = URLEncoder.encode(langPrefix(language), Charsets.UTF_8)
        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .header("User-Agent", "https://github.com/yole/etymograph")
            .uri(URI.create("https://en.wiktionary.org/w/rest.php/v1/page/$langPrefix$title"))
            .build()
        val response = client.send(request, BodyHandlers.ofString())
        if (response.statusCode() == 404) return null
        return response.body()
    }

    private fun langPrefix(language: Language): String {
        val langPrefix = if (language.reconstructed)
            "Reconstruction:${language.name}/"
        else
            ""
        return langPrefix
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

fun main() {
    val ieRepo = JsonGraphRepository.fromJson(Path.of("data/ie"))
    val oe = ieRepo.languageByShortName("OE")!!
    val wiktionary = Wiktionary()
    val result = wiktionary.lookup(ieRepo, oe, "wer")
    for (word in result) {
        println(word.fullGloss)
        println(word.pos)
        println(word.classes)
    }
}
